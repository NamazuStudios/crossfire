package dev.getelements.elements.crossfire.model.error;

import dev.getelements.elements.crossfire.model.ProtocolMessageType;
import dev.getelements.elements.sdk.model.exception.BaseException;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.IllegalFormatException;

import static dev.getelements.elements.crossfire.model.ProtocolMessageType.ERROR;
import static dev.getelements.elements.crossfire.model.error.ProtocolError.Code.TIMEOUT;
import static dev.getelements.elements.crossfire.model.error.ProtocolError.Code.UNKNOWN;
import static java.lang.String.format;

/**
 * Indicates there has been a protocol error.
 */
public class StandardProtocolError implements ProtocolError {

    private static final Logger logger = LoggerFactory.getLogger(StandardProtocolError.class);

    @NotNull
    private String code;

    @NotNull
    private String message;

    @Override
    public ProtocolMessageType getType() {
        return ERROR;
    }

    /**
     * The error code.
     *
     * @return the error code
     */
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    /**
     * The error message. A human-readable description of the error.
     * @return the error message
     */
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Factory mehtod for the protocol error.
     * @param th the exception
     * @return the {@link StandardProtocolError}
     */
    public static ProtocolError from(final Throwable th) {

        final var error = new StandardProtocolError();

        if (th instanceof BaseException bex) {
            error.setCode(bex.getCode().toString());
        } if (th instanceof TimeoutException tex) {
            error.setCode(TIMEOUT.name());
        } else {
            error.setCode(UNKNOWN.toString());
        }

        error.setMessage(th == null || th.getMessage() == null
                ? "An unknown error occurred"
                : th.getMessage()
        );

        return error;

    }

    /**
     * Factory mehtod for the protocol error.
     * @param code the error code
     * @return the {@link StandardProtocolError}
     */
    public static ProtocolError from(final Code code) {
        return from(code, "%s", code);
    }

    /**
     * Factory method for the protocol error.
     *
     * @param code the error code
     * @param format the format of the message string
     * @param args the formatting args
     *
     * @return the {@link StandardProtocolError}
     */
    public static ProtocolError from(final Code code, final String format, final Object ... args) {

        final var error = new StandardProtocolError();
        error.setCode(code.toString());

        try {
            final var message = format(format, args);
            error.setMessage(message);
        } catch (final IllegalFormatException ex) {
            logger.error("Invalid error format.", ex);
            error.setMessage(code.getDefaultMessage());
        }

        return error;

    }

}
