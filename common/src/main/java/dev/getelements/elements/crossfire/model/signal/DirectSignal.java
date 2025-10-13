package dev.getelements.elements.crossfire.model.signal;

import dev.getelements.elements.sdk.annotation.ElementPublic;

/**
 * Generic interface for signals that are sent to a specific recipient. These signals include the profile ID of both the
 * sender and the recipient which will be placed int the recipient's inbox. It is not valid to deliver a direct signal
 * to the originator of the signal.
 *
 * All direct signals should have the {@link #isServerOnly()} flag false as these signals are sent by clients to other
 * clients.
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

    /**
     * Checks that the signal's recipient matches the supplied profile ID.
     *
     * @param profileId the profile ID to check
     * @return the profile ID matches the recipient of the signal
     */
    default boolean isFor(final String profileId) {
        return profileId != null && getRecipientProfileId() != null && profileId.equals(getRecipientProfileId());
    }

}
