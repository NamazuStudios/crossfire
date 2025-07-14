package dev.getelements.elements.crossfire.protocol.v10;


import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.crossfire.model.signal.SignalWithRecipient;
import dev.getelements.elements.crossfire.protocol.*;
import jakarta.inject.Inject;
import jakarta.websocket.PongMessage;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Category.HANDSHAKE;
import static dev.getelements.elements.crossfire.protocol.ConnectionPhase.*;

public class V10ProtocolMessageHandler implements ProtocolMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(V10ProtocolMessageHandler.class);

    private Pinger pinger;

    private HandshakeHandler handshakeHandler;

    private SignalingHandler signalingHandler;

    private AtomicReference<ConnectionPhaseRecord> phase = new AtomicReference<>(ConnectionPhaseRecord.create());

    @Override
    public AuthRecord getAuthRecord() {
        return phase.get().auth();
    }

    @Override
    public ConnectionPhase getPhase() {
        return phase.get().phase();
    }

    @Override
    public void start(final Session session) throws IOException {
        final var result = phase.updateAndGet(existing -> existing.start(session));
        pinger.start(session);
        getHandshakeHandler().start(this, session);
        logger.debug("{}: Connection started for session {}", result.phase(), session.getId());
    }

    @Override
    public void stop(final Session session) throws IOException {
        final var result = phase.updateAndGet(ConnectionPhaseRecord::terminate);
        logger.debug("{}: Stopping protocol message handler {}.", result.phase(), result.session().getId());
    }

    @Override
    public void onMessage(final Session session, final PongMessage message) throws IOException {
        getPinger().onPong(session, message);
    }

    @Override
    public void onMessage(final Session session, final ProtocolMessage message) throws IOException {

        final var phase = this.phase.get().phase();
        logger.debug("{}: Session {} received protocol message {}", phase, session.getId(), message.getType());

        switch (phase) {
            case HANDSHAKE -> onHandshakeMessage(session, message);
            case SIGNALING -> onSignalingMessage(session, message);
            default -> invalid(message);
        }

    }

    private void onHandshakeMessage(final Session session, final ProtocolMessage message) {
        if (HANDSHAKE.equals(message.getType().getCategory())) {
            getHandshakeHandler().onMessage(this, session, (HandshakeRequest) message);
        } else {
            invalid(message);
        }
    }

    private void onSignalingMessage(final Session session, final ProtocolMessage message) {
        switch (message.getType().getCategory()) {
            case SIGNALING -> getSignalingHandler().onMessage(this, session, (Signal) message);
            case SIGNALING_DIRECT -> getSignalingHandler().onMessageDirect(this, session, (SignalWithRecipient) message);
            default -> invalid(message);
        }
    }

    private void invalid(final ProtocolMessage message) {

        logger.error("{}: Not ready to accept {}({}} messages in phase.",
                phase,
                message.getType().getCategory(),
                message.getType()
        );

        throw new ProtocolStateException(
                "Unexpected message type " +
                message.getType() +
                " in phase " +
                phase
        );

    }

    @Override
    public void auth(AuthRecord authRecord) {
        phase.updateAndGet(existing -> existing.auth(authRecord));
    }

    public Pinger getPinger() {
        return pinger;
    }

    @Inject
    public void setPinger(Pinger pinger) {
        this.pinger = pinger;
    }

    public HandshakeHandler getHandshakeHandler() {
        return handshakeHandler;
    }

    @Inject
    public void setHandshakeHandler(HandshakeHandler handshakeHandler) {
        this.handshakeHandler = handshakeHandler;
    }

    public SignalingHandler getSignalingHandler() {
        return signalingHandler;
    }

    @Inject
    public void setSignalingHandler(SignalingHandler signalingHandler) {
        this.signalingHandler = signalingHandler;
    }

    private record ConnectionPhaseRecord(
            Session session,
            AuthRecord auth,
            ConnectionPhase phase
    ) {

        public static ConnectionPhaseRecord create() {
            return new ConnectionPhaseRecord(null, null, WAITING);
        }

        public ConnectionPhaseRecord start(final Session session) {

            if (phase() != WAITING) {
                throw new IllegalStateException("Cannot open session in phase " + phase());
            }

            return new ConnectionPhaseRecord(session, null, ConnectionPhase.HANDSHAKE);

        }

        public ConnectionPhaseRecord auth(final AuthRecord auth) {

            if (phase() != ConnectionPhase.HANDSHAKE) {
                throw new IllegalStateException("Cannot authenticate in phase " + phase());
            }

            return new ConnectionPhaseRecord(session(), auth, SIGNALING);

        }

        public ConnectionPhaseRecord terminate() {
            return new ConnectionPhaseRecord(session(), auth(), TERMINATED);
        }

    }

}
