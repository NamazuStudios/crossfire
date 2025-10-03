package dev.getelements.elements.crossfire.model.signal;

import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.STRING_BROADCAST;

/**
 * A simple implementation of a broadcast signal that carries a string payload. This signal can be used to send text
 * data to all participants in a match, excluding the sender. It includes the profile ID of the sender, the lifecycle of
 * the signal, and the string payload itself. The originator of the signal can define the lifecycle based on the
 * intended duration of relevance for the signal.
 */
public class StringBroadcastSignal implements BroadcastSignal {

    @NotNull
    private String profileId;

    @NotNull
    private SignalLifecycle lifecycle;

    @NotNull
    private String payload;

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
    public Type getType() {
        return STRING_BROADCAST;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

}
