package dev.getelements.elements.crossfire.model.signal;

import dev.getelements.elements.crossfire.model.ProtocolMessageType;
import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.ProtocolMessageType.BINARY_BROADCAST;
import static dev.getelements.elements.crossfire.model.signal.SignalLifecycle.ONCE;

/**
 * A broadcast signal that contains binary payload data. This signal type is used to send binary data to all
 * participants in the match. The lifecycle of the signal determines how it is cached and delivered by the server and
 * can be set by the originator of the signal. On the wire-the binary payload is base64 encoded.
 */
public class BinaryBroadcastSignal implements BroadcastSignal {

    @NotNull
    private String profileId;

    @NotNull
    private byte[] payload;

    @NotNull
    private SignalLifecycle lifecycle = ONCE;

    @Override
    public ProtocolMessageType getType() {
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
