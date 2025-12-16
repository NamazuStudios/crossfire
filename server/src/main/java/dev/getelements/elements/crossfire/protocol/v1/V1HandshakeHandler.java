package dev.getelements.elements.crossfire.protocol.v1;

import dev.getelements.elements.crossfire.api.MatchHandle;
import dev.getelements.elements.crossfire.api.MatchmakingAlgorithm;
import dev.getelements.elements.crossfire.api.model.Version;
import dev.getelements.elements.crossfire.api.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.api.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.protocol.HandshakeHandler;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.sdk.ElementRegistrySupplier;
import dev.getelements.elements.sdk.dao.ApplicationConfigurationDao;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.dao.ProfileDao;
import dev.getelements.elements.sdk.dao.Transaction;
import dev.getelements.elements.sdk.model.application.ElementServiceReference;
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

import static dev.getelements.elements.crossfire.protocol.HandshakePhase.MATCHING;
import static dev.getelements.elements.crossfire.protocol.HandshakePhase.TERMINATED;
import static dev.getelements.elements.sdk.service.Constants.UNSCOPED;

public abstract class V1HandshakeHandler implements HandshakeHandler {

    private static final Logger logger = LoggerFactory.getLogger(V1HandshakeHandler.class);

    protected final AtomicReference<V1HandshakeStateRecord> state = new AtomicReference<>(initStateRecord());

    private Validator validator;

    private MultiMatchDao multiMatchDao;

    private ApplicationConfigurationDao applicationConfigurationDao;

    private ProfileDao profileDao;

    private SessionService sessionService;

    private Provider<Transaction> transactionProvider;

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

        final var state = this.state.updateAndGet(V1HandshakeStateRecord::terminate);

        if (MATCHING.equals(state.phase())) {
            state.leave();
        }

    }
    protected abstract V1HandshakeStateRecord initStateRecord();

    protected abstract MatchmakingAlgorithm<?, ?> getDefaultMatchmakingAlgorithm();

    protected MatchmakingAlgorithm<?, ?> algorithmFromConfiguration(final ElementServiceReference matchmaker) {

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

    protected void startMatching(final MatchHandle<?> matchHandle) {

        final var state = this.state.updateAndGet(s -> s.matching(matchHandle));

        switch (state.phase()) {
            case MATCHING -> matchHandle.startMatcing();
            case TERMINATED -> state.leave();
            default -> throw new ProtocolStateException("Unexpected handshake state: " + state.phase());
        }

    }

    protected void auth(final ProtocolMessageHandler handler,
                        final HandshakeRequest request,
                        final Consumer<ProtocolMessageHandler.AuthRecord> onAuthenticated) {

        final var state = this.state.updateAndGet(V1HandshakeStateRecord::authenticating);

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
        }

    }

    private void doAuthenticate(final ProtocolMessageHandler handler,
                                final HandshakeRequest request,
                                final Consumer<ProtocolMessageHandler.AuthRecord> onAuthenticated) {

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

        final var record = new ProtocolMessageHandler.AuthRecord(profile, session);
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

}
