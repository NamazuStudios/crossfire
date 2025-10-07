package dev.getelements.elements.crossfire.model.signal;

import dev.getelements.elements.crossfire.model.ProtocolMessageType;
import jakarta.validation.constraints.NotNull;

public class LeaveBroadcastSignal implements BroadcastSignal {

    @NotNull
    private String profileId;

    @Override
    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    @Override
    public SignalLifecycle getLifecycle() {
        return SignalLifecycle.MATCH;
    }

    @Override
    public ProtocolMessageType getType() {
        return ProtocolMessageType.SIGNAL_LEAVE;
    }

}
