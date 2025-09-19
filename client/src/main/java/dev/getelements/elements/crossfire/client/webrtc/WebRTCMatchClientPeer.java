package dev.getelements.elements.crossfire.client.webrtc;

import dev.getelements.elements.crossfire.client.PeerException;
import dev.getelements.elements.crossfire.client.PeerStatus;
import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.model.signal.*;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.Publisher;
import dev.getelements.elements.sdk.util.SimpleLazyValue;
import dev.onvoid.webrtc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class WebRTCMatchClientPeer extends WebRTCPeer {

    private static final Logger logger = LoggerFactory.getLogger(WebRTCMatchClientPeer.class);

    private final Record peerRecord;

    private final Subscription subscription;

    private final AtomicReference<WebRTCPeerConnectionState> peerConnectionState = new AtomicReference<>(WebRTCPeerConnectionState.create());

    private final PeerConnectionObserver peerConnectionObserver = new LoggingPeerConnectionObserver() {

        @Override
        public Logger getLogger() {
            return logger;
        }

        @Override
        public void onIceCandidate(final RTCIceCandidate candidate) {
            final var signal = new CandidateDirectSignal();
            signal.setProfileId(peerRecord.profileId());
            signal.setRecipientProfileId(peerRecord.remoteProfileId());
            signal.setMid(candidate.sdpMid);
            signal.setCandidate(candidate.sdp);
            signal.setMidIndex(candidate.sdpMLineIndex);
            peerRecord.signaling().signal(signal);
        }

        @Override
        public void onIceCandidateError(final RTCPeerConnectionIceErrorEvent event) {

            logger.error("ICE candidate error: {} for remote {}",
                    event.getErrorText(),
                    peerRecord.remoteProfileId
            );

            onError.publish(new PeerException(event.getErrorText()));
            close();

        }

        @Override
        public void onConnectionChange(final RTCPeerConnectionState state) {
            logger.debug("Connection state {} for remote {}", state, peerRecord.remoteProfileId);
        }

        @Override
        public void onDataChannel(final RTCDataChannel dataChannel) {
            final var dataChannelObserver = newDataChannelObserver(dataChannel);
            dataChannel.registerObserver(dataChannelObserver);
            peerConnectionState.updateAndGet(s -> s.channel(dataChannel));
        }

    };

    public WebRTCMatchClientPeer(final Record peerRecord) {
        super(peerRecord.signaling, peerRecord.onPeerStatus);
        this.peerRecord = requireNonNull(peerRecord, "peerRecord");
        this.subscription = peerRecord.signaling
                .onSignal((s, signal) -> onSignal(signal))
                .chain(super.subscription);
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

        processSignalBacklog();

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
    protected Optional<RTCPeerConnection> findPeerConnection() {
        return peerConnectionState.get().findConnection();
    }

    @Override
    public String getProfileId() {
        return peerRecord.remoteProfileId;
    }

    private void onSignal(final Signal signal) {
        switch (signal.getType()) {
            case SDP_OFFER -> onSignalOffer((SdpOfferDirectSignal) signal);
            case DISCONNECT -> onSignalDisconnect((DisconnectBroadcastSignal) signal);
        }
    }

    private void onSignalOffer(final SdpOfferDirectSignal offer) {

        // Ignore signals from peers that aren't relevant to this particular peer connection.

        if (!getProfileId().equals(offer.getProfileId())) {
            return;
        }

        peerConnectionState.get().findConnection().ifPresent(connection -> {

            final var description = new RTCSessionDescription(RTCSdpType.OFFER, offer.getPeerSdp());

            logger.debug("Got SDP OFFER From {}\n{}",
                    offer.getProfileId(),
                    offer.getPeerSdp()
            );

            connection.setRemoteDescription(description, new SetSessionDescriptionObserver() {

                @Override
                public void onSuccess() {
                    logger.info("Set remote session description for peer {}", peerRecord.remoteProfileId);
                    createAnswer();
                }

                @Override
                public void onFailure(final String error) {
                    logger.error("Failed to get answer: {}. Closing connection.", error);
                    close();
                }

            });

        });

    }

    private void createAnswer() {
        peerConnectionState
                .get()
                .findConnection()
                .ifPresent(connection ->
                    connection.createAnswer(peerRecord.answerOptions, new CreateSessionDescriptionObserver() {

                        @Override
                        public void onSuccess(final RTCSessionDescription description) {
                            final var signal = new SdpAnswerDirectSignal();
                            signal.setPeerSdp(description.sdp);
                            signal.setProfileId(peerRecord.profileId());
                            signal.setRecipientProfileId(peerRecord.remoteProfileId());
                            peerRecord.signaling.signal(signal);
                        }

                        @Override
                        public void onFailure(final String error) {
                            logger.error("Failed to create answer: {}. Closing connection.", error);
                            close();
                        }

                    })

                );
    }

    private void onSignalDisconnect(final DisconnectBroadcastSignal signal) {

        final var profileId = signal.getProfileId();

        if (profileId.equals(peerRecord.profileId()) || profileId.equals(peerRecord.remoteProfileId())) {
            close();
        }

    }

    @Override
    public void close() {

        final var old = peerConnectionState.getAndUpdate(WebRTCPeerConnectionState::close);

        if (old.open()) {
            subscription.unsubscribe();
            old.findChannel().ifPresent(RTCDataChannel::close);
            old.findConnection().ifPresent(RTCPeerConnection::close);
        }

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

        public String profileId() {
            return signaling.getState().getProfileId();
        }

    }

}
