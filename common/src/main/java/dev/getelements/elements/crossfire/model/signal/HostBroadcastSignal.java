package dev.getelements.elements.crossfire.model.signal;

import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.HOST;

public class HostBroadcastSignal implements BroadcastSignal {

    @NotNull
    private String profileId;

    @NotNull
    private SignalLifecycle lifecycle;

    @Override
    public Type getType() {
        return HOST;
    }

    @Override
    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    @Override
    public SignalLifecycle getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(SignalLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Override
    public boolean isServerOnly() {
        return true;
    }

}
