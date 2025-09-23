package dev.getelements.elements.crossfire.client;

import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.Monitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.SECONDS;

public class StandardHostPeerQueue implements PeerQueue {

    private static final Logger log = LoggerFactory.getLogger(StandardHostPeerQueue.class);
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
        this.subscription = host.onPeerStatus(this::updatePeerStatus);
    }

    private void updatePeerStatus(final Subscription subscription, final PeerStatus peerStatus) {
        try (final var mon = Monitor.enter(lock)) {

            log.debug("Update peer status for {}: {}",
                    peerStatus.peer().getProfileId(),
                    peerStatus.phase()
            );

            condition.signalAll();

        }
    }

    @Override
    public Stream<Peer> waitForAllPeers(final PeerPhase peerPhase) throws InterruptedException {

        log.debug("Waiting for {} peers", peerPhase);

        try (var mon = Monitor.enter(lock)) {

            while (open && !areAllPeersReady(peerPhase)) {
                while (!condition.await(1, SECONDS)) {
                    log.debug("Still waiting for all peers to be in phase {}", peerPhase);
                }
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
                .peek(pid -> log.debug("Checking peer profile id {}", pid))
                .peek(pid -> host.findPeer(pid).ifPresentOrElse(
                        p -> log.debug("Peer {} found in phase {}", pid, p.getPhase()),
                        () -> log.debug("Peer {} not found", pid)
                ))
                .filter(Predicate.not(hostProfileId::equals))
                .map(host::findPeer)
                .peek(o -> o.ifPresent(p -> log.debug("Found peer {} in phase {}",
                        p.getProfileId(),
                        p.getPhase())
                ))
                .allMatch(o -> o.map(p -> p.getPhase().equals(phase)).orElse(false));

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
