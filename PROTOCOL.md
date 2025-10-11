# Crossfire Protocol

The Crossfire protocol is a WebSocket driven protocol to enable signaling and data exchange to establish cross-platform matchmaking among various gaming platforms. It is designed to be extensible, secure, and efficient.

It provides a set of messages and operations which can be used to facilitate matchmaking, game session management, and participant communication. For Peer to Peer communication, we use WebRTC as the wire protocol as well as a JSON based signaling protocol for establishing connections. The signaling protocol can also be used to exchange and relay real time game data.

**Note:** At the time of this writing, the only supported protocol version is 1.0. The server uses the literal string `V_1_0` to indicate this version. See [Version.java](common/src/main/java/dev/getelements/elements/crossfire/model/Version.java)

## Operational Overview

The Crossfire Protocol operates over a single WebSocket connection between the client and the server. The client initiates the connection and performs a handshake to authenticate and establish the session. Once the handshake is complete, the client can send and receive messages to participate in matchmaking and game sessions.

From the client's perspective, the protocol has three major phases:
* **Handshake Phase** - The client establishes a WebSocket connection to the server and performs a handshake to authenticate and establish the session. The client MUST send one and only one handshake request, and the server MUST respond with one and only one handshake response. Only after successful doses the protocol transition to the signaling phase. The server MUST NOT accept any other messages at this time..
* **Signaling Phase** - The client sends and receives signaling messages to facilitate matchmaking and game session management. This also includes the exchange of control messages as well as signaling messages. The server MUST accept any valid messages during this phase. Any non-recoverable errors MUST result in a ProtocolError and termination of the session.
* **Termination Phase** - The server will neither accept nor forward any messages. The server MAY silently ignore messages in this phase but MAY log them for debugging purposes. The server MUST close the WebSocket connection as soon as possible after entering this phase.

As an implementation detail, the server has an additional phases which are not visible to the client and are used for internal state management.

**Sources:**
* [ConnectionPhase.java](element/src/main/java/dev/getelements/elements/crossfire/protocol/ConnectionPhase.java)

# Message Categories and Types

All communication in the Crossfire protocol is done through WebSocket messages. Each message is a JSON object with a `type` field that indicates the specific message function and messages are divided into general categories for different functionalities.

All messages must contain the following fields:
* `type`  - Indicates the type of message being sent.

**Sources:**
* [ProtocolMessage.java](common/src/main/java/dev/getelements/elements/crossfire/model/ProtocolMessage.java)
* [ProtocolMessageType.java](common/src/main/java/dev/getelements/elements/crossfire/model/ProtocolMessageType.java)
* [ProtocolMessageCategory.java](common/src/main/java/dev/getelements/elements/crossfire/model/ProtocolMessageCategory.java)

## Handshake

The handshake messages are used to initiate the matchmaking process. Upon establishing the WebSocket connection, the client must send a `HANDSHAKE` message to the server. The server will only accept a single `HANDSHAKE` message per connection and will respond with either a Handshake Response or appropriate Protocol Error message.

The client MUST wait until it receives a `HandshakeResponse` before sending any other messages or else the server will report a ProtocolError and terminate communication.

For all `HANDSHAKE` messages the following rules apply:
* The requested profile ID MUST be associated with the provided session key or be owned by the user requesting the match.
* The requested profile ID MUST match the application associated with requested matchmaking configuration.
* If the profile ID is not provided, the session key MUST be bound to a single profile ID or else the server MUST close the connection with a ProtocolError.
* All other security and validation rules associated MUST apply. For example the server MUST respect the privacy settings of the profile and MUST NOT leak personally identifiable information to all participants.

**Sources:**
 * [HandshakeRequest.java](common/src/main/java/dev/getelements/elements/crossfire/model/handshake/HandshakeRequest.java)
 * [HandshakeResponse.java](common/src/main/java/dev/getelements/elements/crossfire/model/handshake/HandshakeResponse.java)

### `FIND` (Client to Server)

The `FIND` message is used to find a match and if no Matches are created, the server may opt to create a match such that other participants can join. The server MUST NOT respond until it assigns a match or reports an error. The server MAY create a match and assign the client to it or it MAY assign the client to an existing match.

Upon successful match, the server MUST respond with a `MATCHED` message.

