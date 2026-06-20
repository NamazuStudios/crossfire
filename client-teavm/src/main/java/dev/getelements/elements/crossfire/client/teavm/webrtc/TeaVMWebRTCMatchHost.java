package dev.getelements.elements.crossfire.client.teavm.webrtc;

import dev.getelements.elements.crossfire.api.model.Protocol;
import dev.getelements.elements.crossfire.api.model.signal.ConnectBroadcastSignal;
import dev.getelements.elements.crossfire.api.model.signal.DisconnectBroadcastSignal;
import dev.getelements.elements.crossfire.api.model.signal.Signal;
import dev.getelements.elements.crossfire.client.*;
import dev.getelements.elements.crossfire.client.teavm.signaling.TeaVMPublisher;
import dev.getelements.elements.sdk.Subscription;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static dev.getelements.elements.crossfire.api.model.Protocol.WEBRTC;

/**
 * WebRTC {@link MatchHost} for TeaVM browser targets.
 * Creates one {@link TeaVMWebRTCOfferingPeer} per remote participant.
 * JS is single-threaded — no concurrent collections are used.
 */
public class TeaVMWebRTCMatchHost implements MatchHost {

    private final SignalingClient signaling;
    private final String profileId;
    private final String iceServersJson;
    private final Subscription subscription;
    private boolean open = true;
    private final Map<String, TeaVMWebRTCOfferingPeer> peers = new HashMap<>();
    private final TeaVMPublisher<PeerStatus> onPeerStatus = new TeaVMPublisher<>();

    public TeaVMWebRTCMatchHost(final SignalingClient signaling, final String iceServersJson) {
        this.signaling      = signaling;
        this.iceServersJson = iceServersJson;
        this.profileId      = signaling.getState().getProfileId();
        this.subscription   = Subscription.begin()
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
        if (remoteProfileId.equals(profileId)) return;
        if (peers.containsKey(remoteProfileId)) return;
        final var label = "data-" + profileId + "-" + remoteProfileId;
        final var peer  = new TeaVMWebRTCOfferingPeer(signaling, remoteProfileId, onPeerStatus, iceServersJson, label);
        peers.put(remoteProfileId, peer);
        peer.connect();
    }

    @Override
    public Protocol getProtocol() {
        return WEBRTC;
    }

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
            peers.values().forEach(TeaVMWebRTCOfferingPeer::close);
            peers.clear();
        }
    }
}
