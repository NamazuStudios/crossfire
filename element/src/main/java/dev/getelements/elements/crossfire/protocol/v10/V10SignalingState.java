package dev.getelements.elements.crossfire.protocol.v10;

import dev.getelements.elements.crossfire.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.protocol.SignalingPhase;
import dev.getelements.elements.sdk.Subscription;

import static dev.getelements.elements.crossfire.protocol.SignalingPhase.*;
import static java.util.Objects.requireNonNull;

public record V10SignalingState(
        SignalingPhase phase,
        Subscription subscription) {

    public static SignalingPhase create() {
        return new SignalingPhase(READY, null);
    }

    public V10SignalingState start(final Subscription subscription) {

        requireNonNull(subscription, "Subscription is required.");

        return switch (phase()) {
            case READY -> new V10SignalingState(SIGNALING, subscription);
            case TERMINATED -> this;
            default -> throw new ProtocolStateException("Unexpected value: " + phase());
        };

    }

    public V10SignalingState terminate() {

        requireNonNull(subscription, "Subscription is required.");

        return switch (phase()) {
            case TERMINATED -> this;
            default -> new V10SignalingState(TERMINATED, subscription());
        };

    }
    
}
