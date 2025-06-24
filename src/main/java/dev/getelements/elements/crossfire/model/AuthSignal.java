package dev.getelements.elements.crossfire.model;

import static dev.getelements.elements.crossfire.model.Signal.Type.AUTH;

@SignalModel(AUTH)
public class AuthSignal implements Signal {

    private String profileId;

    private String sessionKey;

    @Override
    public Type getType() {
        return AUTH;
    }

    @Override
    public String getProfileId() {
        return profileId;
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
