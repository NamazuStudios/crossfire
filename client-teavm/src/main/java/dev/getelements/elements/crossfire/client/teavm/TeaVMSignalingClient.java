package dev.getelements.elements.crossfire.client.teavm;

import dev.getelements.elements.crossfire.api.model.Version;
import dev.getelements.elements.crossfire.api.model.control.ControlMessage;
import dev.getelements.elements.crossfire.api.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.api.model.handshake.HandshakeResponse;
import dev.getelements.elements.crossfire.api.model.signal.Signal;
import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.client.SignalingClientPhase;
import dev.getelements.elements.sdk.Subscription;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * Stub {@link SignalingClient} for TeaVM browser targets. The real implementation will use
 * TeaVM's JS interop to drive a browser WebSocket. Until then, subscription methods return
 * no-ops so {@link AbstractCrossfire} can be constructed; all other methods throw.
 */
class TeaVMSignalingClient implements SignalingClient {

    private static final MatchState STUB_STATE = new MatchState() {
        @Override public SignalingClientPhase getPhase() { return SignalingClientPhase.READY; }
        @Override public String getHost()      { return null; }
        @Override public String getMatchId()   { return null; }
        @Override public String getProfileId() { return null; }
        @Override public List<String> getProfiles() { return List.of(); }
    };

    @Override
    public Version getVersion() {
        throw new UnsupportedOperationException("TeaVM signaling not yet implemented");
    }

    @Override
    public MatchState getState() {
        return STUB_STATE;
    }

    @Override
    public Stream<Signal> backlog() {
        throw new UnsupportedOperationException("TeaVM signaling not yet implemented");
    }

    @Override
    public void signal(final Signal signal) {
        throw new UnsupportedOperationException("TeaVM signaling not yet implemented");
    }

    @Override
    public void control(final ControlMessage control) {
        throw new UnsupportedOperationException("TeaVM signaling not yet implemented");
    }

    @Override
    public Optional<HandshakeResponse> findHandshakeResponse() {
        throw new UnsupportedOperationException("TeaVM signaling not yet implemented");
    }

    @Override
    public void handshake(final HandshakeRequest request) {
        throw new UnsupportedOperationException("TeaVM signaling not yet implemented");
    }

    @Override
    public Subscription onHandshake(final BiConsumer<Subscription, HandshakeResponse> listener) {
        return Subscription.begin();
    }

    @Override
    public Subscription onSignal(final BiConsumer<Subscription, Signal> listener) {
        return Subscription.begin();
    }

    @Override
    public Subscription onClientError(final BiConsumer<Subscription, Throwable> listener) {
        return Subscription.begin();
    }

    @Override
    public Optional<DisconnectStatus> waitForDisconnect(final long time, final TimeUnit units) throws InterruptedException {
        throw new UnsupportedOperationException("TeaVM signaling not yet implemented");
    }

    @Override
    public void close() {}

}
