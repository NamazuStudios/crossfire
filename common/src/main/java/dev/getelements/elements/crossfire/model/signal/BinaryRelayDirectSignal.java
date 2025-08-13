package dev.getelements.elements.crossfire.model.signal;

import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.signal.SignalLifecycle.ONCE;

public class BinaryRelayDirectSignal implements DirectSignal {

    @NotNull
    private String profileId;

    @NotNull
    private String recipientProfileId;

    @NotNull
    private byte[] payload;

    @NotNull
    private SignalLifecycle lifecycle = ONCE;

    @Override
    public Type getType() {
        return Type.BINARY_RELAY;
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

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    @Override
    public SignalLifecycle getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(SignalLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

}
