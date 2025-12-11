package dev.getelements.elements.crossfire.model;

import dev.getelements.elements.crossfire.model.control.CloseControlMessage;
import dev.getelements.elements.crossfire.model.control.EndControlMessage;
import dev.getelements.elements.crossfire.model.control.LeaveControlMessage;
import dev.getelements.elements.crossfire.model.control.OpenControlMessage;
import dev.getelements.elements.crossfire.model.error.StandardProtocolError;
import dev.getelements.elements.crossfire.model.handshake.CreateHandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.FindHandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.JoinHandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.MatchedResponse;
import dev.getelements.elements.crossfire.model.signal.*;
import dev.getelements.elements.sdk.annotation.ElementPublic;

import java.util.Optional;
import java.util.stream.Stream;

import static dev.getelements.elements.crossfire.model.ProtocolMessageCategory.*;

/**
 * The type of the protocol message. Each type is associated with a specific category and message class.
 */
@ElementPublic
public enum ProtocolMessageType {

    /**
     * Indicates the request is to find a match. The server will select
     */
    FIND(HANDSHAKE, FindHandshakeRequest.class),

    /**
     * Indicates the request is to join a match. The server will select the specific match.
     */
    JOIN(HANDSHAKE, JoinHandshakeRequest.class),

    /**
     * Indicates the request is to join a match. The server will select the specific match.
     */
    CREATE(HANDSHAKE, CreateHandshakeRequest.class),

    /**
     * Indicates that the client has successfully connected to a match.
     */
    MATCHED(HANDSHAKE, MatchedResponse.class),

    /**
     * Represents a signal indicating that a profile disconnected from the session.
     */
    CONNECT(SIGNALING, ConnectBroadcastSignal.class),

    /**
     * Represents a signal indicating that a profile disconnected from the session.
     */
    DISCONNECT(SIGNALING, DisconnectBroadcastSignal.class),

    /**
     * Represents a signal that carries an SDP offer.
     */
    SDP_OFFER(SIGNALING_DIRECT, SdpOfferDirectSignal.class),

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
     * Represents a signal that carries a binary payload to be broadcasted to all profiles in the match.
     */
    STRING_BROADCAST(SIGNALING, StringBroadcastSignal.class),

    /**
     * Represents a signal that carries a binary payload to be relayed to a specific profile in the match.
     */
    STRING_RELAY(SIGNALING_DIRECT, StringRelayDirectSignal.class),

    /**
     * Represents a signal that carries a candidate for the WebRTC connection.
     */
    CANDIDATE(SIGNALING_DIRECT, CandidateDirectSignal.class),

    /**
     * Specifies the designated HOST profile
     */
    HOST(SIGNALING, HostBroadcastSignal.class),

    /**
     * Specifies when a participant has joined the match.
     */
    SIGNAL_JOIN(SIGNALING, JoinBroadcastSignal.class),

    /**
     * Specifies when a participant has left the match.
     */
    SIGNAL_LEAVE(SIGNALING, LeaveBroadcastSignal.class),

    /**
     * Requests that the client leave the match.
     */
    LEAVE(CONTROL, LeaveControlMessage.class),

    /**
     * Requests that the client open the match for new participants.
     */
    OPEN(CONTROL, OpenControlMessage.class),

    /**
     * Requests that the client close the match for new participants.
     */
    CLOSE(CONTROL, CloseControlMessage.class),

    /**
     * Requests that the client leave the match and close the match for new participants.
     */
    END(CONTROL, EndControlMessage.class),

    /**
     * Represents a signal that carries a message to be sent to the host.
     */
    ERROR(ProtocolMessageCategory.ERROR, StandardProtocolError.class);

    private final ProtocolMessageCategory category;

    private final Class<? extends ProtocolMessage> messageType;

    ProtocolMessageType(ProtocolMessageCategory category, Class<? extends ProtocolMessage> messageType) {
        this.category = category;
        this.messageType = messageType;
    }

    /**
     * Gets the category of the message type.
     *
     * @return the category
     */
    public ProtocolMessageCategory getCategory() {
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
     * Finds the {@link ProtocolMessageType} from the string representation of the type.
     *
     * @return an {@link Optional} of the type
     */
    public static Optional<ProtocolMessageType> findType(final String value) {
        return Stream
                .of(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst();
    }


}
