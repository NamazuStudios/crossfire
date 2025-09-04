package dev.getelements.elements.crossfire.model.signal;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.sdk.annotation.ElementPublic;

/**
 * Represents a signal in the Crossfire signaling system.
 */
@ElementPublic
public interface BroadcastSignal extends Signal {

    /**
     * The profile id of the subject of the signal. This is typically the originator of the signal.
     *
     * @return the profile id
     */
    String getProfileId();

}
