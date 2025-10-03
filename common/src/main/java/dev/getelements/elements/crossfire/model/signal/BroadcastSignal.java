package dev.getelements.elements.crossfire.model.signal;

import dev.getelements.elements.sdk.annotation.ElementPublic;

/**
 * Represents a signal in the Crossfire signaling system. Signals are exchanged in the signaling phase after a match has
 * been assigned to the current session. All broadcast signals must include the profile ID of the originator of the
 * signal and will be delivered to every participant in the match except for the originator.
 */
@ElementPublic
public interface BroadcastSignal extends Signal {

    /**
     * The profile id of the originator of the signal.
     *
     * @return the profile id
     */
    String getProfileId();

}
