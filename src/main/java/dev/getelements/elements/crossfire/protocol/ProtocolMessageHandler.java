package dev.getelements.elements.crossfire.protocol;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import jakarta.websocket.PongMessage;
import jakarta.websocket.Session;

import java.io.IOException;

/**
 * Handles all protocol messages.
 */
public interface ProtocolMessageHandler {

    /**
     * Starts the protocol message handler.
     *
     * @param session the session
     * @throws IOException
     */
    void start(Session session) throws IOException;

    /**
     * Stops the protocol message handler.
     *
     * @param session the sesion
     * @throws IOException any IO exception if there was a problem writing
     */
    void stop(Session session) throws IOException;

    /**
     * Handles all pong messages messages.
     *
     * @param session the session
     * @param message the protocol message request
     */
    void onMessage(Session session, PongMessage message) throws IOException;

    /**
     * Handles all protocol messages.
     *
     * @param session the session
     * @param message the protocol message request
     */
    void onMessage(Session session, ProtocolMessage message) throws IOException;

}
