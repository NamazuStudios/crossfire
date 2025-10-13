package dev.getelements.elements.crossfire.model.signal;

import dev.getelements.elements.sdk.annotation.ElementPublic;

import java.util.Objects;

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

    /**
     * Checks that the signal's originator does not match the supplied profile ID. For server-only signals this will
     * always be delivered to all clients as they are not sent by any client but rather driven by server-side events.
     *
     * @param profileId the profile id
     * @return true if the profile ID does not match the originator of the signal
     */
    default boolean isFor(final String profileId) {
        return isServerOnly() || profileId != null && getProfileId() != null && !profileId.equals(getProfileId());
    }

}
