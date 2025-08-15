package dev.getelements.elements.crossfire.protocol;

/**
 * Indicates what the phase is for signaling.
 */
public enum SignalingPhase {

    /**
     * Indicates signaling is ready.
     */
    READY,

    /**
     * Indicates that the signaling system is active and exchanging signals.
     */
    SIGNALING,

    /**
     * Indicates that the signaling is terminated.
     */
    TERMINATED

}
