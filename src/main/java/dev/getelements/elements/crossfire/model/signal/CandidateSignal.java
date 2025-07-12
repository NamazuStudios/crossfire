package dev.getelements.elements.crossfire.model.signal;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.CANDIDATE;

public class CandidateSignal implements Signal {

    private String profileId;

    private String mid;

    private String candidate;

    @Override
    public Type getType() {
        return CANDIDATE;
    }

    @Override
    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getMid() {
        return mid;
    }

    public void setMid(String mid) {
        this.mid = mid;
    }

    public String getCandidate() {
        return candidate;
    }

    public void setCandidate(String candidate) {
        this.candidate = candidate;
    }

}
