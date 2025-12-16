package dev.getelements.elements.crossfire.api.model.handshake;

import dev.getelements.elements.crossfire.api.model.ProtocolMessageType;
import dev.getelements.elements.crossfire.api.model.Version;
import jakarta.validation.constraints.NotNull;

/**
 * A handshake request for joining a match using a join code in the Elements Crossfire protocol. This is used when
 */
public class JoinCodeHandshakeRequest implements HandshakeRequest {

    @NotNull
    private Version version;

    private String profileId;

    @NotNull
    private String joinCode;

    @NotNull
    private String sessionKey;

    @Override
    public ProtocolMessageType getType() {
        return ProtocolMessageType.JOIN_CODE;
    }

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

    public String getJoinCode() {
        return joinCode;
    }

    public void setJoinCode(String joinCode) {
        this.joinCode = joinCode;
    }

    @Override
    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

}
