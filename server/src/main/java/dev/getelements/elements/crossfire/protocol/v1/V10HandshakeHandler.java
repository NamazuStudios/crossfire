package dev.getelements.elements.crossfire.protocol.v1;

import dev.getelements.elements.crossfire.api.FindMatchmakingAlgorithm;
import dev.getelements.elements.crossfire.api.model.Version;
import dev.getelements.elements.crossfire.api.model.error.UnexpectedMessageException;
import dev.getelements.elements.crossfire.api.model.handshake.FindHandshakeRequest;
import dev.getelements.elements.crossfire.api.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.api.model.handshake.JoinHandshakeRequest;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

public class V10HandshakeHandler extends V1HandshakeHandler {

    private FindMatchmakingAlgorithm findMatchmakingAlgorithm;

    @Override
    public void onMessage(
            final ProtocolMessageHandler handler,
            final Session session,
            final HandshakeRequest request) {
        final var type = request.getType();
        switch (type) {
            case FIND -> onFindMessage(handler, session, (FindHandshakeRequest) request);
            case JOIN -> onJoinMessage(handler, session, (JoinHandshakeRequest) request);
            default -> throw new UnexpectedMessageException("Unsupported handshake request type: " + request.getType());
        }
    }

    private void onFindMessage(
            final ProtocolMessageHandler handler,
            final Session session,
            final FindHandshakeRequest request) {
        auth(handler, request, (auth) -> {

            final var application = auth.profile().getApplication();

            final var applicationConfiguration = getApplicationConfigurationDao().getApplicationConfiguration(
                    MatchmakingApplicationConfiguration.class,
                    application.getId(),
                    request.getConfiguration()
            );

            final var matchRequest = new V1MatchRequest<>(
                    handler,
                    state,
                    auth.profile(),
                    request,
                    applicationConfiguration
            );

            final var algorithm = getMatchmakingAlgorithm(
                    FindMatchmakingAlgorithm.class,
                    applicationConfiguration,
                    getFindMatchmakingAlgorithm()
            );

            final var pending = algorithm.initialize(matchRequest);
            startMatching(pending);

        });
    }

    private void onJoinMessage(
            final ProtocolMessageHandler handler,
            final Session session,
            final JoinHandshakeRequest request) {
        auth(handler, request, (auth) -> {

            final var match = getMultiMatchDao().getMultiMatch(request.getMatchId());
            final var applicationConfiguration = match.getConfiguration();

            final var matchRequest = new V1MatchRequest<>(
                    handler,
                    state,
                    auth.profile(),
                    request,
                    applicationConfiguration
            );

            final var algorithm = getMatchmakingAlgorithm(
                    FindMatchmakingAlgorithm.class,
                    applicationConfiguration,
                    getFindMatchmakingAlgorithm()
            );

            final var pending = algorithm.resume(matchRequest);
            startMatching(pending);

        });
    }

    @Override
    protected V1HandshakeStateRecord initStateRecord() {
        return V1HandshakeStateRecord.create(Version.V_1_0);
    }

    public FindMatchmakingAlgorithm getFindMatchmakingAlgorithm() {
        return findMatchmakingAlgorithm;
    }

    @Inject
    public void setFindMatchmakingAlgorithm(final FindMatchmakingAlgorithm findMatchmakingAlgorithm) {
        this.findMatchmakingAlgorithm = findMatchmakingAlgorithm;
    }

}
