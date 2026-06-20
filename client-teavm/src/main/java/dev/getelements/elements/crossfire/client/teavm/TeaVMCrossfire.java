package dev.getelements.elements.crossfire.client.teavm;

import dev.getelements.elements.crossfire.api.model.Protocol;
import dev.getelements.elements.crossfire.api.model.signal.HostBroadcastSignal;
import dev.getelements.elements.crossfire.client.*;
import dev.getelements.elements.crossfire.client.teavm.signaling.TeaVMSignalingMatchClient;
import dev.getelements.elements.crossfire.client.teavm.signaling.TeaVMSignalingMatchHost;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.getelements.elements.crossfire.api.model.Protocol.SIGNALING;

/**
 * {@link AbstractCrossfire} implementation for TeaVM browser targets.
 * Supports signaling-relay mode; browser WebRTC is not yet implemented.
 */
public class TeaVMCrossfire extends AbstractCrossfire {

    public TeaVMCrossfire(final Protocol defaultProtocol,
                          final Set<Crossfire.Mode> supportedModes,
                          final SignalingClient signaling,
                          final java.util.function.Supplier<URI> defaultUriSupplier) {
        super(defaultProtocol, supportedModes, signaling, defaultUriSupplier);
    }

    @Override
    public TeaVMCrossfire connect(final URI uri) {
        final var ws = TeaVMWebSocket.create(uri.toString());
        ((TeaVMV10SignalingClient) getSignalingClient()).connect(ws);
        return this;
    }

    @Override
    protected void populateHosts(final Map<Protocol, MatchHost> hosts,
                                  final EnumSet<Crossfire.Mode> modes,
                                  final HostBroadcastSignal signal) {
        if (modes.contains(Crossfire.Mode.SIGNALING_HOST)) {
            hosts.put(SIGNALING, new TeaVMSignalingMatchHost(getSignalingClient()));
        }
    }

    @Override
    protected void populateClients(final Map<Protocol, MatchClient> clients,
                                    final EnumSet<Crossfire.Mode> modes,
                                    final HostBroadcastSignal signal) {
        if (modes.contains(Crossfire.Mode.SIGNALING_CLIENT)) {
            clients.put(SIGNALING, new TeaVMSignalingMatchClient(getSignalingClient()));
        }
    }

    public static class Builder implements Crossfire.Builder {

        private Protocol defaultProtocol = Protocol.SIGNALING;

        private Set<Crossfire.Mode> supportedModes =
                EnumSet.of(Crossfire.Mode.SIGNALING_HOST, Crossfire.Mode.SIGNALING_CLIENT);

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
        public Builder withSupportedModes(final Set<Crossfire.Mode> modes) {
            this.supportedModes = modes;
            return this;
        }

        @Override
        public Builder withSupportedModes(final Crossfire.Mode... modes) {
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
                    new TeaVMV10SignalingClient(),
                    () -> capturedUri
            );
        }

    }

}
