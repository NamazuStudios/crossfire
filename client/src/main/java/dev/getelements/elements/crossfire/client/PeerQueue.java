package dev.getelements.elements.crossfire.client;

import java.util.stream.Stream;

/**
 * A queue that allows waiting for all peers to reach a certain phase.
 */
public interface PeerQueue extends AutoCloseable {

    /**
     * Waits for all peers to reach the given phase. This will block the calling thread until all peers are in the
     * given phase, or the queue is closed.
     *
     * @param phase the phase to wait for
     * @return a stream of all peers in the given phase
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    Stream<Peer> waitForAllPeers(PeerPhase phase) throws InterruptedException;

    /**
     * Closes the peer queue and releases all resources. Any threads waiting on {@link #waitForAllPeers(PeerPhase)}
     * will get a zero-length stream.
     */
    @Override
    void close();

}
