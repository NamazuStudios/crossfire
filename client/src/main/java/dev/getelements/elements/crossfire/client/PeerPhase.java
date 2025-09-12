package dev.getelements.elements.crossfire.client;

/**
 * Indicates the peer connection phase.
 */
public enum PeerPhase {

    /**
     * The peer is ready to connect.
     */
    READY,

    /**
     * The peer is connected and ready to send data.
     */
    CONNECTED,

    /**
     * The peer has been terminated or closed.
     */
    TERMINATED

}
