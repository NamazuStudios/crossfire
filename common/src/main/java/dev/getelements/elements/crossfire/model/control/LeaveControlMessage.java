package dev.getelements.elements.crossfire.model.control;

import dev.getelements.elements.crossfire.model.ProtocolMessageType;
import jakarta.validation.constraints.NotNull;

public class LeaveControlMessage implements ControlMessage {

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
        return ProtocolMessageType.LEAVE;
    }

}
