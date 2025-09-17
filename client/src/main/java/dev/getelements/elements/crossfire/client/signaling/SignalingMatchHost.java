package dev.getelements.elements.crossfire.client.signaling;

import dev.getelements.elements.crossfire.client.*;
import dev.getelements.elements.crossfire.model.Protocol;
import dev.getelements.elements.crossfire.model.error.ProtocolError;
import dev.getelements.elements.crossfire.model.signal.ConnectBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.DisconnectBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.ConcurrentDequePublisher;
import dev.getelements.elements.sdk.util.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static dev.getelements.elements.crossfire.client.PeerPhase.CONNECTED;
import static dev.getelements.elements.crossfire.model.Protocol.SIGNALING;

public class SignalingMatchHost implements MatchHost {

    private static final Logger logger = LoggerFactory.getLogger(SignalingMatchHost.class);

    private final SignalingClient signaling;

    private final String profileId;

    private final Subscription subscription;

    private final AtomicBoolean open = new AtomicBoolean(true);

    private final ConcurrentMap<String, SignalingPeer> peers = new ConcurrentHashMap<>();

    private final Publisher<PeerStatus> onPeerStatus = new ConcurrentDequePublisher<>();

    public SignalingMatchHost(final SignalingClient signaling) {

        this.signaling = signaling;

        this.profileId = signaling
                .getState()
                .getProfileId();

        this.subscription = Subscription.begin()
                .chain(this.signaling.onSignal(this::onSignal))
                .chain(this.signaling.onClientError(this::onClientError));

    }

    private void onSignal(final Subscription subscription,
                          final Signal signal) {
        switch (signal.getType()) {
            case ERROR -> onSignalError(subscription, (ProtocolError) signal);
            case CONNECT -> onSignalConnect(subscription, (ConnectBroadcastSignal) signal);
            case DISCONNECT -> onSignalDisconnect(subscription, (DisconnectBroadcastSignal) signal);
        }
    }

    private void onSignalError(final Subscription subscription,
                               final ProtocolError signal) {
        logger.error("Protocol error: {} - {}", signal.getCode(), signal.getMessage());
        close();
    }

    private void onSignalConnect(final Subscription subscription,
                                 final ConnectBroadcastSignal signal) {
        logger.debug("User Connected: {}", signal);
        connect(signal.getProfileId());
    }

    @Override
    public void start() {
        if (open.get()) {
            signaling.getState()
                    .getProfiles()
                    .stream()
                    .filter(Predicate.not(profileId::equals))
                    .forEach(this::connect);
        } else {
            throw new IllegalStateException("Cannot start match host. Closed.");
        }
    }

    private void connect(final String remoteProfileId) {

        final var peer = new SignalingPeer(signaling, profileId, remoteProfileId, onPeerStatus);
        final var existing = peers.putIfAbsent(remoteProfileId, peer);

        if (existing == null)
            peer.connect();
        else
            peer.close();

    }

    private void onSignalDisconnect(final Subscription subscription,
                                    final DisconnectBroadcastSignal signal) {

        final var existing = peers.remove(signal.getProfileId());

        if (existing != null)
            existing.close();

    }

    private void onClientError(final Subscription subscription,
                               final Throwable throwable) {
        logger.error("Client error.", throwable);
        close();
    }

    @Override
    public Protocol getProtocol() {
        return SIGNALING;
    }

    @Override
    public Stream<Peer> knownPeers() {
        return peers.values().stream().map(Peer.class::cast);
    }

    @Override
    public PeerQueue newPeerQueue() {
        return new StandardHostPeerQueue(signaling, this);
    }

    @Override
    public Optional<Peer> findPeer(final String profileId) {
        final var result = Optional.ofNullable((Peer)peers.get(profileId));
        return result;
    }

    @Override
    public Subscription onPeerStatus(final BiConsumer<Subscription, PeerStatus> onPeerStatus) {
        return this.onPeerStatus.subscribe(onPeerStatus);
    }

    @Override
    public void close() {
        if (open.compareAndSet(true, false)) {
            subscription.unsubscribe();
            peers.values().forEach(SignalingPeer::close);
        }
    }

}
