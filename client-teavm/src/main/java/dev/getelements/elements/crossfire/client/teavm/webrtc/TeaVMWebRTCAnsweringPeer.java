package dev.getelements.elements.crossfire.client.teavm.webrtc;

import dev.getelements.elements.crossfire.api.model.signal.CandidateDirectSignal;
import dev.getelements.elements.crossfire.api.model.signal.DisconnectBroadcastSignal;
import dev.getelements.elements.crossfire.api.model.signal.SdpAnswerDirectSignal;
import dev.getelements.elements.crossfire.api.model.signal.SdpOfferDirectSignal;
import dev.getelements.elements.crossfire.api.model.signal.Signal;
import dev.getelements.elements.crossfire.client.PeerException;
import dev.getelements.elements.crossfire.client.PeerStatus;
import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.client.teavm.signaling.TeaVMPublisher;
import dev.getelements.elements.sdk.Subscription;

/**
 * WebRTC peer that waits for an SDP offer and answers it (the "client" side).
 * JS is single-threaded — no locking or atomic references are used.
 */
class TeaVMWebRTCAnsweringPeer extends TeaVMWebRTCPeer {

    private final String iceServersJson;
    private final Subscription subscription;

    TeaVMWebRTCAnsweringPeer(final SignalingClient signaling,
                              final String remoteProfileId,
                              final TeaVMPublisher<PeerStatus> onPeerStatus,
                              final String iceServersJson) {
        super(signaling, signaling.getState().getProfileId(), remoteProfileId, onPeerStatus);
        this.iceServersJson = iceServersJson;
        this.subscription   = signaling.onSignal(this::onSignal);
    }

    // -------------------------------------------------------------------------
    // Connect
    // -------------------------------------------------------------------------

    void connect() {
        pc = RTCPeerConnectionOverlay.create(iceServersJson);

        pc.setOnIceCandidate(event -> {
            if (RTCPeerConnectionOverlay.hasIceCandidate(event)) {
                signalCandidate(
                        RTCPeerConnectionOverlay.getIceCandidateMid(event),
                        RTCPeerConnectionOverlay.getIceCandidateMLineIndex(event),
                        RTCPeerConnectionOverlay.getIceCandidateStr(event)
                );
            }
        });

        pc.setOnDataChannel(event ->
                setupDataChannel(RTCPeerConnectionOverlay.getDataChannel(event))
        );

        // Replay backlog signals in case the offer arrived before connect() was called
        signaling.backlog().forEach(this::onBacklogSignal);
    }

    private void onBacklogSignal(final Signal signal) {
        if (signal.getType() == dev.getelements.elements.crossfire.api.model.ProtocolMessageType.SDP_OFFER) {
            handleOffer((SdpOfferDirectSignal) signal);
        }
    }

    // -------------------------------------------------------------------------
    // Signal handling
    // -------------------------------------------------------------------------

    private void onSignal(final Subscription sub, final Signal signal) {
        switch (signal.getType()) {
            case SDP_OFFER  -> handleOffer((SdpOfferDirectSignal) signal);
            case CANDIDATE  -> handleCandidateSignal((CandidateDirectSignal) signal);
            case DISCONNECT -> {
                final var disc = (DisconnectBroadcastSignal) signal;
                if (disc.getProfileId().equals(remoteProfileId)) close();
            }
            default -> {}
        }
    }

    private void handleOffer(final SdpOfferDirectSignal offer) {
        if (!offer.getProfileId().equals(remoteProfileId)) return;
        if (pc == null) return;
        pc.receiveOfferCreateAnswer(
                offer.getPeerSdp(),
                this::signalAnswer,
                err -> onError.publish(new PeerException("receiveOfferCreateAnswer failed: " + err))
        );
    }

    private void signalAnswer(final String sdp) {
        final var signal = new SdpAnswerDirectSignal();
        signal.setProfileId(localProfileId);
        signal.setRecipientProfileId(remoteProfileId);
        signal.setPeerSdp(sdp);
        signaling.signal(signal);
    }

    // -------------------------------------------------------------------------
    // Close
    // -------------------------------------------------------------------------

    @Override
    public void close() {
        if (open) {
            super.close();
            subscription.unsubscribe();
        }
    }
}
