package dev.getelements.elements.crossfire.protocol;

import dev.getelements.elements.sdk.annotation.ElementDefaultAttribute;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.websocket.OnMessage;
import jakarta.websocket.PongMessage;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Pinger {

    @ElementDefaultAttribute("30")
    public static final String PING_TIMEOUT = "dev.getelements.elements.ping.interval.seconds";

    private static final Logger logger = LoggerFactory.getLogger(Pinger.class);

    private static final ScheduledExecutorService pinger = Executors.newSingleThreadScheduledExecutor();

    private int pingInterval;

    private Session session;

    private ScheduledFuture<?> future;

    public void start(final Session session) {
        future = pinger.scheduleAtFixedRate(
                () -> {
                    try {
                        session.getBasicRemote().sendPing(ByteBuffer.allocate(0));
                    } catch (IOException e) {
                        logger.error("No failed to ping remote.", e);
                    }
                },
                getPingInterval(),
                getPingInterval(),
                TimeUnit.SECONDS
        );
    }

    @OnMessage
    public void onPong(final Session session, final PongMessage message) throws IOException {

        logger.debug("Received PongMessage from match.");

    }

    public void stop() {

        final var future = this.future;
        this.future = null;

        if (future != null)
            future.cancel(false);

    }

    public int getPingInterval() {
        return pingInterval;
    }

    @Inject
    public void setPingInterval(@Named(PING_TIMEOUT) int pingInterval) {
        this.pingInterval = pingInterval;
    }

}
