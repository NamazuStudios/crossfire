package dev.getelements.elements.crossfire.client.signaling;

import dev.getelements.elements.crossfire.client.MatchClient;
import dev.getelements.elements.crossfire.client.Peer;
import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.model.error.ProtocolError;
import dev.getelements.elements.crossfire.model.signal.DisconnectBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.sdk.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class SignalingMatchClient implements MatchClient {

    private static final Logger logger = LoggerFactory.getLogger(SignalingMatchClient.class);

    private final SignalingPeer peer;

    private final SignalingClient signaling;

    private final Subscription subscription;

    private final AtomicBoolean open = new AtomicBoolean(true);

    public SignalingMatchClient(final SignalingClient signaling) {
        final var state = signaling.getState();
        this.signaling = signaling;
        this.peer = new SignalingPeer(signaling, state.getProfileId(), state.getHost());
        this.subscription = Subscription.begin()
                .chain(signaling.onSignal(this::onSignal))
                .chain(signaling.onClientError(this::onClientError));
    }

    private void onSignal(final Subscription subscription, final Signal signal) {
        switch (signal.getType()) {
            case ERROR -> onSignalError(subscription, (ProtocolError) signal);
            case DISCONNECT -> onSignalDisconnect(subscription, (DisconnectBroadcastSignal) signal);
        }
    }

    private void onSignalError(final Subscription subscription, final ProtocolError signal) {
        logger.error("Got protocol error: {}", signal);
        close();
    }

    private void onSignalDisconnect(final Subscription subscription, final DisconnectBroadcastSignal signal) {

        logger.debug("User Disconnected: {}", signal.getProfileId());

        final var profileId = signaling.getState().getProfileId();
        final var hostProfileId = peer.getProfileId();
        final var signalProfileId = signal.getProfileId();

        if (signalProfileId.equals(hostProfileId) || signalProfileId.equals(profileId)) {
            logger.info("Host or self disconnected, closing client.");
            close();
        }

    }

    private void onClientError(final Subscription subscription, final Throwable throwable) {
        logger.error("Client error.", throwable);
        close();
    }

    @Override
    public Peer getPeer() {
        return peer;
    }

    @Override
    public void close() {
        if (open.compareAndSet(true, false)) {
            peer.close();
            subscription.unsubscribe();
        }
    }

}
