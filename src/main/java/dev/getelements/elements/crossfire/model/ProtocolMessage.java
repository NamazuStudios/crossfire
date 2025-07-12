package dev.getelements.elements.crossfire.model;

import dev.getelements.elements.crossfire.model.error.StandardProtocolError;
import dev.getelements.elements.crossfire.model.handshake.ConnectedResponse;
import dev.getelements.elements.crossfire.model.handshake.FindHandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.JoinHandshakeRequest;
import dev.getelements.elements.crossfire.model.signal.*;

import java.util.Optional;
import java.util.stream.Stream;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Category.HANDSHAKE;
import static dev.getelements.elements.crossfire.model.ProtocolMessage.Category.SIGNALING;

public interface ProtocolMessage {

    /**
     * Returns the type of the message.
     *
     * @return the type
     */
    Type getType();

    enum Type {

        /**
         * Indicates the request is to find a match. The server will select
         */
        FIND(HANDSHAKE, FindHandshakeRequest.class),

        /**
         * Indicates the request is to join a match. The server will select the specific match.
         */
        JOIN(HANDSHAKE, JoinHandshakeRequest.class),

        /**
         * Indicates that the client has successfully connected to a match.
         */
        CONNECTED(HANDSHAKE, ConnectedResponse.class),

        /**
         * Represents a signal that carries an SDP offer.
         */
        SDP_OFFER(SIGNALING, SdpOfferSignal.class),

        /**
         * Represents a signal that carries an SDP answer.
         */
        SDP_ANSWER(SIGNALING, SdpAnswerSignal.class),

        /**
         * Represents a signal that carries a candidate for the WebRTC connection.
         */
        CANDIDATE(SIGNALING, CandidateSignal.class),

        /**
         * Represents a signal indicating that a profile disconnected from the session.
         */
        DISCONNECT(SIGNALING, DisconnectSignal.class),

        /**
         * Specifies the designated HOST profile
         */
        HOST(SIGNALING, HostSignal.class),

        /**
         * Represents a signal that carries a message to be sent to the host.
         */
        ERROR(Category.ERROR, StandardProtocolError.class);

        private final Category category;

        private final Class<? extends ProtocolMessage> messageType;

        Type(Category category, Class<? extends ProtocolMessage> messageType) {
            this.category = category;
            this.messageType = messageType;
        }

        /**
         * Gets the message type.
         *
         * @return the message type
         */
        public Class<? extends ProtocolMessage> getMessageType() {
            return messageType;
        }

        /**
         * Finds the {@link Type} from the string representation of the type.
         *
         * @return an {@link Optional} of the type
         */
        public static Optional<Type> findType(final String value) {
            return Stream
                    .of(values())
                    .filter(type -> type.name().equalsIgnoreCase(value))
                    .findFirst();
        }


    }

    /**
     * The category of the protocol message. The life of the websocket connection is divided into categories for each
     * phase of the matchmaking system.
     */
    enum Category {

        /**
         * Used in the handshake process, before a match starts.
         */
        HANDSHAKE,

        /**
         * Used in the signaling process, after a match starts.
         */
        SIGNALING,

        /**
         * Used for error messages.
         */
        ERROR

    }

}
