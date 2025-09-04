package dev.getelements.elements.crossfire.client.webrtc;

import dev.getelements.elements.crossfire.client.PeerError;
import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.model.signal.*;
import dev.getelements.elements.sdk.Subscription;
import dev.onvoid.webrtc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class WebRTCMatchHostPeer extends WebRTCPeer {

    private static final Logger logger = LoggerFactory.getLogger(WebRTCMatchHostPeer.class);

    private final Record peerRecord;

    private final Subscription subscription;

    private final RTCDataChannel dataChannel;

    private final RTCPeerConnection peerConnection;

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

    };

    private void createOffer() {

        final var options = peerRecord.offerOptions;

        peerConnection.createOffer(options, new CreateSessionDescriptionObserver() {

            @Override
            public void onSuccess(final RTCSessionDescription description) {
                logger.debug("Received session description: {}", description);
                setLocalDescription(description);
            }

            @Override
            public void onFailure(final String error) {
                logger.error("Failed to create offer: {}. Closing connection.", error);
                onError.publish(new PeerError(error));
                close();
            }

        });

    }

    private void setLocalDescription(final RTCSessionDescription description) {
        peerConnection.setLocalDescription(description, new SetSessionDescriptionObserver() {

            @Override
            public void onSuccess() {
                logger.debug("Set local session description: {}", description);
                final var signal = new SdpOfferDirectSignal();
                signal.setPeerSdp(description.sdp);
                signal.setProfileId(peerRecord.profileId);
                signal.setRecipientProfileId(peerRecord.remoteProfileId);
                peerRecord.signaling.signal(signal);
            }

            @Override
            public void onFailure(String error) {
                logger.error("Failed to set description: {}. Closing connection.", error);
                onError.publish(new PeerError(error));
                close();
            }

        });
    }

    public WebRTCMatchHostPeer(final Record peerRecord) {

        this.peerRecord = requireNonNull(peerRecord, "peerRecord");
        this.peerConnection = peerRecord.peerConnectionConstructor().apply(peerConnectionObserver);

        this.dataChannel = peerConnection.createDataChannel(
                peerRecord.dataChannelLabel,
                peerRecord.dataChannelInit
        );

        this.dataChannel.registerObserver(dataChannelObserver);
        this.subscription = peerRecord.signaling.onSignal(this::onSignal);
        createOffer();

    }

    private void onSignal(final Subscription subscription, final Signal signal) {
        switch (signal.getType()) {
            case SDP_ANSWER -> onSignalAnswer(subscription, (SdpAnswerDirectSignal) signal);
            case DISCONNECT -> onSignalDisconnect(subscription, (DisconnectBroadcastSignal) signal);
        }
    }

    private void onSignalAnswer(final Subscription subscription,
                                final SdpAnswerDirectSignal signal) {

        final var description = new RTCSessionDescription(RTCSdpType.ANSWER, signal.getPeerSdp());

        peerConnection.setRemoteDescription(description, new SetSessionDescriptionObserver() {

            @Override
            public void onSuccess() {
                logger.info("Set remote session description for peer {}", peerRecord.remoteProfileId);
            }

            @Override
            public void onFailure(final String error) {
                logger.error("Failed to get answer: {}. Closing connection.", error);
                close();
            }

        });

    }

    private void onSignalDisconnect(final Subscription subscription,
                                    final DisconnectBroadcastSignal signal) {
        if (signal.getProfileId().equals(peerRecord.remoteProfileId)) {
            doClose(subscription);
        }
    }

    @Override
    protected Optional<RTCDataChannel> findDataChannel() {
        return Optional.of(dataChannel);
    }

    @Override
    public String getProfileId() {
        return peerRecord.remoteProfileId();
    }

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
           String dataChannelLabel,
           RTCOfferOptions offerOptions,
           RTCDataChannelInit dataChannelInit,
           Function<PeerConnectionObserver, RTCPeerConnection> peerConnectionConstructor) {

        public Record {
            requireNonNull(profileId, "profileId");
            requireNonNull(remoteProfileId, "remoteProfileId");
            requireNonNull(signaling, "signaling");
            requireNonNull(dataChannelLabel, "dataChannelLabel");
            requireNonNull(offerOptions, "offerOptions");
            requireNonNull(dataChannelInit, "dataChannelInit");
            requireNonNull(peerConnectionConstructor, "peerConnectionConstructor");
        }

    }

}
