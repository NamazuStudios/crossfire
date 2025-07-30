package dev.getelements.elements.crossfire.protocol.v10;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.protocol.ConnectionPhase;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler.MultiMatchRecord;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import jakarta.websocket.Session;

import java.util.ArrayList;
import java.util.List;

import static dev.getelements.elements.crossfire.protocol.ConnectionPhase.*;
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
        return session() != null ? session().getId() : V10ProtocolMessageHandler.UNKNOWN_SESSION;
    }

    public V10ConnectionStateRecord start(final Session session) {

        if (phase() != WAITING) {
            throw new ProtocolStateException("Cannot open session in phase " + phase());
        }

        return new V10ConnectionStateRecord(session, null, null, READY, ib(), ob());

    }

    public V10ConnectionStateRecord handshake() {

        if (phase() != READY) {
            throw new ProtocolStateException("Cannot start handshake in phase " + phase());
        }

        return new V10ConnectionStateRecord(session(), match(), auth(), HANDSHAKE, ib(), ob());

    }

    public V10ConnectionStateRecord matched(final MultiMatchRecord match) {

        requireNonNull(match, "Match cannot be null");

        if (HANDSHAKE.equals(phase())) {
            throw new ProtocolStateException("Cannot match in phase " + phase());
        }

        final var phase = auth() != null ? SIGNALING : HANDSHAKE;
        final var ob = SIGNALING.equals(phase) ? List.<ProtocolMessage>of() : ob();
        final var ib = SIGNALING.equals(phase) ? List.<ProtocolMessage>of() : ib();

        return new V10ConnectionStateRecord(session(), match, auth(), phase, ib, ob);

    }

    public V10ConnectionStateRecord authenticated(final ProtocolMessageHandler.AuthRecord auth) {

        requireNonNull(auth, "Auth Record cannot be null");

        if (HANDSHAKE.equals(phase())) {
            throw new ProtocolStateException("Cannot authenticate in phase " + phase());
        }

        final var phase = match() != null ? SIGNALING : HANDSHAKE;
        final var ob = SIGNALING.equals(phase) ? List.<ProtocolMessage>of() : ob();
        final var ib = SIGNALING.equals(phase) ? List.<ProtocolMessage>of() : ib();

        return new V10ConnectionStateRecord(session(), match(), auth, phase, ib, ob);

    }

    public V10ConnectionStateRecord bufferInbound(final ProtocolMessage message) {
        final var ib = append(ib(), message);
        return new V10ConnectionStateRecord(session(), match(), auth(), phase(), unmodifiableList(ib), ob());
    }

    public V10ConnectionStateRecord bufferOutbound(final ProtocolMessage message) {
        final var ob = append(ob(), message);
        return new V10ConnectionStateRecord(session(), match(), auth(), phase(), ib(), unmodifiableList(ob));
    }

    private List<ProtocolMessage> append(final List<ProtocolMessage> base, final ProtocolMessage message) {
        if (base == null) {
            return List.of(message);
        } else {
            final var replacement = new ArrayList<>(base);
            replacement.add(message);
            return unmodifiableList(replacement);
        }
    }

    public V10ConnectionStateRecord terminate() {
        return new V10ConnectionStateRecord(session(), match(), auth(), TERMINATED, ib(), ob());
    }

}
