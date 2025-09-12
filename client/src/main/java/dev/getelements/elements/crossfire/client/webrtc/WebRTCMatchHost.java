package dev.getelements.elements.crossfire.client.webrtc;

import dev.getelements.elements.crossfire.client.MatchHost;
import dev.getelements.elements.crossfire.client.Peer;
import dev.getelements.elements.crossfire.client.PeerStatus;
import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.model.Protocol;
import dev.getelements.elements.crossfire.model.error.ProtocolError;
import dev.getelements.elements.crossfire.model.signal.ConnectBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.DisconnectBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.ConcurrentDequePublisher;
import dev.getelements.elements.sdk.util.LazyValue;
import dev.getelements.elements.sdk.util.Publisher;
import dev.getelements.elements.sdk.util.SimpleLazyValue;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCDataChannelInit;
import dev.onvoid.webrtc.RTCOfferOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static dev.getelements.elements.crossfire.model.Protocol.WEBRTC;
import static java.util.Objects.requireNonNull;

/**
 * An implementation of {@link MatchHost} which uses WebRTC as the backing protocol.
 */
public class WebRTCMatchHost implements MatchHost {

    private static final Logger logger = LoggerFactory.getLogger(WebRTCMatchHost.class);

    private final SignalingClient signaling;

    private final PeerConnectionFactory peerConnectionFactory;

    private final Subscription subscription;

    private final Function<String, RTCConfiguration> peerConfigurationProvider;

    private final AtomicBoolean open = new AtomicBoolean(true);

    private final Supplier<RTCOfferOptions> offerOptionsSupplier;

    private final Supplier<RTCDataChannelInit> dataChannelInitSupplier;

    private final Publisher<PeerStatus> onPeerStatus = new ConcurrentDequePublisher<>();

    private final ConcurrentMap<String, WebRTCMatchHostPeer> connections = new ConcurrentHashMap<>();

    public WebRTCMatchHost(final SignalingClient signalingClient,
                           final PeerConnectionFactory peerConnectionFactory,
                           final Supplier<RTCOfferOptions> offerOptionsSupplier,
                           final Supplier<RTCDataChannelInit> dataChannelInitSupplier,
                           final Function<String, RTCConfiguration> peerConfigurationProvider) {
        this.signaling = requireNonNull(signalingClient, "signalingClient");
        this.peerConnectionFactory = requireNonNull(peerConnectionFactory, "peerConnectionFactory");
        this.offerOptionsSupplier = requireNonNull(offerOptionsSupplier, "offerOptionsSupplier");
        this.dataChannelInitSupplier = requireNonNull(dataChannelInitSupplier, "dataChannelInitSupplier");
        this.peerConfigurationProvider = requireNonNull(peerConfigurationProvider, "peerConfigurationProvider");
        this.subscription = Subscription.begin()
                .chain(this.signaling.onSignal(this::onSignal))
                .chain(this.signaling.onClientError(this::onClientError));
    }

    private void onSignal(final Subscription subscription, final Signal signal) {
        switch (signal.getType()) {
            case ERROR -> onProtocolError((ProtocolError) signal);
            case CONNECT -> onSignalConnect((ConnectBroadcastSignal) signal);
            case DISCONNECT -> onSignalDisconnect((DisconnectBroadcastSignal) signal);
        }
    }

    private void onSignalConnect(final ConnectBroadcastSignal signal) {
        logger.debug("Received connection signal: {}", signal);
        connect(signal.getProfileId());
    }

    private void onSignalDisconnect(final DisconnectBroadcastSignal signal) {
        connections.remove(signal.getProfileId());
        logger.debug("Removed peer {} due to disconnect signal", signal.getProfileId());
    }

    private void onProtocolError(final ProtocolError protocolError) {

        logger.error("Protocol error. Terminating host: {} - {}",
                protocolError.getCode(),
                protocolError.getMessage()
        );

        close();

    }

    private void onClientError(final Subscription subscription, final Throwable throwable) {
        logger.error("Client error. Terminating host.");
        close();
    }

    private void connect(final String remoteProfileId) {

        // The host does not connect to themselves
        if (Objects.equals(remoteProfileId, signaling.getState().getProfileId()))
            return;

        final var profileId = signaling.getState().getProfileId();

        final LazyValue<WebRTCMatchHostPeer> peer = new SimpleLazyValue<>(() -> new WebRTCMatchHostPeer(
                new WebRTCMatchHostPeer.Record(
                        remoteProfileId,
                        signaling,
                        "data-channel-" + profileId + "-" + remoteProfileId,
                        offerOptionsSupplier.get(),
                        dataChannelInitSupplier.get(),
                        onPeerStatus,
                        observer -> {
                            final var configuration = peerConfigurationProvider.apply(remoteProfileId);
                            return peerConnectionFactory.createPeerConnection(configuration, observer);
                        }
                )
            )
        );

        // Compute the connection if absent.
        final var connected = connections.computeIfAbsent(profileId, id -> peer.get());

        // Connect any peer that was actually used and put in the map

        peer.getOptional()
            .filter(p -> p == connected)
            .ifPresent(WebRTCMatchHostPeer::connect);

        // Close any peer that was created but not used. This shouldn't happen but this is safeguard
        // as the underlying host contains a handle to native resources which must be closed.

        peer.getOptional()
            .filter(p -> p != connected)
            .ifPresent(p -> {
                logger.warn("Duplicate peer for remote profile id {}", remoteProfileId);
                p.close();
            });

    }

