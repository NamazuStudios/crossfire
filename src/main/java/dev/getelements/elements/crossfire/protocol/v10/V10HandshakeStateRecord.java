package dev.getelements.elements.crossfire.protocol.v10;

import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import jakarta.websocket.Session;

record V10HandshakeStateRecord(
        Session session,
        ProtocolMessageHandler handler) {
}
