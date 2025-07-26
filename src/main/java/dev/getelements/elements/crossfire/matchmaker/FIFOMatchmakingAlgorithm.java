package dev.getelements.elements.crossfire.matchmaker;

import dev.getelements.elements.crossfire.api.MatchmakingAlgorithm;

public class FIFOMatchmakingAlgorithm implements MatchmakingAlgorithm {

    public static final String NAME = "FIFO";

    @Override
    public PendingMatch start(final Request request) {
        return () -> {};
    }

}
