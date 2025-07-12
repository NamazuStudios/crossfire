package dev.getelements.elements.crossfire.jackson;

import jakarta.websocket.EncodeException;
import jakarta.websocket.Encoder;

import static dev.getelements.elements.crossfire.jackson.Jackson.getMapper;

public class JacksonEncoder implements Encoder.Text<Object> {

    @Override
    public String encode(final Object object) throws EncodeException {
        try {
            return getMapper().writeValueAsString(object);
        } catch (Exception e) {
            throw new EncodeException(object, "Failed to encode object to JSON", e);
        }
    }

}
