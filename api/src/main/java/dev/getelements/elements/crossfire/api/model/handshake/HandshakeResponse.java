package dev.getelements.elements.crossfire.api.model.handshake;

import dev.getelements.elements.crossfire.api.model.ProtocolMessage;
import dev.getelements.elements.crossfire.api.model.Version;
import dev.getelements.elements.sdk.annotation.ElementPublic;

/**
 * Interface representing a response to a handshake request in the Elements Crossfire protocol.
 */
@ElementPublic
public interface HandshakeResponse extends ProtocolMessage {

    /**
     * Gets the version of the protocol.
     * @return the version
     */
    Version getVersion();

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
