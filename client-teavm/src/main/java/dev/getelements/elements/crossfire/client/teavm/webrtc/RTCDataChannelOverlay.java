package dev.getelements.elements.crossfire.client.teavm.webrtc;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * JSO abstract class overlay for the browser's {@code RTCDataChannel} API.
 * All event wiring uses {@code @JSProperty} setters for handler assignment.
 * Binary data is exchanged as {@code ArrayBuffer} via {@code @JSBody} helpers.
 */
public abstract class RTCDataChannelOverlay implements JSObject {

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    @JSProperty("onopen")
    public abstract void setOnOpen(VoidHandler handler);

    @JSProperty("onmessage")
    public abstract void setOnMessage(MessageHandler handler);

    @JSProperty("onclose")
    public abstract void setOnClose(VoidHandler handler);

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    @JSProperty("binaryType")
    public abstract void setBinaryType(String type);

    // -------------------------------------------------------------------------
    // Send / close
    // -------------------------------------------------------------------------

    public abstract void send(String data);

    /** Sends a binary {@code ArrayBuffer} through the data channel. */
    @JSBody(params = {"b"}, script = "this.send(b)")
    public native void sendBinary(JSObject arrayBuffer);

    public abstract void close();

    // -------------------------------------------------------------------------
    // Static event helpers
    // -------------------------------------------------------------------------

    /** Returns the {@code event.data} as a string (for text messages). */
    @JSBody(params = {"event"}, script = "return event.data")
    public static native String getMessageDataString(JSObject event);

    /** Returns the {@code event.data} as a raw JS object (for binary ArrayBuffer messages). */
    @JSBody(params = {"event"}, script = "return event.data")
    public static native JSObject getMessageDataBinary(JSObject event);

    /** Returns {@code true} if {@code event.data} is an {@code ArrayBuffer}. */
    @JSBody(params = {"event"}, script = "return event.data instanceof ArrayBuffer")
    public static native boolean isMessageBinary(JSObject event);

    // -------------------------------------------------------------------------
    // Binary conversion helpers
    // -------------------------------------------------------------------------

    /** Copies an {@code ArrayBuffer} into a Java {@code byte[]}. */
    @JSBody(params = {"buf"}, script = "return Array.from(new Uint8Array(buf))")
    public static native byte[] arrayBufferToByteArray(JSObject buf);

    /** Wraps a Java {@code byte[]} into a JS {@code ArrayBuffer}. */
    @JSBody(params = {"b"}, script = "var buf = new ArrayBuffer(b.length); var view = new Uint8Array(buf); for (var i = 0; i < b.length; i++) view[i] = b[i]; return buf;")
    public static native JSObject byteArrayToArrayBuffer(byte[] b);

    // -------------------------------------------------------------------------
    // Functor interfaces
    // -------------------------------------------------------------------------

    @FunctionalInterface
    @JSFunctor
    public interface VoidHandler extends JSObject {
        void run();
    }

    @FunctionalInterface
    @JSFunctor
    public interface MessageHandler extends JSObject {
        void onMessage(JSObject event);
    }
}
