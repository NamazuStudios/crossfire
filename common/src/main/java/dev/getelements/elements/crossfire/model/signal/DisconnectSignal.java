package dev.getelements.elements.crossfire.model.signal;

import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.DISCONNECT;

public class DisconnectSignal implements Signal {

    @NotNull
    private String profileId;

    @Override
    public Type getType() {
        return DISCONNECT;
    }

    @Override
    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

}
