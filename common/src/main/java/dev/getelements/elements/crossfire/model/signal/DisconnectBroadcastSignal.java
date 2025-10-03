package dev.getelements.elements.crossfire.model.signal;

import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.DISCONNECT;
import static dev.getelements.elements.crossfire.model.signal.SignalLifecycle.ONCE;

/**
 * A signal indicating that a participant has disconnected from the match. This signal is sent by the server to all
 * participants in the match. It is server-only and its lifecycle is ONCE, meaning it is relevant only at the moment.
 */
public class DisconnectBroadcastSignal implements BroadcastSignal {

    @NotNull
    private String profileId;

    @Override
    public Type getType() {
        return DISCONNECT;
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
        return ONCE;
    }

    @Override
    public boolean isServerOnly() {
        return true;
    }

}
