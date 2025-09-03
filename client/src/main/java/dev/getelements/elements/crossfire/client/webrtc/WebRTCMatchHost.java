package dev.getelements.elements.crossfire.client.webrtc;

import dev.getelements.elements.crossfire.client.MatchHost;
import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.model.signal.*;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.ConcurrentDequePublisher;
import dev.getelements.elements.sdk.util.Publisher;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.RTCPeerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class WebRTCMatchHost implements MatchHost {

    private static final Logger logger = LoggerFactory.getLogger(WebRTCMatchHost.class);

    private final String profileId;

    private final SignalingClient signaling;

    private final PeerConnectionFactory peerConnectionFactory;

    private final Subscription subscription;

    private final AtomicBoolean open = new AtomicBoolean(true);

    private final Publisher<Message> onMessage = new ConcurrentDequePublisher<>();

    private final ConcurrentMap<String, RTCPeerConnection> connections = new ConcurrentHashMap<>();

    public WebRTCMatchHost(final String profileId,
                           final SignalingClient signalingClient,
                           final PeerConnectionFactory peerConnectionFactory) {
        this.profileId = profileId;
        this.signaling = signalingClient;
        this.peerConnectionFactory = peerConnectionFactory;
        this.subscription = Subscription.begin()
                .chain(this.signaling.onSignal(this::onSignal));
    }

    @Override
    public SendStatus send(
            final String profileId,
            final ByteBuffer buffer,
            final Consumer<ByteBuffer> onSent) {
        return SendStatus.NOT_READY;
    }

    private void onSignal(final Subscription subscription, final Signal signal) {
        switch (signal.getType()) {
            case SDP_ANSWER -> onSignalAnswer((SdpAnswerDirectSignal) signal);
            case CANDIDATE -> onSignalCandidate((CandidateBroadcastSignal) signal);
            case CONNECT -> onSignalConnect((ConnectBroadcastSignal) signal);
            case DISCONNECT -> onSignalDisconnect((DisconnectBroadcastSignal) signal);
            default -> logger.trace("Ignoring signal type: {}", signal.getType());
        }
    }

    private void onSignalAnswer(final SdpAnswerDirectSignal signal) {

    }

    private void onSignalCandidate(final CandidateBroadcastSignal signal) {

    }

    private void onSignalConnect(final ConnectBroadcastSignal signal) {

    }

    private void onSignalDisconnect(final DisconnectBroadcastSignal signal) {

    }

    @Override
    public Subscription onMessage(final BiConsumer<Subscription, Message> onMessage) {
        return this.onMessage.subscribe(onMessage);
    }

    @Override
    public void close() {
        if (open.compareAndExchange(true, false)) {
            subscription.unsubscribe();
            connections.values().forEach(RTCPeerConnection::close);
            connections.clear();
        }
    }

}
