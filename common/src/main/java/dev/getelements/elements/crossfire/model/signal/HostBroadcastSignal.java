package dev.getelements.elements.crossfire.model.signal;

import dev.getelements.elements.crossfire.model.ProtocolMessageType;
import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.ProtocolMessageType.HOST;
import static dev.getelements.elements.crossfire.model.signal.SignalLifecycle.SESSION;

/**
 * A signal indicating that a participant has been assigned as the host of the match. This signal is sent by the server
 * to all participants in the match. It is server-only and its lifecycle is determined by the originator of the signal.
 */
public class HostBroadcastSignal implements BroadcastSignal {

    @NotNull
    private String profileId;

    @Override
    public ProtocolMessageType getType() {
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
        return SESSION;
    }

    @Override
    public boolean isServerOnly() {
        return true;
    }

}
