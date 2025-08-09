package dev.getelements.elements.crossfire.api;

import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.sdk.model.match.MultiMatch;

/**
 * Gets the name of the matchmaking algorithm.
 */
public interface Match<RequestT extends HandshakeRequest> {

    /**
     * Starts the matchmaking algorithm. Note that this method is non-blocking and returns immediately and does not
     * write to the database or perform any blocking operations. At some point in the future, the match will transition
     * to a new state, which can be observed by calling {@link #getResult()}.
     */
    void start();

    /**
     * Cancels the matchmaking algorithm removing the player from the matchmaking queue.
     */
    void cancel();

    /**
     * Gets the {@link MultiMatch}
     *
     * @return the match
     */
    MultiMatch getResult();

    /**
     * Gets the request used to make this match.
     *
     * @return the matchmaking request
     */
    MatchmakingRequest<RequestT> getRequest();

    /**
     * Gets the matchmaking algorithm used to make this match.
     *
     * @return the matchmaking algorithm
     */
    MatchmakingAlgorithm getAlgorithm();

}
