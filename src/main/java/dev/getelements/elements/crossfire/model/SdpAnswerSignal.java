package dev.getelements.elements.crossfire.model;

import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.Signal.Type.SDP_ANSWER;

@SignalModel(SDP_ANSWER)
public class SdpAnswerSignal implements SignalWithRecipient {

    @NotNull
    private String profileId;

    @NotNull
    private String recipientProfileId;

    @NotNull
    private String peerSdp;

    @Override
    public Type getType() {
        return SDP_ANSWER;
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
