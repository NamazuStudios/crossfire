package dev.getelements.elements.crossfire.client;

import dev.getelements.elements.crossfire.client.signaling.SignalingMatchClient;
import dev.getelements.elements.crossfire.client.signaling.SignalingMatchHost;
import dev.getelements.elements.crossfire.client.v10.V10SignalingClient;
import dev.getelements.elements.crossfire.client.webrtc.WebRTCMatchClient;
import dev.getelements.elements.crossfire.client.webrtc.WebRTCMatchHost;
import dev.getelements.elements.crossfire.api.model.Protocol;
import dev.onvoid.webrtc.RTCDataChannelInit;
import dev.onvoid.webrtc.RTCIceServer;
import dev.onvoid.webrtc.RTCOfferOptions;
import dev.onvoid.webrtc.TlsCertPolicy;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.WebSocketContainer;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Supplier;

import static dev.getelements.elements.crossfire.api.model.Protocol.WEBRTC;
import static java.util.Objects.requireNonNull;

/**
 * {@link AbstractCrossfire} implementation backed by the onvoid JNI WebRTC library and a
 * Jakarta WebSocket signaling transport.
 */
public class OnvoidCrossfire extends AbstractCrossfire {

    private final WebSocketContainer webSocketContainer;

    private final Supplier<WebRTCMatchHost.Builder> webrtcHostBuilder;

    private final Supplier<WebRTCMatchClient.Builder> webrtcClientBuilder;

    public OnvoidCrossfire(
            final Protocol defaultProtocol,
            final Set<Mode> supportedModes,
            final SignalingClient signaling,
            final Supplier<URI> defaultUriSupplier,
            final WebSocketContainer webSocketContainer,
            final Supplier<WebRTCMatchHost.Builder> webrtcHostBuilder,
            final Supplier<WebRTCMatchClient.Builder> webrtcClientBuilder) {

        super(defaultProtocol, supportedModes, signaling, defaultUriSupplier);
        this.webSocketContainer = requireNonNull(webSocketContainer, "webSocketContainer");
        this.webrtcHostBuilder  = requireNonNull(webrtcHostBuilder, "webrtcHostBuilder");
        this.webrtcClientBuilder = requireNonNull(webrtcClientBuilder, "webrtcClientBuilder");

    }

    @Override
    public OnvoidCrossfire connect(final URI uri) {

        try {
            webSocketContainer.connectToServer(getSignalingClient(), uri);
        } catch (IOException | DeploymentException e) {
            throw new SignalingClientException(e);
        }

        return this;

    }

    @Override
    protected void populateHosts(final Map<Protocol, MatchHost> hosts,
                                  final EnumSet<Mode> modes,
                                  final dev.getelements.elements.crossfire.api.model.signal.HostBroadcastSignal signal) {
        modes.forEach(m -> {
            switch (m) {
                case WEBRTC_HOST    -> hosts.put(m.getProtocol(), webrtcHostBuilder.get()
                        .withSignalingClient(getSignalingClient())
                        .build());
                case SIGNALING_HOST -> hosts.put(m.getProtocol(), new SignalingMatchHost(getSignalingClient()));
            }
        });
    }

