package dev.getelements.elements.crossfire.api;

/**
 * Represents the state of the matchmaking algorithm.
 */
public enum MatchPhase {

    /**
     * The matchmaking algorithm is ready to start.
     */
    READY,

    /**
     * The matchmaking algorithm is currently running.
     */
    MATCHING,

    /**
     * The matchmaking algorithm has found a match.
     */
    MATCHED,

    /**
     * The matchmaking algorithm has failed.
     */
    TERMINATED

}
