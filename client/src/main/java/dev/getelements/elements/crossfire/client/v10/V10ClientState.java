package dev.getelements.elements.crossfire.client.v10;

import dev.getelements.elements.crossfire.client.ClientPhase;
import dev.getelements.elements.crossfire.model.error.ProtocolStateException;
import jakarta.websocket.Session;

import java.io.IOException;

import static dev.getelements.elements.crossfire.client.ClientPhase.*;

record V10ClientState(ClientPhase phase, Session session) {

    public static V10ClientState create() {
        return new V10ClientState(READY, null);
    }

    public V10ClientState connected(final Session session) {
        return switch (phase()) {
            case TERMINATED -> new V10ClientState(READY, session);
            case READY -> new V10ClientState(CONNECTED, session);
            default -> throw new ProtocolStateException("Invalid connection phase " + phase());
        };
    }

    public V10ClientState terminate() {
        return switch (phase()) {
            case TERMINATED -> this;
            default -> new V10ClientState(TERMINATED, session());
        };
    }

    public V10ClientState handshaking() {
        return switch (phase()) {
            case CONNECTED -> new V10ClientState(HANDSHAKING, session());
            case TERMINATED -> new V10ClientState(TERMINATED, session());
            default -> throw new ProtocolStateException("Invalid handshake phase " + phase());
        };
    }

    public void closeSession() throws IOException {
        if (session() != null)
            session().close();
    }

}
