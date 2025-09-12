package dev.getelements.elements.crossfire.client.webrtc;

import dev.getelements.elements.crossfire.client.Peer;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.ConcurrentDequePublisher;
import dev.getelements.elements.sdk.util.Publisher;
import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCDataChannelBuffer;
import dev.onvoid.webrtc.RTCDataChannelObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.BiConsumer;

import static dev.getelements.elements.crossfire.client.Peer.SendStatus.NOT_READY;
import static dev.getelements.elements.crossfire.client.Peer.SendStatus.TERMINATED;
import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class WebRTCPeer implements Peer, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(WebRTCPeer.class);

    protected final Publisher<Message> onMessage = new ConcurrentDequePublisher<>();

    protected final Publisher<StringMessage> onStringMessage = new ConcurrentDequePublisher<>();

    protected final Publisher<Throwable> onError = new ConcurrentDequePublisher<>();

    protected final RTCDataChannelObserver dataChannelObserver = new RTCDataChannelObserver() {

        @Override
        public void onStateChange() {
            findDataChannel().ifPresent(dataChannel -> logger.trace(
                    "Data channel state changed: {}",
                    dataChannel.getState())
            );
        }

        @Override
        public void onMessage(final RTCDataChannelBuffer buffer) {
            if (buffer.binary) {
                final var message = new Message(getProfileId(), buffer.data);
                onMessage.publish(message);
            } else {
                final var string = UTF_8.decode(buffer.data).toString();
                final var message = new StringMessage(getProfileId(), string);
                onStringMessage.publish(message);
            }
        }

        @Override
        public void onBufferedAmountChange(final long previousAmount) {
            logger.debug("Data channel buffer size changed from {}", previousAmount);
        }

    };


    public abstract void close();

    protected abstract Optional<RTCDataChannel> findDataChannel();

    @Override
    public SendStatus send(final ByteBuffer buffer) {
        return findDataChannel().map(dataChannel -> switch (dataChannel.getState()) {
            case CONNECTING -> NOT_READY;
            case CLOSED, CLOSING -> TERMINATED;
            case OPEN -> {
                try {
                    dataChannel.send(new RTCDataChannelBuffer(buffer, true));
                    yield SendStatus.SENT;
                } catch (final Exception e) {
                    logger.error("Failed to send data channel.", e);
                    yield SendStatus.ERROR;
                }
            }
        }).orElse(NOT_READY);
    }

    @Override
    public SendStatus send(final String string) {
        return findDataChannel().map(dataChannel -> switch (dataChannel.getState()) {
            case CONNECTING -> NOT_READY;
            case CLOSED, CLOSING -> TERMINATED;
            case OPEN -> {
                try {
                    final var buffer = UTF_8.encode(string);
                    dataChannel.send(new RTCDataChannelBuffer(buffer, false));
                    yield SendStatus.SENT;
                } catch (final Exception e) {
                    logger.error("Failed to send data channel.", e);
                    yield SendStatus.ERROR;
                }
            }
        }).orElse(NOT_READY);
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

}
