package dev.getelements.elements.crossfire.client.teavm.signaling;

import dev.getelements.elements.crossfire.api.model.Protocol;
import dev.getelements.elements.crossfire.api.model.error.ProtocolError;
import dev.getelements.elements.crossfire.api.model.signal.BinaryRelayDirectSignal;
import dev.getelements.elements.crossfire.api.model.signal.Signal;
import dev.getelements.elements.crossfire.api.model.signal.StringRelayDirectSignal;
import dev.getelements.elements.crossfire.client.*;
import dev.getelements.elements.sdk.Subscription;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.BiConsumer;

import static dev.getelements.elements.crossfire.api.model.Protocol.SIGNALING;
import static dev.getelements.elements.crossfire.api.model.signal.SignalLifecycle.ONCE;
import static dev.getelements.elements.crossfire.client.Peer.SendResult.SENT;
import static dev.getelements.elements.crossfire.client.PeerPhase.*;

/**
 * Signaling-relay {@link Peer} for TeaVM browser targets.
 * Uses {@link TeaVMPublisher} instead of ConcurrentDequePublisher — JS is single-threaded.
 */
class TeaVMSignalingPeer implements Peer, AutoCloseable {

    private final SignalingClient signaling;
    private final String localProfileId;
    private final String remoteProfileId;
    private final Subscription subscription;
    private final TeaVMPublisher<PeerStatus> onPeerStatus;
    private final TeaVMPublisher<Message> onMessage = new TeaVMPublisher<>();
    private final TeaVMPublisher<StringMessage> onStringMessage = new TeaVMPublisher<>();
    private final TeaVMPublisher<Throwable> onError = new TeaVMPublisher<>();
    private PeerPhase status = READY;

    TeaVMSignalingPeer(final SignalingClient signaling,
                       final String localProfileId,
                       final String remoteProfileId,
                       final TeaVMPublisher<PeerStatus> onPeerStatus) {
        this.signaling       = signaling;
        this.localProfileId  = localProfileId;
        this.remoteProfileId = remoteProfileId;
        this.onPeerStatus    = onPeerStatus;
        this.subscription = Subscription.begin()
                .chain(signaling.onSignal(this::onSignal))
                .chain(signaling.onClientError(this::onClientError));
    }

    private void onSignal(final Subscription sub, final Signal signal) {
        switch (signal.getType()) {
            case ERROR        -> onProtocolError((ProtocolError) signal);
            case BINARY_RELAY -> onBinaryRelay((BinaryRelayDirectSignal) signal);
            case STRING_RELAY -> onStringRelay((StringRelayDirectSignal) signal);
            default           -> {}
        }
    }

    private void onBinaryRelay(final BinaryRelayDirectSignal signal) {
        if (Objects.equals(remoteProfileId, signal.getProfileId())) {
            onMessage.publish(new Message(this, ByteBuffer.wrap(signal.getPayload())));
        }
    }

    private void onStringRelay(final StringRelayDirectSignal signal) {
        if (Objects.equals(remoteProfileId, signal.getProfileId())) {
            onStringMessage.publish(new StringMessage(this, signal.getPayload()));
        }
    }

    private void onClientError(final Subscription sub, final Throwable t) {
        onError.publish(t);
        close();
    }

    private void onProtocolError(final ProtocolError error) {
        onError.publish(new PeerException(error.getMessage()));
        close();
    }

    void connect() {
        if (status == READY) {
            status = CONNECTED;
            onPeerStatus.publish(new PeerStatus(READY,     this));
            onPeerStatus.publish(new PeerStatus(CONNECTED, this));
        }
    }

    @Override public String    getProfileId() { return remoteProfileId; }
    @Override public PeerPhase getPhase()     { return status;          }
    @Override public Protocol  getProtocol()  { return SIGNALING;       }

    @Override
    public SendResult send(final ByteBuffer buffer) {
        if (status != CONNECTED) return SendResult.TERMINATED;
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
        if (status != CONNECTED) return SendResult.TERMINATED;
        final var signal = new StringRelayDirectSignal();
        signal.setLifecycle(ONCE);
        signal.setPayload(string);
        signal.setProfileId(localProfileId);
        signal.setRecipientProfileId(remoteProfileId);
        signaling.signal(signal);
        return SENT;
    }

    @Override public Subscription onError(final BiConsumer<Subscription, Throwable> l)       { return onError.subscribe(l);         }
    @Override public Subscription onMessage(final BiConsumer<Subscription, Message> l)        { return onMessage.subscribe(l);       }
    @Override public Subscription onStringMessage(final BiConsumer<Subscription, StringMessage> l) { return onStringMessage.subscribe(l); }

    @Override
    public void close() {
        if (status != TERMINATED) {
            status = TERMINATED;
            subscription.unsubscribe();
            onPeerStatus.publish(new PeerStatus(TERMINATED, this));
        }
    }
}
