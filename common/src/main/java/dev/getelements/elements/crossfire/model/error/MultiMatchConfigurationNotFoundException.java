package dev.getelements.elements.crossfire.model.error;

import dev.getelements.elements.sdk.model.exception.NotFoundException;

public class MultiMatchConfigurationNotFoundException extends NotFoundException {

    public MultiMatchConfigurationNotFoundException() {}

    public MultiMatchConfigurationNotFoundException(String message) {
        super(message);
    }

    public MultiMatchConfigurationNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public MultiMatchConfigurationNotFoundException(Throwable cause) {
        super(cause);
    }

    public MultiMatchConfigurationNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
