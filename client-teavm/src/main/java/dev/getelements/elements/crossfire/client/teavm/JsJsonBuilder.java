package dev.getelements.elements.crossfire.client.teavm;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/**
 * Utilities for building and parsing JSON in the TeaVM browser target.
 * Uses {@code @JSBody} inline JS so Jackson is not needed.
 */
final class JsJsonBuilder {

    private JsJsonBuilder() {}

    @JSBody(params = {}, script = "return {}")
    static native JSObject newObj();

    @JSBody(params = {"obj", "key", "value"}, script = "if (value !== null && value !== undefined) obj[key] = value")
    static native void set(JSObject obj, String key, String value);

    @JSBody(params = {"obj"}, script = "return JSON.stringify(obj)")
    static native String stringify(JSObject obj);

    @JSBody(params = {"json"}, script = "return JSON.parse(json)")
    static native JsMessage parseMessage(String json);

    @JSBody(params = {"obj", "key", "value"}, script = "obj[key] = value")
    static native void setInt(JSObject obj, String key, int value);
}
