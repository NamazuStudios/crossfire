package dev.getelements.elements.crossfire.api;

import dev.getelements.elements.crossfire.model.handshake.FindHandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.JoinHandshakeRequest;

/**
 * Represents a matchmaking algorithm that can be used to match players or profiles based on certain criteria.
 */
public interface MatchmakingAlgorithm {

    /**
     * Gets the name of the matchmaking algorithm.
     *
     * @return the name
     */
    String getName();

    /**
     * Starts the matchmaking algorithm. Note that this method is non-blocking and returns immediately and does not
     * write to the database or perform any blocking operations.
     *
     * @param request the matchmaking request
     * @return the match handle
     */
    MatchHandle<FindHandshakeRequest> find(MatchmakingRequest<FindHandshakeRequest> request);

    /**
     * Starts the matchmaking algorithm. Note that this method is non-blocking and returns immediately and does not
     * write to the database or perform any blocking operations.
     *
     * @param request the matchmaking request
     * @return the match handle
     */
    MatchHandle<JoinHandshakeRequest> join(MatchmakingRequest<JoinHandshakeRequest> request);

}
