package dev.getelements.elements.crossfire.model.handshake;

import dev.getelements.elements.crossfire.model.ProtocolMessageType;
import dev.getelements.elements.crossfire.model.Version;
import dev.getelements.elements.sdk.annotation.ElementPublic;
import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.ProtocolMessageType.MATCHED;

/**
 * Indicates that the client has successfully connected to a matched. This response includes the assigned match ID.
 */
@ElementPublic
public class MatchedResponse implements HandshakeResponse {

    /**
     * The version of the protocol.
     */
    private Version version = Version.V_1_0;

    @NotNull
    private String matchId;

    @NotNull
    private String profileId;

    @NotNull
    private String joinCode;

    @Override
    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    @Override
    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    @Override
    public ProtocolMessageType getType() {
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

    public String getJoinCode() {
        return joinCode;
    }

    public void setJoinCode(String joinCode) {
        this.joinCode = joinCode;
    }

    @Override
    public boolean isServerOnly() {
        return true;
    }

}
