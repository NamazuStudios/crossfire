package dev.getelements.elements.crossfire.protocol.v10;

import dev.getelements.elements.crossfire.api.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler.AuthRecord;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler.MultiMatchRecord;
import dev.getelements.elements.crossfire.protocol.SignalingPhase;
import dev.getelements.elements.sdk.Subscription;

import static dev.getelements.elements.crossfire.protocol.SignalingPhase.*;
import static java.util.Objects.requireNonNull;

public record V10SignalingState(
        SignalingPhase phase,
        MultiMatchRecord match,
        AuthRecord auth,
        Subscription subscription) {

    private static final Subscription NOOP = () -> {};

    public static V10SignalingState create() {
        return new V10SignalingState(READY, null, null, NOOP);
    }

    public V10SignalingState start(final Subscription subscription,
                                   final MultiMatchRecord match,
                                   final AuthRecord auth) {

        requireNonNull(subscription, "Subscription is required.");

        return switch (phase()) {
            case READY -> new V10SignalingState(SIGNALING, match, auth, subscription);
            case TERMINATED -> this;
            default -> throw new ProtocolStateException("Unexpected value: " + phase());
        };

    }

    public V10SignalingState terminate() {

        requireNonNull(subscription, "Subscription is required.");

        return switch (phase()) {
            case TERMINATED -> this;
            default -> new V10SignalingState(TERMINATED, match(), auth(), subscription());
        };

    }
    
}
