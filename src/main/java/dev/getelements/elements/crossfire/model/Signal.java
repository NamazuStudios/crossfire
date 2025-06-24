package dev.getelements.elements.crossfire.model;

/**
 * Represents a signal in the Crossfire signaling system.
 */
public interface Signal {

    /**
     * Returns the type of the signal.
     *
     * @return the type
     */
    Type getType();

    /**
     * Gets the id of the profile that sent the signal.
     *
     * @return the profile id
     */
    String getProfileId();

    /**
     * SignalType represents the type of signal in the Crossfire signaling system.
     */
    enum Type {

        /**
         * Represents the AUTH signal, which is used for authentication purposes.
         */
        AUTH,

        /**
         * Represents a signal indicating that authentication was successful.
         */
        AUTH_OK,

        /**
         * Represents a signal that carries an SDP offer.
         */
        SDP_OFFER,

        /**
         * Represents a signal that carries an SDP answer.
         */
        SDP_ANSWER,

        /**
         * Represents a signal that carries a candidate for the WebRTC connection.
         */
        CANDIDATE,

        /**
         * Represents a signal indicating that a profile disconnected from the session.
         */
        DISCONNECT,

        /**
         * Specifies the designated HOST profile
         */
        HOST,

        /**
         * A signal indicating an error occurred during the signaling process.
         */
        ERROR

    }

}
