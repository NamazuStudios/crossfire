package dev.getelements.elements.crossfire.api.model.signal;

import dev.getelements.elements.crossfire.api.model.ProtocolMessageType;
import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.api.model.ProtocolMessageType.STRING_RELAY;
import static dev.getelements.elements.crossfire.api.model.signal.SignalLifecycle.ONCE;

/**
 * A simple implementation of a direct signal that carries a string payload. This signal can be used to send text data
 * to a specific participant in a match. It includes the profile ID of the sender, the profile ID of the recipient,
 * the lifecycle of the signal, and the string payload itself. The originator of the signal can define the lifecycle
 * based on the intended duration of relevance for the signal.
 */
public class StringRelayDirectSignal implements DirectSignal {

    @NotNull
    private String profileId;

    @NotNull
    private String recipientProfileId;

    @NotNull
    private SignalLifecycle lifecycle = ONCE;

    @NotNull
    public String payload;

    @Override
    public ProtocolMessageType getType() {
        return STRING_RELAY;
    }

    @Override
    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    @Override
    public String getRecipientProfileId() {
        return recipientProfileId;
    }

    public void setRecipientProfileId(String recipientProfileId) {
        this.recipientProfileId = recipientProfileId;
    }

    @Override
    public SignalLifecycle getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(SignalLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

}
