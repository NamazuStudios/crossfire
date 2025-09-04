package dev.getelements.elements.crossfire.client;

public class PeerError extends RuntimeException {

    public PeerError() {
    }

    public PeerError(String message) {
        super(message);
    }

    public PeerError(String message, Throwable cause) {
        super(message, cause);
    }

    public PeerError(Throwable cause) {
        super(cause);
    }

    public PeerError(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
