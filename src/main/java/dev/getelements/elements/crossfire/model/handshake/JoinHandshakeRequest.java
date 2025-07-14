package dev.getelements.elements.crossfire.model.handshake;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.JOIN;

public class JoinHandshakeRequest implements HandshakeRequest {

    @NotNull
    @Pattern(regexp = "\\Q" + VERSION_1_0 + "\\E", message = "Version must be " + VERSION_1_0)
    private String version;

    private String profileId;

    @NotNull
    private String sessionKey;

    @NotNull
    private String matchId;

    @Override
    public Type getType() {
        return JOIN;
    }

    @Override
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getMatchId() {
        return matchId;
    }

    public void setMatchId(String matchId) {
        this.matchId = matchId;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

}
