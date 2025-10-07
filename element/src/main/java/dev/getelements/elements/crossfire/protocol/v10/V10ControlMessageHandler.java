package dev.getelements.elements.crossfire.protocol.v10;

import dev.getelements.elements.crossfire.model.control.ControlMessage;
import dev.getelements.elements.crossfire.protocol.ControlMessageHandler;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import jakarta.websocket.Session;

public class V10ControlMessageHandler implements ControlMessageHandler {
    @Override
    public void start(
            final ProtocolMessageHandler handler,
            final Session session,
            final ProtocolMessageHandler.MultiMatchRecord match,
            final ProtocolMessageHandler.AuthRecord auth) {

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
            final ControlMessage request) {

    }

}
