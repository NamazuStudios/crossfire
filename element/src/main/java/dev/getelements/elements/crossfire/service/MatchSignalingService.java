package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.signal.BroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.DirectSignal;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.annotation.ElementPublic;
import dev.getelements.elements.sdk.annotation.ElementServiceExport;

import java.util.function.BiConsumer;

/**
 * Handles the interchange of SDP Messages.
 */
@ElementPublic
@ElementServiceExport
public interface MatchSignalingService {

    /**
     * Sends the signal to the match.
     *
     * @param matchId the match ID
     * @param signal the signal
     */
    void send(String matchId, DirectSignal signal);

    /**
     * Broadcasts the supplied signal to everyone in the match.
     *
     * @param matchId the match ID
     * @param signal the signal
     */
    void send(String matchId, BroadcastSignal signal);

    /**
     * Subscribes to updates with the supplied connection id, match id, profile id, and consumers. If this is the first
     * subscription, then it will immediately receive all backlogged messages. Only one {@link Subscription} may exist
     * at a time. Existing subscriptions
     *
     * @param matchId the Match ID
     * @param profileId the profile ID.
     * @param onMessage the message consumer
     * @param onError the message error
     */
    Subscription subscribe(
            String matchId,
            String profileId,
            BiConsumer<Subscription, ProtocolMessage> onMessage,
            BiConsumer<Subscription, Throwable > onError
    );

}
