package dev.getelements.elements.crossfire.model.control;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.sdk.annotation.ElementPublic;

/**
 * Represents a control message in the Crossfire system. Control messages are special messages that are used by clients
 * to communicate control information to the server, such as requests to join or leave a match, or to perform
 * operations such as closing the match.
 */
@ElementPublic
public interface ControlMessage extends ProtocolMessage {

    /***
     * Gets the profile ID associated with this control message. This is the client requesting the control action.
     *
     * @return the profile id
     */
    String getProfileId();

    /**
     * Indicates if this control message is intended for the host only. If true, the message will only be processed if
     * the requesting participant is the host of the match. If false, the message will be processed for all connections
     * to the match.
     *
     * @return true if host only, false otherwise
     */
    default boolean isHostOnly() {
        return false;
    }

}
