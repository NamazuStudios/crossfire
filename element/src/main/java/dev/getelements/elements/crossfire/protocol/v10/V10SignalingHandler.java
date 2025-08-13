package dev.getelements.elements.crossfire.protocol.v10;

import dev.getelements.elements.crossfire.model.signal.BroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.SignalWithRecipient;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.crossfire.protocol.SignalingHandler;
import dev.getelements.elements.crossfire.service.MatchSignalingService;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

public class V10SignalingHandler implements SignalingHandler {

    private MatchSignalingService matchSignalingService;

    @Override
    public void start(
            final ProtocolMessageHandler handler,
            final Session session) {

    }

    @Override
    public void stop(
            final ProtocolMessageHandler handler,
            final Session session) {

    }

    @Override
    public void onMessage(
            final ProtocolMessageHandler handler,
            final Session session,
            final BroadcastSignal broadcastSignal) {

    }

    @Override
    public void onMessageDirect(
            final ProtocolMessageHandler handler,
            final Session session,
            final SignalWithRecipient signal) {

    }

    public MatchSignalingService getMatchSignalingService() {
        return matchSignalingService;
    }

    @Inject
    public void setMatchSignalingService(final MatchSignalingService matchSignalingService) {
        this.matchSignalingService = matchSignalingService;
    }

}
