package dev.getelements.elements.crossfire.model.signal;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.HOST;

public class HostSignal implements Signal {

    private String profileId;

    @Override
    public Type getType() {
        return HOST;
    }

    @Override
    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

}
