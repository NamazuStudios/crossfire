package dev.getelements.elements.crossfire.protocol;

/**
 * Indicates the current phase of the connection.
 */
public enum ConnectionPhase {

    /**
     * Waiting for the connection to be established.
     */
    WAITING,

    /**
     * The connection is ready to send and receive messages.
     */
    READY,

    /**
     * The initial phase where the connection is being established.
     */
    HANDSHAKE,

    /**
     * The phase where the client may proceed with signaling operations.
     */
    SIGNALING,

    /**
     * The phase where the connection is closed and no further signaling is expected.
     */
    TERMINATED

}
