package dev.getelements.elements.crossfire.protocol.v10;

import dev.getelements.elements.crossfire.model.error.UnexpectedMessageException;
import dev.getelements.elements.crossfire.model.handshake.FindHandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.JoinHandshakeRequest;
import dev.getelements.elements.crossfire.protocol.HandshakeHandler;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.sdk.dao.MatchDao;
import dev.getelements.elements.sdk.service.auth.SessionService;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

import java.util.concurrent.ExecutorService;

public class V10HandshakeHandler implements HandshakeHandler {

    private MatchDao matchDao;

    private SessionService sessionService;

    private ExecutorService executor;

    @Override
    public void onMessage(
            final ProtocolMessageHandler handler,
            final Session session,
            final HandshakeRequest request) {
        final var type = request.getType();
        switch (request.getType()) {
            case FIND -> onFindMessage(handler, session, (FindHandshakeRequest) request);
            case JOIN -> onJoinMessage(handler, session, (JoinHandshakeRequest) request);
            default -> throw new UnexpectedMessageException("Unsupported handshake request type: " + request.getType());
        }
    }

    private void onFindMessage(
            final ProtocolMessageHandler handler,
            final Session session,
            final FindHandshakeRequest request) {
    }

    private void onJoinMessage(
            final ProtocolMessageHandler handler,
            final Session session,
            final JoinHandshakeRequest request) {
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    @Inject
    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    public MatchDao getMatchDao() {
        return matchDao;
    }

    @Inject
    public void setMatchDao(MatchDao matchDao) {
        this.matchDao = matchDao;
    }

    public SessionService getSessionService() {
        return sessionService;
    }

    @Inject
    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    private record HandshakeStateRecord(
            Session session,
            ProtocolMessageHandler handler) {}

}
