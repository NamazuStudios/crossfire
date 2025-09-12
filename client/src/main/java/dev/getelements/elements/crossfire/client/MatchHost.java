package dev.getelements.elements.crossfire.client;

import dev.getelements.elements.crossfire.model.Protocol;
import dev.getelements.elements.sdk.Subscription;

import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Once connected via signaling, a MatchHost allows sending and receiving messages to/from peers. In order to simplify
 * connection states, this interface does not provide connection state information.
 */
public interface MatchHost extends AutoCloseable {

    /**
     * Gets the {@link Protocol} used by this host.
     *
     * @return the protocol
     */
    Protocol getProtocol();

    /**
     * Starts the host to begin accepting connections. This must be called before any peers can connect.
     */
    void start();

    /**
     * Finds the peer with the given profile ID.
     *
     * @param profileId the profile id
     * @return the peer if found, otherwise empty
     */
    Optional<Peer> findPeer(String profileId);

    /**
     * Receives notifications when a peer's status changes
     *
     * @return a {@link Subscription}
     */
    Subscription onPeerStatus(BiConsumer<Subscription, PeerStatus> onPeerStatus);

    /**
     * Closes the server and all underlying connections.
     */
    @Override
    void close();

}
