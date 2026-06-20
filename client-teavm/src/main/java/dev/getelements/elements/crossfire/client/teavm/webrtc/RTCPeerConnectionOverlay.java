package dev.getelements.elements.crossfire.client.teavm.webrtc;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * JSO abstract class overlay for the browser's {@code RTCPeerConnection} API.
 * All async operations are chained via Promise {@code .then()} in {@code @JSBody} inline JS.
 * No concurrency primitives are used — JS is single-threaded.
 */
public abstract class RTCPeerConnectionOverlay implements JSObject {

    /**
     * Creates a new {@code RTCPeerConnection} using the supplied JSON ICE server array.
     *
     * @param iceServersJson JSON array string, e.g. {@code [{"urls":["stun:..."]},{"urls":["turn:..."],"username":"u","credential":"p"}]}
     * @return a new {@code RTCPeerConnectionOverlay} instance
     */
    @JSBody(params = {"j"}, script = "return new RTCPeerConnection({iceServers:JSON.parse(j)})")
    public static native RTCPeerConnectionOverlay create(String iceServersJson);

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    @JSProperty("onicecandidate")
    public abstract void setOnIceCandidate(IceCandidateHandler handler);

    @JSProperty("ondatachannel")
    public abstract void setOnDataChannel(DataChannelHandler handler);

    // -------------------------------------------------------------------------
    // Data channel creation
    // -------------------------------------------------------------------------

    /**
     * Creates a new data channel with the given label and options JSON.
     *
     * @param label      channel label
     * @param optionsJson JSON options object, e.g. {@code {"ordered":true}}
     * @return the new {@code RTCDataChannelOverlay}
     */
    @JSBody(params = {"l", "o"}, script = "return this.createDataChannel(l, JSON.parse(o))")
    public native RTCDataChannelOverlay createDataChannel(String label, String optionsJson);

    // -------------------------------------------------------------------------
    // Offer / answer flow
    // -------------------------------------------------------------------------

    /**
     * Creates an offer, sets it as the local description, then calls {@code onSdp} with the local SDP string.
     */
    @JSBody(
        params = {"onSdp", "onError"},
        script =
            "var pc = this;" +
            "pc.createOffer()" +
            "  .then(function(o) { return pc.setLocalDescription(o); })" +
            "  .then(function() { onSdp(pc.localDescription.sdp); })" +
            "  .catch(function(e) { onError(e.message || String(e)); });"
    )
    public native void createOfferSetLocal(StringHandler onSdp, ErrorHandler onError);

    /**
     * Sets the remote answer SDP, then calls {@code onDone} on success or {@code onError} on failure.
     */
    @JSBody(
        params = {"sdp", "onDone", "onError"},
        script =
            "this.setRemoteDescription({type:'answer', sdp:sdp})" +
            "  .then(function() { onDone(); })" +
            "  .catch(function(e) { onError(e.message || String(e)); });"
    )
    public native void setRemoteAnswer(String sdp, VoidHandler onDone, ErrorHandler onError);

    /**
     * Receives an SDP offer, creates an answer, sets it as the local description,
     * then calls {@code onAnswerSdp} with the answer SDP string.
     */
    @JSBody(
        params = {"offerSdp", "onAnswerSdp", "onError"},
        script =
            "var pc = this;" +
            "pc.setRemoteDescription({type:'offer', sdp:offerSdp})" +
            "  .then(function() { return pc.createAnswer(); })" +
            "  .then(function(a) { return pc.setLocalDescription(a); })" +
            "  .then(function() { onAnswerSdp(pc.localDescription.sdp); })" +
            "  .catch(function(e) { onError(e.message || String(e)); });"
    )
    public native void receiveOfferCreateAnswer(String offerSdp, StringHandler onAnswerSdp, ErrorHandler onError);

    /**
     * Adds an ICE candidate to the peer connection.
     */
    @JSBody(
        params = {"c", "m", "i", "onDone", "onError"},
        script =
            "this.addIceCandidate({candidate:c, sdpMid:m, sdpMLineIndex:i})" +
            "  .then(function() { onDone(); })" +
            "  .catch(function(e) { onError(e.message || String(e)); });"
    )
    public native void addIceCandidate(String c, String m, int i, VoidHandler onDone, ErrorHandler onError);

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public abstract void close();

    // -------------------------------------------------------------------------
    // Static helpers to extract fields from ICE candidate events
    // -------------------------------------------------------------------------

    /** Returns {@code true} if the ice candidate event contains a candidate (not end-of-candidates). */
    @JSBody(params = {"event"}, script = "return !!(event.candidate && event.candidate.candidate)")
    public static native boolean hasIceCandidate(JSObject event);

    /** Extracts the candidate string from an {@code RTCPeerConnectionIceEvent}. */
    @JSBody(params = {"event"}, script = "return event.candidate.candidate")
    public static native String getIceCandidateStr(JSObject event);

    /** Extracts the {@code sdpMid} from an {@code RTCPeerConnectionIceEvent}. */
    @JSBody(params = {"event"}, script = "return event.candidate.sdpMid")
    public static native String getIceCandidateMid(JSObject event);

    /** Extracts the {@code sdpMLineIndex} from an {@code RTCPeerConnectionIceEvent}. */
    @JSBody(params = {"event"}, script = "return event.candidate.sdpMLineIndex")
    public static native int getIceCandidateMLineIndex(JSObject event);

    /** Extracts the data channel from an {@code RTCDataChannelEvent}. */
    @JSBody(params = {"event"}, script = "return event.channel")
    public static native RTCDataChannelOverlay getDataChannel(JSObject event);

    // -------------------------------------------------------------------------
    // Functor interfaces
    // -------------------------------------------------------------------------

    @FunctionalInterface
    @JSFunctor
    public interface IceCandidateHandler extends JSObject {
        void onIceCandidate(JSObject event);
    }

    @FunctionalInterface
    @JSFunctor
    public interface DataChannelHandler extends JSObject {
        void onDataChannel(JSObject event);
    }

    @FunctionalInterface
    @JSFunctor
    public interface StringHandler extends JSObject {
        void accept(String value);
    }

    @FunctionalInterface
    @JSFunctor
    public interface VoidHandler extends JSObject {
        void run();
    }

    @FunctionalInterface
    @JSFunctor
    public interface ErrorHandler extends JSObject {
        void onError(String message);
    }
}
