package dev.getelements.elements.crossfire.model;

import static dev.getelements.elements.crossfire.model.Signal.Type.CANDIDATE;

@SignalModel(CANDIDATE)
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
