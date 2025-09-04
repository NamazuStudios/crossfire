package dev.getelements.elements.crossfire.client;

/**
 * A client that participates in a match. It has a single peer connection to the host instance.
 */
public interface MatchClient extends AutoCloseable {

    /**
     * Gets the {@link Peer} which can be used to communicate with the remote.
     *
     * @return the {@link Peer}
     */
    Peer getPeer();

    /**
     * Closes the client and all underlying connections.
     */
    @Override
    void close();

}
