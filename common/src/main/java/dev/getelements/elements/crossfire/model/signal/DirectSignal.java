package dev.getelements.elements.crossfire.model.signal;

import dev.getelements.elements.sdk.annotation.ElementPublic;

/**
 * Generic interface for signals that are sent to a specific recipient. These signals include the profile ID of both the
 * sender and the recipient which will be placed int the recipient's inbox. It is not valid to deliver a direct signal
 * to the originator of the signal.
 */
@ElementPublic
public interface DirectSignal extends Signal {

    /**
     * The profile id of the subject of the signal. This is typically the originator of the signal.
     *
     * @return the profile id
     */
    String getProfileId();

    /**
     * Get the ID of the recipient for this signal.
     *
     * @return the profile ID of the recipient
     */
    String getRecipientProfileId();

}
