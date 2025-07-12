package dev.getelements.elements.crossfire.model.handshake;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.CONNECTED;

/**
 * Indicates that the client has successfully connected to a match.
 */
public class ConnectedResponse implements HandshakeResponse {

    private String matchId;

    @Override
    public Type getType() {
        return CONNECTED;
    }

    /**
     * Returns the match ID of the connected match.
     *
     * @return the match id
     */
    public String getMatchId() {
        return matchId;
    }

    public void setMatchId(String matchId) {
        this.matchId = matchId;
    }

}
