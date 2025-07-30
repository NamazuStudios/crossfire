package dev.getelements.elements.crossfire.protocol.v10;

import dev.getelements.elements.crossfire.api.MatchmakingAlgorithm;
import dev.getelements.elements.crossfire.api.MatchmakingAlgorithm.PendingMatch;
import dev.getelements.elements.crossfire.protocol.HandshakePhase;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import jakarta.websocket.Session;

record V10HandshakeStateRecord(
        HandshakePhase phase,
        PendingMatch pending) {

}
