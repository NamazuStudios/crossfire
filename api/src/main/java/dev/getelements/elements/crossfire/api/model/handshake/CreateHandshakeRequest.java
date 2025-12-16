package dev.getelements.elements.crossfire.api.model.handshake;

import dev.getelements.elements.crossfire.api.model.ProtocolMessageType;
import dev.getelements.elements.crossfire.api.model.Version;
import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.api.model.ProtocolMessageType.CREATE;
import static dev.getelements.elements.crossfire.api.model.Version.V_1_1;

public class CreateHandshakeRequest implements HandshakeRequest {

    private Version version = V_1_1;

    private String profileId;

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
        return CREATE;
    }

    @Override
    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    @Override
    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

}
