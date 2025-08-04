package dev.getelements.elements.crossfire.model.signal;

import jakarta.validation.constraints.NotNull;

public class BinaryRelaySignal implements SignalWithRecipient {

    @NotNull
    private String profileId;

    @NotNull
    private String recipientProfileId;

    @NotNull
    private byte[] payload;

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

}
