package dev.getelements.elements.crossfire.api;

import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.sdk.model.match.MultiMatch;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Represents a handle for a matchmaking process, allowing you to start, leave, and retrieve the result of the match.
 * Consider this the live connection to the match which must be closed out when the connection fails or the match is no
 * longer needed.
 */
public interface MatchHandle<RequestT extends HandshakeRequest> {

    /**
     * Starts the matchmaking algorithm. Note that this method is non-blocking and returns immediately and does not
     * write to the database or perform any blocking operations. At some point in the future, the match will transition
     * to a new state, which can be observed by calling {@link #getResult()}.
     */
    void start();

    /**
     * Cancels the matchmaking algorithm removing the player from the matchmaking queue.
     */
    void leave();

    /**
     * Gets the {@link MultiMatch}, throwing NoSuchElementException if the match is not yet complete or otherwise missing.
     *
     * @return the match
     * @throws NoSuchElementException if the match is not yet complete or otherwise missing
     */
    default MultiMatch getResult() {
        return findResult().orElseThrow(NoSuchElementException::new);
    }

    /**
     * Finds the {@link MultiMatch}, if it exists, without throwing an exception and returning an empty optional if the
     * match is not yet complete or otherwise missing.
     *
     * @return an {@link Optional} containing the match if it exists, or empty if not
     */
    Optional<MultiMatch> findResult();

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
