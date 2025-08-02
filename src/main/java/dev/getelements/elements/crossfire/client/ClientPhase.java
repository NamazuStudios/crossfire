package dev.getelements.elements.crossfire.client;

public enum ClientPhase {

    /**
     * Indicates that the client is not connected to any server, but is ready to connect to one.
     */
    READY,

    /**
     * Indicates that the client is connected to a server and is ready to begin the handshaking process.
     */
    CONNECTED,

    /**
     * Indicates that the client is handshaking with the server.
     */
    HANDSHAKING,

    /**
     * Indicates that the client is signaling and capable of exchanging`.
     */
    SIGNALING,

    /**
     * Indicates that the client is closed and no longer will exchange data
     * with the server.
     */
    TERMINATED

}
