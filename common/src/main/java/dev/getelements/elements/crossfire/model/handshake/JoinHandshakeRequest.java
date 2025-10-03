package dev.getelements.elements.crossfire.model.handshake;

import dev.getelements.elements.crossfire.model.Version;
import dev.getelements.elements.sdk.annotation.ElementPublic;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.JOIN;

/**
 * Requets to join an existing match in the Elements Crossfire protocol. This is used when the player already has
 * a match ID and wishes to join that specific match. They will need to provide the match ID along with their
 * session key and optionally their profile ID.
 *
 * Typically, this is used to re-join a match they were previously part of in case of a disconnection or network
 * interruption.
 */
@ElementPublic
public class JoinHandshakeRequest implements HandshakeRequest {

    @NotNull
    private Version version;

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
