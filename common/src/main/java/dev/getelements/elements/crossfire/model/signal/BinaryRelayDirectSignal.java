package dev.getelements.elements.crossfire.model.signal;

import dev.getelements.elements.crossfire.model.ProtocolMessageType;
import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.signal.SignalLifecycle.ONCE;

/**
 * A direct signal that contains binary payload data. This signal type is used to send binary data to a specific
 * recipient in the match. The lifecycle of the signal determines how it is cached and delivered by the server and
 * can be set by the originator of the signal. On the wire-the binary payload is base64 encoded.
 */
public class BinaryRelayDirectSignal implements DirectSignal {

    @NotNull
    private String profileId;

    @NotNull
    private String recipientProfileId;

    @NotNull
    private byte[] payload;

    @NotNull
    private SignalLifecycle lifecycle = ONCE;

    @Override
    public ProtocolMessageType getType() {
        return ProtocolMessageType.BINARY_RELAY;
    }

    @Override
    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    @Override
    public String getRecipientProfileId() {
        return recipientProfileId;
    }

    public void setRecipientProfileId(String recipientProfileId) {
        this.recipientProfileId = recipientProfileId;
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
