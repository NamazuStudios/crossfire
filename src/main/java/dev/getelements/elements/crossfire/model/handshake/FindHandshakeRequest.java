package dev.getelements.elements.crossfire.model.handshake;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.FIND;

/**
 * A handshake request for finding a match in the Elements Crossfire protocol. This is used when the player does not
 * already have a match ID, and wishes to be assigned into a matchmaking queue. Once found, they will receive a match
 * ID and can then begin exchanging signaling data to establish the peer communication.
 */
public class FindHandshakeRequest implements HandshakeRequest {

    private String profileId;

    private String sessionKey;

    private String configuration;

    @Override
    public Type getType() {
        return FIND;
    }

    @Override
    public String getProfileId() {
        return profileId;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
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
