package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.signal.BroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.DirectSignal;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.annotation.ElementPublic;
import dev.getelements.elements.sdk.annotation.ElementServiceExport;

import java.util.function.Consumer;

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
     * Assigns a host to matches that do not have one. This assigns a host arbitrarily. If a host is already present
     * then this method does not change it.
     *
     * @param matchId the match ID
     */
    void assignHost(String matchId);

    /**
     * Assigns a host to matches. If a host is already present then this method does change the host.
     */
    void assignHost(String matchId, String profileId);

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
    Subscription join(
            String matchId,
            String profileId,
            Consumer<ProtocolMessage> onMessage,
            Consumer<Throwable> onError
    );

}