The FIND message contains the following fields:
* `version` - *Required.* The version of the protocol the client is using. This is used to ensure compatibility between client and server. Currently only `V_1_0` is supported.
* `profileId` - *Optional.* The profile ID of the client. _Required only if the `sessionKey` is not bound to a session._
* `sessionKey` - *Required.* The session key provided by the Elements RESTful API
* `configuration` *Required.* The name or ID of the MatchmakingApplicationConfiguration used to perform the matchmaking.

**Sources**:
* [JoinHandshakeRequest.java](common/src/main/java/dev/getelements/elements/crossfire/model/handshake/JoinHandshakeRequest.java)

### `JOIN` (Client To Server)

The `JOIN` message is used to join an existing match, provided the client already knows the match ID. Typically this is used to re-join a match that the client previously joined by may have disconnected.

Upon successful match, the server MUST respond with a `MATCHED` message.

**Sources**:
* [FindHandshakeRequest.java](common/src/main/java/dev/getelements/elements/crossfire/model/handshake/FindHandshakeRequest.java)

* `version` - *Required.* The version of the protocol the client is using. This is used to ensure compatibility between client and server. Currently only `V_1_0` is supported.
* `profileId` - *Optional.* The profile ID of the client. _Required only if the `sessionKey` is not bound to a session._
* `sessionKey` - *Required.* The session key provided by the Elements RESTful API
* `matchId` - *Required.* The match ID.

### `MATCHED` (Server to Client)

The server MUST send a `MATCHED` message to the client when the client has been successfully matched to a game session. The `MATCHED` message contains information about the match, including the match ID, the list of participants, and any additional metadata.

* `matchId` - *Required.* The unique identifier for the match.
* `profilId` - *Required.* The profile ID of the client which connected.

**Sources**:
* [MatchedResponse.java](common/src/main/java/dev/getelements/elements/crossfire/model/handshake/MatchedResponse.java)

## Signaling

Signaling messages begin immediately after the handshake process is complete. These messages are used to facilitate the exchange of WebRTC signaling data between clients in a match. The server acts as a relay for these messages, forwarding them to the appropriate participants in the match.

The server MUST send signaling messages after the handshake is complete and the client MUST be prepared to receive signaling messages at any time after the handshake.

Signing messages have a lifecycle associated with determines how long the server buffers the message in each client's outgoing mailbox.

Some signals are considered server only in which case the server MUST be the sender of the signal. A client MUST NOT attempt to send a server only signal. If a client attempts to send a server only signal, the server MUST disconnect the client with a ProtocolError.

**Sources**:
* [Signal.java](common/src/main/java/dev/getelements/elements/crossfire/model/signal/Signal.java)

### Broadcast Signals

Broadcast signals are sent to all participants in a match. The server MUST forward the message to all participants except the originator. All broadcast signals, in addition to the base protocol message fields, MUST contain the following fields:

* `profileId` - **Required.** The profile ID of the originator of the signal. Note that the server MAY send a signal on behalf of a participant. In this document we use the term "originator" to mean the source of a signal even if that signal was not send directly by the participant's client.


**Sources**:
* [BroadcastSignal.java](common/src/main/java/dev/getelements/elements/crossfire/model/signal/BroadcastSignal.java)

### Direct Signals

Direct signals are sent to a single specific participant in a match. The server MUST forward the message to the intended recipient if they are in the same match as the originator. A client MUST NOT send a direct signal to itself. All direct signals, in addition to the base protocol message fields, MUST contain the following fields:

* `profileId` - _Same as Broadcast Signals._
* `recipientProfileId` - **Required.** The profile ID of the intended recipient of the signal. The server MUST forward the message to the participant with this profile ID and ONLY this profile id if they are in the same match as the originator. If the recipient is not in the same match, the server MUST respond with a ProtocolError message.

**Sources**: 
* [DirectSignal.java](common/src/main/java/dev/getelements/elements/crossfire/model/signal/DirectSignal.java)

### Signaling Lifecycle

All signals contain a lifecycle which indicates long the server should retain the message in the client's outgoing mailbox. The lifecycle is defined by the `lifecycle` field in the signaling message and can have one of the following values.

