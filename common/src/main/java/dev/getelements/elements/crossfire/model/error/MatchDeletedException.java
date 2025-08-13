package dev.getelements.elements.crossfire.model.error;

public class MatchDeletedException extends IllegalStateException {

    public MatchDeletedException() {
    }

    public MatchDeletedException(String s) {
        super(s);
    }

    public MatchDeletedException(String message, Throwable cause) {
        super(message, cause);
    }

    public MatchDeletedException(Throwable cause) {
        super(cause);
    }

}
