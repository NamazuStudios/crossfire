package dev.getelements.elements.crossfire.protocol.v1;

import dev.getelements.elements.crossfire.api.model.control.ControlMessage;
import dev.getelements.elements.crossfire.api.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.api.model.error.UnexpectedMessageException;
import dev.getelements.elements.crossfire.api.model.signal.BroadcastSignal;
import dev.getelements.elements.crossfire.api.model.signal.DirectSignal;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.crossfire.protocol.SignalingHandler;
import dev.getelements.elements.crossfire.service.ControlService;
import dev.getelements.elements.crossfire.service.MatchSignalingService;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.model.exception.ForbiddenException;
import jakarta.inject.Inject;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static dev.getelements.elements.crossfire.protocol.v1.V1SignalingState.create;
import static dev.getelements.elements.crossfire.protocol.SignalingPhase.TERMINATED;

public class V1SignalingHandler implements SignalingHandler {

    private static final Logger logger = LoggerFactory.getLogger(V1SignalingHandler.class);

    private void joinSignaling(final String matchId, final String profileId) {
        if (getMatchSignalingService().join(matchId, profileId)) {
            logger.debug("Joined match {}", matchId);
        } else {
            logger.debug("Already in match {}", matchId);
        }
    }

    private ControlService controlService;

    private MatchSignalingService matchSignalingService;

    private MultiMatchDao multiMatchDao;

    private final AtomicReference<V1SignalingState> state = new AtomicReference<>(create());

    @Override
    public void start(
            final ProtocolMessageHandler handler,
            final Session session,
            final ProtocolMessageHandler.MultiMatchRecord match,
            final ProtocolMessageHandler.AuthRecord auth) {

        final var matchId = match.getId();
        final var profileId = auth.profile().getId();

        try {
            joinSignaling(matchId, profileId);
        } catch (final ForbiddenException notMemberAtStart) {
            // crossfire#32: the handshake already admitted this profile — the handshake
            // response (MATCHED/CREATED) is written only after the handshake's join
            // transaction commits — so a "not part of match" here means the membership
            // read raced its own commit (the elements#207 snapshot wiring) and lost.
            // The start() flow runs on the same thread that just committed, which is
            // exactly when that staleness bites. Self-heal: re-add the profile through
            // this Element's own (non transaction scoped) MultiMatchDao — the same
            // immune-write path MatchMetadataWriter uses — and retry once. Genuine
            // admission failures cannot reach this point (they are rejected in the
            // handshake); post-admission enforcement (kick/ban) rides the REST
            // tripwire and the #24 admission listener.
            logger.warn(
                    "Signaling start could not find profile {} in match {} (elements#207 stale snapshot); re-adding membership.",
                    profileId, matchId, notMemberAtStart);
            getMultiMatchDao().addProfile(matchId, auth.profile());
            joinSignaling(matchId, profileId);
        }

        final var subscription = getMatchSignalingService().connect(
                matchId,
                profileId,
                m -> session.getAsyncRemote().sendObject(m),
                e -> logger.error("Error in signaling for match {} and profile {}", matchId, profileId, e)
        );

        try {

            final var updated = state.updateAndGet(s -> s.start(subscription, match, auth));
            logger.debug("Started signaling for match {} and profile {}", matchId, profileId);

            switch (updated.phase()) {
                case TERMINATED -> subscription.unsubscribe();
                default -> logger.debug("Successfully started signaling for match {} and profile {}", matchId, profileId);
            }

        } catch (Exception ex) {
            subscription.unsubscribe();
            logger.error("Caught error while starting signaling for match {} and profile {}", matchId, profileId, ex);
            throw ex;
        }

    }

    @Override
    public void stop(
            final ProtocolMessageHandler handler,
            final Session session) {

        final var updated = state.updateAndGet(V1SignalingState::terminate);
        logger.debug("Stopped signaling.");

        if (updated.subscription() != null) {
            logger.warn("Signaling in unexpected state {}. Cleaning up subscription.", updated.phase());
            updated.subscription().unsubscribe();
        }

    }

    @Override
    public void onMessage(
            final ProtocolMessageHandler handler,
            final Session session,
            final BroadcastSignal signal) {
        onMessage(handler, session, signal, 0);
    }

    private void onMessage(
            final ProtocolMessageHandler handler,
            final Session session,
            final BroadcastSignal signal,
            final int deferrals) {

        if (deferUntilSignalingStarted(handler, () -> onMessage(handler, session, signal, deferrals + 1), deferrals)) {
            return;
        }

        final var state = this.state.get();
        checkAuth(state, signal::getProfileId);

        switch (state.phase()) {
            case SIGNALING -> getMatchSignalingService().send(state.match().getId(), signal);
            case TERMINATED -> logger.debug("Dropping message. Signaling terminated.");
            default -> throw new ProtocolStateException("Unexpected state: " + state.phase());
        }

    }

    @Override
    public void onMessageDirect(
            final ProtocolMessageHandler handler,
            final Session session,
            final DirectSignal signal) {
        onMessageDirect(handler, session, signal, 0);
    }

