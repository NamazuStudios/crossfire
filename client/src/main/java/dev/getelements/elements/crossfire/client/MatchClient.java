package dev.getelements.elements.crossfire.client;

import dev.getelements.elements.crossfire.model.Protocol;
import dev.getelements.elements.sdk.Subscription;

import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * A client that participates in a match. It has a single peer connection to the host instance.
 */
public interface MatchClient extends AutoCloseable {

    /**
     * Gets the {@link Protocol} used by this client.
     * @return the protocol
     */
    Protocol getProtocol();

    /**
     * Connects the client to the host. This must be called before the peer can be used.
     */
    void connect();

    /**
     * Gets the {@link Peer} which can be used to communicate with the remote.
     *
     * @return the {@link Peer}
     */
    Optional<Peer> findPeer();

    /**
     * Receives notifications when a peer's status changes
     *
     * @return a {@link Subscription}
     */
    Subscription onPeerStatus(BiConsumer<Subscription, PeerStatus> onPeerStatus);

    /**
     * Closes the client and all underlying connections.
     */
    @Override
    void close();

}
