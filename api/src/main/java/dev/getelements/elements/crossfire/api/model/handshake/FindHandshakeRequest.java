package dev.getelements.elements.crossfire.api.model.handshake;

import dev.getelements.elements.crossfire.api.model.ProtocolMessageType;
import dev.getelements.elements.crossfire.api.model.Version;
import dev.getelements.elements.sdk.annotation.ElementPublic;
import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.api.model.ProtocolMessageType.FIND;

/**
 * A handshake request for finding a match in the Elements Crossfire protocol. This is used when the player does not
 * already have a match ID, and wishes to be assigned into a matchmaking queue. Once found, they will receive a match
 * ID and can then begin exchanging signaling data to establish the peer communication.
 */
@ElementPublic
public class FindHandshakeRequest implements HandshakeRequest {

    @NotNull
    private Version version = Version.V_1_0;

    private String profileId;

    @NotNull
    private String sessionKey;

    @NotNull
    private String configuration;

    @Override
    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    @Override
    public ProtocolMessageType getType() {
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
