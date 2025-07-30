package dev.getelements.elements.crossfire.protocol.v10;

import dev.getelements.elements.crossfire.api.MatchmakingAlgorithm;
import dev.getelements.elements.crossfire.model.configuration.CrossfireConfiguration;
import dev.getelements.elements.crossfire.model.error.UnexpectedMessageException;
import dev.getelements.elements.crossfire.model.handshake.FindHandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.JoinHandshakeRequest;
import dev.getelements.elements.crossfire.protocol.HandshakeHandler;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler.AuthRecord;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler.MultiMatchRecord;
import dev.getelements.elements.sdk.ElementRegistrySupplier;
import dev.getelements.elements.sdk.dao.ApplicationConfigurationDao;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.dao.ProfileDao;
import dev.getelements.elements.sdk.dao.Transaction;
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

    private AtomicReference<MatchmakingAlgorithm.PendingMatch> pendingMatch = new AtomicReference<>();

    @Override
    public void start(final ProtocolMessageHandler handler,
                      final Session session) {

    }

    @Override
    public void stop(final ProtocolMessageHandler handler,
                     final Session session) {

    }

    @Override
    public void onMessage(
            final ProtocolMessageHandler handler,
            final Session session,
            final HandshakeRequest request) {
        final var type = request.getType();
        switch (request.getType()) {
            case FIND -> onFindMessage(handler, session, (FindHandshakeRequest) request);
            case JOIN -> onJoinMessage(handler, session, (JoinHandshakeRequest) request);
            default -> throw new UnexpectedMessageException("Unsupported handshake request type: " + request.getType());
        }
    }

    private void onFindMessage(
            final ProtocolMessageHandler handler,
            final Session wsSession,
            final FindHandshakeRequest request) {
        auth(handler, request, (auth) -> {

            final var application = auth.profile().getApplication();

            final var applicationConfiguration = getApplicationConfigurationDao().getApplicationConfiguration(
                    MatchmakingApplicationConfiguration.class,
                    application.getId(),
                    request.getConfiguration()
            );

            final var crossfireConfiguration = CrossfireConfiguration
                    .from(applicationConfiguration)
                    .filter(c -> validate(c, applicationConfiguration));

            final var mRequest = new V10MatchRequest(
                    handler,
                    auth.profile(),
                    applicationConfiguration
            );

            final var algorithm = crossfireConfiguration
                    .filter(c -> c.getMatchmaker() != null)
                    .map(this::algorithmFromConfiguration)
                    .orElseGet(this::getDefaultMatchmakingAlgorithm);

            final var pending = algorithm.start(mRequest);

        });
    }

    private boolean validate(final CrossfireConfiguration crossfireConfiguration,
                             final MatchmakingApplicationConfiguration applicationConfiguration) {

        final var violations = getValidator().validate(crossfireConfiguration);

        if (violations.isEmpty()) {
            logger.debug("Valid Crossfire configuration found for application configuration {}",
                    applicationConfiguration.getId()
            );
            return true;
        } else {
            logger.error("Invalid Crossfire found for application {}.\nViolations:\n{}",
                    applicationConfiguration.getId(),
                    violations.stream()
                            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                            .reduce("", (a, b) -> a + "\n" + b)
            );
            return false;
        }

    }

    private MatchmakingAlgorithm algorithmFromConfiguration(final CrossfireConfiguration configuration) {

        final var matchmaker = configuration.getMatchmaker();

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
                .ofNullable(configuration.getMatchmaker().getServiceType())
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

    private void onJoinMessage(
            final ProtocolMessageHandler handler,
            final Session session,
            final JoinHandshakeRequest request) {
        auth(handler, request, (auth) -> {

            final var match = getMultiMatchDao().getMultiMatch(request.getMatchId());
            final var profiles = getMultiMatchDao().getProfiles(match.getId());
            final var found = profiles.stream().anyMatch(p -> p.getId().equals(auth.profile().getId()));

            if (!found) {
                logger.debug("Profile {} not found in match {}", auth.profile().getId(), match.getId());
                throw new ForbiddenException("Profile not found in match");
            }

            final var applicationConfiguration = match.getConfiguration();

            final var crossfireConfiguration = CrossfireConfiguration
                    .from(applicationConfiguration)
                    .filter(c -> validate(c, applicationConfiguration));

            final var multiMatchRecord = new MultiMatchRecord(
                    match,
                    crossfireConfiguration.orElse(null)
            );

            handler.matched(multiMatchRecord);

        });
    }

    private void auth(final ProtocolMessageHandler handler,
                      final HandshakeRequest request,
                      final Consumer<AuthRecord> onAuthenticated) {
        handler.submit(() -> {

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
            handler.authenticated(record);
            onAuthenticated.accept(record);

        });

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
