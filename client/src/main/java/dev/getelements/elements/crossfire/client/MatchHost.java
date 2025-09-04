package dev.getelements.elements.crossfire.client;

import java.util.Optional;

/**
 * Once connected via signaling, a MatchHost allows sending and receiving messages to/from peers. In order to simplify
 * connection states, this interface does not provide connection state information.
 */
public interface MatchHost extends AutoCloseable {

    /**
     * Finds the peer with the given profile ID.
     *
     * @param profileId the profile id
     * @return the peer if found, otherwise empty
     */
    Optional<Peer> findPeer(String profileId);

    /**
     * Closes the server and all underlying connections.
     */
    @Override
    void close();

}