* `ONCE` - The server will attempt to deliver the message once. If the client is not connected or does not acknowledge receipt of the message, the message will be discarded.
* `SESSION` - The server will retain the message as long as the originator's session closes the websocket connection to the server.
* `MATCH` - The server will retain the message until the match ends, or the participant leave the match. The match ends when all participants have left the match or when the server terminates the match. Note: a participant may disconnect and reconnect to the match without losing messages with this lifecycle.

**Sources**:
* [SignalingLifecycle.java](common/src/main/java/dev/getelements/elements/crossfire/model/signal/SignalLifecycle.java)

### `SIGNAL_JOIN` (Server Only)

The `SIGNAL_JOIN` signal is used to notify participants in a match that a new participant has joined the match. The server MUST send a `SIGNAL_JOIN` signal to all participants in the match except the originator when a new participant joins the match. The `SIGNAL_JOIN` signal contains the base broadcast signal fields indicating the player has joined.

**Sources**:
* [JoinBroadcastSignal.java](common/src/main/java/dev/getelements/elements/crossfire/model/signal/JoinBroadcastSignal.java)

### `SIGNAL_LEAVE` (Server Only)

The `SIGNAL_LEAVE` signal is used to notify participants in a match that a participant has left the match. The server MUST send a `SIGNAL_LEAVE` signal to all participants in the match except the originator when a participant leaves the match. The `SIGNAL_LEAVE` signal contains the base broadcast signal fields indicating the player has joined.

**Sources**:
* [LeaveBroadcastSignal.java](common/src/main/java/dev/getelements/elements/crossfire/model/signal/LeaveBroadcastSignal.java)

### `SIGNAL_END` (Server Only)

The `SIGNAL_LEAVE` signal is used to notify participants in a match that a participant has left the match. The server MUST send a `SIGNAL_LEAVE` signal to all participants in the match except the originator when a participant leaves the match. The `SIGNAL_LEAVE` signal contains the base broadcast signal fields indicating the player has joined.

**Sources**:
* [LeaveBroadcastSignal.java](common/src/main/java/dev/getelements/elements/crossfire/model/signal/LeaveBroadcastSignal.java)

### `CONNECT` (Server Only)

The `CONNECT` signal is used to notify participants in a match that a new participant has connected to the match. The server MUST send a `CONNECT` signal to all participants in the match except the originator when a new participant joins the match when the participant establishes a connection and successful `HANDSHAKE`.

**Sources**:
* [ConnectBroadcastSignal.java](common/src/main/java/dev/getelements/elements/crossfire/model/signal/ConnectBroadcastSignal.java)

### `DISCONNECT` (Server Only)

The `DISCONNECT` signal is used to notify participants in a match that a participant has disconnected from the match. The server MUST send a `DISCONNECT` signal to all participants in the match except the originator when a participant disconnects from the match. Note, that a `DISCONNECT` does not indicate that the participant has been removed but rather they have just lost connection to the match. The participant may reconnect to the match (eg using a `JOIN` message) and continue. The server MUST NOT remove the participant from the match until they leave the match or the match ends.

**Sources**:
* [DisconnectBroadcastSignal.java](common/src/main/java/dev/getelements/elements/crossfire/model/signal/DisconnectBroadcastSignal.java)

### `SDP_OFFER`

The `SDP_OFFER` is a direct signal used to send a WebRTC SDP offer to a specific participant in the match. The server MUST forward the message to the intended recipient if they are in the same match as the originator. A client MUST NOT send an `SDP_OFFER` to itself. The `SDP_OFFER` message contains the following fields in addition to the direct signal message fields:

* `peerSdp` - **Required.** The SDP offer string.

**Sources**: 
* [SdpOfferDirectSignal.java](common/src/main/java/dev/getelements/elements/crossfire/model/signal/SdpOfferDirectSignal.java)

### `SDP_ANSWER`

The `SDP_ANSWER` is a direct signal used to send a WebRTC SDP answer to a specific participant in the match. The server MUST forward the message to the intended recipient if they are in the same match as the originator. A client MUST NOT send an `SDP_ANSWER` to itself. The `SDP_ANSWER` message contains the following fields in addition to the direct signal message fields:

* `peerSdp` - **Required.** The SDP answer string.

**Sources**:
* [SdpAnswerDirectSignal.java](common/src/main/java/dev/getelements/elements/crossfire/model/signal/SdpAnswerDirectSignal.java)

### `CANDIDATE`

