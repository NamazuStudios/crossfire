package dev.getelements.elements.crossfire.jackson;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import jakarta.websocket.DecodeException;
import jakarta.websocket.Decoder;

public class FailDecoder implements Decoder.Text<ProtocolMessage> {

    @Override
    public ProtocolMessage decode(final String message) throws DecodeException {
        throw new DecodeException(message, "Unable to decode messge");
    }

    @Override
    public boolean willDecode(String s) {
        return true;
    }

}
