package dev.getelements.elements.crossfire.protocol;

import dev.getelements.elements.crossfire.model.control.ControlMessage;
import jakarta.websocket.Session;

public interface ControlMessageHandler {

    /**
     * Starts the signaling handler.
     *
     * @param handler the protocol message handler
     * @param session the session
     * @param match
     * @param auth
     */
    void start(ProtocolMessageHandler handler,
               Session session,
               ProtocolMessageHandler.MultiMatchRecord match,
               ProtocolMessageHandler.AuthRecord auth);

    /**
     * Stops the signaling handler.
     *
     * @param handler the protocol message handler
     * @param session the session
     */
    void stop(ProtocolMessageHandler handler, Session session);

    /**
     * Handles the control message.
     *
     * @param handler the protocol message handler
     * @param session the session
     * @param request the handshake request
     */
    void onMessage(ProtocolMessageHandler handler, Session session, ControlMessage request);

}
