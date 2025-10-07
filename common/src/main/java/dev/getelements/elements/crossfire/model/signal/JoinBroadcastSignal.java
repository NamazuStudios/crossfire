package dev.getelements.elements.crossfire.model.signal;

import dev.getelements.elements.crossfire.model.ProtocolMessageType;
import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.ProtocolMessageType.SIGNAL_JOIN;
import static dev.getelements.elements.crossfire.model.signal.SignalLifecycle.MATCH;

/**
 * A signal sent to indicate that a profile is joining a match.
 */
public class JoinBroadcastSignal implements BroadcastSignal {

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
    public boolean isServerOnly() {
        return true;
    }

    @Override
    public SignalLifecycle getLifecycle() {
        return MATCH;
    }

    @Override
    public ProtocolMessageType getType() {
        return SIGNAL_JOIN;
    }

}
