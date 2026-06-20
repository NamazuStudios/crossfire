package dev.getelements.elements.crossfire.client.teavm.signaling;

import dev.getelements.elements.crossfire.api.model.Protocol;
import dev.getelements.elements.crossfire.api.model.error.ProtocolError;
import dev.getelements.elements.crossfire.api.model.signal.DisconnectBroadcastSignal;
import dev.getelements.elements.crossfire.api.model.signal.Signal;
import dev.getelements.elements.crossfire.client.*;
import dev.getelements.elements.sdk.Subscription;

import java.util.Optional;
import java.util.function.BiConsumer;

import static dev.getelements.elements.crossfire.api.model.Protocol.SIGNALING;

/**
 * Signaling-relay {@link MatchClient} for TeaVM browser targets.
 * Uses a single {@link TeaVMSignalingPeer} targeting the match host.
 */
public class TeaVMSignalingMatchClient implements MatchClient {

    private final SignalingClient signaling;
    private final TeaVMSignalingPeer peer;
    private final Subscription subscription;
    private boolean open = true;
    private final TeaVMPublisher<PeerStatus> onPeerStatus = new TeaVMPublisher<>();

    public TeaVMSignalingMatchClient(final SignalingClient signaling) {
        final var state = signaling.getState();
        this.signaling = signaling;
        this.peer = new TeaVMSignalingPeer(signaling, state.getProfileId(), state.getHost(), onPeerStatus);
        this.subscription = Subscription.begin()
                .chain(signaling.onSignal(this::onSignal))
                .chain(signaling.onClientError(this::onClientError));
    }

    private void onSignal(final Subscription sub, final Signal signal) {
        switch (signal.getType()) {
            case ERROR      -> close();
            case DISCONNECT -> onSignalDisconnect((DisconnectBroadcastSignal) signal);
            default         -> {}
        }
    }

    private void onSignalDisconnect(final DisconnectBroadcastSignal signal) {
        final var state          = signaling.getState();
        final var myProfileId    = state.getProfileId();
        final var hostProfileId  = peer.getProfileId();
        final var disconnectedId = signal.getProfileId();
        if (disconnectedId.equals(hostProfileId) || disconnectedId.equals(myProfileId)) {
            close();
        }
    }

    private void onClientError(final Subscription sub, final Throwable t) {
        close();
    }

    @Override public Protocol getProtocol() { return SIGNALING; }

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
            subscription.unsubscribe();
        }
    }
}
