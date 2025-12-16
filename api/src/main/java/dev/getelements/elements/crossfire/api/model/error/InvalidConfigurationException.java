package dev.getelements.elements.crossfire.api.model.error;

public class InvalidConfigurationException extends IllegalStateException {

    public InvalidConfigurationException() {}

    public InvalidConfigurationException(String s) {
        super(s);
    }

    public InvalidConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidConfigurationException(Throwable cause) {
        super(cause);
    }

}
