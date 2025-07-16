package dev.getelements.elements.crossfire.model.error;

import java.nio.BufferOverflowException;

/**
 * Exception thrown when a message buffer overrun occurs, indicating that the message being processed exceeds the
 * allocated buffer size.
 */
public class MessageBufferOverrunException extends IllegalStateException {

    public MessageBufferOverrunException() {
        super();
    }

    public MessageBufferOverrunException(String message) {
        super(message);
    }

    public MessageBufferOverrunException(String message, Throwable cause) {
        super(message, cause);
    }

    public MessageBufferOverrunException(Throwable cause) {
        super(cause);
    }

    public MessageBufferOverrunException(BufferOverflowException cause) {
        super(cause);
    }

}
