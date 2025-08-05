package dev.getelements.elements.crossfire.client.v10;

import dev.getelements.elements.crossfire.client.ClientPhase;
import dev.getelements.elements.crossfire.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.model.handshake.HandshakeResponse;
import jakarta.websocket.Session;

import java.io.IOException;

import static dev.getelements.elements.crossfire.client.ClientPhase.*;

record V10ClientState(ClientPhase phase,
                      Session session,
                      HandshakeResponse handshake) {

    public static V10ClientState create() {
        return new V10ClientState(READY, null, null);
    }

    public V10ClientState connected(final Session session) {
        return switch (phase()) {
            case TERMINATED -> new V10ClientState(READY, session, handshake());
            case READY -> new V10ClientState(CONNECTED, session, handshake());
            default -> throw new ProtocolStateException("Invalid connection phase " + phase());
        };
    }

    public V10ClientState terminate() {
        return switch (phase()) {
            case TERMINATED -> this;
            default -> new V10ClientState(TERMINATED, session(), handshake());
        };
    }

    public V10ClientState handshaking() {
        return switch (phase()) {
            case CONNECTED -> new V10ClientState(HANDSHAKING, session(), handshake());
            case TERMINATED -> new V10ClientState(TERMINATED, session(), handshake());
            default -> throw new ProtocolStateException("Invalid handshake phase " + phase());
        };
    }

    public V10ClientState matched(final HandshakeResponse message) {
        return switch (phase()) {
            case CONNECTED -> new V10ClientState(HANDSHAKING, session(), handshake());
            case TERMINATED -> new V10ClientState(TERMINATED, session(), handshake());
            default -> throw new ProtocolStateException("Invalid handshake phase " + phase());
        };
    }

    public void closeSession() throws IOException {
        if (session() != null)
            session().close();
    }

}
