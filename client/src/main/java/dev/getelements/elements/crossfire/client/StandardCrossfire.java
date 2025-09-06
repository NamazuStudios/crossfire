package dev.getelements.elements.crossfire.client;

import dev.getelements.elements.crossfire.model.Protocol;
import dev.getelements.elements.crossfire.model.error.ProtocolError;
import dev.getelements.elements.crossfire.model.handshake.HandshakeResponse;
import dev.getelements.elements.crossfire.model.signal.HostBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.sdk.Subscription;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

public class StandardCrossfire implements Crossfire {

    private final Protocol defaultProtocol;

    private final Set<Mode> supportedModes;

    private final SignalingClient signaling;

    private final Subscription subscription;

    private final AtomicReference<State> state = new AtomicReference<>();

    public StandardCrossfire(
            final Protocol defaultProtocol,
            final Set<Mode> supportedModes,
            final SignalingClient signaling) {

        this.defaultProtocol = requireNonNull(defaultProtocol, "defaultProtocol");
        this.supportedModes = Collections.unmodifiableSet(EnumSet.copyOf(supportedModes));
        this.signaling = requireNonNull(signaling, "signaling");

        if (!supportedModes.stream().anyMatch(m -> getMode().getProtocol().equals(defaultProtocol))) {
            throw new IllegalArgumentException("defaultProtocol must be supported by at least one mode");
        }

        this.subscription = Subscription.begin()
                .chain(signaling.onSignal(this::onSignal))
                .chain(signaling.onHandshake(this::ohHandshake))
                .chain(signaling.onClientError(this::onClientError));

    }

    private void onSignal(final Subscription subscription, final Signal signal) {
        switch (signal.getType()) {
            case HOST -> onHost(subscription, (HostBroadcastSignal) signal);
            case ERROR -> onProtocolError(subscription, (ProtocolError) signal);
        }
    }

    private void onHost(final Subscription subscription, final HostBroadcastSignal signal) {

    }

    private void onProtocolError(final Subscription subscription, final ProtocolError signal) {
        
    }

    private void onClientError(final Subscription subscription, final Throwable throwable) {

    }

    private void ohHandshake(final Subscription subscription, final HandshakeResponse handshakeResponse) {

    }

    @Override
    public Mode getMode() {
        return state.get().mode();
    }

    @Override
    public Set<Mode> getSupportedModes() {
        return supportedModes;
    }

    @Override
    public SignalingClient getSignalingClient() {
        return null;
    }

    @Override
    public Optional<MatchHost> findMatchHost() {
        return Optional.empty();
    }

    @Override
    public Optional<MatchClient> findMatchClient() {
        return Optional.empty();
    }

    @Override
    public Optional<MatchHost> findSignalingMatchHost() {
        return Optional.empty();
    }

    @Override
    public Optional<MatchClient> findSignalingMatchClient() {
        return Optional.empty();
    }

    @Override
    public void close() {

    }

    record State(boolean open, Mode mode) {

        public static State begin() {
            return new State(true, null);
        }

    }

}
