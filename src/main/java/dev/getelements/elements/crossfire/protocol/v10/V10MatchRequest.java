package dev.getelements.elements.crossfire.protocol.v10;

import dev.getelements.elements.crossfire.api.MatchmakingAlgorithm;
import dev.getelements.elements.crossfire.model.handshake.ConnectedResponse;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import dev.getelements.elements.sdk.model.profile.Profile;

public class V10MatchRequest implements MatchmakingAlgorithm.Request {

    private final ProtocolMessageHandler protocolMessageHandler;

    private final Profile profile;

    private final MatchmakingApplicationConfiguration configuration;

    public V10MatchRequest(
            final ProtocolMessageHandler protocolMessageHandler,
            final Profile profile,
            final MatchmakingApplicationConfiguration configuration) {
        this.profile = profile;
        this.configuration = configuration;
        this.protocolMessageHandler = protocolMessageHandler;
    }

    @Override
    public Profile getProfile() {
        return profile;
    }

    @Override
    public MatchmakingApplicationConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void failure(final Throwable th) {
        protocolMessageHandler.terminate(th);
    }

    @Override
    public void success(final MultiMatch match) {
        final var response = new ConnectedResponse();
        response.setMatchId(match.getId());
        protocolMessageHandler.send(response);
    }

}
