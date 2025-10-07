package dev.getelements.elements.crossfire.model.signal;

import dev.getelements.elements.crossfire.model.ProtocolMessageType;
import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.ProtocolMessageType.CONNECT;
import static dev.getelements.elements.crossfire.model.signal.SignalLifecycle.SESSION;

/**
 * A signal indicating that a participant has connected to the match. This signal is sent by the server to all
 * participants in the match. It is server-only and has a lifecycle of SESSION, meaning it is relevant for the duration
 * of the connected session.
 */
public class ConnectBroadcastSignal implements BroadcastSignal {

    @NotNull
    private String profileId;

    @Override
    public ProtocolMessageType getType() {
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
        return SESSION;
    }

    @Override
    public boolean isServerOnly() {
        return true;
    }

    @Override
    public String toString() {
        return "ConnectBroadcastSignal{" +
                "profileId='" + profileId + '\'' +
                '}';
    }

}
