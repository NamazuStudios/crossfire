package dev.getelements.elements.crossfire.api;

import dev.getelements.elements.crossfire.api.model.handshake.FindHandshakeRequest;
import dev.getelements.elements.crossfire.api.model.handshake.JoinHandshakeRequest;

public interface FindMatchmakingAlgorithm extends MatchmakingAlgorithm<FindHandshakeRequest, JoinHandshakeRequest> {}
