package dev.getelements.elements.crossfire.model;

import dev.getelements.elements.crossfire.model.error.StandardProtocolError;
import dev.getelements.elements.crossfire.model.handshake.MatchedResponse;
import dev.getelements.elements.crossfire.model.handshake.FindHandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.JoinHandshakeRequest;
import dev.getelements.elements.crossfire.model.signal.*;

import java.util.Optional;
import java.util.stream.Stream;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Category.*;

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
        MATCHED(HANDSHAKE, MatchedResponse.class),

        /**
         * Represents a signal that carries an SDP offer.
         */
        SDP_OFFER(SIGNALING, SdpOfferBroadcastSignal.class),

        /**
         * Represents a signal that carries an SDP answer.
         */
        SDP_ANSWER(SIGNALING_DIRECT, SdpAnswerDirectSignal.class),

        /**
         * Represents a signal that carries a binary payload to be broadcasted to all profiles in the match.
         */
        BINARY_BROADCAST(SIGNALING, BinaryBroadcastSignal.class),

        /**
         * Represents a signal that carries a binary payload to be relayed to a specific profile in the match.
         */
        BINARY_RELAY(SIGNALING_DIRECT, BinaryRelayDirectSignal.class),

        /**
         * Represents a signal that carries a candidate for the WebRTC connection.
         */
        CANDIDATE(SIGNALING, CandidateBroadcastSignal.class),

        /**
         * Represents a signal indicating that a profile disconnected from the session.
         */
        DISCONNECT(SIGNALING, DisconnectBroadcastSignal.class),

        /**
         * Specifies the designated HOST profile
         */
        HOST(SIGNALING, HostBroadcastSignal.class),

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
         * Gets the category of the message type.
         *
         * @return the category
         */
        public Category getCategory() {
            return category;
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
         * Used for error messages.
         */
        ERROR

    }

}
