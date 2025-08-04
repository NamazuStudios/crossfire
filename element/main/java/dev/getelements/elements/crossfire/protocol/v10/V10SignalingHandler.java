package dev.getelements.elements.crossfire.protocol.v10;

import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.crossfire.model.signal.SignalWithRecipient;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.crossfire.protocol.SignalingHandler;
import jakarta.websocket.Session;

public class V10SignalingHandler implements SignalingHandler {

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
            final Signal signal) {

    }

    @Override
    public void onMessageDirect(
            final ProtocolMessageHandler handler,
            final Session session,
            final SignalWithRecipient signal) {

    }

}
