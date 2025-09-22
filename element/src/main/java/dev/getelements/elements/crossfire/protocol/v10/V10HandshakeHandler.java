package dev.getelements.elements.crossfire.protocol.v10;

import dev.getelements.elements.crossfire.api.MatchHandle;
import dev.getelements.elements.crossfire.api.MatchmakingAlgorithm;
import dev.getelements.elements.crossfire.model.error.MultiMatchConfigurationNotFoundException;
import dev.getelements.elements.crossfire.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.model.error.UnexpectedMessageException;
import dev.getelements.elements.crossfire.model.handshake.FindHandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.JoinHandshakeRequest;
import dev.getelements.elements.crossfire.protocol.HandshakeHandler;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler.AuthRecord;
import dev.getelements.elements.sdk.ElementRegistrySupplier;
import dev.getelements.elements.sdk.dao.ApplicationConfigurationDao;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.dao.ProfileDao;
import dev.getelements.elements.sdk.dao.Transaction;
import dev.getelements.elements.sdk.model.application.ElementServiceReference;
import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
import dev.getelements.elements.sdk.model.exception.ForbiddenException;
import dev.getelements.elements.sdk.model.profile.Profile;
import dev.getelements.elements.sdk.service.auth.SessionService;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.validation.Validator;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static dev.getelements.elements.crossfire.protocol.HandshakePhase.TERMINATED;
import static dev.getelements.elements.sdk.service.Constants.UNSCOPED;

public class V10HandshakeHandler implements HandshakeHandler {

    private static final Logger logger = LoggerFactory.getLogger(V10HandshakeHandler.class);

    private Validator validator;

    private MultiMatchDao multiMatchDao;

    private ApplicationConfigurationDao applicationConfigurationDao;

    private ProfileDao profileDao;

    private SessionService sessionService;

    private Provider<Transaction> transactionProvider;

    private MatchmakingAlgorithm defaultMatchmakingAlgorithm;

    private final AtomicReference<V10HandshakeStateRecord> state = new AtomicReference<>(V10HandshakeStateRecord.create());

    @Override
    public void start(final ProtocolMessageHandler handler,
                      final Session session) {

        final var state = this.state.updateAndGet(s -> s.start(session));

        if (TERMINATED.equals(state.phase())) {
            state.leave();
        }

    }

    @Override
    public void stop(final ProtocolMessageHandler handler,
                     final Session session) {
        final var state = this.state.updateAndGet(V10HandshakeStateRecord::terminate);
        state.leave();
    }

    @Override
    public void onMessage(
            final ProtocolMessageHandler handler,
            final Session session,
            final HandshakeRequest request) {
        final var type = request.getType();
        switch (type) {
            case FIND -> onFindMessage(handler, session, (FindHandshakeRequest) request);
            case JOIN -> onJoinMessage(handler, session, (JoinHandshakeRequest) request);
            default -> throw new UnexpectedMessageException("Unsupported handshake request type: " + request.getType());
        }
    }

    private void onFindMessage(
            final ProtocolMessageHandler handler,
            final Session session,
            final FindHandshakeRequest request) {
        auth(handler, request, (auth) -> {

            final var application = auth.profile().getApplication();

            final var applicationConfigurationOptional = getApplicationConfigurationDao().findApplicationConfiguration(
                    MatchmakingApplicationConfiguration.class,
                    application.getId(),
                    request.getConfiguration()
            );

            if(applicationConfigurationOptional.isEmpty()) {
                throw new MultiMatchConfigurationNotFoundException("Matchmaking Configuration with name " + request.getConfiguration() + " not found.");
            }

            final var applicationConfiguration = applicationConfigurationOptional.get();

            final var matchRequest = new V10MatchRequest<>(
                    handler,
                    state,
                    auth.profile(),
                    request,
                    applicationConfiguration
            );

            final var algorithm = Optional
                    .ofNullable(applicationConfiguration.getMatchmaker())
                    .map(this::algorithmFromConfiguration)
                    .orElseGet(this::getDefaultMatchmakingAlgorithm);

            final var pending = algorithm.find(matchRequest);
            doStartMatching(pending);

        });
    }

    private void onJoinMessage(
            final ProtocolMessageHandler handler,
            final Session session,
            final JoinHandshakeRequest request) {
        auth(handler, request, (auth) -> {

            final var match = getMultiMatchDao().getMultiMatch(request.getMatchId());
            final var applicationConfiguration = match.getConfiguration();

            final var matchRequest = new V10MatchRequest<>(
                    handler,
                    state,
                    auth.profile(),
                    request,
                    applicationConfiguration
            );

            final var algorithm = Optional
                    .ofNullable(applicationConfiguration.getMatchmaker())
                    .map(this::algorithmFromConfiguration)
                    .orElseGet(this::getDefaultMatchmakingAlgorithm);

            final var pending = algorithm.join(matchRequest);
            doStartMatching(pending);

        });
    }

