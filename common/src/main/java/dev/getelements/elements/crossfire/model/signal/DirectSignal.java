package dev.getelements.elements.crossfire.model.signal;

import dev.getelements.elements.crossfire.model.ProtocolMessage;

/**
 * Generic interface for signals that are sent to a specific recipient.
 */
public interface DirectSignal extends ProtocolMessage {

    /**
     * Gets the lifecycle of the signal.
     *
     * @return the lifecycle of the signal
     */
    SignalLifecycle getLifecycle();

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
