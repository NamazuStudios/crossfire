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
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class WebRTCAnsweringPeer extends WebRTCPeer {

    private static final Logger logger = LoggerFactory.getLogger(WebRTCAnsweringPeer.class);

    private final Record peerRecord;

    private final Subscription subscription;

    private final PeerConnectionObserver peerConnectionObserver = new LoggingPeerConnectionObserver() {

        @Override
        public Logger getLogger() {
            return logger;
        }

        @Override
        public void onIceCandidate(final RTCIceCandidate candidate) {
            signalCandidate(candidate, peerRecord.localProfileId(), peerRecord.remoteProfileId());
        }

        @Override
        public void onIceCandidateError(final RTCPeerConnectionIceErrorEvent event) {

            loggerICE.error("ICE candidate error: {} for remote {}",
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

    public WebRTCAnsweringPeer(final Record peerRecord) {
        super(peerRecord.signaling, peerRecord.onPeerStatus);
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
        return peerRecord.remoteProfileId;
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

            loggerICE.debug("Got SDP OFFER {} -> {}\n{}",
                    offer.getProfileId(),
                    offer.getRecipientProfileId(),
                    offer.getPeerSdp()
            );

            final var description = new RTCSessionDescription(RTCSdpType.OFFER, offer.getPeerSdp());
            update(s -> s.description(description));

        } else {
            loggerICE.debug("Dropping SDP OFFER from {} intended for {} (not relevant to this peer {}).",
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
    protected void startICE(final WebRTCPeerConnectionState replacement) {
        replacement.connection().setRemoteDescription(replacement.description(), new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
                logger.info("Set remote session description for answering peer {}", peerRecord.remoteProfileId);
                createAnswer(replacement);
            }

            @Override
            public void onFailure(String error) {
                logger.error("Failed to set remote description for answering peer: {}. Closing connection.", error);
                onError.publish(new PeerException(error));
                close();
            }
        });
    }

    private void createAnswer(final WebRTCPeerConnectionState state) {
            state.connection().createAnswer(peerRecord.answerOptions, new CreateSessionDescriptionObserver() {

                @Override
                public void onSuccess(final RTCSessionDescription description) {
                    logger.info("Successfully set local session description for answering peer {}", getProfileId());
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

        loggerICE.debug("Setting local description {} for answering peer {}.",
                description,
                getProfileId()
        );

        final var connection = state.connection();

        connection.setLocalDescription(description, new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {

                final var signal = new SdpAnswerDirectSignal();
                signal.setPeerSdp(description.sdp);
                signal.setProfileId(peerRecord.localProfileId());
                signal.setRecipientProfileId(peerRecord.remoteProfileId());

                loggerICE.debug("Signaling answer to offerer: {} -> {}\n{}",
                        signal.getProfileId(),
                        signal.getRecipientProfileId(),
                        signal.getPeerSdp()
                );

                peerRecord.signaling.signal(signal);

                loggerICE.debug("Setting canddiate {} for peer {}",
                        state.candidate(),
                        getProfileId()
                );

                connection.addIceCandidate(state.candidate());

            }

            @Override
            public void onFailure(String error) {
                logger.error("Failed to set description: {}. Closing connection.", error);
                onError.publish(new PeerException(error));
                close();
            }
        });

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

        public String localProfileId() {
            return signaling.getState().getProfileId();
        }

    }

}
