package dev.getelements.elements.crossfire.model.signal;

/**
 * Defines the lifecycle of the signaling messages in the Crossfire protocol. This controls how messages are buffered
 * in the server. When a new connection is made, the persistent messages are sent to the new client based on the
 * lifecycle rules.
 */
public enum SignalLifecycle {

    /**
     * The message is sent once and once delivered it is not expected to be sent again. This is the default. Messages
     * with this lifecycle are not buffered and therefore may get lost if the client is not connected at the time they
     * originated.
     */
    ONCE,

    /**
     * The message lives for the lifetime of the session which is the originator of the message. Once the connection
     * is closed, the message is no longer valid and therefore removed from signaling.
     */
    SESSION,

    /**
     * The message is sent
     */
    MATCH

}
