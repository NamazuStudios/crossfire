package dev.getelements.elements.crossfire.model.error;

/**
 * Exception thrown when the protocol is in an unexpected state. This can occur when a message is received that is not
 * valid for the current state of the protocol. It is considered an expected error, and is thrown when the client sends
 * a message that is not valid for the current connection phase.
 *
 * In cases of implementation errors, such as a message being sent when the connection is not ready, this exception
 * should not be used.
 */
public class ProtocolStateException extends IllegalStateException {

    public ProtocolStateException() {
        super();
    }

    public ProtocolStateException(String message) {
        super(message);
    }

    public ProtocolStateException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProtocolStateException(Throwable cause) {
        super(cause);
    }

}
