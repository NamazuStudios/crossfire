package dev.getelements.elements.crossfire.client.signaling;

import dev.getelements.elements.crossfire.client.MatchHost;
import dev.getelements.elements.crossfire.client.Peer;
import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.model.error.ProtocolError;
import dev.getelements.elements.crossfire.model.signal.ConnectBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.DisconnectBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.sdk.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class SignalingMatchHost implements MatchHost {

    private static final Logger logger = LoggerFactory.getLogger(SignalingMatchHost.class);

    private final SignalingClient signaling;

    private final String profileId;

    private final Subscription subscription;

    private AtomicBoolean open = new AtomicBoolean(true);

    private final ConcurrentMap<String, SignalingPeer> peers = new ConcurrentHashMap<>();

    public SignalingMatchHost(final SignalingClient signaling,
                              final String profileId) {
        this.signaling = signaling;
        this.profileId = profileId;
        this.subscription = Subscription.begin()
                .chain(this.signaling.onSignal(this::onSignal))
                .chain(this.signaling.onClientError(this::onClientError));
    }

    private void onSignal(final Subscription subscription, final Signal signal) {
        switch (signal.getType()) {
            case ERROR -> onSignalError(subscription, (ProtocolError) signal);
            case CONNECT -> onSignalConnect(subscription, (ConnectBroadcastSignal) signal);
            case DISCONNECT -> onSignalDisconnect(subscription, (DisconnectBroadcastSignal) signal);
        }
    }

    private void onSignalError(final Subscription subscription, final ProtocolError signal) {
        logger.error("Protocol error: {} - {}", signal.getCode(), signal.getMessage());
        close();
    }

    private void onSignalConnect(final Subscription subscription, final ConnectBroadcastSignal signal) {
        logger.debug("User Connected: {}", signal);
        connect(signal.getProfileId());
    }

    private void connect(final String remoteProfileId) {

        final var peer = new SignalingPeer(signaling, profileId, remoteProfileId);
        final var existing = peers.putIfAbsent(remoteProfileId, peer);

        // This should not happen, but if it does, close the new peer because it wasn't added
        // and isn't necessary.

        if (existing != null)
            peer.close();

    }

    private void onSignalDisconnect(final Subscription subscription, final DisconnectBroadcastSignal signal) {

        final var existing = peers.remove(signal.getProfileId());

        if (existing != null)
            existing.close();

    }

    private void onClientError(final Subscription subscription, final Throwable throwable) {
        logger.error("Client error.", throwable);
        close();
    }

    @Override
    public Optional<Peer> findPeer(final String profileId) {
        return Optional.ofNullable(peers.get(profileId));
    }

    @Override
    public void close() {
        if (open.compareAndSet(true, false)) {
            subscription.unsubscribe();
            peers.values().forEach(SignalingPeer::close);
        }
    }

}
