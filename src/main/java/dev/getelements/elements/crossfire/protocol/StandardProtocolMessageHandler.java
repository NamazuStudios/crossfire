package dev.getelements.elements.crossfire.protocol;


import dev.getelements.elements.crossfire.model.ProtocolMessage;
import jakarta.inject.Inject;
import jakarta.websocket.PongMessage;
import jakarta.websocket.Session;

import java.io.IOException;

public class StandardProtocolMessageHandler implements ProtocolMessageHandler {

    private Pinger pinger;

    @Override
    public void start(Session session) throws IOException {
        pinger.start(session);
    }

    @Override
    public void stop(Session session) throws IOException {

    }

    @Override
    public void onMessage(final Session session, final PongMessage message) throws IOException {
        getPinger().onPong(session, message);
    }

    @Override
    public void onMessage(final Session session, final ProtocolMessage message) throws IOException {

    }

    public Pinger getPinger() {
        return pinger;
    }

    @Inject
    public void setPinger(Pinger pinger) {
        this.pinger = pinger;
    }

}
