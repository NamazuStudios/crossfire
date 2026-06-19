package dev.getelements.elements.crossfire.client.teavm;

import dev.getelements.elements.crossfire.api.model.Protocol;
import dev.getelements.elements.crossfire.client.*;
import dev.getelements.elements.sdk.Subscription;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Skeleton {@link Crossfire} implementation for TeaVM browser targets.
 * Browser WebRTC bindings (RTCPeerConnection JS interop) are not yet implemented.
 */
public class TeaVMCrossfire implements Crossfire {

    @Override
    public Mode getMode() {
        throw new UnsupportedOperationException("TeaVM WebRTC not yet implemented");
    }

    @Override
    public Set<Mode> getSupportedModes() {
        throw new UnsupportedOperationException("TeaVM WebRTC not yet implemented");
    }

    @Override
    public Crossfire connect() {
        throw new UnsupportedOperationException("TeaVM WebRTC not yet implemented");
    }

    @Override
    public Crossfire connect(final URI uri) {
        throw new UnsupportedOperationException("TeaVM WebRTC not yet implemented");
    }

    @Override
    public SignalingClient getSignalingClient() {
        throw new UnsupportedOperationException("TeaVM WebRTC not yet implemented");
    }

    @Override
    public Optional<MatchHost> findMatchHost() {
        throw new UnsupportedOperationException("TeaVM WebRTC not yet implemented");
    }

    @Override
    public Optional<MatchClient> findMatchClient() {
        throw new UnsupportedOperationException("TeaVM WebRTC not yet implemented");
    }

    @Override
    public Optional<MatchHost> findMatchHost(final Protocol protocol) {
        throw new UnsupportedOperationException("TeaVM WebRTC not yet implemented");
    }

    @Override
    public Optional<MatchClient> findMatchClient(final Protocol protocol) {
        throw new UnsupportedOperationException("TeaVM WebRTC not yet implemented");
    }

    @Override
    public Subscription onHostOpenStatus(final BiConsumer<Subscription, OpenStatus<MatchHost>> onHostOpenStatus) {
        throw new UnsupportedOperationException("TeaVM WebRTC not yet implemented");
    }

    @Override
    public Subscription onClientOpenStatus(final BiConsumer<Subscription, OpenStatus<MatchClient>> onClientOpenStatus) {
        throw new UnsupportedOperationException("TeaVM WebRTC not yet implemented");
    }

    @Override
    public void close() {
        // no-op stub
    }

    public static class Builder implements Crossfire.Builder {

        @Override
        public Builder withDefaultUri(final URI uri) {
            return this;
        }

        @Override
        public Builder withDefaultProtocol(final Protocol protocol) {
            return this;
        }

        @Override
        public Builder withSupportedModes(final Set<Mode> modes) {
            return this;
        }

        @Override
        public Builder withSupportedModes(final Mode... modes) {
            return this;
        }

        @Override
        public Builder withIceServers(final List<CrossfireIceServer> iceServers) {
            return this;
        }

        @Override
        public Builder withOfferOptions(final CrossfireOfferOptions options) {
            return this;
        }

        @Override
        public Builder withDataChannelConfig(final CrossfireDataChannelConfig config) {
            return this;
        }

        @Override
        public Crossfire build() {
            return new TeaVMCrossfire();
        }

    }

}
