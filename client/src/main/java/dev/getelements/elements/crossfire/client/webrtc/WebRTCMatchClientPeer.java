package dev.getelements.elements.crossfire.client.webrtc;

import dev.getelements.elements.crossfire.client.PeerError;
import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.model.signal.*;
import dev.getelements.elements.sdk.Subscription;
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

    private final RTCPeerConnection peerConnection;

    private final AtomicReference<RTCDataChannel> dataChannel = new AtomicReference<>();

    private final PeerConnectionObserver peerConnectionObserver = new PeerConnectionObserver() {

        @Override
        public void onIceCandidate(final RTCIceCandidate candidate) {
            final var signal = new CandidateDirectSignal();
            signal.setProfileId(peerRecord.profileId);
            signal.setRecipientProfileId(peerRecord.remoteProfileId);
            signal.setMid(candidate.sdpMid);
            signal.setCandidate(candidate.sdp);
            peerRecord.signaling.signal(signal);
        }

        @Override
        public void onIceCandidateError(final RTCPeerConnectionIceErrorEvent event) {

            logger.error("ICE candidate error: {} for remote {}",
                    event.getErrorText(),
                    peerRecord.remoteProfileId
            );

            onError.publish(new PeerError(event.getErrorText()));
            close();

        }

        @Override
        public void onConnectionChange(final RTCPeerConnectionState state) {
            logger.debug("Connection state {} for remote {}", state, peerRecord.remoteProfileId);
        }

        @Override
        public void onDataChannel(final RTCDataChannel dataChannel) {
            WebRTCMatchClientPeer.this.dataChannel.set(dataChannel);
        }

    };

    public WebRTCMatchClientPeer(final Record peerRecord) {
        this.peerRecord = requireNonNull(peerRecord, "peerRecord");
        this.peerConnection = peerRecord.peerConnectionConstructor().apply(peerConnectionObserver);
        this.subscription = peerRecord.signaling.onSignal(this::onSignal);
    }


    @Override
    protected Optional<RTCDataChannel> findDataChannel() {
        return Optional.ofNullable(dataChannel.get());
    }

    @Override
    public String getProfileId() {
        return peerRecord.remoteProfileId;
    }

    private void onSignal(final Subscription subscription, final Signal signal) {
        switch (signal.getType()) {
            case SDP_OFFER -> onSignalOffer(subscription, (SdpOfferDirectSignal) signal);
            case DISCONNECT -> onSignalDisconnect(subscription, (DisconnectBroadcastSignal) signal);
        }
    }

    private void onSignalOffer(final Subscription subscription,
                               final SdpOfferDirectSignal signal) {

        final var description = new RTCSessionDescription(RTCSdpType.OFFER, signal.getPeerSdp());

        peerConnection.setRemoteDescription(description, new SetSessionDescriptionObserver() {

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

    }

    private void createAnswer() {
        peerConnection.createAnswer(peerRecord.answerOptions, new CreateSessionDescriptionObserver() {

            @Override
            public void onSuccess(final RTCSessionDescription description) {
                final var signal = new SdpAnswerDirectSignal();
                signal.setPeerSdp(description.sdp);
                signal.setProfileId(peerRecord.profileId);
                signal.setRecipientProfileId(peerRecord.remoteProfileId);
                peerRecord.signaling.signal(signal);
            }

            @Override
            public void onFailure(final String error) {
                logger.error("Failed to create answer: {}. Closing connection.", error);
                close();
            }

        });
    }

    private void onSignalDisconnect(final Subscription subscription,
                                    final DisconnectBroadcastSignal signal) {

        final var profileId = signal.getProfileId();

        if (profileId.equals(peerRecord.profileId) || profileId.equals(peerRecord.remoteProfileId)) {
            doClose(subscription);
        }

    }

    @Override
    public void close() {
        doClose(subscription);
    }

    private void doClose(final Subscription subscription) {
        subscription.unsubscribe();
        peerConnection.close();
    }

    public record Record(
            String profileId,
            String remoteProfileId,
            SignalingClient signaling,
            RTCAnswerOptions answerOptions,
            Function<PeerConnectionObserver, RTCPeerConnection> peerConnectionConstructor) {}

}
