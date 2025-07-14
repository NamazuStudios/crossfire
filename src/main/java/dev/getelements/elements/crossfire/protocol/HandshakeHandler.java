package dev.getelements.elements.crossfire.protocol;

import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import jakarta.websocket.Session;

/**
 * Handles the handshake.
 */
public interface HandshakeHandler {

    /**
     * STarts the handshake handler with the given protocol message handler and session.
     *
     * @param handler the protocol message handler
     * @param session the session
     */
    void start(ProtocolMessageHandler handler, Session session);

    /**
     * Handles the handshake message request.
     *
     * @param handler
     * @param session the session
     * @param request the handshake request
     */
    void onMessage(ProtocolMessageHandler handler, Session session, HandshakeRequest request);

}
