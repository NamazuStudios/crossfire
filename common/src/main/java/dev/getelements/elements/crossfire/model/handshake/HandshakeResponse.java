package dev.getelements.elements.crossfire.model.handshake;

import dev.getelements.elements.crossfire.model.ProtocolMessage;

/**
 * Interface representing a response to a handshake request in the Elements Crossfire protocol.
 */
public interface HandshakeResponse extends ProtocolMessage {

    /**
     * Gets the type of the response.
     *
     * @return the type
     */
    Type getType();

}
