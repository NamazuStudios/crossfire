package dev.getelements.elements.crossfire.api.model.error;

import dev.getelements.elements.crossfire.api.model.ProtocolMessage;
import dev.getelements.elements.sdk.annotation.ElementPublic;

/**
 * Represents a protocol error in the Crossfire system. The server will respond with a protocol error message when it
 * detects an error condition, such as an invalid message format or a timeout. The error message includes an error code
 * and a human-readable message describing the error condition. Clients should handle protocol error messages
 * appropriately. Typically, this involves logging the error and potentially notifying the user. Once sending an error
 * the server will immediately close the connection and disconnect the participant from the match.
 */
@ElementPublic
public interface ProtocolError extends ProtocolMessage {

    /**
     * Gets the error code.
     *
     * @return the error code
     */
    String getCode();

    /**
     * Gets the error message describing the error condition.
     *
     * @return the message
     */
    String getMessage();

    /**
     * Built-in error codes for protocol errors.
     */
    enum Code {

        /**
         * Indicates an unknown error condition.
         */
        UNKNOWN("An unknown error occurred."),

        /**
         * Indicates a timeout error.
         */
        TIMEOUT("Remote timed out."),

        /**
         * Indicates an invalid message format.
         */
        INVALID_MESSAGE("Invalid message format.");

        private final String defaultMessage;

        Code(final String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }

        /**
         * Gets the default message associated with the error code.
         *
         * @return the default message
         */
        public String getDefaultMessage() {
            return defaultMessage;
        }

    }

}
