package dev.getelements.elements.crossfire.client.signaling;

import dev.getelements.elements.crossfire.client.*;
import dev.getelements.elements.crossfire.model.error.ProtocolError;
import dev.getelements.elements.crossfire.model.signal.*;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.ConcurrentDequePublisher;
import dev.getelements.elements.sdk.util.Publisher;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static dev.getelements.elements.crossfire.client.Peer.SendResult.SENT;
import static dev.getelements.elements.crossfire.model.signal.SignalLifecycle.ONCE;

public class SignalingPeer implements Peer, AutoCloseable {

    private final SignalingClient signaling;

    private final String profileId;

    private final String remoteProfileId;

    private final Subscription subscription;

    private final AtomicBoolean open = new AtomicBoolean(true);

    private final Publisher<PeerStatus> onPeerStatus;

    private final Publisher<Message> onMessage = new ConcurrentDequePublisher<>();

    private final Publisher<StringMessage> onStringMessage = new ConcurrentDequePublisher<>();

    private final Publisher<Throwable> onError = new ConcurrentDequePublisher<>();

    public SignalingPeer(
            final SignalingClient signaling,
            final String profileId,
            final String remoteProfileId,
            final Publisher<PeerStatus> onPeerStatus) {
        this.remoteProfileId = remoteProfileId;
        this.signaling = signaling;
        this.profileId = profileId;
        this.onPeerStatus = onPeerStatus;
        this.subscription = Subscription.begin()
                .chain(signaling.onSignal(this::onSignal))
                .chain(signaling.onClientError(this::onClientError));
    }

    private void onSignal(final Subscription subscription, final Signal signal) {
        switch (signal.getType()) {
            case ERROR -> onProtocolError(subscription, (ProtocolError) signal);
            case BINARY_RELAY -> onBinaryRelay(subscription, (BinaryRelayDirectSignal) signal);
            case BINARY_BROADCAST -> onBinaryBroadcast(subscription, (BinaryBroadcastSignal) signal);
            case STRING_RELAY -> onStringRelay(subscription, (StringRelayDirectSignal) signal);
            case STRING_BROADCAST -> onStringBroadcast(subscription, (StringBroadcastSignal) signal);
        }
    }

    private void onBinaryRelay(final Subscription subscription, final BinaryRelayDirectSignal signal) {
        final var buffer = ByteBuffer.wrap(signal.getPayload());
        final var message = new Message(signal.getProfileId(), buffer);
        onMessage.publish(message);
    }

    private void onBinaryBroadcast(final Subscription subscription, final BinaryBroadcastSignal signal) {
        final var buffer = ByteBuffer.wrap(signal.getPayload());
        final var message = new Message(signal.getProfileId(), buffer);
        onMessage.publish(message);
    }

    private void onStringRelay(final Subscription subscription, final StringRelayDirectSignal signal) {
        final var message = new StringMessage(signal.getProfileId(), signal.getPayload());
        onStringMessage.publish(message);
    }

    private void onStringBroadcast(final Subscription subscription, final StringBroadcastSignal signal) {
        final var message = new StringMessage(signal.getProfileId(), signal.getPayload());
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

    @Override
    public String getProfileId() {
        return profileId;
    }

    @Override
    public SendResult send(final ByteBuffer buffer) {

        if (!open.get()) {
            return SendResult.TERMINATED;
        }

        final var array = new byte[buffer.remaining()];
        buffer.get(array);

        final var signal = new BinaryRelayDirectSignal();
        signal.setLifecycle(ONCE);
        signal.setPayload(array);
        signal.setProfileId(profileId);
        signal.setRecipientProfileId(remoteProfileId);
        signaling.signal(signal);

        return SENT;

    }

    @Override
    public SendResult send(final String string) {

        if (!open.get()) {
            return SendResult.TERMINATED;
        }

        final var signal = new StringRelayDirectSignal();
        signal.setLifecycle(ONCE);
        signal.setPayload(string);
        signal.setProfileId(profileId);
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
        if (open.compareAndExchange(true, false)) {
            subscription.unsubscribe();
            onPeerStatus.publish(new PeerStatus(PeerPhase.TERMINATED, this));
        }
    }

}
