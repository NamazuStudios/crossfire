package dev.getelements.elements.crossfire.protocol;

import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import jakarta.websocket.Session;

/**
 * Handles the handshake.
 */
public interface HandshakeHandler {

    /**
     *
     * @param session
     * @param request
     */
    void onMessage(Session session, HandshakeRequest request);

}
