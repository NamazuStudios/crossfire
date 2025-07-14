package dev.getelements.elements.crossfire.protocol.v10;


import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.crossfire.model.signal.SignalWithRecipient;
import dev.getelements.elements.crossfire.protocol.*;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import jakarta.inject.Inject;
import jakarta.websocket.PongMessage;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static dev.getelements.elements.crossfire.protocol.ConnectionPhase.*;
import static java.util.Objects.requireNonNull;

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
            case HANDSHAKE -> onHandshakeMessage(phase, session, message);
            case SIGNALING -> onSignalingMessage(phase, session, message);
            default -> invalid(phase, message);
        }

    }

    private void onHandshakeMessage(
            final ConnectionPhase phase,
            final Session session,
            final ProtocolMessage message) {
        if (ProtocolMessage.Category.HANDSHAKE.equals(message.getType().getCategory())) {
            getHandshakeHandler().onMessage(this, session, (HandshakeRequest) message);
        } else {
            invalid(phase, message);
        }
    }

    private void onSignalingMessage(
            final ConnectionPhase phase,
            final Session session,
            final ProtocolMessage message) {
        switch (message.getType().getCategory()) {
            case SIGNALING -> getSignalingHandler().onMessage(this, session, (Signal) message);
            case SIGNALING_DIRECT -> getSignalingHandler().onMessageDirect(this, session, (SignalWithRecipient) message);
            default -> invalid(phase, message);
        }
    }

    @Override
    public void matched(final MultiMatch multiMatch) {
        final var result = phase.updateAndGet(existing -> existing.matched(multiMatch));
        logger.debug("{}: Matched session {} to match {}", result.phase(), result.session().getId(), multiMatch.getId());
    }

    @Override
    public void authenticated(final AuthRecord authRecord) {

        final var result = phase.updateAndGet(existing -> existing.authenticated(authRecord));

        logger.debug("{}: Authenticated session {} for {}",
                result.phase(),
                result.session().getId(),
                authRecord.session().getProfile().getId()
        );

    }

    private void invalid(final ConnectionPhase phase, final ProtocolMessage message) {

        logger.error("{}: Not ready to accept {}({}} messages in phase.",
                phase,
                message.getType().getCategory(),
                message.getType()
        );

        throw new ProtocolStateException(
                "Unexpected message type " +
                        message.getType() +
                        " in phase " +
                        this.phase
        );

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
            MultiMatch match,
            AuthRecord auth,
            ConnectionPhase phase
    ) {

        public static ConnectionPhaseRecord create() {
            return new ConnectionPhaseRecord(null, null, null, WAITING);
        }

        public ConnectionPhaseRecord start(final Session session) {

            if (phase() != WAITING) {
                throw new ProtocolStateException("Cannot open session in phase " + phase());
            }

            return new ConnectionPhaseRecord(session, null, null, ConnectionPhase.HANDSHAKE);

        }

        public ConnectionPhaseRecord matched(final MultiMatch match) {

            requireNonNull(match, "Match cannot be null");

            if (ConnectionPhase.HANDSHAKE.equals(phase())) {
                throw new ProtocolStateException("Cannot match in phase " + phase());
            }

            final var phase = auth() != null ? SIGNALING : HANDSHAKE;
            return new ConnectionPhaseRecord(session(), match, auth(), phase);

        }

        public ConnectionPhaseRecord authenticated(final AuthRecord auth) {

            requireNonNull(auth, "Auth Record cannot be null");

            if (ConnectionPhase.HANDSHAKE.equals(phase())) {
                throw new ProtocolStateException("Cannot authenticate in phase " + phase());
            }

            final var phase = match() != null ? SIGNALING : HANDSHAKE;
            return new ConnectionPhaseRecord(session(), match(), auth, phase);

        }

        public ConnectionPhaseRecord terminate() {
            return new ConnectionPhaseRecord(session(), match(), auth(), TERMINATED);
        }

    }

}
