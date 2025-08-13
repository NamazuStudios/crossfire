package dev.getelements.elements.crossfire.model.signal;

import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.BINARY_BROADCAST;
import static dev.getelements.elements.crossfire.model.signal.SignalLifecycle.ONCE;

public class BinaryBroadcastSignal implements BroadcastSignal {

    @NotNull
    private String profileId;

    @NotNull
    private byte[] payload;

    @NotNull
    private SignalLifecycle lifecycle = ONCE;

    @Override
    public Type getType() {
        return BINARY_BROADCAST;
    }

    @Override
    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
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
