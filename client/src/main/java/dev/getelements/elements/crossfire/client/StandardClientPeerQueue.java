package dev.getelements.elements.crossfire.client;

import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.Monitor;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

public class StandardClientPeerQueue implements PeerQueue {

    boolean open = true;

    private final MatchClient client;

    private final Lock lock = new ReentrantLock();

    private final Condition condition = lock.newCondition();

    private final Subscription subscription;

    public StandardClientPeerQueue(final MatchClient client) {
        this.client = client;
        this.subscription = client.onPeerStatus(this::onPeerStatus);
    }

    private void onPeerStatus(final Subscription subscription, final PeerStatus peerStatus) {
        try (final var mon = Monitor.enter(lock)) {
            condition.signalAll();
        }
    }

    @Override
    public Stream<Peer> waitForAllPeers(final PeerPhase phase) throws InterruptedException {
        try (final var mon = Monitor.enter(lock)) {

            while (open && client.findPeer().map(p -> p.gePhase().equals(phase)).orElse(false)) {
                condition.await();
            }

            return open ?
                    client.findPeer().stream() :
                    Stream.empty();

        }
    }

    @Override
    public void close() {

        subscription.unsubscribe();

        try (final var mon = Monitor.enter(lock)) {
            open = false;
            condition.signalAll();
        }

    }

}
