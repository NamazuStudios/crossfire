package dev.getelements.elements.crossfire.api;

import dev.getelements.elements.crossfire.api.model.handshake.HandshakeRequest;
import dev.getelements.elements.sdk.annotation.ElementPublic;
import dev.getelements.elements.sdk.model.match.MultiMatch;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Represents a handle for a matchmaking process, allowing you to start, leave, and retrieve the result of the match.
 * Since the matchmaking process may enforce specific rules for how the match is managed, the implementation of this
 * provides hooks for managing the lifecycle of the match.
 *
 * @param <RequestT> the type of handshake request used to initiate the matchmaking process
 */
@ElementPublic
public interface MatchHandle<RequestT extends HandshakeRequest> {

    /**
     * Gets the matchmaking algorithm used to make this match.
     *
     * @return the matchmaking algorithm
     */
    MatchmakingAlgorithm<?, ?> getAlgorithm();

    /**
     * Starts the matchmaking algorithm. Note that this method is non-blocking and returns immediately and does not
     * write to the database or perform any blocking operations. At some point in the future, the match will transition
     * to a new state, which can be observed by calling {@link #getResult()}.
     */
    void startMatching();

    /**
     * Ends the matchmaking algorithm, indicating that the player is no longer interested in being matched. This does
     * not.
     */
    void endMatch();

    /**
     * Opens the match indicating that the match is open and ready to accept players.
     */
    void openMatch();

    /**
     * Closes the match indicating that the match is no longer accepting players until it is reopened.
     */
    void closeMatch();

    /**
     * Cancels the matchmaking algorithm removing the player from the matchmaking queue. If the match has not  yet
     * been found then this just cancels the pending matchmaking operation.
     */
    void leaveMatch();

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

}
