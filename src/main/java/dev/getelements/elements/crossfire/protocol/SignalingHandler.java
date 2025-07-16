package dev.getelements.elements.crossfire.protocol;

import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.crossfire.model.signal.SignalWithRecipient;
import jakarta.websocket.Session;

/**
 * Handles the signaling messages for the Crossfire protocol.
 * This interface is responsible for processing handshake requests.
 */
public interface SignalingHandler {

    /**
     * Starts the signaling handler.
     *
     * @param handler the protocol message handler
     * @param session the session
     */
    void start(ProtocolMessageHandler handler, Session session);

    /**
     * Handles the handshake message request.
     *
     * @param handler                        the handler
     * @param session                        the session
     * @param signal                         the signal
     */
    void onMessage(ProtocolMessageHandler handler, Session session, Signal signal);

    /**
     * Handles the handshake message request.
     *
     * @param handler                        the handler
     * @param session                        the session
     * @param signal                         the signal
     */
    void onMessageDirect(ProtocolMessageHandler handler, Session session, SignalWithRecipient signal);

}
