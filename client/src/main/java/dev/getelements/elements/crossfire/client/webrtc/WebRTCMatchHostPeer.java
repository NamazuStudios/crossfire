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

public class WebRTCMatchHostPeer extends WebRTCPeer {

    private static final Logger logger = LoggerFactory.getLogger(WebRTCMatchHostPeer.class);

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
            signal.setRecipientProfileId(peerRecord.remoteProfileId);
            signal.setMid(candidate.sdpMid);
            signal.setCandidate(candidate.sdp);
            signal.setMidIndex(candidate.sdpMLineIndex);
            peerRecord.signaling.signal(signal);
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

    };

    private void setLocalDescription(final RTCSessionDescription description) {
        peerConnectionState
                .get()
                .findConnection()
                .ifPresent(c -> c.setLocalDescription(description, new SetSessionDescriptionObserver() {

                    @Override
                    public void onSuccess() {
                        logger.debug("Set local session description: {}", description);
                        final var signal = new SdpOfferDirectSignal();
                        signal.setPeerSdp(description.sdp);
                        signal.setProfileId(peerRecord.profileId());
                        signal.setRecipientProfileId(peerRecord.remoteProfileId());
                        peerRecord.signaling().signal(signal);
                    }

                    @Override
                    public void onFailure(String error) {
                        logger.error("Failed to set description: {}. Closing connection.", error);
                        onError.publish(new PeerException(error));
                        close();
                    }

                }));
    }

    public WebRTCMatchHostPeer(final Record peerRecord) {
        super(peerRecord.signaling, peerRecord.onPeerStatus);
        this.peerRecord = requireNonNull(peerRecord, "peerRecord");
        this.subscription = peerRecord.signaling
                .onSignal((s, signal) -> onSignal(signal))
                .chain(super.subscription);
    }

    private void onSignal(final Signal signal) {
        switch (signal.getType()) {
            case SDP_ANSWER -> onSignalAnswer((SdpAnswerDirectSignal) signal);
            case DISCONNECT -> onSignalDisconnect((DisconnectBroadcastSignal) signal);
        }
    }

    private void onSignalAnswer(final SdpAnswerDirectSignal answer) {

        // Ignore signals from peers that aren't relevant to this particular peer connection.

        if (!getProfileId().equals(answer.getProfileId())) {
            return;
        }

        logger.debug("Got SDP ANSWER From {}\n{}",
                answer.getProfileId(),
                answer.getPeerSdp()
        );

        peerConnectionState.get().findConnection().ifPresent(connection -> {

            final var description = new RTCSessionDescription(RTCSdpType.ANSWER, answer.getPeerSdp());

            connection.setRemoteDescription(description, new SetSessionDescriptionObserver() {

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

        });

    }

    private void onSignalDisconnect(final DisconnectBroadcastSignal signal) {
        if (signal.getProfileId().equals(getProfileId())) {
            close();
        }
    }

    public void connect() {

        final var connection = new SimpleLazyValue<>(() -> peerRecord
                .peerConnectionConstructor
                .apply(peerConnectionObserver)
        );

        final var dataChannel = new SimpleLazyValue<>(() -> {

            final var dc = connection.get().createDataChannel(
                    peerRecord.dataChannelLabel,
                    peerRecord.dataChannelInit
            );

            final var dataChannelObserver = newDataChannelObserver(dc);
            dc.registerObserver(dataChannelObserver);

            return dc;

        });

        final var result = peerConnectionState.updateAndGet(state -> state.connection() == null
            ? state.connect(connection.get(), dataChannel.get())
            : state
        );

        // This should never happen. But if it does, we close the new connection to avoid leaks.

        dataChannel
                .getOptional()
                .filter(d -> result.channel() != d)
                .ifPresent(RTCDataChannel::close);

        connection
                .getOptional()
                .filter(c -> result.connection() != c)
                .ifPresent(RTCPeerConnection::close);

        offer();

        processSignalBacklog();

        peerRecord
                .signaling()
                .backlog()
                .forEach(this::onSignal);

    }

    private void offer() {

        final var options = peerRecord.offerOptions;

        peerConnectionState
                .get()
                .findConnection()
                .ifPresent(c -> c.createOffer(options, new CreateSessionDescriptionObserver() {

                    @Override
                    public void onSuccess(final RTCSessionDescription description) {
                        logger.debug("Received session description: {}", description);
                        setLocalDescription(description);
                    }

                    @Override
                    public void onFailure(final String error) {
                        logger.error("Failed to create offer: {}. Closing connection.", error);
                        onError.publish(new PeerException(error));
                        close();
                    }

                }));

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
        return peerRecord.remoteProfileId();
    }

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
           String dataChannelLabel,
           RTCOfferOptions offerOptions,
           RTCDataChannelInit dataChannelInit,
           Publisher<PeerStatus> onPeerStatus,
           Function<PeerConnectionObserver, RTCPeerConnection> peerConnectionConstructor) {

        public Record {
            requireNonNull(signaling, "signaling");
            requireNonNull(remoteProfileId, "remoteProfileId");
            requireNonNull(dataChannelLabel, "dataChannelLabel");
            requireNonNull(offerOptions, "offerOptions");
            requireNonNull(dataChannelInit, "dataChannelInit");
            requireNonNull(peerConnectionConstructor, "peerConnectionConstructor");
            requireNonNull(onPeerStatus, "onPeerStatus");
        }

        public String profileId() {
            return signaling().getState().getProfileId();
        }

    }

}
