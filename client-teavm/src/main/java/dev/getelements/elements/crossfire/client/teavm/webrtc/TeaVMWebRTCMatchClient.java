package dev.getelements.elements.crossfire.client.teavm.webrtc;

import dev.getelements.elements.crossfire.api.model.Protocol;
import dev.getelements.elements.crossfire.client.*;
import dev.getelements.elements.crossfire.client.teavm.signaling.TeaVMPublisher;
import dev.getelements.elements.sdk.Subscription;

import java.util.Optional;
import java.util.function.BiConsumer;

import static dev.getelements.elements.crossfire.api.model.Protocol.WEBRTC;

/**
 * WebRTC {@link MatchClient} for TeaVM browser targets.
 * Creates a single {@link TeaVMWebRTCAnsweringPeer} targeting the match host.
 * JS is single-threaded — no concurrent collections are used.
 */
public class TeaVMWebRTCMatchClient implements MatchClient {

    private final SignalingClient signaling;
    private final TeaVMWebRTCAnsweringPeer peer;
    private boolean open = true;
    private final TeaVMPublisher<PeerStatus> onPeerStatus = new TeaVMPublisher<>();

    public TeaVMWebRTCMatchClient(final SignalingClient signaling, final String iceServersJson) {
        final var state = signaling.getState();
        this.signaling = signaling;
        this.peer = new TeaVMWebRTCAnsweringPeer(signaling, state.getHost(), onPeerStatus, iceServersJson);
    }

    @Override
    public Protocol getProtocol() {
        return WEBRTC;
    }

    @Override
    public void connect() {
        if (!open) throw new IllegalStateException("Client is closed.");
        peer.connect();
    }

    @Override
    public Optional<Peer> findPeer() {
        return open ? Optional.of(peer) : Optional.empty();
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
            peer.close();
        }
    }
}
