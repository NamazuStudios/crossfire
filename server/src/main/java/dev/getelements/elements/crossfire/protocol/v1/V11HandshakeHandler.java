package dev.getelements.elements.crossfire.protocol.v1;

import dev.getelements.elements.crossfire.api.model.Version;
import dev.getelements.elements.crossfire.api.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import jakarta.websocket.Session;

public class V11HandshakeHandler extends V1HandshakeHandler {

    @Override
    protected V1HandshakeStateRecord initStateRecord() {
        return V1HandshakeStateRecord.create(Version.V_1_1);
    }

    @Override
    public void start(final ProtocolMessageHandler handler,
                      final Session session) {

    }

    @Override
    public void stop(final ProtocolMessageHandler handler,
                     final Session session) {

    }

    @Override
    public void onMessage(final ProtocolMessageHandler handler,
                          final Session session,
                          final HandshakeRequest request) {

     }

}
