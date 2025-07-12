package dev.getelements.elements.crossfire.model.signal;

import dev.getelements.elements.crossfire.model.ProtocolMessage;

/**
 * Represents a signal in the Crossfire signaling system.
 */
public interface Signal extends ProtocolMessage {

    /**
     * The profile id of the subject of the signal. This is typically the originator of the signal.
     *
     * @return the profile id
     */
    String getProfileId();

}
