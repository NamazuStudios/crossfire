package dev.getelements.elements.crossfire.protocol.v10;

import dev.getelements.elements.crossfire.api.MatchmakingAlgorithm.PendingMatch;
import dev.getelements.elements.crossfire.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.protocol.HandshakePhase;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler.AuthRecord;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler.MultiMatchRecord;
import jakarta.websocket.Session;

import static dev.getelements.elements.crossfire.protocol.HandshakePhase.*;
import static dev.getelements.elements.crossfire.protocol.v10.V10ProtocolMessageHandler.UNKNOWN_SESSION;
import static java.util.Objects.requireNonNull;

record V10HandshakeStateRecord(
        HandshakePhase phase,
        Session session,
        AuthRecord auth,
        MultiMatchRecord match,
        PendingMatch pending) {

    public static V10HandshakeStateRecord create() {
        return new V10HandshakeStateRecord(WAITING, null, null, null, null);
    }

    public V10HandshakeStateRecord start(final Session session) {

        requireNonNull(session, "Session must not be null.");

        return switch (phase()) {
            case TERMINATED -> this;
            case WAITING -> new V10HandshakeStateRecord(READY, session, auth(), match(), pending());
            default -> throw new ProtocolStateException("Cannot start handshake in phase " + phase());
        };

    }

    public V10HandshakeStateRecord authenticating() {
        return switch (phase()) {
            case TERMINATED -> this;
            case READY -> new V10HandshakeStateRecord(AUTHENTICATING, session(), auth(), match(), pending());
            default -> throw new ProtocolStateException("Cannot start authentication in phase " + phase());
        };
    }

    public V10HandshakeStateRecord authenticated(final AuthRecord auth) {

        requireNonNull(auth, "Auth must not be null.");

        return switch (phase()) {
            case TERMINATED -> this;
            case AUTHENTICATING -> new V10HandshakeStateRecord(AUTHENTICATED, session(), auth, match(), pending());
            default -> throw new ProtocolStateException("Cannot start handshake in phase " + phase());

        };

    }

    public V10HandshakeStateRecord matching(final PendingMatch pending) {

        requireNonNull(pending, "Pending match must not be null.");

        return switch (phase()) {
            case TERMINATED -> new V10HandshakeStateRecord(TERMINATED, session(), auth(), match(), pending);
            case AUTHENTICATED -> new V10HandshakeStateRecord(MATCHING, session(), auth(), match(), pending);
            default -> throw new ProtocolStateException("Cannot match in phase " + phase());
        };

    }

    public V10HandshakeStateRecord matched(final MultiMatchRecord match) {
        return switch (phase()) {
            case TERMINATED -> this;
            case MATCHING, AUTHENTICATED -> new V10HandshakeStateRecord(MATCHING, session(), auth(), match, null);
            default -> throw new ProtocolStateException("Cannot match phase " + phase());
        };
    }

    public V10HandshakeStateRecord terminate() {
        return new V10HandshakeStateRecord(TERMINATED, session(), auth(), match(), pending());
    }

    public String sessionId() {
        return session() != null ? session().getId() : UNKNOWN_SESSION;
    }

    public void cancelPending() {
        if (pending() != null) {
            pending().cancel();
        }
    }

}
