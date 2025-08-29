package dev.getelements.elements.crossfire.client.v10;

import dev.getelements.elements.crossfire.client.Client;
import dev.getelements.elements.crossfire.client.PeerConnectionPool;
import dev.getelements.elements.crossfire.model.handshake.HandshakeResponse;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.sdk.Subscription;
import dev.onvoid.webrtc.RTCPeerConnection;
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

    private final Client client;

    private final Subscription subscription;

    private final ConcurrentMap<String, RTCPeerConnection> connections = new ConcurrentHashMap<>();

    public V10PeerConnectionPool(final Client client) {
        this.client = client;
        this.subscription = Subscription.begin()
                .chain(this.client.onSignal(this::onSignal))
                .chain(this.client.onHandshake(this::onHandshake));
    }

    @Override
    public void enqueue(final String profileId,
                        final ByteBuffer buffer,
                        final Consumer<ByteBuffer> onSent) {

    }

    private void onSignal(final Subscription subscription,
                          final Signal signal) {

    }

    private void onHandshake(final Subscription subscription,
                             final HandshakeResponse handshakeResponse) {

    }

}
