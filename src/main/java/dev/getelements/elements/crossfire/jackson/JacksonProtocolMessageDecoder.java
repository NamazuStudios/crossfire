package dev.getelements.elements.crossfire.jackson;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import jakarta.websocket.DecodeException;
import jakarta.websocket.Decoder;

import static dev.getelements.elements.crossfire.jackson.Jackson.getMapper;
import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.findType;

public class    JacksonProtocolMessageDecoder implements Decoder.Text<ProtocolMessage> {

    @Override
    public ProtocolMessage decode(final String s) throws DecodeException {
        try {
            final var root = getMapper().readTree(s);
            final var type = findType(root.get("type").asText()).get();
            return getMapper().treeToValue(root, type.getMessageType());
        } catch (Exception ex) {
            throw new DecodeException(s, "Unable to parse JSON.", ex);
        }
    }

    @Override
    public boolean willDecode(final String s) {
        try {

            final var root = getMapper().readTree(s);
            final var type = root.get("type");

            if (type == null || !type.isTextual()) {
                return false;
            }

            return findType(type.asText()).isPresent();

        } catch (Exception e) {
            return false;
        }
    }

}
