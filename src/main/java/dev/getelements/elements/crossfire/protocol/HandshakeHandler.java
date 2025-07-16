package dev.getelements.elements.crossfire.protocol;

import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import jakarta.websocket.Session;

/**
 * Handles the handshake.
 */
public interface HandshakeHandler {

    /**
     * Handles the handshake message request.
     *
     * @param handler
     * @param session the session
     * @param request the handshake request
     */
    void onMessage(ProtocolMessageHandler handler, Session session, HandshakeRequest request);

}
