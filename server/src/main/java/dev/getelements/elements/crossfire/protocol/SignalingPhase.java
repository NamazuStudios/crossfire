package dev.getelements.elements.crossfire.protocol;

import dev.getelements.elements.sdk.annotation.ElementPublic;

/**
 * Indicates what the phase is for signaling.
 */
@ElementPublic
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
