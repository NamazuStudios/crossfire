package dev.getelements.elements.crossfire.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify the request type for a signal. A request is a signal that is sent from the client to the
 * server. The client is requesting that the server relay the signal to either a specific recipient or all recipients
 * depending on the type of request and it's defined behavior.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SignalModel {

    /**
     * Specifies request type to handle the signal.
     *
     * @return the value of the request type
     */
    Signal.Type value();

}
