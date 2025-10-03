package dev.getelements.elements.crossfire.model.signal;

import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.SDP_OFFER;
import static dev.getelements.elements.crossfire.model.signal.SignalLifecycle.SESSION;

/**
 * Represents an SDP answer signal in the Crossfire signaling system. This signal is used to send an SDP answer from one
 * peer to another during the WebRTC signaling process. The signal includes the profile ID of the sender, the profile ID
 * of the recipient, and the SDP answer itself.
 */
public class SdpOfferDirectSignal implements DirectSignal {

    @NotNull
    private String profileId;

    @NotNull
    private String recipientProfileId;

    @NotNull
    private String peerSdp;

    @Override
    public Type getType() {
        return SDP_OFFER;
    }

    @Override
    public SignalLifecycle getLifecycle() {
        return SESSION;
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

    public String getPeerSdp() {
        return peerSdp;
    }

    public void setPeerSdp(String peerSdp) {
        this.peerSdp = peerSdp;
    }

}
