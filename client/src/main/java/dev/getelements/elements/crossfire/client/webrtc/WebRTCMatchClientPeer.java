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

    private final AtomicReference<WebRTCPeerConnectionState> peerConnectionState = new AtomicReference<>(WebRTCPeerConnectionState.create());

    private final PeerConnectionObserver peerConnectionObserver = new PeerConnectionObserver() {

        @Override
        public void onIceCandidate(final RTCIceCandidate candidate) {
            final var signal = new CandidateDirectSignal();
            signal.setProfileId(peerRecord.profileId());
            signal.setRecipientProfileId(peerRecord.remoteProfileId());
            signal.setMid(candidate.sdpMid);
            signal.setCandidate(candidate.sdp);
            peerRecord.signaling().signal(signal);
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
            peerConnectionState.updateAndGet(s -> s.channel(dataChannel));
        }

    };

    public WebRTCMatchClientPeer(final Record peerRecord) {
        this.peerRecord = requireNonNull(peerRecord, "peerRecord");
        this.subscription = peerRecord.signaling.onSignal(this::onSignal);
    }

    public void connect() {

        final var connection = peerRecord.peerConnectionConstructor.apply(peerConnectionObserver);
        final var state = peerConnectionState.updateAndGet(s -> s.connect(connection));

        if (state.connection() != connection)
            connection.close();
        else if (!state.open())
            connection.close();

    }

    @Override
    protected Optional<RTCDataChannel> findDataChannel() {
        return peerConnectionState.get().findChannel();
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

        peerConnectionState.get().findConnection().ifPresent(connection ->
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

                }));

    }

    private void createAnswer() {
        peerConnectionState.get().findConnection().ifPresent(connection ->
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

                }));
    }

    private void onSignalDisconnect(final Subscription subscription,
                                    final DisconnectBroadcastSignal signal) {

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
            Function<PeerConnectionObserver, RTCPeerConnection> peerConnectionConstructor) {

        public String profileId() {
            return signaling.getState().getProfileId();
        }

    }

}
