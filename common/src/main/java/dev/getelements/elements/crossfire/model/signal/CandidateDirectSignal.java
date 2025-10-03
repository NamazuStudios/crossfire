package dev.getelements.elements.crossfire.model.signal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.CANDIDATE;

/**
 * A direct signal that contains a candidate for establishing a peer-to-peer connection. The candidate is typically an
 * SDP message for ICE candidates used in WebRTC connections. This signal is sent from one participant to another
 * to establish a direct connection. The mid and midIndex help identify the media stream the candidate is associated
 * with in scenarios with multiple media streams.
 */
public class CandidateDirectSignal implements DirectSignal {

    @NotNull
    private String profileId;

    @NotNull
    private String recipientProfileId;

    @NotNull
    private String mid;

    @NotNull
    private String candidate;

    @Min(0)
    private int midIndex;

    @Override
    public Type getType() {
        return CANDIDATE;
    }

    @Override
    public SignalLifecycle getLifecycle() {
        return SignalLifecycle.SESSION;
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

    public int getMidIndex() {
        return midIndex;
    }

    public void setMidIndex(int midIndex) {
        this.midIndex = midIndex;
    }
}
