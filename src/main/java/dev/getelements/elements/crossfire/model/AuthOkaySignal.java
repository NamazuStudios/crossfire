package dev.getelements.elements.crossfire.model;

import static dev.getelements.elements.crossfire.model.Signal.Type.AUTH_OK;

@SignalModel(AUTH_OK)
public class AuthOkaySignal implements Signal {

    private String profileId;

    @Override
    public Signal.Type getType() {
        return AUTH_OK;
    }

    @Override
    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

}
