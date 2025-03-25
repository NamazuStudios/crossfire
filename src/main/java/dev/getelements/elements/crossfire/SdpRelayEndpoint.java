package dev.getelements.elements.crossfire;

import dev.getelements.elements.sdk.Subscription;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static jakarta.websocket.CloseReason.CloseCodes.GOING_AWAY;
import static jakarta.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION;

@ServerEndpoint("/sdp/{matchId}/{profileId}")
public class SdpRelayEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(SdpRelayEndpoint.class);

    private static final ScheduledExecutorService pinger = Executors.newSingleThreadScheduledExecutor();

    private Subscription subscription;

    private ScheduledFuture<?> pingFuture;

    private static SdpRelayService getSdpRelayService() {
        //TODO Replace this with an Element lookup
        return MemorySdpRelayService.getInstance();
    }

    @OnOpen
    public void onOpen(final @PathParam("matchId") String matchId,
                       final @PathParam("profileId") String profileId,
                       final Session session) {

        final var remote = session.getAsyncRemote();

        pingFuture = pinger.scheduleAtFixedRate(
                () -> {
                    try {
                        session.getBasicRemote().sendPing(ByteBuffer.allocate(0));
                    } catch (IOException e) {
                        logger.error("No failed to ping remote.", e);
                    }
                },
                30,
                30,
                TimeUnit.SECONDS
        );

        subscription = getSdpRelayService().subscribeToUpdates(
                matchId,
                profileId,
                remote::sendText,
                ex -> doClose(session, ex)
        );

    }

    @OnMessage
    public void onMessage(final @PathParam("matchId") String matchId,
                          final @PathParam("profileId") String profileId,
                          final String message) {
        getSdpRelayService().addSessionDescription(matchId, profileId, message);
    }

    @OnMessage
    public void onMessage(final @PathParam("matchId") String matchId,
                          final Session session,
                          final PongMessage message) throws IOException {
        logger.debug("Received PongMessage from match: {}", matchId);

        if (getSdpRelayService().pingMatch(matchId)) {
            logger.debug("Successfully reset match {}", matchId);
        } else {
            session.close();
        }

    }

    @OnClose
    public void onClose(final Session session) {

        logger.debug("Session {} closed", session.getId());

        if (pingFuture != null)
            pingFuture.cancel(false);

        if (subscription != null)
            subscription.unsubscribe();

    }

    @OnError
    public void onError(final Session session, final Throwable th) {
        logger.debug("Closing session due to error {}", session.getId(), th);
        doClose(session, th);
        unsubscribe();
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
