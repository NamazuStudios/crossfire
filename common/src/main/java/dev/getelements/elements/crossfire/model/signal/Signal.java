package dev.getelements.elements.crossfire.model.signal;

import dev.getelements.elements.crossfire.model.ProtocolMessage;

/**
 * Represents a signal in the Crossfire protocol. Signals are used to communicate various events and data among clients.
 */
public interface Signal extends ProtocolMessage {

    /**
     * Gets the lifecycle of the signal.
     *
     * @return the lifecycle of the signal
     */
    SignalLifecycle getLifecycle();

}
