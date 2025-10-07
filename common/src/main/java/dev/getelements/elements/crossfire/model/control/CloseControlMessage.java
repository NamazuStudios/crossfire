package dev.getelements.elements.crossfire.model.control;

import dev.getelements.elements.crossfire.model.ProtocolMessageType;

public class CloseControlMessage implements ControlMessage {

    private String profileId;

    @Override
    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    @Override
    public ProtocolMessageType getType() {
        return ProtocolMessageType.CLOSE;
    }

    @Override
    public boolean isHostOnly() {
        return true;
    }

}
