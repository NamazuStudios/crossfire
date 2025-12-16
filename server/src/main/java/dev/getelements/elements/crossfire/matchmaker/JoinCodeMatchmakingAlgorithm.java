package dev.getelements.elements.crossfire.matchmaker;

import dev.getelements.elements.crossfire.api.MatchHandle;
import dev.getelements.elements.crossfire.api.MatchmakingAlgorithm;
import dev.getelements.elements.crossfire.api.MatchmakingRequest;
import dev.getelements.elements.crossfire.api.model.handshake.CreateHandshakeRequest;
import dev.getelements.elements.crossfire.api.model.handshake.FindHandshakeRequest;
import dev.getelements.elements.crossfire.api.model.handshake.JoinHandshakeRequest;
import dev.getelements.elements.sdk.annotation.ElementServiceExport;

@ElementServiceExport(value = MatchmakingAlgorithm.class)
@ElementServiceExport(value = MatchmakingAlgorithm.class, name = JoinCodeMatchmakingAlgorithm.NAME)
public class JoinCodeMatchmakingAlgorithm implements MatchmakingAlgorithm<CreateHandshakeRequest, JoinHandshakeRequest> {

    public static final String NAME = "JOIN_CODE";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public MatchHandle<CreateHandshakeRequest> initialize(final MatchmakingRequest<CreateHandshakeRequest> request) {
        return null;
    }

    @Override
    public MatchHandle<JoinHandshakeRequest> resume(final MatchmakingRequest<JoinHandshakeRequest> request) {
        return null;
    }

}
