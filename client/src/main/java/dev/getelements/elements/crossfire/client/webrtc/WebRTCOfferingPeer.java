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

public class WebRTCOfferingPeer extends WebRTCPeer {

    private static final Logger logger = LoggerFactory.getLogger(WebRTCOfferingPeer.class);

    static {
        WebRTC.load();
    }

    private final Record peerRecord;

    private final Subscription subscription;

    private final PeerConnectionObserver peerConnectionObserver = new WebRTCPeer.ConnectionObserver(WebRTCAnsweringPeer.class);

    public WebRTCOfferingPeer(final Record peerRecord) {
        super(peerRecord.signaling, peerRecord.onPeerStatus);

        logger.debug("Creating offering peer for {} -> {}",
                peerRecord.localProfileId(),
                peerRecord.remoteProfileId()
        );

        this.peerRecord = requireNonNull(peerRecord, "peerRecord");
        this.subscription = peerRecord.signaling
                .onSignal((s, signal) -> onSignal(signal));

    }

    private void onSignal(final Signal signal) {
        switch (signal.getType()) {
            case CANDIDATE -> onSignalCandidate((CandidateDirectSignal) signal);
            case SDP_ANSWER -> onSignalAnswer((SdpAnswerDirectSignal) signal);
            case DISCONNECT -> onSignalDisconnect((DisconnectBroadcastSignal) signal);
        }
    }

    private void onSignalAnswer(final SdpAnswerDirectSignal answer) {
        if (getProfileId().equals(answer.getProfileId())) {

            logger.debug("Got SDP ANSWER From {} -> {} \n {}",
                    answer.getProfileId(),
                    answer.getRecipientProfileId(),
                    answer.getPeerSdp()
            );

            final var description = new RTCSessionDescription(RTCSdpType.ANSWER, answer.getPeerSdp());
            updateAndGet(s -> s.description(description));

        } else {
            logger.warn("Dropping SDP ANSWER from {} intended for {} (not relevant to this peer {}).",
                    answer.getProfileId(),
                    answer.getRecipientProfileId(),
                    getProfileId()
            );
        }
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

        peerRecord
                .signaling()
                .backlog()
                .forEach(this::onSignal);

        createAndSendOffer();

    }

    private void createAndSendOffer() {

        final var options = peerRecord.offerOptions;

        peerConnectionState
                .get()
                .findConnection()
                .ifPresent(c -> c.createOffer(options, new CreateSessionDescriptionObserver() {

                    @Override
                    public void onSuccess(final RTCSessionDescription description) {

                        logger.debug("Created {}'s {} session description for remote peer {}:\n{}",
                                WebRTCOfferingPeer.class.getSimpleName(),
                                description.sdpType,
                                getProfileId(),
                                description.sdp
                        );

                        setLocalDescription(c, description);

                    }

                    @Override
                    public void onFailure(final String error) {

                        logger.error("Failed to create offer for remote peer {}: {}. Closing connection.",
                                getProfileId(),
                                error
                        );

                        onError.publish(new PeerException(error));
                        close();

                    }

                }));

    }

    private void setLocalDescription(final RTCPeerConnection connection,
                                     final RTCSessionDescription description) {
        connection.setLocalDescription(description, new SetSessionDescriptionObserver() {

            @Override
            public void onSuccess() {

                logger.debug("Set {}'s local {} session description for profile {}:\n{}",
                        WebRTCOfferingPeer.this.getClass().getSimpleName(),
                        description.sdpType,
                        getProfileId(),
                        description.sdp
                );

                final var signal = new SdpOfferDirectSignal();
                signal.setPeerSdp(description.sdp);
                signal.setProfileId(peerRecord.localProfileId());
                signal.setRecipientProfileId(peerRecord.remoteProfileId());

                peerRecord.signaling().signal(signal);

            }

            @Override
            public void onFailure(String error) {

                logger.error("Failed to set {}'s local description: {}. Closing connection.",
                        WebRTCOfferingPeer.this.getClass().getSimpleName(),
                        error
                );

                onError.publish(new PeerException(error));
                close();

            }

        });
    }

    @Override
    protected void startICE(final WebRTCPeerConnectionState state) {

        logger.debug("Setting {}'s remote {} session description for peer {}:\n{}",
                WebRTCOfferingPeer.this.getClass().getSimpleName(),
                state.description().sdpType,
                getProfileId(),
                state.description().sdp
        );

        final var connection = state.connection();

        connection.setRemoteDescription(state.description(), new SetSessionDescriptionObserver() {

            @Override
            public void onSuccess() {
                logger.debug("Successfully {}'s set remote ANSWER description for remote peer {}.",
                        WebRTCOfferingPeer.this.getClass().getSimpleName(),
                        getProfileId()
                );
            }

            @Override
            public void onFailure(final String error) {

                logger.error("Failed to {} remote description for peer {}: {}. Closing connection.",
                        WebRTCOfferingPeer.this.getClass().getSimpleName(),
                        getProfileId(),
                        error
                );

                close();

            }

        });

        state.candidates().forEach(candidate -> {

            logger.debug("Adding the candidates for remote peer {}:\n{}.",
                    getProfileId(),
                    candidate
            );

            connection.addIceCandidate(candidate);

        });

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

    public void close() {

        final var old = peerConnectionState.getAndUpdate(WebRTCPeerConnectionState::close);

        if (old.open()) {
            subscription.unsubscribe();
//            old.findChannel().ifPresent(RTCDataChannel::close);
//            old.findConnection().ifPresent(RTCPeerConnection::close);
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

        public String localProfileId() {
            return signaling().getState().getProfileId();
        }

    }

}
