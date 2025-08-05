package dev.getelements.elements.crossfire.client.v10;

import dev.getelements.elements.crossfire.client.PeerConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public class V10PeerConnectionPool implements PeerConnectionPool {

    private static final Logger logger = LoggerFactory.getLogger(V10PeerConnectionPool.class);

    private static void log(final ByteBuffer buffer) { logger.trace("Sent buffer {}", buffer); }

    private static void noop(final ByteBuffer buffer) {}

    private static final Consumer<ByteBuffer> NOOP_ON_SENT = logger.isTraceEnabled()
            ? V10PeerConnectionPool::log
            : V10PeerConnectionPool::noop;

    private final ConcurrentMap<String, V10PeerConnection> connections = new ConcurrentHashMap<>();

    @Override
    public void enqueue(final String profileId,
                        final ByteBuffer buffer,
                        final Consumer<ByteBuffer> onSent) {

    }

}
