package dev.getelements.elements.crossfire.client;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Represents a pool of peer connections that can be used to send data to connected peers.
 */
public interface PeerConnectionPool extends AutoCloseable {

    /**
     * Sends a message to a peer identified by the given profile ID. If there is no peer connected, the message will
     * buffer until a peer connects.  Note this will not block the thread, but will assume that the buffer will
     * remain untouched until the message is sent.
     *
     * @param profileId the profile id
     * @param buffer the buffer of data to send
     * @return true if the message was accepted for sending, false otherwise
     */
    default boolean enqueue(final String profileId, final ByteBuffer buffer) {
        enqueue(profileId, buffer, null);
    }

    /**
     * Sends a message to a peer identified by the given profile ID. If there is no peer connected, the message will
     * buffer until a peer connects.  Note this will not block the thread, but will assume that the buffer will
     * remain untouched until the message is sent.
     *
     * @param profileId the profile id
     * @param buffer the buffer of data to send
     * @param onSent a callback that will be called when the message is sent allowing the caller to reclaim the buffer.
     *               May be null, in which case the buffer will be subject to garbage collection once the message is
     *               sent.
     * @return true if the message was accepted for sending, false otherwise
     */
    boolean enqueue(String profileId, ByteBuffer buffer, Consumer<ByteBuffer> onSent);

    /**
     * Closes the connection pool and all underlying connections.
     */
    void close();

}
