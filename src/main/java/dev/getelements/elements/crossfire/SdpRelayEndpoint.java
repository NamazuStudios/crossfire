package dev.getelements.elements.crossfire;

import dev.getelements.elements.sdk.Subscription;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static jakarta.websocket.CloseReason.CloseCodes.GOING_AWAY;
import static jakarta.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION;

@Singleton
@ServerEndpoint("/sdp/{matchId}")
public class SdpRelayEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(SdpRelayEndpoint.class);

    private Subscription subscription;

    private SdpRelayService sdpRelayService;

    @OnOpen
    public void onOpen(final @PathParam("matchId") String matchId,
                       final Session session) {

        final var remote = session.getAsyncRemote();

        subscription = getSdpRelayService().subscribeToUpdates(
                matchId,
                remote::sendText,
                ex -> doClose(session, ex));

    }

    @OnMessage
    public void onMessage(final @PathParam("matchId") String matchId, final String message) {
        getSdpRelayService().addSessionDescription(matchId, message);
    }

    @OnClose
    public void onClose(final Session session) {
        logger.debug("Session {} closed", session.getId());
        if (subscription != null) subscription.unsubscribe();
    }

    @OnError
    public void onError(final Session session, final Throwable th) {
        logger.debug("Closing session due to error {}", session.getId(), th);
        doClose(session, th);
        unsubscribe();
    }

    public SdpRelayService getSdpRelayService() {
        return sdpRelayService;
    }

    @Inject
    public void setSdpRelayService(final SdpRelayService sdpRelayService) {
        this.sdpRelayService = sdpRelayService;
    }

    private void unsubscribe() {
        final var subscription = this.subscription;
        this.subscription = null;
        if (subscription != null) subscription.unsubscribe();
    }

    private void doClose(final Session session, final Throwable th) {
        try {
            logger.info("Closing session {}", session.getId(), th);
            if (th instanceof TimeoutException) {
                session.close(new CloseReason(GOING_AWAY, th.getMessage()));
            } else {
                session.close(new CloseReason(UNEXPECTED_CONDITION, th.getMessage()));
            }
        } catch (Exception ex) {
            logger.error("Error while closing session {}", session.getId(), ex);
        }
    }

}
