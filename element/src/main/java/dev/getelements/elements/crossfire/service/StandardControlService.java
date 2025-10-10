package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.model.control.*;
import dev.getelements.elements.crossfire.model.error.UnexpectedMessageException;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import jakarta.inject.Inject;

public class StandardControlService implements ControlService {

    private MatchSignalingService matchSignalingService;

    @Override
    public void process(
            final ProtocolMessageHandler.MultiMatchRecord match,
            final ProtocolMessageHandler.AuthRecord auth,
            final ControlMessage message) {

        switch (message.getType()) {
            case END -> onMessageEnd(match, auth, (EndControlMessage) message);
            case OPEN -> onMessageOpen(match, auth, (OpenControlMessage) message);
            case CLOSE -> onMessageClose(match, auth, (CloseControlMessage) message);
            case LEAVE -> onMessageLeave(match, auth, (LeaveControlMessage) message);
            default -> throw new UnexpectedMessageException("Unexpected value: " + message.getType());
        }

    }

    private void onMessageEnd(final ProtocolMessageHandler.MultiMatchRecord match,
                              final ProtocolMessageHandler.AuthRecord auth,
                              final EndControlMessage message) {
        match.matchHandle().endMatch();
    }

    private void onMessageOpen(final ProtocolMessageHandler.MultiMatchRecord match,
                               final ProtocolMessageHandler.AuthRecord auth,
                               final OpenControlMessage message) {
        match.matchHandle().openMatch();
    }

    private void onMessageClose(final ProtocolMessageHandler.MultiMatchRecord match,
                                final ProtocolMessageHandler.AuthRecord auth,
                                final CloseControlMessage message) {
        match.matchHandle().closeMatch();
    }

    private void onMessageLeave(final ProtocolMessageHandler.MultiMatchRecord match,
                                final ProtocolMessageHandler.AuthRecord auth,
                                final LeaveControlMessage message) {
        match.matchHandle().leave();
    }

    public MatchSignalingService getMatchSignalingService() {
        return matchSignalingService;
    }

    @Inject
    public void setMatchSignalingService(MatchSignalingService matchSignalingService) {
        this.matchSignalingService = matchSignalingService;
    }

}
