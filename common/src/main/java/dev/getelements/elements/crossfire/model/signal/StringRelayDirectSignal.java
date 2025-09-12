package dev.getelements.elements.crossfire.model.signal;

import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.STRING_RELAY;
import static dev.getelements.elements.crossfire.model.signal.SignalLifecycle.ONCE;

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
    public Type getType() {
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
