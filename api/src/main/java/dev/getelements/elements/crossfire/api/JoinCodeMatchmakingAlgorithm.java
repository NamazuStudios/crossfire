package dev.getelements.elements.crossfire.api;

import dev.getelements.elements.crossfire.api.model.handshake.CreateHandshakeRequest;
import dev.getelements.elements.crossfire.api.model.handshake.JoinCodeHandshakeRequest;

/**
 * A matchmaking algorithm specifically for matchmaking style where one participant creates a match and invites others
 * via a join code.
 */
public interface JoinCodeMatchmakingAlgorithm extends MatchmakingAlgorithm<CreateHandshakeRequest, JoinCodeHandshakeRequest> {}
