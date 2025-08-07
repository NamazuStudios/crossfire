package dev.getelements.elements.crossfire.protocol.v10;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.protocol.ConnectionPhase;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler.MultiMatchRecord;
import jakarta.websocket.Session;

import java.util.ArrayList;
import java.util.List;

import static dev.getelements.elements.crossfire.protocol.ConnectionPhase.*;
import static dev.getelements.elements.crossfire.protocol.v10.V10ProtocolMessageHandler.UNKNOWN_SESSION;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

record V10ConnectionStateRecord(
        Session session,
        MultiMatchRecord match,
        ProtocolMessageHandler.AuthRecord auth,
        ConnectionPhase phase,
        List<ProtocolMessage> ib,
        List<ProtocolMessage> ob
) {

    public static V10ConnectionStateRecord create() {
        return new V10ConnectionStateRecord(null, null, null, WAITING, null, null);
    }

    public String sessionId() {
        return session() != null ? session().getId() : UNKNOWN_SESSION;
    }

    public V10ConnectionStateRecord start(final Session session) {

        requireNonNull(session, "Session cannot be null");

        return switch (phase()) {
            case WAITING -> new V10ConnectionStateRecord(session, match(), auth(), READY, ib(), ob());
            case TERMINATED -> this;
            default -> throw new ProtocolStateException("Cannot start session in phase " + phase());
        };

    }

    public V10ConnectionStateRecord handshake() {
        return switch (phase()) {
            case READY -> new V10ConnectionStateRecord(session(), match(), auth(), HANDSHAKE, ib(), ob());
            case TERMINATED -> this;
            default -> throw new ProtocolStateException("Cannot start handshaking in " + phase());
        };
    }

    public V10ConnectionStateRecord matched(final MultiMatchRecord match) {

        requireNonNull(match, "Match cannot be null");

        return switch (phase()) {
            case TERMINATED -> this;
            case HANDSHAKE -> {
                final var phase = auth() != null ? SIGNALING : HANDSHAKE;
                final var ob = SIGNALING.equals(phase) ? List.<ProtocolMessage>of() : ob();
                final var ib = SIGNALING.equals(phase) ? List.<ProtocolMessage>of() : ib();
                yield new V10ConnectionStateRecord(session(), match, auth(), phase, ib, ob);
            }
            default -> throw new ProtocolStateException("Cannot match in " + phase());
        };

    }

    public V10ConnectionStateRecord authenticated(final ProtocolMessageHandler.AuthRecord auth) {

        requireNonNull(auth, "Auth Record cannot be null");

        return switch (phase()) {
            case TERMINATED -> this;
            case HANDSHAKE -> {
                final var phase = match() != null ? SIGNALING : HANDSHAKE;
                final var ob = SIGNALING.equals(phase) ? List.<ProtocolMessage>of() : ob();
                final var ib = SIGNALING.equals(phase) ? List.<ProtocolMessage>of() : ib();
                yield new V10ConnectionStateRecord(session(), match(), auth, phase, ib, ob);
            }
            default -> throw new ProtocolStateException("Cannot authenticate in " + phase());
        };

    }

    public V10ConnectionStateRecord bufferInbound(final ProtocolMessage message) {
        final var ib = append(ib(), message);
        return new V10ConnectionStateRecord(session(), match(), auth(), phase(), unmodifiableList(ib), ob());
    }

    public V10ConnectionStateRecord bufferOutbound(final ProtocolMessage message) {
        final var ob = append(ob(), message);
        return new V10ConnectionStateRecord(session(), match(), auth(), phase(), ib(), unmodifiableList(ob));
    }

    private static List<ProtocolMessage> append(final List<ProtocolMessage> base, final ProtocolMessage message) {
        if (base == null) {
            return List.of(message);
        } else {
            final var replacement = new ArrayList<>(base);
            replacement.add(message);
            return unmodifiableList(replacement);
        }
    }

    public V10ConnectionStateRecord terminate() {
        return switch (phase()) {
            case TERMINATED -> this;
            default -> new V10ConnectionStateRecord(session(), match(), auth(), TERMINATED, ib(), ob());
        };
    }

}
