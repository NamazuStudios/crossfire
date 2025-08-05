package dev.getelements.elements.crossfire.client;

import dev.getelements.elements.crossfire.model.error.ProtocolError;
import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.HandshakeResponse;
import dev.getelements.elements.sdk.Subscription;

import java.util.function.BiConsumer;

public interface Client extends AutoCloseable {

    /**
     * Sends a new {@link HandshakeRequest} to the server to initiate the handshake process.
     *
     * @param request the handshake request to send
     */
    void handshake(HandshakeRequest request);

    /**
     * Subscribes to the error events.
     *
     * @return the current phase
     */
    Subscription onError(BiConsumer<Subscription, ProtocolError> listener);

    /**
     * Subscribes to the handshake response events.
     *
     * @return the current phase
     */
    Subscription onHandshake(BiConsumer<Subscription, HandshakeResponse> listener);

    /**
     * Closes the client connection and releases any resources associated with it.
     */
    void close();

}
