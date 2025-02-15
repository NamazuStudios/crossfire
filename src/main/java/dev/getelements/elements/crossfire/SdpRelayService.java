package dev.getelements.elements.crossfire;

import dev.getelements.elements.sdk.Subscription;

import java.util.function.Consumer;

/**
 * Handles the interchange of SDP Messages.
 */
public interface SdpRelayService {

    /**
     * Adds a session description for the supplied match ID.
     *
     * @param matchId the matchId
     * @param sdpMessage thd SDP formated message
     */
    void addSessionDescription(String matchId, String sdpMessage);

    /**
     * Subscribes to updates with the supplied connection id, match id, and consumers. Upon subscription, all currently
     * pending matches will dispatch to the message consumer.
     *
     * @param sdpMessageConsumer the message consumer
     * @param sdpErrorConsumer   called when there is an error, thus terminating the connection
     */
    Subscription subscribeToUpdates(
            String matchId,
            Consumer<String> sdpMessageConsumer,
            Consumer<Throwable> sdpErrorConsumer);

}
