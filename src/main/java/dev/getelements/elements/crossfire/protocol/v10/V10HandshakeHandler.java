package dev.getelements.elements.crossfire.protocol.v10;

import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.protocol.HandshakeHandler;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import jakarta.websocket.Session;

public class V10HandshakeHandler implements HandshakeHandler {

    @Override
    public void start(final ProtocolMessageHandler handler, final Session session) {

    }

    @Override
    public void onMessage(final ProtocolMessageHandler handler, final Session session, final HandshakeRequest request) {

    }

    private record HandshakeRecord(
            Session session,
            ProtocolMessageHandler handler) {



    }

}
