package dev.getelements.elements.crossfire.model.signal;

import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.CANDIDATE;

public class CandidateBroadcastSignal implements BroadcastSignal {

    @NotNull
    private String profileId;

    @NotNull
    private String mid;

    @NotNull
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
