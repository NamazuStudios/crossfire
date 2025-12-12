package dev.getelements.elements.crossfire.api.model.error;

/**
 * Exception thrown when a message is received that is not expected, such as a message that does not match the current
 * protocol specification.
 */
public class UnexpectedMessageException extends IllegalArgumentException {

    public UnexpectedMessageException() {}

    public UnexpectedMessageException(String s) {
        super(s);
    }

    public UnexpectedMessageException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnexpectedMessageException(Throwable cause) {
        super(cause);
    }

}
