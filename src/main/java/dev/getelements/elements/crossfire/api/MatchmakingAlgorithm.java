package dev.getelements.elements.crossfire.api;

import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import dev.getelements.elements.sdk.model.profile.Profile;

/**
 * Represents a matchmaking algorithm that can be used to match players or profiles based on certain criteria.
 */
public interface MatchmakingAlgorithm {

    /**
     * Starts the matchmaking algorithm.
     *
     * @param request the matchmaking request
     */
    void start(Request request);

    /**
     * Cancels the matchmaking algorithm for the given request.
     *
     * @param request the matchmaking request to cancel
     */
    void cancel(Request request);

    /**
     * Starts the matchmaking algorithm with the given request.
     */
    interface Request {

        /**
         * Gets the profile of the matchmaking request.
         *
         * @return the profile of the matchmaking request
         */
        Profile getProfile();

        /**
         * Gets the matchmaking application configuration.
         *
         * @return the matchmaking application configuration
         */
        MatchmakingApplicationConfiguration getConfiguration();

        /**
         * Fails the matchmaking request without a throwable.
         */
        default void failure() {
            failure(null);
        }

        /**
         * Fails the matchmaking request with the given throwable.
         *
         * @param th the throwable to fail with, may be null
         */
        void failure(Throwable th);

        /**
         * Completes the matchmaking request with the given match.
         */
        void success(MultiMatch match);

    }

}
