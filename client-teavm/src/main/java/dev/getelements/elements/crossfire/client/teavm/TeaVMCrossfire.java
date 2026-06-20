package dev.getelements.elements.crossfire.client.teavm;

import dev.getelements.elements.crossfire.api.model.Protocol;
import dev.getelements.elements.crossfire.api.model.signal.HostBroadcastSignal;
import dev.getelements.elements.crossfire.client.*;
import dev.getelements.elements.crossfire.client.teavm.signaling.TeaVMSignalingMatchClient;
import dev.getelements.elements.crossfire.client.teavm.signaling.TeaVMSignalingMatchHost;
import dev.getelements.elements.crossfire.client.teavm.webrtc.TeaVMWebRTCMatchClient;
import dev.getelements.elements.crossfire.client.teavm.webrtc.TeaVMWebRTCMatchHost;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.getelements.elements.crossfire.api.model.Protocol.SIGNALING;
import static dev.getelements.elements.crossfire.api.model.Protocol.WEBRTC;

/**
 * {@link AbstractCrossfire} implementation for TeaVM browser targets.
 * Supports both signaling-relay and browser WebRTC modes.
 */
public class TeaVMCrossfire extends AbstractCrossfire {

    private final String iceServersJson;

    public TeaVMCrossfire(final Protocol defaultProtocol,
                          final Set<Crossfire.Mode> supportedModes,
                          final SignalingClient signaling,
                          final java.util.function.Supplier<URI> defaultUriSupplier,
                          final String iceServersJson) {
        super(defaultProtocol, supportedModes, signaling, defaultUriSupplier);
        this.iceServersJson = iceServersJson;
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
        if (modes.contains(Crossfire.Mode.WEBRTC_HOST)) {
            hosts.put(WEBRTC, new TeaVMWebRTCMatchHost(getSignalingClient(), iceServersJson));
        }
    }

    @Override
    protected void populateClients(final Map<Protocol, MatchClient> clients,
                                    final EnumSet<Crossfire.Mode> modes,
                                    final HostBroadcastSignal signal) {
        if (modes.contains(Crossfire.Mode.SIGNALING_CLIENT)) {
            clients.put(SIGNALING, new TeaVMSignalingMatchClient(getSignalingClient()));
        }
        if (modes.contains(Crossfire.Mode.WEBRTC_CLIENT)) {
            clients.put(WEBRTC, new TeaVMWebRTCMatchClient(getSignalingClient(), iceServersJson));
        }
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static class Builder implements Crossfire.Builder {

        private Protocol defaultProtocol = Protocol.SIGNALING;

        private Set<Crossfire.Mode> supportedModes = EnumSet.allOf(Crossfire.Mode.class);

        private URI defaultUri;

        private List<CrossfireIceServer> iceServers = CrossfireIceServer.googleDefaults();

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
            this.iceServers = iceServers;
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
            final var capturedUri    = defaultUri;
            final var iceServersJson = buildIceServersJson(iceServers);
            return new TeaVMCrossfire(
                    defaultProtocol,
                    supportedModes,
                    new TeaVMV10SignalingClient(),
                    () -> capturedUri,
                    iceServersJson
            );
        }

        private static String buildIceServersJson(final List<CrossfireIceServer> servers) {
            if (servers == null || servers.isEmpty()) return "[]";
            final var sb = new StringBuilder("[");
            for (int i = 0; i < servers.size(); i++) {
                if (i > 0) sb.append(",");
                final var s = servers.get(i);
                sb.append("{\"urls\":[");
                final var urls = s.urls();
                for (int j = 0; j < urls.size(); j++) {
                    if (j > 0) sb.append(",");
                    sb.append("\"").append(urls.get(j)).append("\"");
                }
                sb.append("]");
                if (s.username() != null) {
                    sb.append(",\"username\":\"").append(s.username()).append("\"");
                }
                if (s.password() != null) {
                    sb.append(",\"credential\":\"").append(s.password()).append("\"");
                }
                sb.append("}");
            }
            sb.append("]");
            return sb.toString();
        }

    }

}
