package dev.getelements.elements.crossfire.client.signaling;

import dev.getelements.elements.crossfire.client.*;
import dev.getelements.elements.crossfire.model.Protocol;
import dev.getelements.elements.crossfire.model.error.ProtocolError;
import dev.getelements.elements.crossfire.model.signal.*;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.ConcurrentDequePublisher;
import dev.getelements.elements.sdk.util.Publisher;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static dev.getelements.elements.crossfire.client.Peer.SendResult.SENT;
import static dev.getelements.elements.crossfire.client.PeerPhase.*;
import static dev.getelements.elements.crossfire.model.Protocol.SIGNALING;
import static dev.getelements.elements.crossfire.model.signal.SignalLifecycle.ONCE;

public class SignalingPeer implements Peer, AutoCloseable {

    private final SignalingClient signaling;

    private final String localProfileId;

    private final String remoteProfileId;

    private final Subscription subscription;

    private final AtomicReference<PeerPhase> status = new AtomicReference<>(READY);

    private final Publisher<PeerStatus> onPeerStatus;

    private final Publisher<Message> onMessage = new ConcurrentDequePublisher<>();

    private final Publisher<StringMessage> onStringMessage = new ConcurrentDequePublisher<>();

    private final Publisher<Throwable> onError = new ConcurrentDequePublisher<>();

    public SignalingPeer(
            final SignalingClient signaling,
            final String localProfileId,
            final String remoteProfileId,
            final Publisher<PeerStatus> onPeerStatus) {
        this.localProfileId = localProfileId;
        this.remoteProfileId = remoteProfileId;
        this.signaling = signaling;
        this.onPeerStatus = onPeerStatus;
        this.subscription = Subscription.begin()
                .chain(signaling.onSignal(this::onSignal))
                .chain(signaling.onClientError(this::onClientError));
    }

    private void onSignal(final Subscription subscription, final Signal signal) {
        switch (signal.getType()) {
            case ERROR -> onProtocolError(subscription, (ProtocolError) signal);
            case BINARY_RELAY -> onBinaryRelay(subscription, (BinaryRelayDirectSignal) signal);
            case STRING_RELAY -> onStringRelay(subscription, (StringRelayDirectSignal) signal);
        }
    }

    private void onBinaryRelay(final Subscription subscription, final BinaryRelayDirectSignal signal) {
        final var buffer = ByteBuffer.wrap(signal.getPayload());
        final var message = new Message(this, signal.getProfileId(), buffer);
        onMessage.publish(message);
    }

    private void onStringRelay(final Subscription subscription, final StringRelayDirectSignal signal) {
        final var message = new StringMessage(this, signal.getProfileId(), signal.getPayload());
        onStringMessage.publish(message);
    }

    private void onClientError(final Subscription subscription, final Throwable throwable) {
        onError.publish(throwable);
        close();
    }

    private void onProtocolError(final Subscription subscription, final ProtocolError protocolError) {
        final var error = new PeerException(protocolError.getMessage());
        onError.publish(error);
        close();
    }

    public void connect() {
        if (status.compareAndSet(READY, CONNECTED)) {
            onPeerStatus.publish(new PeerStatus(READY, this));
            onPeerStatus.publish(new PeerStatus(CONNECTED, this));
        }
    }

    @Override
    public String getProfileId() {
        return remoteProfileId;
    }

    @Override
    public PeerPhase gePhase() {
        return status.get();
    }

    @Override
    public Protocol getProtocol() {
        return SIGNALING;
    }

    @Override
    public SendResult send(final ByteBuffer buffer) {

        if (!CONNECTED.equals(status.get())) {
            return SendResult.TERMINATED;
        }

        final var array = new byte[buffer.remaining()];
        buffer.get(array);

        final var signal = new BinaryRelayDirectSignal();
        signal.setLifecycle(ONCE);
        signal.setPayload(array);
        signal.setProfileId(localProfileId);
        signal.setRecipientProfileId(remoteProfileId);
        signaling.signal(signal);

        return SENT;

    }

    @Override
    public SendResult send(final String string) {

        if (!CONNECTED.equals(status.get())) {
            return SendResult.TERMINATED;
        }

        final var signal = new StringRelayDirectSignal();
        signal.setLifecycle(ONCE);
        signal.setPayload(string);
        signal.setProfileId(localProfileId);
        signal.setRecipientProfileId(remoteProfileId);
        signaling.signal(signal);

        return SENT;

    }

    @Override
    public Subscription onError(final BiConsumer<Subscription, Throwable> onError) {
        return this.onError.subscribe(onError);
    }

    @Override
    public Subscription onMessage(final BiConsumer<Subscription, Message> onMessage) {
        return this.onMessage.subscribe(onMessage);
    }

    @Override
    public Subscription onStringMessage(BiConsumer<Subscription, StringMessage> onMessage) {
        return this.onStringMessage.subscribe(onMessage);
    }

    @Override
    public void close() {

        final var old = status.getAndSet(TERMINATED);

        if (!TERMINATED.equals(old)) {
            subscription.unsubscribe();
            onPeerStatus.publish(new PeerStatus(TERMINATED, this));
        }

    }

}
