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

import static dev.getelements.elements.crossfire.api.model.ProtocolMessageType.*;
import static dev.getelements.elements.crossfire.client.PeerPhase.TERMINATED;

/**
 * WebRTC peer that creates the SDP offer and data channel (the "host" side).
 * JS is single-threaded — no locking or atomic references are used.
 */
class TeaVMWebRTCOfferingPeer extends TeaVMWebRTCPeer {

    private final String iceServersJson;
    private final String dataChannelLabel;
    private final Subscription subscription;

    TeaVMWebRTCOfferingPeer(final SignalingClient signaling,
                             final String remoteProfileId,
                             final TeaVMPublisher<PeerStatus> onPeerStatus,
                             final String iceServersJson,
                             final String dataChannelLabel) {
        super(signaling, signaling.getState().getProfileId(), remoteProfileId, onPeerStatus);
        this.iceServersJson   = iceServersJson;
        this.dataChannelLabel = dataChannelLabel;
        this.subscription     = signaling.onSignal(this::onSignal);
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

        final var dcTemp = pc.createDataChannel(dataChannelLabel, "{\"ordered\":true}");
        setupDataChannel(dcTemp);

        pc.createOfferSetLocal(
                this::signalOffer,
                err -> onError.publish(new PeerException("createOffer failed: " + err))
        );
    }

    private void signalOffer(final String sdp) {
        final var signal = new SdpOfferDirectSignal();
        signal.setProfileId(localProfileId);
        signal.setRecipientProfileId(remoteProfileId);
        signal.setPeerSdp(sdp);
        signaling.signal(signal);
    }

    // -------------------------------------------------------------------------
    // Signal handling
    // -------------------------------------------------------------------------

    private void onSignal(final Subscription sub, final Signal signal) {
        switch (signal.getType()) {
            case SDP_ANSWER -> {
                final var answer = (SdpAnswerDirectSignal) signal;
                if (answer.getProfileId().equals(remoteProfileId) && pc != null) {
                    pc.setRemoteAnswer(
                            answer.getPeerSdp(),
                            () -> {},
                            err -> onError.publish(new PeerException("setRemoteAnswer failed: " + err))
                    );
                }
            }
            case CANDIDATE -> handleCandidateSignal((CandidateDirectSignal) signal);
            case DISCONNECT -> {
                final var disc = (DisconnectBroadcastSignal) signal;
                if (disc.getProfileId().equals(remoteProfileId)) close();
            }
            default -> {}
        }
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
