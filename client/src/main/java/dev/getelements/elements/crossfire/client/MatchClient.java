package dev.getelements.elements.crossfire.client;

import dev.getelements.elements.crossfire.api.model.Protocol;
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
     * Gets the peer queue for this host. The peer queue allows waiting for all peers to reach a certain phase. This
     * will open a new peer queue each time it is called, so be sure to close the queue when done.
     */
    PeerQueue newPeerQueue();

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
