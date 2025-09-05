package dev.getelements.elements.crossfire.client.signaling;

import dev.getelements.elements.crossfire.client.MatchClient;
import dev.getelements.elements.crossfire.client.Peer;
import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.sdk.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class SignalingMatchClient implements MatchClient {

    private static final Logger logger = LoggerFactory.getLogger(SignalingMatchClient.class);

    private final SignalingPeer peer;

    private final Subscription subscription;

    private final AtomicBoolean open = new AtomicBoolean(true);

    public SignalingMatchClient(final SignalingClient signaling) {
        final var state = signaling.getState();
        this.peer = new SignalingPeer(signaling, state.getProfileId(), state.getHost());
        this.subscription = Subscription.begin()
                .chain(signaling.onSignal(this::onSignal))
                .chain(signaling.onClientError(this::onClientError));
    }

    private void onClientError(final Subscription subscription, final Throwable throwable) {
        logger.error("Client error.", throwable);
        close(subscription);
    }


    @Override
    public Peer getPeer() {
        return peer;
    }

    @Override
    public void close() {
        peer.close();
    }

}
