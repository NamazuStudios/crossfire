package dev.getelements.elements.crossfire.client.teavm;

import dev.getelements.elements.crossfire.api.model.Protocol;
import dev.getelements.elements.crossfire.api.model.signal.HostBroadcastSignal;
import dev.getelements.elements.crossfire.client.*;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skeleton {@link AbstractCrossfire} for TeaVM browser targets.
 * Browser WebRTC bindings (RTCPeerConnection JS interop) are not yet implemented.
 */
public class TeaVMCrossfire extends AbstractCrossfire {

    public TeaVMCrossfire(final Protocol defaultProtocol,
                          final Set<Mode> supportedModes,
                          final SignalingClient signaling,
                          final java.util.function.Supplier<URI> defaultUriSupplier) {
        super(defaultProtocol, supportedModes, signaling, defaultUriSupplier);
    }

    @Override
    public TeaVMCrossfire connect(final URI uri) {
        throw new UnsupportedOperationException("TeaVM WebRTC not yet implemented");
    }

    @Override
    protected void populateHosts(final Map<Protocol, MatchHost> hosts,
                                  final EnumSet<Mode> modes,
                                  final HostBroadcastSignal signal) {
        throw new UnsupportedOperationException("TeaVM WebRTC not yet implemented");
    }

    @Override
    protected void populateClients(final Map<Protocol, MatchClient> clients,
                                    final EnumSet<Mode> modes,
                                    final HostBroadcastSignal signal) {
        throw new UnsupportedOperationException("TeaVM WebRTC not yet implemented");
    }

    public static class Builder implements Crossfire.Builder {

        private Protocol defaultProtocol = Protocol.WEBRTC;

        private Set<Mode> supportedModes = EnumSet.allOf(Mode.class);

        private URI defaultUri;

        @Override
        public Builder withDefaultUri(final URI uri) {
            this.defaultUri = uri;
            return this;
        }

        @Override
        public Builder withDefaultProtocol(final Protocol protocol) {
            this.defaultProtocol = protocol;
            return this;
        }

        @Override
        public Builder withSupportedModes(final Set<Mode> modes) {
            this.supportedModes = modes;
            return this;
        }

        @Override
        public Builder withSupportedModes(final Mode... modes) {
            this.supportedModes = Set.of(modes);
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
            final var capturedUri = defaultUri;
            return new TeaVMCrossfire(
                    defaultProtocol,
                    supportedModes,
                    new TeaVMSignalingClient(),
                    () -> capturedUri
            );
        }

    }

}
