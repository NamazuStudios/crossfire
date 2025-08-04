package dev.getelements.elements.crossfire.client;

import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;

public interface Client extends AutoCloseable {

    /**
     * Sends a new {@link HandshakeRequest} to the server to initiate the handshake process.
     *
     * @param request the handshake request to send
     */
    void handshake(HandshakeRequest request);

    /**
     * Closes the client connection and releases any resources associated with it.
     */
    void close();

}
