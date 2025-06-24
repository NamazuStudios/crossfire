package dev.getelements.elements.crossfire.model;

/**
 * Generic interface for signals that are sent to a specific recipient.
 */
public interface SignalWithRecipient extends Signal {

    /**
     * Get the ID of the recipient for this signal.
     * @return the profile ID of the recipient
     */
    String getRecipientProfileId();

}
