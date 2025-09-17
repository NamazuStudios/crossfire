package dev.getelements.elements.crossfire.client;

import dev.getelements.elements.crossfire.model.Protocol;
import dev.getelements.elements.sdk.Subscription;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

/**
 * Represents a connection to a peer.
 */
public interface Peer {

    /**
     * Gets the {@link PeerPhase} of the peer.
     *
     * @return the peer
     */
    PeerPhase getPhase();

    /**
     * Gets the {@link Protocol} associated with this peer.
     *
     * @return the protocol
     */
    Protocol getProtocol();

    /**
     * Gets the profile id for the peer.
     *
     * @return the profile id for the peer.
     */
    String getProfileId();

    /**
     * Sends a message to a peer identified by the given profile ID. If there is no peer connected, the message will
     * buffer until a peer connects.  Note this will not block the thread, but will assume that the buffer will
     * remain untouched until the message is sent.
     *
     * @param string the buffer of data to send
     * @return SendStatus if the message was accepted for sending, false otherwise
     */
    SendResult send(String string);

    /**
     * Sends a message to a peer identified by the given profile ID. If there is no peer connected, the message will
     * buffer until a peer connects.  Note this will not block the thread, but will assume that the buffer will
     * remain untouched until the message is sent.
     *
     * @param buffer the buffer of data to send
     * @return SendStatus if the message was accepted for sending, false otherwise
     */
    SendResult send(ByteBuffer buffer);

    /**
     * Receives messages sent from a client.
     *
     * @param onError the message
     * @return a {@link Subscription}
     */
    Subscription onError(BiConsumer<Subscription, Throwable> onError);

    /**
     * Receives messages sent from a client.
     *
     * @param onMessage the message
     * @return a {@link Subscription}
     */
    Subscription onMessage(BiConsumer<Subscription, Message> onMessage);

    /**
     * Receives messages sent from a client.
     *
     * @param onMessage the message
     * @return a {@link Subscription}
     */
    Subscription onStringMessage(BiConsumer<Subscription, StringMessage> onMessage);

    /**
     * Indicates the status of the message.
     */
    enum SendResult {

        /**
         * Message was successfully sent.
         */
        SENT,

        /**
         * Message was not sent because the Peer is not ready to send messages but may be later.
         */
        NOT_READY,

        /**
         * Indicates that an error occurred and the message was not sent.
         */
        ERROR,

        /**
         * Indicates that the connection to the peer has been terminated and the message will not be sent.
         */
        TERMINATED;

        /**
         * Returns true if the message was sent successfully.
         *
         * @return true if successful, false otherwise
         */
        public boolean isSuccessful() {
            return SENT == this;
        }

    }

    /**
     * A message received from a client.
     *
     * @param data
     */
    record Message(Peer peer, ByteBuffer data) {}

    /**
     * A message received from a client.
     *
     * @param data
     */
    record StringMessage(Peer peer, String data) {}
}
