package dev.getelements.elements.crossfire.client.webrtc;

import dev.getelements.elements.crossfire.client.MatchClient;
import dev.getelements.elements.crossfire.client.Peer;
import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.model.error.ProtocolError;
import dev.getelements.elements.sdk.Subscription;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.RTCAnswerOptions;
import dev.onvoid.webrtc.RTCConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * An implementation of {@link MatchClient} which uses WebRTC as the backing protocol.
 */
public class WebRTCMatchClient implements MatchClient {

    private static final Logger logger = LoggerFactory.getLogger(WebRTCMatchClient.class);

    private final Subscription subscription;

    private final AtomicBoolean open = new AtomicBoolean(true);

    private final WebRTCMatchClientPeer peer;

    public WebRTCMatchClient(
            final String profileId,
            final String remoteProfileId,
            final SignalingClient signaling,
            final PeerConnectionFactory peerConnectionFactory,
            final Function<String, RTCConfiguration> peerConfigurationProvider,
            final Supplier<RTCAnswerOptions> answerOptionsSupplier) {

        requireNonNull(profileId, "profileId");
        requireNonNull(signaling, "signaling");
        requireNonNull(peerConnectionFactory, "peerConnectionFactory");
        requireNonNull(peerConfigurationProvider, "peerConfigurationProvider");
        requireNonNull(answerOptionsSupplier, "offerOptionsSupplier");

        this.subscription = Subscription.begin()
                .chain(signaling.onClientError(this::onClientError))
                .chain(signaling.onProtocolError(this::onProtocolError));

        this.peer = new WebRTCMatchClientPeer(new WebRTCMatchClientPeer.Record(
                profileId,
                remoteProfileId,
                signaling,
                answerOptionsSupplier.get(),
                observer -> {
                    final var configuration = peerConfigurationProvider.apply(remoteProfileId);
                    return peerConnectionFactory.createPeerConnection(configuration, observer);
                }
        ));

    }

    private void onClientError(final Subscription subscription, final Throwable throwable) {
        logger.error("Client error. Terminating host.");
        close();
    }

    private void onProtocolError(final Subscription subscription, final ProtocolError protocolError) {

        logger.error("Protocol error. Terminating host: {} - {}",
                protocolError.getCode(),
                protocolError.getMessage()
        );

        close();

    }

    @Override
    public Peer getPeer() {
        return peer;
    }

    @Override
    public void close() {
        if (open.compareAndSet(true, false)) {
            peer.close();
            subscription.unsubscribe();
        }
    }

    public static class Builder {

        private String profileId;

        private String remoteProfileId;

        private SignalingClient signaling;

        private PeerConnectionFactory peerConnectionFactory;

        private Supplier<RTCAnswerOptions> answerOptionsSupplier = RTCAnswerOptions::new;

        private Function<String, RTCConfiguration> peerConfigurationProvider = pid -> new RTCConfiguration();

        /**
         * Sets the profile id of this client.
         *
         * @param profileId the profile id
         * @return this builder
         */
        public Builder withProfileId(final String profileId) {
            this.profileId = profileId;
            return this;
        }

        /**
         * Sets the profile id of the remote peer to connect to.
         *
         * @param remoteProfileId the remote profile id
         * @return this builder
         */
        public Builder withRemoteProfileId(final String remoteProfileId) {
            this.remoteProfileId = remoteProfileId;
            return this;
        }

        /**
         * Sets the signaling client to use for signaling.
         * @param signaling the signaling client
         * @return this builder
         */
        public Builder withSignalingClient(final SignalingClient signaling) {
            this.signaling = signaling;
            return this;
        }

        /**
         * Sets the peer connection factory to use for creating peer connections.
         *
         * @param peerConnectionFactory the peer connection factory
         * @return this builder
         */
        public Builder withPeerConnectionFactory(final PeerConnectionFactory peerConnectionFactory) {
            this.peerConnectionFactory = peerConnectionFactory;
            return this;
        }

        /**
         * Sets the provider for RTCConfiguration for a given peer profile id.
         *
         * @param peerConfigurationProvider
         * @return
         */
        public Builder withPeerConfigurationProvider(final Function<String, RTCConfiguration> peerConfigurationProvider) {
            this.peerConfigurationProvider = peerConfigurationProvider;
            return this;
        }

        /**
         * Sets the supplier for RTCOfferOptions to use when creating an offer.
         *
         * @param answerOptionsSupplier the offer options supplier
         * @return this builder
         */
        public Builder withOfferOptionsSupplier(final Supplier<RTCAnswerOptions> answerOptionsSupplier) {
            this.answerOptionsSupplier = answerOptionsSupplier;
            return this;
        }

        /**
         * Builds the WebRTCMatchClient.
         *
         * @return the newly built instance
         */
        public WebRTCMatchClient build() {

            // We check for the null value of the connection factory here to avoid static initialization
            // in case the user provides their own instance.

            final var pcf = peerConnectionFactory == null
                    ? SharedPeerConnectionFactory.getInstance()
                    : peerConnectionFactory;

            return new WebRTCMatchClient(
                    profileId,
                    remoteProfileId,
                    signaling,
                    pcf,
                    peerConfigurationProvider,
                    answerOptionsSupplier
            );

        }
    }

}
