package dev.getelements.elements.crossfire.model.handshake;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.FIND;

/**
 * A handshake request for finding a match in the Elements Crossfire protocol. This is used when the player does not
 * already have a match ID, and wishes to be assigned into a matchmaking queue. Once found, they will receive a match
 * ID and can then begin exchanging signaling data to establish the peer communication.
 */
public class FindHandshakeRequest implements HandshakeRequest {

    @NotNull
    @Pattern(regexp = "\\Q" + VERSION_1_0 + "\\E", message = "Version must be " + VERSION_1_0)
    private String version;

    private String profileId;

    @NotNull
    private String sessionKey;

    @NotNull
    private String configuration;

    @Override
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public Type getType() {
        return FIND;
    }

    @Override
    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    @Override
    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

}
