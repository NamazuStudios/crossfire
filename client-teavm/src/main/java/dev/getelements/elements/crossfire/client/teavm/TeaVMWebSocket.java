package dev.getelements.elements.crossfire.client.teavm;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * JSO overlay for the browser's native WebSocket API.
 * Construction via {@link #create(String)} rather than {@code new} since TeaVM generates
 * overlay constructors differently from factory-method-style JSBody calls.
 */
abstract class TeaVMWebSocket implements JSObject {

    @JSBody(params = {"url"}, script = "return new WebSocket(url)")
    static native TeaVMWebSocket create(String url);

    @JSProperty("onopen")
    abstract void setOnOpen(OpenHandler handler);

    @JSProperty("onmessage")
    abstract void setOnMessage(MessageHandler handler);

    @JSProperty("onerror")
    abstract void setOnError(ErrorHandler handler);

    @JSProperty("onclose")
    abstract void setOnClose(CloseHandler handler);

    abstract void send(String data);

    abstract void close();

    @JSBody(params = {"event"}, script = "return typeof event.data === 'string' ? event.data : null")
    static native String getMessageData(JSObject event);

    @JSBody(params = {"event"}, script = "return event.code || 0")
    static native int getCloseCode(JSObject event);

    @JSBody(params = {"event"}, script = "return event.reason || ''")
    static native String getCloseReason(JSObject event);

    @JSFunctor
    @FunctionalInterface
    interface OpenHandler extends JSObject {
        void handle(JSObject event);
    }

    @JSFunctor
    @FunctionalInterface
    interface MessageHandler extends JSObject {
        void handle(JSObject event);
    }

    @JSFunctor
    @FunctionalInterface
    interface ErrorHandler extends JSObject {
        void handle(JSObject event);
    }

    @JSFunctor
    @FunctionalInterface
    interface CloseHandler extends JSObject {
        void handle(JSObject event);
    }
}