The `CANDIDATE` is a direct signal used to send a WebRTC ICE candidate to a specific participant in the match. The server MUST forward the message to the intended recipient if they are in the same match as the originator. This signal is used for Trickle ICE where a candidate may come in separately from the SDP. A client MUST NOT send an `CANDIDATE` to itself. The `CANDIDATE` message contains the following fields in addition to the direct signal message fields:

* `candidate` - **Required.** The ICE candidate string. In some WebRTC implementations this is also called "sdp" as it is typically a single line of SDP.
* `mid` - **Required.** The media ID associated with the candidate. Typically supplied by WebRTC implementations when generating the candidate.
* `midIndex` - **Required.** The media line index associated with the candidate. Typically supplied by WebRTC implementations when generating the candidate. For single data-channel applications this is usually `0`.

**Sources**: 
* [CandidateDirectSignal.java](common/src/main/java/dev/getelements/elements/crossfire/model/signal/CandidateDirectSignal.java)

### `HOST` (Server Only)

The `HOST` signal designs a participant as the host of the match. The host is typically responsible for managing the match and may have additional privileges. The server MUST send a `HOST` signal to all participants in the match when a participant is designated as the host. The `HOST` signal is a broadcast signal and contains no additional fields beyond the base broadcast signal fields.

**Sources**: 
* [HostBroadcastSignal.java](common/src/main/java/dev/getelements/elements/crossfire/model/signal/HostBroadcastSignal.java)

### `BINARY_RELAY` and `STRING_RELAY`

The `BINARY_RELAY` and `STRING_RELAY` are functionally similar signals which allow a client to send arbitrary data to other participants in the match. The `BINARY_RELAY` is used to send binary data while the `STRING_RELAY` is used to send string data. The server MUST forward the message to the intended recipient if they are in the same match as the originator. The `BINARY_RELAY` and `STRING_RELAY` messages contain the following fields in addition to the direct signal message fields:

