package dev.getelements.elements.crossfire.model.signal;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.sdk.annotation.ElementPublic;

/**
 * Represents a signal in the Crossfire signaling system. Signals are exchanged in the signaling phase after a match has
 * been assigned to the current session. All signals must include the lifecycle of the signal which indicates how the
 * server will cache the signal and deliver it to recipients.
 */
@ElementPublic
public interface Signal extends ProtocolMessage {

    /**
     * Gets the lifecycle of the signal.
     *
     * @return the lifecycle of the signal
     */
    SignalLifecycle getLifecycle();

}
