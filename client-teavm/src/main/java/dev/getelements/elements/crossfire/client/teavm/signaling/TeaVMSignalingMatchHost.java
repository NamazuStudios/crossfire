package dev.getelements.elements.crossfire.client.teavm.signaling;

import dev.getelements.elements.crossfire.api.model.Protocol;
import dev.getelements.elements.crossfire.api.model.error.ProtocolError;
import dev.getelements.elements.crossfire.api.model.signal.ConnectBroadcastSignal;
import dev.getelements.elements.crossfire.api.model.signal.DisconnectBroadcastSignal;
import dev.getelements.elements.crossfire.api.model.signal.Signal;
import dev.getelements.elements.crossfire.client.*;
import dev.getelements.elements.sdk.Subscription;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static dev.getelements.elements.crossfire.api.model.Protocol.SIGNALING;

/**
 * Signaling-relay {@link MatchHost} for TeaVM browser targets.
 * Uses HashMap and {@link TeaVMPublisher} instead of concurrent collections.
 */
public class TeaVMSignalingMatchHost implements MatchHost {

    private final SignalingClient signaling;
    private final String profileId;
    private final Subscription subscription;
    private boolean open = true;
    private final Map<String, TeaVMSignalingPeer> peers = new HashMap<>();
    private final TeaVMPublisher<PeerStatus> onPeerStatus = new TeaVMPublisher<>();

    public TeaVMSignalingMatchHost(final SignalingClient signaling) {
        this.signaling = signaling;
        this.profileId = signaling.getState().getProfileId();
        this.subscription = Subscription.begin()
                .chain(signaling.onSignal(this::onSignal))
                .chain(signaling.onClientError(this::onClientError));
    }

    private void onSignal(final Subscription sub, final Signal signal) {
        switch (signal.getType()) {
            case ERROR      -> close();
            case CONNECT    -> onSignalConnect((ConnectBroadcastSignal) signal);
            case DISCONNECT -> onSignalDisconnect((DisconnectBroadcastSignal) signal);
            default         -> {}
        }
    }

    private void onSignalConnect(final ConnectBroadcastSignal signal) {
        connect(signal.getProfileId());
    }

    private void onSignalDisconnect(final DisconnectBroadcastSignal signal) {
        final var existing = peers.remove(signal.getProfileId());
        if (existing != null) existing.close();
    }

    private void onClientError(final Subscription sub, final Throwable t) {
        close();
    }

    @Override
    public void start() {
        if (!open) throw new IllegalStateException("Cannot start: host is closed.");
        signaling.getState()
                .getProfiles()
                .stream()
                .filter(Predicate.not(profileId::equals))
                .forEach(this::connect);
    }

    private void connect(final String remoteProfileId) {
        final var peer = new TeaVMSignalingPeer(signaling, profileId, remoteProfileId, onPeerStatus);
        if (peers.putIfAbsent(remoteProfileId, peer) == null) {
            peer.connect();
        } else {
            peer.close();
        }
    }

    @Override public Protocol getProtocol() { return SIGNALING; }

    @Override
    public Stream<Peer> knownPeers() {
        return peers.values().stream().map(Peer.class::cast);
    }

    @Override
    public Optional<Peer> findPeer(final String peerId) {
        return Optional.ofNullable(peers.get(peerId));
    }

    @Override
    public PeerQueue newPeerQueue() {
        throw new UnsupportedOperationException(
                "Blocking PeerQueue is not supported in TeaVM browser target — use onPeerStatus() callbacks instead");
    }

    @Override
    public Subscription onPeerStatus(final BiConsumer<Subscription, PeerStatus> listener) {
        return onPeerStatus.subscribe(listener);
    }

    @Override
    public void close() {
        if (open) {
            open = false;
            subscription.unsubscribe();
            peers.values().forEach(TeaVMSignalingPeer::close);
            peers.clear();
        }
    }
}
