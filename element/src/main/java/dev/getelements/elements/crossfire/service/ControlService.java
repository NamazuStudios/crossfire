package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.model.control.ControlMessage;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;

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
    void process(ProtocolMessageHandler.MultiMatchRecord match,
                 ProtocolMessageHandler.AuthRecord auth,
                 ControlMessage message);

}
