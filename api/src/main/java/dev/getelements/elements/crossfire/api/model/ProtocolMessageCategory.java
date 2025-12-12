package dev.getelements.elements.crossfire.api.model;

import dev.getelements.elements.sdk.annotation.ElementPublic;

/**
 * The category of the protocol message. The life of the websocket connection is divided into categories for each phase
 * of the matchmaking system.
 */
@ElementPublic
public enum ProtocolMessageCategory {

    /**
     * Used in the handshake process, before a match starts.
     */
    HANDSHAKE,

    /**
     * Used in the signaling process, after a match starts signals in this category will be sent to all profiles in
     * the match via the signaling server.
     */
    SIGNALING,

    /**
     * Used for direct signaling between profiles in a match. After a match starts, profiles can send messages
     * directly to the recipient profile via the signaling server.
     **/
    SIGNALING_DIRECT,

    /**
     * Used for control messages that manage the match itself.
     */
    CONTROL,

    /**
     * Used for error messages.
     */
    ERROR

}
