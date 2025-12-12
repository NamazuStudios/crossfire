package dev.getelements.elements.crossfire.protocol;

import dev.getelements.elements.sdk.annotation.ElementDefaultAttribute;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.websocket.CloseReason;
import jakarta.websocket.PongMessage;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static jakarta.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION;
import static java.util.concurrent.TimeUnit.SECONDS;

public class StandardPinger implements Pinger {

    @ElementDefaultAttribute("90")
    public static final String TIMEOUT_SECONDS = "dev.getelements.elements.timeout.seconds";

    @ElementDefaultAttribute("30")
    public static final String PING_INTERVAL_SECONDS = "dev.getelements.elements.ping.interval.seconds";

    private static final ByteBuffer ZERO_BUFFER = ByteBuffer
            .allocate(0)
            .asReadOnlyBuffer();

    private static final Logger logger = LoggerFactory.getLogger(StandardPinger.class);

    private int timeout;

    private int pingInterval;

    private ScheduledFuture<?> future;

    private ScheduledExecutorService scheduledExecutorService;

    @Override
    public void start(final Session session) {

        logger.debug("Starting for session {}", session.getId());

        final var timeout = SECONDS.toMillis(getTimeout());
        session.setMaxIdleTimeout(timeout);

        future = getScheduledExecutorService().scheduleWithFixedDelay(
                () -> ping(session),
                getPingInterval(),
                getPingInterval(),
                SECONDS
        );

    }

    private void ping(final Session session) {
        if (session.isOpen()) {
            try {
                session.getBasicRemote().sendPing(ZERO_BUFFER);
            } catch (IOException e) {
                logger.error("Failed failed to ping remote.", e);
                close(session);
            }
        } else {
            stop();
        }
    }

    private void close(final Session session) {

        final var reason = new CloseReason(UNEXPECTED_CONDITION, "Failed to ping remote.");

        try {
            session.close(reason);
        } catch (IOException e) {
            logger.error("Failed failed to close remote.", e);
        }

    }

    @Override
    public void onPong(final Session session, final PongMessage message) {
        logger.debug("Received PongMessage from matching session {}", session.getId());
    }

    @Override
    public void stop() {

        final var future = this.future;
        this.future = null;

        if (future != null)
            future.cancel(false);

    }

    public int getTimeout() {
        return timeout;
    }

    @Inject
    public void setTimeout(@Named(TIMEOUT_SECONDS) int timeout) {
        this.timeout = timeout;
    }

    public int getPingInterval() {
        return pingInterval;
    }

    @Inject
    public void setPingInterval(@Named(PING_INTERVAL_SECONDS) int pingInterval) {
        this.pingInterval = pingInterval;
    }

    public ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }

    @Inject
    public void setScheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
    }

}
