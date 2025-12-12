package dev.getelements.elements.crossfire.client.webrtc;

import dev.getelements.elements.crossfire.api.model.signal.*;
import dev.getelements.elements.crossfire.client.PeerException;
import dev.getelements.elements.crossfire.client.PeerStatus;
import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.Publisher;
import dev.getelements.elements.sdk.util.SimpleLazyValue;
import dev.onvoid.webrtc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class WebRTCAnsweringPeer extends WebRTCPeer {

    private static final Logger logger = LoggerFactory.getLogger(WebRTCAnsweringPeer.class);

    static {
        WebRTC.load();
    }

    private final Record peerRecord;

    private final Subscription subscription;

    private final PeerConnectionObserver peerConnectionObserver = new WebRTCPeer.ConnectionObserver(WebRTCAnsweringPeer.class) {

        @Override
        public void onDataChannel(final RTCDataChannel dataChannel) {
            final var dataChannelObserver = newDataChannelObserver(dataChannel);
            dataChannel.registerObserver(dataChannelObserver);
            peerConnectionState.updateAndGet(s -> s.channel(dataChannel));
        }

    };

    public WebRTCAnsweringPeer(final Record peerRecord) {
        super(peerRecord.signaling, peerRecord.onPeerStatus);

        logger.debug("Creating answering peer for {} -> {}",
                peerRecord.localProfileId(),
                peerRecord.remoteProfileId()
        );

        this.peerRecord = requireNonNull(peerRecord, "peerRecord");
        this.subscription = peerRecord.signaling
                .onSignal((s, signal) -> onSignal(signal));

    }

    public void connect() {

        final var connection = new SimpleLazyValue<>(() -> peerRecord.peerConnectionConstructor.apply(peerConnectionObserver));

        final var result = peerConnectionState.updateAndGet(existing -> existing.connection() == null
                ? existing.connect(connection.get())
                : existing
        );

        connection.getOptional()
                .filter(c -> result.connection() != c)
                .ifPresent(RTCPeerConnection::close);

        peerRecord
                .signaling()
                .backlog()
                .forEach(this::onSignal);

    }

    @Override
    protected Optional<RTCDataChannel> findDataChannel() {
        return peerConnectionState.get().findChannel();
    }

    @Override
    public String getProfileId() {
        return peerRecord.remoteProfileId();
    }

    @Override
    protected String getLocalProfileId() {
        return peerRecord.localProfileId();
    }

    private void onSignal(final Signal signal) {
        switch (signal.getType()) {
            case CANDIDATE -> onSignalCandidate((CandidateDirectSignal) signal);
            case SDP_OFFER -> onSignalOffer((SdpOfferDirectSignal) signal);
            case DISCONNECT -> onSignalDisconnect((DisconnectBroadcastSignal) signal);
        }
    }

    private void onSignalOffer(final SdpOfferDirectSignal offer) {
        if (getProfileId().equals(offer.getProfileId())) {

            logger.debug("Got SDP OFFER {} -> {}\n{}",
                    offer.getProfileId(),
                    offer.getRecipientProfileId(),
                    offer.getPeerSdp()
            );

            final var description = new RTCSessionDescription(RTCSdpType.OFFER, offer.getPeerSdp());
            updateAndGet(s -> s.description(description));

        } else {
            logger.warn("Dropping SDP OFFER from {} intended for {} (not relevant to this peer {}).",
                    offer.getProfileId(),
                    offer.getRecipientProfileId(),
                    getProfileId()
            );
        }
    }

    private void onSignalDisconnect(final DisconnectBroadcastSignal signal) {

        final var profileId = signal.getProfileId();

        if (profileId.equals(peerRecord.localProfileId()) || profileId.equals(peerRecord.remoteProfileId())) {
            close();
        }

    }

    @Override
    protected void startICE(final WebRTCPeerConnectionState state) {

        final var description = state.description();

        logger.debug("Setting {}'s remote {} description for {}:\n{}",
                WebRTCAnsweringPeer.this.getClass().getSimpleName(),
                description.sdpType,
                getProfileId(),
                description.sdp
        );

        final var connection = state.connection();

        connection.setRemoteDescription(state.description(), new SetSessionDescriptionObserver() {

            @Override
            public void onSuccess() {

                logger.debug("Successfully set {}'s remote session description for {}",
                        WebRTCAnsweringPeer.this.getClass().getSimpleName(),
                        getProfileId()
                );

                createAnswer(state);

            }

            @Override
            public void onFailure(String error) {

                logger.error("Failed to set {}'s remote description for {}: {}. Closing connection.",
                        WebRTCAnsweringPeer.this.getClass().getSimpleName(),
                        getProfileId(),
                        error
                );

                onError.publish(new PeerException(error));
                close();

            }

        });

        state.candidates().forEach(candidate -> {

            logger.debug("Adding the candidates for remote peer {}:\n{}",
                    getProfileId(),
                    candidate
            );

            connection.addIceCandidate(candidate);

        });

    }

    private void createAnswer(final WebRTCPeerConnectionState state) {

        logger.debug("Creating {}'s answer session description for {}.",
                WebRTCAnsweringPeer.this.getClass().getSimpleName(),
                getProfileId()
        );

        state.connection().createAnswer(peerRecord.answerOptions, new CreateSessionDescriptionObserver() {

                @Override
                public void onSuccess(final RTCSessionDescription description) {

                    logger.debug("Successfully {}'s set local session description for {}.",
                            WebRTCAnsweringPeer.this.getClass().getSimpleName(),
                            getProfileId()
                    );

                    setLocalDescription(description, state);

                }

                @Override
                public void onFailure(final String error) {
                    logger.error("Failed to create answer: {}. Closing connection.", error);
                    onError.publish(new PeerException(error));
                    close();
                }

            });
    }

    private void setLocalDescription(final RTCSessionDescription description,
                                     final WebRTCPeerConnectionState state) {

        logger.debug("Setting {}'s local description for remote peer {}:\n{}",
                WebRTCAnsweringPeer.this.getClass().getSimpleName(),
                getProfileId(),
                description.sdp
        );

        final var connection = state.connection();

        connection.setLocalDescription(description, new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {

                final var signal = new SdpAnswerDirectSignal();
                signal.setPeerSdp(description.sdp);
                signal.setProfileId(peerRecord.localProfileId());
                signal.setRecipientProfileId(peerRecord.remoteProfileId());

                logger.debug("Signaling {}'s answer to offerer: {} -> {}\n{}",
                        WebRTCAnsweringPeer.this.getClass().getSimpleName(),
                        signal.getProfileId(),
                        signal.getRecipientProfileId(),
                        signal.getPeerSdp()
                );

                peerRecord.signaling.signal(signal);

            }

            @Override
            public void onFailure(String error) {

                logger.error("Failed to set {}'s description: {}. Closing connection.",
                        WebRTCAnsweringPeer.this.getClass().getSimpleName(),
                        error
                );

                onError.publish(new PeerException(error));
                close();
            }

        });

    }

    @Override
    protected void close(final WebRTCPeerConnectionState state) {
        subscription.unsubscribe();
    }

    public record Record(
            String remoteProfileId,
            SignalingClient signaling,
            RTCAnswerOptions answerOptions,
            Publisher<PeerStatus> onPeerStatus,
            Function<PeerConnectionObserver, RTCPeerConnection> peerConnectionConstructor) {

        public Record {
            requireNonNull(remoteProfileId, "remoteProfileId must not be null");
            requireNonNull(signaling, "signaling must not be null");
            requireNonNull(answerOptions, "answerOptions must not be null");
            requireNonNull(peerConnectionConstructor, "peerConnectionConstructor must not be null");
            requireNonNull(onPeerStatus, "onPeerStatus must not be null");
        }

        public String localProfileId() {
            return signaling.getState().getProfileId();
        }

    }

}
