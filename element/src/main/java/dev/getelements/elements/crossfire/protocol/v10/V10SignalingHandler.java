package dev.getelements.elements.crossfire.protocol.v10;

import dev.getelements.elements.crossfire.model.control.ControlMessage;
import dev.getelements.elements.crossfire.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.model.signal.BroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.DirectSignal;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.crossfire.protocol.SignalingHandler;
import dev.getelements.elements.crossfire.service.ControlService;
import dev.getelements.elements.crossfire.service.MatchSignalingService;
import dev.getelements.elements.sdk.model.exception.ForbiddenException;
import jakarta.inject.Inject;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static dev.getelements.elements.crossfire.protocol.v10.V10SignalingState.create;

public class V10SignalingHandler implements SignalingHandler {

    private static final Logger logger = LoggerFactory.getLogger(V10SignalingHandler.class);

    private ControlService controlService;

    private MatchSignalingService matchSignalingService;

    private final AtomicReference<V10SignalingState> state = new AtomicReference<>(create());

    @Override
    public void start(
            final ProtocolMessageHandler handler,
            final Session session,
            final ProtocolMessageHandler.MultiMatchRecord match,
            final ProtocolMessageHandler.AuthRecord auth) {

        final var matchId = match.getId();
        final var profileId = auth.profile().getId();

        if (getMatchSignalingService().join(matchId, profileId)) {
            logger.debug("Joined match {}", matchId);
        } else {
            logger.debug("Already in match {}", matchId);
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
        final var updated = state.updateAndGet(V10SignalingState::terminate);
        updated.subscription().unsubscribe();
        logger.debug("Stopped signaling.");
    }

    @Override
    public void onMessage(
            final ProtocolMessageHandler handler,
            final Session session,
            final BroadcastSignal signal) {

        final var state = this.state.get();
        checkAuth(signal::getProfileId);

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

        final var state = this.state.get();
        checkAuth(signal::getProfileId);

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

        final var state = this.state.get();
        checkAuth(message::getProfileId);

        switch (state.phase()) {
            case SIGNALING -> getControlService().process(state.match(), state.auth(), message);
            case TERMINATED -> logger.debug("Dropping message. Signaling terminated.");
            default -> throw new ProtocolStateException("Unexpected state: " + state.phase());
        }

    }

    private void checkAuth(final Supplier<String> profileIdSupplier) {

        final var authProfileId = state.get().auth().profile().getId();
        final var senderProfileId = profileIdSupplier.get();

        if (!Objects.equals(authProfileId, senderProfileId)) {
            throw new ForbiddenException();
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

}
