package dev.getelements.elements.crossfire.jackson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A set of Jackson utility classes.
 */
public class Jackson {

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Gets the sahred mapper for the Jackson encoder and decoder..
     * @return the shared ObjectMapper instance
     */
    public static ObjectMapper getMapper() {
        return mapper;
    }

}
