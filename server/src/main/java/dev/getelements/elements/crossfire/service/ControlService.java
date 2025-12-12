package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.api.model.control.ControlMessage;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.sdk.annotation.ElementPublic;
import dev.getelements.elements.sdk.annotation.ElementServiceExport;

/**
 * Service to process control messages.
 */
@ElementPublic
@ElementServiceExport
public interface ControlService {

    /**
     * Process a control message.
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
    @ElementPublic
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