    @Override
    protected void populateClients(final Map<Protocol, MatchClient> clients,
                                    final EnumSet<Mode> modes,
                                    final dev.getelements.elements.crossfire.api.model.signal.HostBroadcastSignal signal) {
        modes.forEach(m -> {
            switch (m) {
                case WEBRTC_CLIENT    -> clients.put(m.getProtocol(), webrtcClientBuilder.get()
                        .withRemoteProfileId(signal.getProfileId())
                        .withSignalingClient(getSignalingClient())
                        .build());
                case SIGNALING_CLIENT -> clients.put(m.getProtocol(), new SignalingMatchClient(getSignalingClient()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static class Builder implements Crossfire.Builder {

        private Protocol defaultProtocol = WEBRTC;

        private Set<Mode> supportedModes = EnumSet.allOf(Mode.class);

        private Supplier<URI> defaultUriSupplier = () -> {

            final var env = System.getenv(URI_ENV_VARIABLE);
            final var property = System.getProperty(URI_SYSTEM_PROPERTY, env);

            if (property == null) {
                throw new SignalingClientException("No default URI specified. Set the "
                        + URI_ENV_VARIABLE + " environment variable or the "
                        + URI_SYSTEM_PROPERTY + " system property."
                );
            }

            try {
                return new URI(property);
            } catch (URISyntaxException ex) {
                throw new SignalingClientException(ex);
            }

        };

        private Supplier<WebSocketContainer> webSocketContainerSupplier;

        private Supplier<SignalingClient> signalingClientSupplier = V10SignalingClient::new;

        private Supplier<WebRTCMatchHost.Builder> webrtcHostBuilder = WebRTCMatchHost.Builder::new;

        private Supplier<WebRTCMatchClient.Builder> webrtcClientBuilder = WebRTCMatchClient.Builder::new;

        private List<CrossfireIceServer> iceServers = CrossfireIceServer.googleDefaults();

        private CrossfireOfferOptions offerOptions = CrossfireOfferOptions.defaults();

        private CrossfireDataChannelConfig dataChannelConfig = CrossfireDataChannelConfig.defaults();

        @Override
        public Builder withDefaultUri(final URI uri) {
            return withDefaultUriSupplier(() -> uri);
        }

        public Builder withWebSocketContainer(final WebSocketContainer webSocketContainer) {
            return withWebSocketContainerSupplier(() -> webSocketContainer);
        }

        public Builder withWebSocketContainerSupplier(final Supplier<WebSocketContainer> webSocketContainerSupplier) {
            requireNonNull(webSocketContainerSupplier, "WebSocket container supplier must be specified.");
            this.webSocketContainerSupplier = webSocketContainerSupplier;
            return this;
        }

        public Builder withDefaultUriSupplier(final Supplier<URI> defaultUriSupplier) {
            requireNonNull(defaultUriSupplier, "Default URI supplier must be specified.");
            this.defaultUriSupplier = defaultUriSupplier;
            return this;
        }

        @Override
        public Builder withDefaultProtocol(final Protocol defaultProtocol) {
            requireNonNull(defaultProtocol, "Default protocol must be specified.");
            this.defaultProtocol = defaultProtocol;
            return this;
        }

        @Override
        public Builder withSupportedModes(final Mode... supportedModes) {
            return withSupportedModes(Set.of(supportedModes));
        }

        @Override
        public Builder withSupportedModes(final Set<Mode> supportedModes) {
            requireNonNull(supportedModes, "Supported modes must be specified.");
            if (supportedModes.isEmpty())
                throw new IllegalArgumentException("At least one supported mode must be specified.");
            this.supportedModes = supportedModes;
            return this;
        }

        public Builder withSignalingClientSupplier(final SignalingClient signaling) {
            requireNonNull(signaling, "Signaling client must be specified.");
            this.signalingClientSupplier = () -> signaling;
            return this;
        }

        public Builder withWebRTCHostBuilder(final Supplier<WebRTCMatchHost.Builder> webrtcHostBuilder) {
            requireNonNull(webrtcHostBuilder, "WebRTC host builder must be specified.");
            this.webrtcHostBuilder = webrtcHostBuilder;
            return this;
        }

        public Builder withWebRTCClientBuilder(final Supplier<WebRTCMatchClient.Builder> webrtcClientBuilder) {
            requireNonNull(webrtcClientBuilder, "WebRTC client builder must be specified.");
            this.webrtcClientBuilder = webrtcClientBuilder;
            return this;
        }

        @Override
        public Builder withIceServers(final List<CrossfireIceServer> iceServers) {
            requireNonNull(iceServers, "ICE servers must be specified.");
            this.iceServers = iceServers;
            return this;
        }

        @Override
        public Builder withOfferOptions(final CrossfireOfferOptions options) {
            requireNonNull(options, "Offer options must be specified.");
            this.offerOptions = options;
            return this;
        }

        @Override
        public Builder withDataChannelConfig(final CrossfireDataChannelConfig config) {
            requireNonNull(config, "Data channel config must be specified.");
            this.dataChannelConfig = config;
            return this;
        }

        @Override
        public OnvoidCrossfire build() {

            final var rtcIceServers = toRtcIceServers(iceServers);
            final var capturedOfferOptions = offerOptions;
            final var capturedDataChannelConfig = dataChannelConfig;

            final Supplier<WebRTCMatchHost.Builder> effectiveHostBuilder = () ->
                    webrtcHostBuilder.get()
                            .withIceServers(rtcIceServers)
                            .withRtcOfferOptionsSupplier(() -> toRtcOfferOptions(capturedOfferOptions))
                            .withDataChanelInitSupplier(() -> toRtcDataChannelInit(capturedDataChannelConfig));

            final Supplier<WebRTCMatchClient.Builder> effectiveClientBuilder = () ->
                    webrtcClientBuilder.get()
                            .withIceServers(rtcIceServers);

            final var websocketContainer = webSocketContainerSupplier == null
                    ? SharedWebSocketContainer.getInstance()
                    : webSocketContainerSupplier.get();

            return new OnvoidCrossfire(
                    defaultProtocol,
                    supportedModes,
                    signalingClientSupplier.get(),
                    defaultUriSupplier,
                    websocketContainer,
                    effectiveHostBuilder,
                    effectiveClientBuilder
            );

        }

        private static List<RTCIceServer> toRtcIceServers(final List<CrossfireIceServer> crossfireIceServers) {
            return crossfireIceServers.stream().map(cs -> {
                final var server = new RTCIceServer();
                server.urls = cs.urls();
                if (cs.username() != null) server.username = cs.username();
                if (cs.password() != null) server.password = cs.password();
                if (cs.hostname() != null) server.hostname = cs.hostname();
                if (cs.tlsCertPolicy() != null) {
                    server.tlsCertPolicy = switch (cs.tlsCertPolicy()) {
                        case SECURE -> TlsCertPolicy.SECURE;
                        case INSECURE_NO_CHECK -> TlsCertPolicy.INSECURE_NO_CHECK;
                    };
                }
                return server;
            }).toList();
        }

        private static RTCOfferOptions toRtcOfferOptions(final CrossfireOfferOptions options) {
            final var rtc = new RTCOfferOptions();
            rtc.iceRestart = options.iceRestart();
            rtc.voiceActivityDetection = options.voiceActivityDetection();
            return rtc;
        }

        private static RTCDataChannelInit toRtcDataChannelInit(final CrossfireDataChannelConfig config) {
            final var rtc = new RTCDataChannelInit();
            rtc.ordered = config.ordered();
            rtc.negotiated = config.negotiated();
            rtc.maxPacketLifeTime = config.maxPacketLifeTime();
            rtc.maxRetransmits = config.maxRetransmits();
            rtc.id = config.id();
            if (config.protocol() != null) rtc.protocol = config.protocol();
            return rtc;
        }

    }

}
