package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.api.model.control.*;
import dev.getelements.elements.crossfire.api.model.error.UnexpectedMessageException;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import jakarta.inject.Inject;

import static dev.getelements.elements.crossfire.service.ControlService.Result.CLOSE_CONNECTION;
import static dev.getelements.elements.crossfire.service.ControlService.Result.PERSIST_CONNECTION;

public class StandardControlService implements ControlService {

    private MatchSignalingService matchSignalingService;

    @Override
    public Result process(
            final ProtocolMessageHandler.MultiMatchRecord match,
            final ProtocolMessageHandler.AuthRecord auth,
            final ControlMessage message) {

        return switch (message.getType()) {
            case END -> onMessageEnd(match, auth, (EndControlMessage) message);
            case OPEN -> onMessageOpen(match, auth, (OpenControlMessage) message);
            case CLOSE -> onMessageClose(match, auth, (CloseControlMessage) message);
            case LEAVE -> onMessageLeave(match, auth, (LeaveControlMessage) message);
            default -> throw new UnexpectedMessageException("Unexpected value: " + message.getType());
        };

    }

    private Result onMessageEnd(final ProtocolMessageHandler.MultiMatchRecord match,
                              final ProtocolMessageHandler.AuthRecord auth,
                              final EndControlMessage message) {
        match.matchHandle().endMatch();
        return PERSIST_CONNECTION;
    }

    private Result onMessageOpen(final ProtocolMessageHandler.MultiMatchRecord match,
                               final ProtocolMessageHandler.AuthRecord auth,
                               final OpenControlMessage message) {
        match.matchHandle().openMatch();
        return PERSIST_CONNECTION;
    }

    private Result onMessageClose(final ProtocolMessageHandler.MultiMatchRecord match,
                                final ProtocolMessageHandler.AuthRecord auth,
                                final CloseControlMessage message) {
        match.matchHandle().closeMatch();
        return PERSIST_CONNECTION;
    }

    private Result onMessageLeave(final ProtocolMessageHandler.MultiMatchRecord match,
                                final ProtocolMessageHandler.AuthRecord auth,
                                final LeaveControlMessage message) {
        getMatchSignalingService().leave(match.getId(), auth.profile().getId());
        return CLOSE_CONNECTION;
    }

    public MatchSignalingService getMatchSignalingService() {
        return matchSignalingService;
    }

    @Inject
    public void setMatchSignalingService(MatchSignalingService matchSignalingService) {
        this.matchSignalingService = matchSignalingService;
    }

}
