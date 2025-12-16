package dev.getelements.elements.crossfire.protocol.v1;

import dev.getelements.elements.crossfire.api.MatchHandle;
import dev.getelements.elements.crossfire.api.model.Version;
import dev.getelements.elements.crossfire.api.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.protocol.HandshakePhase;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler.AuthRecord;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler.MultiMatchRecord;
import jakarta.websocket.Session;

import static dev.getelements.elements.crossfire.protocol.HandshakePhase.*;
import static java.util.Objects.requireNonNull;

public record V1HandshakeStateRecord(
        HandshakePhase phase,
        Session session,
        AuthRecord auth,
        MultiMatchRecord match,
        MatchHandle pending,
        Version version) {

    public static V1HandshakeStateRecord create(final Version version) {
        return new V1HandshakeStateRecord(WAITING, null, null, null, null, version);
    }

    public V1HandshakeStateRecord start(final Session session) {

        requireNonNull(session, "Session must not be null.");

        return switch (phase()) {
            case TERMINATED -> this;
            case WAITING -> new V1HandshakeStateRecord(READY, session, auth(), match(), pending(), version());
            default -> throw new ProtocolStateException("Cannot start handshake in phase " + phase());
        };

    }

    public V1HandshakeStateRecord authenticating() {
        return switch (phase()) {
            case TERMINATED -> this;
            case READY -> new V1HandshakeStateRecord(AUTHENTICATING, session(), auth(), match(), pending(), version());
            default -> throw new ProtocolStateException("Cannot start authentication in phase " + phase());
        };
    }

    public V1HandshakeStateRecord authenticated(final AuthRecord auth) {

        requireNonNull(auth, "Auth must not be null.");

        return switch (phase()) {
            case TERMINATED -> this;
            case AUTHENTICATING -> new V1HandshakeStateRecord(AUTHENTICATED, session(), auth, match(), pending(), version());
            default -> throw new ProtocolStateException("Cannot start handshake in phase " + phase());

        };

    }

    public V1HandshakeStateRecord matching(final MatchHandle<?> pending) {

        requireNonNull(pending, "Pending match must not be null.");

        return switch (phase()) {
            case TERMINATED -> new V1HandshakeStateRecord(TERMINATED, session(), auth(), match(), pending, version());
            case AUTHENTICATED -> new V1HandshakeStateRecord(MATCHING, session(), auth(), match(), pending, version());
            default -> throw new ProtocolStateException("Cannot match in phase " + phase());
        };

    }

    public V1HandshakeStateRecord matched(final MultiMatchRecord match) {
        return switch (phase()) {
            case TERMINATED -> this;
            case MATCHING -> new V1HandshakeStateRecord(MATCHING, session(), auth(), match, null, version());
            default -> throw new ProtocolStateException("Cannot match phase " + phase());
        };
    }

    public V1HandshakeStateRecord terminate() {
        return new V1HandshakeStateRecord(TERMINATED, session(), auth(), match(), pending(), version());
    }

    public void leave() {
        if (pending() != null) {
            pending().leaveMatch();
        }
    }

}
