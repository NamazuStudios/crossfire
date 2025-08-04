package dev.getelements.elements.crossfire.protocol;

/**
 * Represents the different phases of the handshake process in the Crossfire protocol.
 * Each phase indicates a specific state in the authentication and matching process.
 */
public enum HandshakePhase {

    /**
     * The initial phase where the protocol is waiting for the session to be established.
     */
    WAITING,

    /**
     * Indicates that the handshake is ready to begin.
     */
    READY,

    /**
     * The phase where the client is authenticating with the server.
     */
    AUTHENTICATING,

    /**
     * The phase where the client has authenticated successfully.
     */
    AUTHENTICATED,

    /**
     * Indicates that the matching process is in progress.
     */
    MATCHING,

    /**
     * Indicates that the player has been matched successfully.
     */
    MATCHED,

    /**
     * Indicates that the matching process has been terminated.
     */
    TERMINATED

}
