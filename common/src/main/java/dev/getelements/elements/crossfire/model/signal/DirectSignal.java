package dev.getelements.elements.crossfire.model.signal;

import dev.getelements.elements.sdk.annotation.ElementPublic;

/**
 * Generic interface for signals that are sent to a specific recipient.
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
