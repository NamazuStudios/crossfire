package dev.getelements.elements.crossfire.client;

import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.Monitor;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class StandardHostPeerQueue implements PeerQueue {

    private final Lock lock = new ReentrantLock();

    private final Condition condition = lock.newCondition();

    private boolean open = true;

    private final Subscription subscription;

    private final MatchHost host;

    private final SignalingClient signalingClient;

    private final Map<String, Peer> peers = new TreeMap<>();

    public StandardHostPeerQueue(final SignalingClient signalingClient, final MatchHost host) {

        this.host = host;
        this.signalingClient = signalingClient;

        try (final var mon = Monitor.enter(lock)) {
            host.knownPeers().forEach(p -> peers.put(p.getProfileId(), p));
        }

        this.subscription = host.onPeerStatus(this::updatePeerStatus);


    }

    private void updatePeerStatus(final Subscription subscription, final PeerStatus peerStatus) {
        try (final var mon = Monitor.enter(lock)) {
            condition.signalAll();
        }
    }

    @Override
    public Stream<Peer> waitForAllPeers(final PeerPhase peerPhase) throws InterruptedException {
        try (var mon = Monitor.enter(lock)) {

            while (open && !areAllPeersReady(peerPhase)) {
                condition.await();
            }

            return open ? host.knownPeers() : Stream.empty();

        }
    }

    private boolean areAllPeersReady(final PeerPhase phase) {

        final var hostProfileId = signalingClient
                .getState()
                .getProfileId();

        return signalingClient
                .getState()
                .getProfiles()
                .stream()
                .filter(Predicate.not(hostProfileId::equals))
                .map(host::findPeer)
                .allMatch(o -> o.map(p -> p.gePhase().equals(phase)).orElse(false));

    }

    @Override
    public void close() {

        subscription.unsubscribe();

        try (var mon = Monitor.enter(lock)) {
            open = false;
            condition.signalAll();
        }

    }

}
