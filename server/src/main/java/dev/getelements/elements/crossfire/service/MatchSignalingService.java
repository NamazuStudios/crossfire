package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.api.model.ProtocolMessage;
import dev.getelements.elements.crossfire.api.model.signal.BroadcastSignal;
import dev.getelements.elements.crossfire.api.model.signal.DirectSignal;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.annotation.ElementPublic;
import dev.getelements.elements.sdk.annotation.ElementServiceExport;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Handles the interchange of SDP Messages.
 */
@ElementPublic
@ElementServiceExport
public interface MatchSignalingService {

    /**
     * Gets the host for the match.
     *
     * @return the host profile ID, or null if no host is assigned.
     */
    Optional<String> findHost();

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
     * Joins the match for the given profile ID. If the user has already joined the match, then this has no effect.
     *
     * @param matchId the match ID
     * @param profileId the profile ID
     * @return true if the profile was added, false if it was already present
     */
    boolean join(String matchId, String profileId);

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
    Subscription connect(
            String matchId,
            String profileId,
            Consumer<ProtocolMessage> onMessage,
            Consumer<Throwable> onError
    );

    /**
     * Leaves the match for the given profile ID. If the profile ID is the host, then the host will be unassigned or
     * reassigned to another participant.
     *
     * @param matchId the match id
     * @param profileId the profile id
     */
    boolean leave(String matchId, String profileId);

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

}
