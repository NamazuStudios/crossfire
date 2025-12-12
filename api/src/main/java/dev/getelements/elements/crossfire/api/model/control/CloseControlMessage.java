package dev.getelements.elements.crossfire.api.model.control;

import dev.getelements.elements.crossfire.api.model.ProtocolMessageType;
import jakarta.validation.constraints.NotNull;

public class CloseControlMessage implements ControlMessage {

    @NotNull
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
