package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.model.control.ControlMessage;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.sdk.annotation.ElementPublic;

/**
 * Service to process control messages.
 */
public interface ControlService {

    /**
     *
     * @param match the match
     * @param auth the au
     * @param message
     */
    Result process(ProtocolMessageHandler.MultiMatchRecord match,
                   ProtocolMessageHandler.AuthRecord auth,
                   ControlMessage message);

    /**
     * The result of processing a control message.
     */
    enum Result {

        /**
         * Persist the connection (default).
         */
        PERSIST_CONNECTION,

        /**
         * Close the connection.
         */
        CLOSE_CONNECTION

    }

}
