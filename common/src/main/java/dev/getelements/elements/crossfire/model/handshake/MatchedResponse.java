package dev.getelements.elements.crossfire.model.handshake;

import dev.getelements.elements.sdk.annotation.ElementPublic;
import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.MATCHED;

/**
 * Indicates that the client has successfully connected to a matched.
 */
@ElementPublic
public class MatchedResponse implements HandshakeResponse {

    @NotNull
    private String matchId;

    @NotNull
    private String profileId;

    @Override
    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    @Override
    public Type getType() {
        return MATCHED;
    }

    /**
     * Returns the matched ID of the connected matched.
     *
     * @return the matched id
     */
    public String getMatchId() {
        return matchId;
    }

    public void setMatchId(String matchId) {
        this.matchId = matchId;
    }

    @Override
    public boolean isServerOnly() {
        return true;
    }

}
