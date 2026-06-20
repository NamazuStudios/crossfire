package dev.getelements.elements.crossfire.client.teavm;

import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * JSO overlay interface for incoming Crossfire protocol messages parsed from JSON.
 * Property names match the Jackson-serialized field names used by the server.
 * Use {@link JsJsonBuilder#parseMessage(String)} to obtain instances.
 */
interface JsMessage extends JSObject {

    @JSProperty String getType();

    @JSProperty String getProfileId();

    @JSProperty String getMatchId();

    @JSProperty String getRecipientProfileId();

    @JSProperty String getLifecycle();

    @JSProperty String getPayload();

    @JSProperty String getMessage();

    @JSProperty String getCode();

    @JSProperty String getVersion();

    @JSProperty String getJoinCode();
}
