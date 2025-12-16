package dev.getelements.elements.crossfire.protocol.v1;

import dev.getelements.elements.crossfire.api.MatchmakingAlgorithm;
import dev.getelements.elements.crossfire.api.model.Version;
import dev.getelements.elements.crossfire.api.model.error.MultiMatchConfigurationNotFoundException;
import dev.getelements.elements.crossfire.api.model.error.UnexpectedMessageException;
import dev.getelements.elements.crossfire.api.model.handshake.*;
import dev.getelements.elements.crossfire.matchmaker.JoinCodeMatchmakingAlgorithm;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.websocket.Session;

import java.util.Optional;

import static dev.getelements.elements.crossfire.matchmaker.JoinCodeMatchmakingAlgorithm.NAME;

public class V11HandshakeHandler extends V1HandshakeHandler {

    private MatchmakingAlgorithm<?, ?> defaultMatchmakingAlgorithm;

    @Override
    protected V1HandshakeStateRecord initStateRecord() {
        return V1HandshakeStateRecord.create(Version.V_1_1);
    }

    @Override
    public void onMessage(
            final ProtocolMessageHandler handler,
            final Session session,
            final HandshakeRequest request) {
        final var type = request.getType();
        switch (type) {
            case CREATE -> onCreateMessage(handler, session, (CreateHandshakeRequest) request);
            case JOIN_CODE -> onJoinCodeMessage(handler, session, (JoinCodeHandshakeRequest) request);
            default -> throw new UnexpectedMessageException("Unsupported handshake request type: " + request.getType());
        }
    }

    private void onCreateMessage(final ProtocolMessageHandler handler,
                                 final Session session,
                                 final CreateHandshakeRequest request) {
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
                    .checked(CreateHandshakeRequest.class, JoinCodeHandshakeRequest.class);

            final var pending = algorithm.initialize(matchRequest);
            startMatching(pending);

        });
    }

    private void onJoinCodeMessage(final ProtocolMessageHandler handler,
                                   final Session session,
                                   final JoinCodeHandshakeRequest request) {
            auth(handler, request, (auth) -> {

                final var match = getMultiMatchDao().getMultiMatch(request.getJoinCode());
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
                        .orElseGet(this::getDefaultMatchmakingAlgorithm)
                        .checked(CreateHandshakeRequest.class, JoinCodeHandshakeRequest.class);

                final var pending = algorithm.resume(matchRequest);
                startMatching(pending);

        });
    }

}
