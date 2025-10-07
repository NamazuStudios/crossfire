package dev.getelements.elements.crossfire.model.signal;

import dev.getelements.elements.crossfire.model.ProtocolMessageType;
import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.ProtocolMessageType.SDP_ANSWER;
import static dev.getelements.elements.crossfire.model.signal.SignalLifecycle.SESSION;

/**
 * Contains an SDP answer from a participant in response to a previously sent SDP offer. This signal is used in the
 * WebRTC signaling process to establish a peer-to-peer connection between participants. The signal includes the
 * SDP answer and identifies both the sender and recipient of the signal. It is a direct signal with a lifecycle of
 * SESSION, meaning it is relevant for the duration of the connected session. If a participant drops then it should
 * force a re-offer from the other peer.
 */
public class SdpAnswerDirectSignal implements DirectSignal {

    @NotNull
    private String profileId;

    @NotNull
    private String recipientProfileId;

    @NotNull
    private String peerSdp;

    @Override
    public ProtocolMessageType getType() {
        return SDP_ANSWER;
    }

    @Override
    public SignalLifecycle getLifecycle() {
        return SESSION;
    }

    public String getPeerSdp() {
        return peerSdp;
    }

    public void setPeerSdp(String peerSdp) {
        this.peerSdp = peerSdp;
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

}
