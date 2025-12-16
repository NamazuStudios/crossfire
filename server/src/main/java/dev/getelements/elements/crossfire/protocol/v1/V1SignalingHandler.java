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

public class V1SignalingHandler implements SignalingHandler {

    private static final Logger logger = LoggerFactory.getLogger(V1SignalingHandler.class);

    private ControlService controlService;

    private MatchSignalingService matchSignalingService;

    private final AtomicReference<V1SignalingState> state = new AtomicReference<>(create());

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

}
