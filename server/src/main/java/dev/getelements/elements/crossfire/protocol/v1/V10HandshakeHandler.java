package dev.getelements.elements.crossfire.protocol.v1;

import dev.getelements.elements.crossfire.api.model.Version;
import dev.getelements.elements.crossfire.api.model.error.MultiMatchConfigurationNotFoundException;
import dev.getelements.elements.crossfire.api.model.error.UnexpectedMessageException;
import dev.getelements.elements.crossfire.api.model.handshake.FindHandshakeRequest;
import dev.getelements.elements.crossfire.api.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.api.model.handshake.JoinHandshakeRequest;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
import jakarta.websocket.Session;

import java.util.Optional;

import static dev.getelements.elements.crossfire.protocol.HandshakePhase.MATCHING;
import static dev.getelements.elements.crossfire.protocol.HandshakePhase.TERMINATED;

public class V10HandshakeHandler extends V1HandshakeHandler {

    @Override
    public void start(final ProtocolMessageHandler handler,
                      final Session session) {

        final var state = this.state.updateAndGet(s -> s.start(session));

        if (TERMINATED.equals(state.phase())) {
            state.leave();
        }

    }

    @Override
    public void stop(final ProtocolMessageHandler handler,
                     final Session session) {

        final var state = this.state.updateAndGet(V1HandshakeStateRecord::terminate);

        if (MATCHING.equals(state.phase())) {
            state.leave();
        }

    }

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

            final var applicationConfigurationOptional = getApplicationConfigurationDao().findApplicationConfiguration(
                    MatchmakingApplicationConfiguration.class,
                    application.getId(),
                    request.getConfiguration()
            );

            if(applicationConfigurationOptional.isEmpty()) {
                throw new MultiMatchConfigurationNotFoundException(
                        "Matchmaking Configuration with name " + request.getConfiguration() +
                        " not found.");
            }

            final var applicationConfiguration = applicationConfigurationOptional.get();

            final var matchRequest = new V1MatchRequest<>(
                    handler,
                    state,
                    auth.profile(),
                    request,
                    applicationConfiguration
            );

            final var algorithm = Optional
                    .ofNullable(applicationConfiguration.getMatchmaker())
                    .map(this::algorithmFromConfiguration)
                    .orElseGet(this::getDefaultMatchmakingAlgorithm)
                    .checked(FindHandshakeRequest.class, JoinHandshakeRequest.class);

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

            final var algorithm = Optional
                    .ofNullable(applicationConfiguration.getMatchmaker())
                    .map(this::algorithmFromConfiguration)
                    .orElseGet(this::getDefaultMatchmakingAlgorithm);

            final var pending = algorithm
                    .checked(FindHandshakeRequest.class, JoinHandshakeRequest.class)
                    .resume(matchRequest);

            startMatching(pending);

        });
    }

    @Override
    protected V1HandshakeStateRecord initStateRecord() {
        return V1HandshakeStateRecord.create(Version.V_1_0);
    }

}
