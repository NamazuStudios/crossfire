package dev.getelements.elements.crossfire.model.handshake;

import jakarta.validation.constraints.NotNull;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.MATCHED;

/**
 * Indicates that the client has successfully connected to a matched.
 */
public class MatchedResponse implements HandshakeResponse {

    @NotNull
    private String matchId;

    @Override
    public Type getType() {
        return MATCHED;
    }

    /**
     * Returns the matched ID of the connected matched.
     *
     * @return the matched id
     */
    public String getMatchId() {
        return matchId;
    }

    public void setMatchId(String matchId) {
        this.matchId = matchId;
    }

}
