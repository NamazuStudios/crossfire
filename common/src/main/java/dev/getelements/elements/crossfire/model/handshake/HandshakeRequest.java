package dev.getelements.elements.crossfire.model.handshake;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.sdk.annotation.ElementPublic;

/**
 * Represents a request for the mode of the WebSocket. The first message sent must be a request type in order to put the
 * socket into the correct mode for processing.
 */
@ElementPublic
public interface HandshakeRequest extends ProtocolMessage {

    /**
     * THe version 1.0 of the Crossfire protocol.
     */
    String VERSION_1_0 = "1_0";

    /**
     * Gets the version of the protocol requested.
     *
     * @return the version of the protocol
     */
    String getVersion();

    /**
     * The profile id of the user making the request
     * @return the profile id
     */
    String getProfileId();

    /**
     * The session key.
     *
     * @return the session key
     */
    String getSessionKey();

}
