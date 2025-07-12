package dev.getelements.elements.crossfire.model.handshake;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.JOIN;

public class JoinHandshakeRequest implements HandshakeRequest {

    private String profileId;

    private String sessionKey;

    private String matchId;

    @Override
    public Type getType() {
        return JOIN;
    }

    @Override
    public String getProfileId() {
        return profileId;
    }

    public String getMatchId() {
        return matchId;
    }

    public void setMatchId(String matchId) {
        this.matchId = matchId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

}
