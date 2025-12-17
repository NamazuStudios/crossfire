package dev.getelements.elements.crossfire.api;

import dev.getelements.elements.crossfire.api.model.handshake.HandshakeRequest;
import dev.getelements.elements.sdk.annotation.ElementPublic;

/**
 * Represents a matchmaking algorithm that can be used to match players or profiles based on certain criteria.
 */
@ElementPublic
public interface MatchmakingAlgorithm<InitializeRequestT extends HandshakeRequest, ResumeRequestT extends HandshakeRequest> {

    /**
     * Gets the name of the matchmaking algorithm.
     *
     * @return the name
     */
    String getName();

    /**
     * Initializes the matchmaking algorithm. Note that this method is non-blocking and returns immediately and does not
     * write to the database or perform any blocking operations. Subsequent calls to the returned MatchHandle's methods
     * will actually perform the operations of the matching process.
     *
     * @param request the matchmaking request
     * @return the match handle
     */
    MatchHandle<InitializeRequestT> initialize(MatchmakingRequest<InitializeRequestT> request);

    /**
     * Allows resuming an existing matchmaking process. Note that this method is non-blocking and returns immediately
     * and does write to the database or perform any blocking operations. Subsequent calls to the returned MatchHandle's
     * methods will actually perform the operations of the matching process. This is used for scenarios in which a
     * participant has been disconnected and wishes to rejoin an existing match.
     *
     * @param request the matchmaking request
     * @return the match handle
     */
    MatchHandle<ResumeRequestT> resume(MatchmakingRequest<ResumeRequestT> request);

}
