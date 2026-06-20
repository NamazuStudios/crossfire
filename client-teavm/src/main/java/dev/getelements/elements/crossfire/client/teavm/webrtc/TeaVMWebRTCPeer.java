package dev.getelements.elements.crossfire.client.teavm.webrtc;

import dev.getelements.elements.crossfire.api.model.Protocol;
import dev.getelements.elements.crossfire.client.*;
import dev.getelements.elements.crossfire.client.teavm.signaling.TeaVMPublisher;
import dev.getelements.elements.sdk.Subscription;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

import static dev.getelements.elements.crossfire.api.model.Protocol.WEBRTC;

/**
 * Abstract base for WebRTC peers in the TeaVM browser target.
 * JS is single-threaded — no locking or atomic references are used.
 * Subclasses implement the offerer (host) or answerer (client) role.
 */
abstract class TeaVMWebRTCPeer implements Peer {

    protected final SignalingClient signaling;
    protected final String localProfileId;
    protected final String remoteProfileId;
    protected final TeaVMPublisher<PeerStatus> onPeerStatus;

    protected RTCPeerConnectionOverlay pc;
    protected RTCDataChannelOverlay dc;
    protected boolean open = true;

    private final TeaVMPublisher<Message>       onMessage       = new TeaVMPublisher<>();
    private final TeaVMPublisher<StringMessage> onStringMessage = new TeaVMPublisher<>();
    protected final TeaVMPublisher<Throwable>   onError         = new TeaVMPublisher<>();

    TeaVMWebRTCPeer(final SignalingClient signaling,
                    final String localProfileId,
                    final String remoteProfileId,
                    final TeaVMPublisher<PeerStatus> onPeerStatus) {
        this.signaling        = signaling;
        this.localProfileId   = localProfileId;
        this.remoteProfileId  = remoteProfileId;
        this.onPeerStatus     = onPeerStatus;
    }

    // -------------------------------------------------------------------------
    // Data channel wiring
    // -------------------------------------------------------------------------

    /**
     * Wires all event handlers on the supplied data channel.
     * Called by the offerer after createDataChannel() and by the answerer from the ondatachannel event.
     */
    protected void setupDataChannel(final RTCDataChannelOverlay channel) {
        this.dc = channel;
        channel.setBinaryType("arraybuffer");
        channel.setOnOpen(this::onDataChannelOpen);
        channel.setOnMessage(this::onDataChannelMessage);
        channel.setOnClose(this::onDataChannelClose);
    }

    private void onDataChannelOpen() {
        if (open) {
            onPeerStatus.publish(new PeerStatus(PeerPhase.CONNECTED, this));
        }
    }

    private void onDataChannelMessage(final JSObject event) {
        if (!open) return;
        if (RTCDataChannelOverlay.isMessageBinary(event)) {
            final var buf = RTCDataChannelOverlay.getMessageDataBinary(event);
            final var bytes = RTCDataChannelOverlay.arrayBufferToByteArray(buf);
            onMessage.publish(new Message(this, ByteBuffer.wrap(bytes)));
        } else {
            final var text = RTCDataChannelOverlay.getMessageDataString(event);
            onStringMessage.publish(new StringMessage(this, text));
        }
    }

    private void onDataChannelClose() {
        close();
    }

    // -------------------------------------------------------------------------
    // ICE candidate signaling
    // -------------------------------------------------------------------------

    protected void signalCandidate(final String sdpMid, final int sdpMLineIndex, final String candidate) {
        final var signal = new dev.getelements.elements.crossfire.api.model.signal.CandidateDirectSignal();
        signal.setProfileId(localProfileId);
        signal.setRecipientProfileId(remoteProfileId);
        signal.setMid(sdpMid);
        signal.setMidIndex(sdpMLineIndex);
        signal.setCandidate(candidate);
        signaling.signal(signal);
    }

    protected void handleCandidateSignal(
            final dev.getelements.elements.crossfire.api.model.signal.CandidateDirectSignal signal) {
        if (!signal.getProfileId().equals(remoteProfileId)) return;
        if (pc == null) return;
        pc.addIceCandidate(
                signal.getCandidate(),
                signal.getMid(),
                signal.getMidIndex(),
                () -> {},
                err -> onError.publish(new PeerException("addIceCandidate failed: " + err))
        );
    }

    // -------------------------------------------------------------------------
    // Peer interface
    // -------------------------------------------------------------------------

    @Override
    public String getProfileId() {
        return remoteProfileId;
    }

    @Override
    public Protocol getProtocol() {
        return WEBRTC;
    }

    @Override
    public PeerPhase getPhase() {
        if (!open)  return PeerPhase.TERMINATED;
        if (dc != null && isDcOpen(dc)) return PeerPhase.CONNECTED;
        return PeerPhase.READY;
    }

    @Override
    public SendResult send(final String string) {
        if (!open || dc == null || !isDcOpen(dc)) return open ? SendResult.NOT_READY : SendResult.TERMINATED;
        dc.send(string);
        return SendResult.SENT;
    }

    @Override
    public SendResult send(final ByteBuffer buffer) {
        if (!open || dc == null || !isDcOpen(dc)) return open ? SendResult.NOT_READY : SendResult.TERMINATED;
        final var bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        dc.sendBinary(RTCDataChannelOverlay.byteArrayToArrayBuffer(bytes));
        return SendResult.SENT;
    }

    @Override
    public Subscription onError(final BiConsumer<Subscription, Throwable> listener) {
        return onError.subscribe(listener);
    }

    @Override
    public Subscription onMessage(final BiConsumer<Subscription, Message> listener) {
        return onMessage.subscribe(listener);
    }

    @Override
    public Subscription onStringMessage(final BiConsumer<Subscription, StringMessage> listener) {
        return onStringMessage.subscribe(listener);
    }

    public void close() {
        if (open) {
            open = false;
            if (dc != null) {
                try { dc.close(); } catch (Exception ignored) {}
                dc = null;
            }
            if (pc != null) {
                try { pc.close(); } catch (Exception ignored) {}
                pc = null;
            }
            onPeerStatus.publish(new PeerStatus(PeerPhase.TERMINATED, this));
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    @JSBody(params = {"dc"}, script = "return dc.readyState === 'open'")
    private static native boolean isDcOpen(JSObject dc);
}
