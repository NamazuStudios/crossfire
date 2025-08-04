package dev.getelements.elements.crossfire.protocol;

import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import jakarta.websocket.Session;

/**
 * Handles the handshake.
 */
public interface HandshakeHandler {

    /**
     * Starts the handshake process.
     *
     * @param handler the protocol message handler
     * @param session the session
     */
    void start(ProtocolMessageHandler handler, Session session);

    /**
     * Stops the handshake process.
     *
     * @param handler the protocol message handler
     * @param session the session
     */
    void stop(ProtocolMessageHandler handler, Session session);

    /**
     * Handles the handshake message request.
     *
     * @param handler the protocol message handler
     * @param session the session
     * @param request the handshake request
     */
    void onMessage(ProtocolMessageHandler handler, Session session, HandshakeRequest request);

}
