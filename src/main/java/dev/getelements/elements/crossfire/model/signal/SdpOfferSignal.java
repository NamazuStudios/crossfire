package dev.getelements.elements.crossfire.model.signal;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.SDP_OFFER;

public class SdpOfferSignal implements Signal {

    private String profileId;

    private String peerSdp;

    @Override
    public Type getType() {
        return SDP_OFFER;
    }

    @Override
    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getPeerSdp() {
        return peerSdp;
    }

    public void setPeerSdp(String peerSdp) {
        this.peerSdp = peerSdp;
    }

}
