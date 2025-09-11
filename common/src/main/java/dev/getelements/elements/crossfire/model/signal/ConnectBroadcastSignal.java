package dev.getelements.elements.crossfire.model.signal;

import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.CONNECT;
import static dev.getelements.elements.crossfire.model.signal.SignalLifecycle.MATCH;

public class ConnectBroadcastSignal implements BroadcastSignal {

    @NotNull
    private String profileId;

    @Override
    public Type getType() {
        return CONNECT;
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
        return MATCH;
    }

    @Override
    public boolean isServerOnly() {
        return true;
    }

}
