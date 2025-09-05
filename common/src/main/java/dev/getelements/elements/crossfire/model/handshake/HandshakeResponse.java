package dev.getelements.elements.crossfire.model.handshake;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.sdk.annotation.ElementPublic;

/**
 * Interface representing a response to a handshake request in the Elements Crossfire protocol.
 */
@ElementPublic
public interface HandshakeResponse extends ProtocolMessage {

    /**
     * Gets the match ID of the connected match.
     * @return the match id
     */
    String getMatchId();

    /**
     * Gets the profile id of the user associated with this handshake response.
     *
     * @return the profile id
     */
    String getProfileId();

}