    private void onMessageDirect(
            final ProtocolMessageHandler handler,
            final Session session,
            final DirectSignal signal,
            final int deferrals) {

        if (deferUntilSignalingStarted(handler, () -> onMessageDirect(handler, session, signal, deferrals + 1), deferrals)) {
            return;
        }

        final var state = this.state.get();
        checkAuth(state, signal::getProfileId);
        checkBounce(state, signal::getRecipientProfileId);

        switch (state.phase()) {
            case SIGNALING -> getMatchSignalingService().send(state.match().getId(), signal);
            case TERMINATED -> logger.debug("Dropping message. Signaling terminated.");
            default -> throw new ProtocolStateException("Unexpected state: " + state.phase());
        }

    }

    @Override
    public void onMessageControl(
            final ProtocolMessageHandler handler,
            final Session session,
            final ControlMessage message) {
        onMessageControl(handler, session, message, 0);
    }

    private void onMessageControl(
            final ProtocolMessageHandler handler,
            final Session session,
            final ControlMessage message,
            final int deferrals) {

        if (deferUntilSignalingStarted(handler, () -> onMessageControl(handler, session, message, deferrals + 1), deferrals)) {
            return;
        }

        final var state = this.state.get();
        checkAuth(state, message::getProfileId);

        switch (state.phase()) {
            case SIGNALING -> {
                final var action = getControlService().process(state.match(), state.auth(), message);
                process(action, session, message);
            }
            case TERMINATED -> logger.debug("Dropping message. Signaling terminated.");
            default -> throw new ProtocolStateException("Unexpected state: " + state.phase());
        }

    }

    // The handshake response (MATCHED/CREATED) is written to the client's socket BEFORE start()
    // finishes joining the signaling roster: V1ProtocolMessageHandler writes it asynchronously and
    // invokes start() in the send callback, which then performs two MongoDB reads (join + connect).
    // A client that fires its first signal the instant MATCHED arrives can therefore land here
    // while this handler's auth record is still null, and checkAuth() used to NPE and kill the
    // session (100% reproducible with an immediate post-MATCHED broadcast on 1.1.6-rc-1). Defer
    // such messages briefly on the connection's executor instead — until start() completes or the
    // session terminates — rather than NPE-ing the session or silently dropping the signal.
    private boolean deferUntilSignalingStarted(
            final ProtocolMessageHandler handler,
            final Runnable dispatch,
            final int deferrals) {

        final var state = this.state.get();

        if (state.auth() != null || state.phase() == TERMINATED) {
            return false;
        }

        if (deferrals >= MAX_SIGNALING_START_DEFERRALS) {
            logger.warn(
                    "Signaling start still pending after {}ms ({} deferrals); dropping message.",
                    MAX_SIGNALING_START_DEFERRALS * SIGNALING_START_DEFERRAL_MILLIS,
                    deferrals
            );
            return true;
        }

        logger.debug("Signaling start pending; deferring message (deferral {}/{}).",
                deferrals + 1, MAX_SIGNALING_START_DEFERRALS);

        try {
            handler.submit(() -> {
                try {
                    Thread.sleep(SIGNALING_START_DEFERRAL_MILLIS);
                } catch (final InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                dispatch.run();
            });
        } catch (final Exception ex) {
            logger.debug("Could not defer message pending signaling start: {}", ex.getMessage());
        }

        return true;

    }

    private static final int MAX_SIGNALING_START_DEFERRALS = 200;
    private static final long SIGNALING_START_DEFERRAL_MILLIS = 10L;

    private void process(final ControlService.Result action,
                         final Session session,
                         final ControlMessage message) {
        try {
            switch (action) {
                case PERSIST_CONNECTION -> logger.debug("Persisting connection: {}", message.getType());
                case CLOSE_CONNECTION -> {
                    logger.debug("Closing connection: {}", message.getType());
                    session.close();
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void checkAuth(final V1SignalingState state, final Supplier<String> profileIdSupplier) {

        final var authProfileId = state.auth().profile().getId();
        final var senderProfileId = profileIdSupplier.get();

        if (!Objects.equals(authProfileId, senderProfileId)) {
            throw new ForbiddenException();
        }

    }

    private void checkBounce(final V1SignalingState state, final Supplier<String> profileIdSupplier) {

        final var profileId = profileIdSupplier.get();
        final var authProfileId = state.auth().profile().getId();

        if (Objects.equals(authProfileId, profileId)) {
            throw new UnexpectedMessageException("Cannot send a signal to the same client.");
        }

    }

    public ControlService getControlService() {
        return controlService;
    }

    @Inject
    public void setControlService(final ControlService controlService) {
        this.controlService = controlService;
    }

    public MatchSignalingService getMatchSignalingService() {
        return matchSignalingService;
    }

    @Inject
    public void setMatchSignalingService(final MatchSignalingService matchSignalingService) {
        this.matchSignalingService = matchSignalingService;
    }

    public MultiMatchDao getMultiMatchDao() {
        return multiMatchDao;
    }

    @Inject
    public void setMultiMatchDao(final MultiMatchDao multiMatchDao) {
        this.multiMatchDao = multiMatchDao;
    }

}
