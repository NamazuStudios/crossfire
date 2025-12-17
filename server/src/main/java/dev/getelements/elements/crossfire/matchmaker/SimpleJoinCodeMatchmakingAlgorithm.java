package dev.getelements.elements.crossfire.matchmaker;

import dev.getelements.elements.crossfire.api.JoinCodeMatchmakingAlgorithm;
import dev.getelements.elements.crossfire.api.MatchHandle;
import dev.getelements.elements.crossfire.api.MatchmakingRequest;
import dev.getelements.elements.crossfire.api.model.handshake.CreateHandshakeRequest;
import dev.getelements.elements.crossfire.api.model.handshake.JoinCodeHandshakeRequest;
import dev.getelements.elements.sdk.annotation.ElementServiceExport;

/**
 * A simple implementation of the JoinCodeMatchmakingAlgorithm that uses simple join codes to determine matches.
 */
@ElementServiceExport(value = JoinCodeMatchmakingAlgorithm.class)
@ElementServiceExport(value = JoinCodeMatchmakingAlgorithm.class, name = SimpleJoinCodeMatchmakingAlgorithm.NAME)
public class SimpleJoinCodeMatchmakingAlgorithm implements JoinCodeMatchmakingAlgorithm {

    public static final String NAME = "SIMPLE_JOIN_CODE";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public MatchHandle<CreateHandshakeRequest> initialize(final MatchmakingRequest<CreateHandshakeRequest> request) {
        return null;
    }

    @Override
    public MatchHandle<JoinCodeHandshakeRequest> resume(final MatchmakingRequest<JoinCodeHandshakeRequest> request) {
        return null;
    }

}
