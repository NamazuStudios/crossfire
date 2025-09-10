package dev.getelements.elements.crossfire.client;

public class SignalingClientException extends RuntimeException {

    public SignalingClientException() {}

    public SignalingClientException(final String message) {
        super(message);
    }

    public SignalingClientException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public SignalingClientException(final Throwable cause) {
        super(cause);
    }

    public SignalingClientException(final String message,
                                    final Throwable cause,
                                    final boolean enableSuppression,
                                    final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
