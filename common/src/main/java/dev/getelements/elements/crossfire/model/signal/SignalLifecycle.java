package dev.getelements.elements.crossfire.model.signal;

import dev.getelements.elements.sdk.annotation.ElementPublic;

/**
 * Defines the lifecycle of the signaling messages in the Crossfire protocol. This controls how messages are buffered
 * in the server. When a new connection is made, the persistent messages are sent to the new client based on the
 * lifecycle rules.
 */
@ElementPublic
public enum SignalLifecycle {

    /**
     * The message is sent once and once delivered it is not expected to be sent again. This is the default. Messages
     * with this lifecycle are not buffered and therefore may get lost if the client is not connected at the time they
     * originated.
     */
    ONCE,

    /**
     * The message lives for the lifetime of the session or when the player holding that session leaves the match.
     */
    SESSION,

    /**
     * The message remains in the mailbox for the entire match and will remain until the match is over, or the player
     * who sent the message leaves the match.
     */
    MATCH

}