* `payload` - **Required.** The data to be sent. For `BINARY_RELAY` this is a base64 encoded string. For `STRING_RELAY` this is a regular string.
* `lifecycle` - **Required.** The lifecycle of the message. See [Signaling Lifecycle](#signaling-lifecycle) for more information.

**Sources**:
* [BinaaryRelayDirectSignal.java](common/src/main/java/dev/getelements/elements/crossfire/model/signal/BinaryRelayDirectSignal.java)
* [StringRelayDirectSignal.java](common/src/main/java/dev/getelements/elements/crossfire/model/signal/StringRelayDirectSignal.java)

### `BINARY_BROADCAST` and `STRING_BROADCAST`

The `BINARY_BROADCAST` and `STRING_BROADCAST` are functionally similar signals which allow a client to broadcast arbitrary data to all other participants in the match. The `BINARY_BROADCAST` is used to send binary data while the `STRING_BROADCAST` is used to send string data. The server MUST forward the message to all participants in the match except the originator. The `BINARY_BROADCAST` and `STRING_BROADCAST` messages contain the following fields in addition to the base broadcast signal fields:

* *payload* - **Required.** The data to be sent. For `BINARY_BROADCAST` this is a base64 encoded string. For `STRING_BROADCAST` this is a regular string.
* *lifecycle* - **Required.** The lifecycle of the message. See [Signaling Lifecycle](#signaling-lifecycle) for more information.

**Sources**:
* [BinaaryBroadcastSignal.java](common/src/main/java/dev/getelements/elements/crossfire/model/signal/BinaryBroadcastSignal.java)
* [StringBroadcastSignal.java](common/src/main/java/dev/getelements/elements/crossfire/model/signal/StringBroadcastSignal.java)

## Control Messages

Control messages are used to manage the state of the match and its participants. Some control messages may be sent only by the host participant while others may be sent by any participant. The server MUST enforce the rules associated with each control message. Unlike signals, the server MUST NOT relay these messages to any other player. The server MUST process the control message and take appropriate action which MAY involve driving other signals.

* `profileId` - **Required.** The profile ID of the originator of the control operation.

**Sources:** 
* [ControlMessage.java](common/src/main/java/dev/getelements/elements/crossfire/model/control/ControlMessage.java)

### `LEAVE` (Client to Server)

The `LEAVE` control MUST be sent by the server to a client when the client has left the match. A participant who has left the match will no longer receive any messages from the server and will be removed from the match. The `LEAVE` message contains the base control message fields simply indicating the participant has left. Once a participant has left the match, they may not rejoin the match and the server MUST delete them from the match. The server MUST NOT allow the participant to re-join the match via a `JOIN` message. The server MAY allow the participant to join again through the `FIND` message if the match otherwise meets criteria to show up in the FIND request. Such circumstances would likely be coincidental.

If all participants leave the match, the server MUST end the match and clean up any resources associated with the match.

**Sources:**
* [LeaveControlMessage.java](common/src/main/java/dev/getelements/elements/crossfire/model/control/LeaveControlMessage.java)

### `OPEN` (Host to Server)

The `OPEN` control message is sent by the host participant to the server to indicate that the match is now open for new participants to join. The server MUST allow new participants to join the match after receiving an `OPEN` message from the host. The `OPEN` message contains the base control message fields. Note, that opening a match does not guarantee that new participants will be able to join. The server MUST still enforce any matchmaking rules associated with the match.

Upon first assignment, a match is considered open by default. Therefore, the host does not need to send an `OPEN` message when the match is first created. The host MAY send an `OPEN` message at any time to re-open the match if it has been closed. If the match is already open, the server MUST ignore the `OPEN` message. It MAY choose to log an error or warning to indicate buggy or problematic client behavior.

**Sources:**
* [OpenControlMessage.java](common/src/main/java/dev/getelements/elements/crossfire/model/control/OpenControlMessage.java)

### `CLOSE` (Host to Server)

The `CLOSE` control message is sent by the host participant to the server to indicate that the match is now closed for new participants to join. The server MUST NOT allow new participants to join the match after receiving a `CLOSE` message from the host. The `CLOSE` message contains the base control message fields. Note, closing a match does not remove existing participants from the match. Existing participants may continue to communicate and exchange messages until they leave the match or the match ends.

It is not necessary for the host to send a `CLOSE` message before ending the match with an `END` message. The server MUST ignore any `CLOSE` messages received if closed or ended. The server MAY choose to log an error or warning to indicate buggy or problematic client behavior.

Closing a match is optional and depends entirely on the application requirements. Some applications may choose to keep the match open for new participants to join while others may choose to close the match to new participants.

**Sources:**
* [CloseControlMessage.java](common/src/main/java/dev/getelements/elements/crossfire/model/control/CloseControlMessage.java)

### `END` (Host to Server)

The `END` control message is sent by the host participant to the server to indicate that the match is now over and should be terminated. The server MUST remove all participants from the match and terminate the match after receiving an `END` message from the host. The `END` message contains the base control message fields. Once a match has been ended, it may not be re-opened or re-joined. The server MUST transition the match to the ENDED state and clean up any resources associated with the match. The server MUST ultimately close all WebSocket connections associated with the match at its discretion.

**Sources:**
* [EndControlMessage.java](common/src/main/java/dev/getelements/elements/crossfire/model/control/EndControlMessage.java)

## Errors

Error messages are used to indicate that an error has occurred during the processing of a message. The server MUST send an `ERROR` message to the client when an error occurs. The client MUST handle `ERROR` messages appropriately. In the event of an error, the server MUST close the socket and terminate the session immediately. The client MAY reestablish connection and attempt to rejoin the match if appropriate provided that it can take corrective action.

The `ERROR` message contains the following fields in addition to the base protocol message fields:

* `code` - **Required.** The error code indicating the type of error that occurred. Codes are determined programmatically and are intended to be machine readable.
* `message` - **Required.** A human readable message describing the error that occurred. This message is intended for debugging purposes and may not be suitable for display to end users.

**Sources**:
* [ProtocolError.java](common/src/main/java/dev/getelements/elements/crossfire/model/error/ProtocolError.java)

# Final Notes

This is a work in progress and not a complete specification. The protocol may evolve over time as new features are added and existing features are refined. The goal is to provide a robust and flexible protocol that can be used to facilitate cross-platform matchmaking and real-time communication in a variety of gaming scenarios.

Known limitations:
* Additional matchmaking systems that establish internet connectivity outside of WebRTC are not natively supported. It would be possible to build such a system on top of the existing protocol using the relay signals but it is not a first class feature of the protocol at this time. It may make sense to add signaling types to include support for such matchmaking and NAT traversal systems.

For additional features, suggestions, and enhancements feel free to open a ticket or a pull request.