    @Override
    public Protocol getProtocol() {
        return WEBRTC;
    }

    @Override
    public void start() {
        this.signaling.getState().getProfiles().forEach(this::connect);
    }

    @Override
    public Optional<Peer> findPeer(final String profileId) {
        return Optional.ofNullable(connections.get(profileId));
    }

    @Override
    public Subscription onPeerStatus(final BiConsumer<Subscription, PeerStatus> onPeerStatus) {
        return this.onPeerStatus.subscribe(onPeerStatus);
    }

    @Override
    public void close() {
        if (open.compareAndExchange(true, false)) {
            subscription.unsubscribe();
            connections.values().forEach(WebRTCMatchHostPeer::close);
            connections.clear();
        }
    }

    /**
     * Builds a new instance of WebRTCMatchHost.
     */
    public static class Builder {

        private SignalingClient signalingClient;

        private PeerConnectionFactory peerConnectionFactory;

        private Supplier<RTCOfferOptions> offerOptionsSupplier = RTCOfferOptions::new;

        private Supplier<RTCDataChannelInit> dataChannelInitSupplier = RTCDataChannelInit::new;

        private Function<String, RTCConfiguration> peerConfigurationProvider = pid -> new RTCConfiguration();

        /**
         * Specifies the {@link SignalingClient} to use to connect matches.
         *
         * @param signalingClient the signaling client
         * @return this instance
         */
        public Builder withSignalingClient(final SignalingClient signalingClient) {
            this.signalingClient = signalingClient;
            return this;
        }

        /**
         * Specifies a supplier that provides the {@link RTCOfferOptions} to use when creating offers. If not specified,
         *
         * @param offerOptionsSupplier the offer options supplier
         * @return this instance
         */
        public Builder withRtcOfferOptionsSupplier(final Supplier<RTCOfferOptions> offerOptionsSupplier) {
            this.offerOptionsSupplier = offerOptionsSupplier;
            return this;
        }

        /**
         * Specifies the {@link RTCDataChannelInit} to use when creating data channels. If not specified, the default
         * value will be used.
         *
         * @param dataChannelInitSupplier the data channel init supplier
         * @return this instance
         */
        public Builder withDataChanelInitSupplier(final Supplier<RTCDataChannelInit> dataChannelInitSupplier) {
            this.dataChannelInitSupplier = dataChannelInitSupplier;
            return this;
        }

        /**
         * Specifies the {@link PeerConnectionFactory} to use to connect matches. If set to null, then the
         * default {@link SharedPeerConnectionFactory} value will be used.
         *
         * @param peerConnectionFactory the peer connection factory
         * @return this instance
         */
        public Builder withPeerConnectionFactory(final PeerConnectionFactory peerConnectionFactory) {
            this.peerConnectionFactory = peerConnectionFactory;
            return this;
        }

        /**
         * Specifies a function that provides the {@link RTCConfiguration} to use when connecting to a peer with the
         * given provider.
         *
         * @param peerConfigurationProvider the peer configuration provider
         * @return this instance
         */
        public Builder withPeerConfigurationProvider(final Function<String, RTCConfiguration> peerConfigurationProvider) {

            this.peerConfigurationProvider = peerConfigurationProvider == null
                    ? pid -> new RTCConfiguration()
                    : peerConfigurationProvider;

            return this;

        }

        /**
         * Builds the {@link WebRTCMatchHost} instance.
         *
         * @return the new WebRTCMatchHost instance
         */
        public WebRTCMatchHost build() {

            if (signalingClient == null) {
                throw new IllegalStateException("All parameters must be set before building WebRTCMatchHost");
            }

            // We check for the null value of the connection factory here to avoid static initialization
            // in case the user provides their own instance.

            final var pcf = peerConnectionFactory == null
                    ? SharedPeerConnectionFactory.getInstance()
                    : peerConnectionFactory;

            return new WebRTCMatchHost(
                    signalingClient,
                    pcf,
                    offerOptionsSupplier,
                    dataChannelInitSupplier,
                    peerConfigurationProvider
            );

        }

    }

}
