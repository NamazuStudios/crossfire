package dev.getelements.elements.crossfire.client;

import dev.getelements.elements.sdk.Subscription;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Once connected via signaling, a MatchHost allows sending and receiving messages to/from peers. In order to simplify
 * connection states, this interface does not provide connection state information.
 */
public interface MatchHost {

    /**
     * Sends a message to a peer identified by the given profile ID. If there is no peer connected, the message will
     * buffer until a peer connects.  Note this will not block the thread, but will assume that the buffer will
     * remain untouched until the message is sent.
     *
     * @param profileId the profile id
     * @param buffer the buffer of data to send
     * @return SendStatus if the message was accepted for sending, false otherwise
     */
    default SendStatus send(final String profileId, final ByteBuffer buffer) {
        return send(profileId, buffer, null);
    }

    /**
     * Sends a message to a peer identified by the given profile ID. If there is no peer connected, the message will
     * buffer until a peer connects.  Note this will not block the thread, but will assume that the buffer will
     * remain untouched until the message is sent.
     *
     * @param profileId the profile id
     * @param buffer the buffer of data to send
     * @param onSent a callback that will be called when the message is sent allowing the caller to reclaim the buffer.
     *               May be null, in which case the buffer will be subject to garbage collection once the message is
     *               sent.
     * @return SendStatus if the message was accepted for sending, false otherwise
     */
    SendStatus send(String profileId, ByteBuffer buffer, Consumer<ByteBuffer> onSent);

    /**
     * Receives messages sent from a client.
     *
     * @param onMessage the message
     * @return a {@link Subscription}
     */
    Subscription onMessage(final BiConsumer<Subscription, Message> onMessage);

    /**
     * Closes the server and all underlying connections.
     */
    void close();

    /**
     * A message received from a client.
     *
     * @param profileId the profile id
     * @param data
     */
    record Message(String profileId, ByteBuffer data) {}

    /**
     * Indicates the status of the message.
     */
    enum SendStatus {

        /**
         * Message was successfully sent.
         */
        SENT,

        /**
         * Message was not sent because there is no peer with the given profile ID.
         */
        NO_PEER,

        /**
         * Message was not sent because the MatchHost is not ready to send messages.
         */
        NOT_READY;

        /**
         * Returns true if the message was sent successfully.
         *
         * @return true if successful, false otherwise
         */
        public boolean isSuccessful() {
            return SENT == this;
        }

    }

}
