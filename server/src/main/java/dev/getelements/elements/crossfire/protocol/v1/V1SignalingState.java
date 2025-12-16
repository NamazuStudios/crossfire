package dev.getelements.elements.crossfire.protocol.v1;

import dev.getelements.elements.crossfire.api.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler.AuthRecord;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler.MultiMatchRecord;
import dev.getelements.elements.crossfire.protocol.SignalingPhase;
import dev.getelements.elements.sdk.Subscription;

import static dev.getelements.elements.crossfire.protocol.SignalingPhase.*;
import static java.util.Objects.requireNonNull;

public record V1SignalingState(
        SignalingPhase phase,
        MultiMatchRecord match,
        AuthRecord auth,
        Subscription subscription) {

    private static final Subscription NOOP = () -> {};

    public static V1SignalingState create() {
        return new V1SignalingState(READY, null, null, NOOP);
    }

    public V1SignalingState start(final Subscription subscription,
                                  final MultiMatchRecord match,
                                  final AuthRecord auth) {

        requireNonNull(subscription, "Subscription is required.");

        return switch (phase()) {
            case READY -> new V1SignalingState(SIGNALING, match, auth, subscription);
            case TERMINATED -> this;
            default -> throw new ProtocolStateException("Unexpected value: " + phase());
        };

    }

    public V1SignalingState terminate() {

        requireNonNull(subscription, "Subscription is required.");

        return switch (phase()) {
            case TERMINATED -> this;
            default -> new V1SignalingState(TERMINATED, match(), auth(), subscription());
        };

    }
    
}