    private MatchmakingAlgorithm algorithmFromConfiguration(final ElementServiceReference matchmaker) {

        final var elementOptional = ElementRegistrySupplier
                .getElementLocal(getClass())
                .get()
                .stream()
                .filter(e -> e.getElementRecord().definition().name().endsWith(matchmaker.getElementName()))
                .findFirst();

        if (elementOptional.isPresent()) {
            logger.warn("Unable to find element with name {}.", matchmaker.getElementName());
            return getDefaultMatchmakingAlgorithm();
        }

        final var element = elementOptional.get();

        final Class<? extends MatchmakingAlgorithm> type = (Class<? extends MatchmakingAlgorithm>) Optional
                .ofNullable(matchmaker.getServiceType())
                .map(t -> {
                    try {
                        return element.getElementRecord()
                                .classLoader()
                                .loadClass(t);
                    } catch (ClassNotFoundException e) {
                        logger.error("Unable to load class {} for matchmaker service type.", t, e);
                        return MatchmakingAlgorithm.class;
                    }
                })
                .filter(t -> {
                    if (MatchmakingAlgorithm.class.isAssignableFrom(t)) {
                        return true;
                    } else {
                        logger.error("Matchmaking algoirthm {} is not assignable from class {}. Using default.",
                                MatchmakingAlgorithm.class
                        );
                        return false;
                    }
                })
                .orElse(MatchmakingAlgorithm.class);

        final var name = matchmaker.getServiceName();

        return name == null
                ? element.getServiceLocator().getInstance(type)
                : element.getServiceLocator().getInstance(type, name);

    }

    private void doStartMatching(final MatchHandle<?> matchHandle) {

        final var state = this.state.updateAndGet(s -> s.matching(matchHandle));

        switch (state.phase()) {
            case MATCHING -> matchHandle.start();
            case TERMINATED -> state.leave();
            default -> throw new ProtocolStateException("Unexpected handshake state: " + state.phase());
        }

    }

    private void auth(final ProtocolMessageHandler handler,
                      final HandshakeRequest request,
                      final Consumer<AuthRecord> onAuthenticated) {

        final var state = this.state.updateAndGet(V10HandshakeStateRecord::authenticating);

        switch (state.phase()) {
            case TERMINATED -> {
                logger.info("Handshake already terminated, cannot authenticate.");
                state.leave();
            }
            case AUTHENTICATING -> handler.submit(() -> doAuthenticate(handler, request, onAuthenticated));
            default -> {
                logger.error("Unexpected handshake state: {}. Cannot authenticate.", state.phase());
                state.leave();
                throw new ProtocolStateException("Invalid handshake state: " + state);
            }
        };
    }

    private void doAuthenticate(final ProtocolMessageHandler handler,
                                final HandshakeRequest request,
                                final Consumer<AuthRecord> onAuthenticated) {

        final var session = getSessionService().checkAndRefreshSessionIfNecessary(request.getSessionKey());

        Profile profile = session.getProfile();

        if (profile == null) {
            logger.debug("No profile found for session. Attempting to find profile by session ID.");
        }

        if (request.getProfileId() != null) {
            logger.debug("Using profile ID from request: {}", request.getProfileId());
            profile = getProfileDao().getActiveProfile(request.getProfileId());
        } else {
            logger.debug("No profile ID in request, using session profile (if available).");
        }

        if (profile == null) {
            logger.debug("Unable to find profile for session and request.");
            throw new ForbiddenException();
        }

        if (!session.getUser().getId().equals(profile.getUser().getId())) {
            logger.debug("User does not have access to session. Attempting to find profile by session ID.");
            throw new ForbiddenException();
        }

        final var record = new AuthRecord(profile, session);
        final var updated = this.state.updateAndGet(s -> s.authenticated(record));

        switch (updated.phase()) {
            case TERMINATED -> {
                logger.info("Handshake already terminated, cannot authenticate.");
                updated.leave();
            }
            case AUTHENTICATED -> {
                handler.authenticated(record);
                handler.submit(() -> onAuthenticated.accept(record));
            }
            default -> {
                logger.error("Handshake not yet completed, attempting to authenticate.");
                updated.leave();
                throw new ProtocolStateException("Invalid handshake state: " + updated.phase());
            }
        }

    }

    public Validator getValidator() {
        return validator;
    }

    @Inject
    public void setValidator(Validator validator) {
        this.validator = validator;
    }

    public MultiMatchDao getMultiMatchDao() {
        return multiMatchDao;
    }

    @Inject
    public void setMultiMatchDao(MultiMatchDao multiMatchDao) {
        this.multiMatchDao = multiMatchDao;
    }

    public ProfileDao getProfileDao() {
        return profileDao;
    }

    @Inject
    public void setProfileDao(ProfileDao profileDao) {
        this.profileDao = profileDao;
    }

    public SessionService getSessionService() {
        return sessionService;
    }

    @Inject
    public void setSessionService(@Named(UNSCOPED) SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public ApplicationConfigurationDao getApplicationConfigurationDao() {
        return applicationConfigurationDao;
    }

    @Inject
    public void setApplicationConfigurationDao(ApplicationConfigurationDao applicationConfigurationDao) {
        this.applicationConfigurationDao = applicationConfigurationDao;
    }

    public Provider<Transaction> getTransactionProvider() {
        return transactionProvider;
    }

    @Inject
    public void setTransactionProvider(Provider<Transaction> transactionProvider) {
        this.transactionProvider = transactionProvider;
    }

    public MatchmakingAlgorithm getDefaultMatchmakingAlgorithm() {
        return defaultMatchmakingAlgorithm;
    }

    @Inject
    public void setDefaultMatchmakingAlgorithm(MatchmakingAlgorithm defaultMatchmakingAlgorithm) {
        this.defaultMatchmakingAlgorithm = defaultMatchmakingAlgorithm;
    }

}
