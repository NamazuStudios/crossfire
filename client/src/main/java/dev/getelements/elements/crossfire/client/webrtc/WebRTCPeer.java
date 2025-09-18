package dev.getelements.elements.crossfire.client.webrtc;

import dev.getelements.elements.crossfire.client.Peer;
import dev.getelements.elements.crossfire.client.PeerPhase;
import dev.getelements.elements.crossfire.client.PeerStatus;
import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.model.Protocol;
import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.signal.CandidateDirectSignal;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.ConcurrentDequePublisher;
import dev.getelements.elements.sdk.util.Publisher;
import dev.onvoid.webrtc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static dev.getelements.elements.crossfire.client.Peer.SendResult.NOT_READY;
import static dev.getelements.elements.crossfire.client.PeerPhase.CONNECTED;
import static dev.getelements.elements.crossfire.client.PeerPhase.READY;
import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class WebRTCPeer implements Peer, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(WebRTCPeer.class);

    private Subscription subscription;

    private final Publisher<PeerStatus> onPeerStatus;

    protected final Publisher<Message> onMessage = new ConcurrentDequePublisher<>();

    protected final Publisher<StringMessage> onStringMessage = new ConcurrentDequePublisher<>();

    protected final Publisher<Throwable> onError = new ConcurrentDequePublisher<>();

    public WebRTCPeer(final SignalingClient signalingClient,
                      final Publisher<PeerStatus> onPeerStatus) {
        this.onPeerStatus = onPeerStatus;
        this.subscription = Subscription.begin()
                .chain(signalingClient.onSignal(this::onSignal));
    }

    private void onSignal(final Subscription subscription, final Signal signal) {
        switch (signal.getType()) {
            case CANDIDATE -> onCandidateMessage(subscription, (CandidateDirectSignal) signal);
        }
    }

    private void onCandidateMessage(final Subscription subscription, final CandidateDirectSignal candidate) {
        findPeerConnection().ifPresent(connection -> {

            final var rtcIceCandidate = new RTCIceCandidate(
                    candidate.getMid(),
                    candidate.getMidIndex(),
                    candidate.getCandidate()
            );

            connection.addIceCandidate(rtcIceCandidate);

        });
    }

    /**
     * Closes this peer and releases any resources associated with it.
     */
    public abstract void close();

    /**
     * Finds the {@link RTCDataChannel} associated with the peer. If available, it will return the datachannel.
     *
     * @return the {@link Optional} containing the data channel
     */
    protected abstract Optional<RTCDataChannel> findDataChannel();

    /**
     * Finds the {@link RTCDataChannel} associated with the peer. If available, it will return the datachannel.
     *
     * @return the {@link Optional} containing the data channel
     */
    protected abstract Optional<RTCPeerConnection> findPeerConnection();

    /**
     * Creates a new {@link RTCDataChannelObserver} for the given data channel. This will relay state changes
     * and messages to the observers of this peer.
     *
     * @param dataChannel the data channel
     * @return the newly created observer
     */
    protected RTCDataChannelObserver newDataChannelObserver(final RTCDataChannel dataChannel) {
        return new RTCDataChannelObserver() {

            @Override
            public void onStateChange() {

                final var status = switch (dataChannel.getState()) {
                    case OPEN -> new PeerStatus(CONNECTED, WebRTCPeer.this);
                    case CONNECTING -> new PeerStatus(READY, WebRTCPeer.this);
                    case CLOSED -> new PeerStatus(PeerPhase.TERMINATED, WebRTCPeer.this);
                    default -> null;
                };

                if (status != null) {
                    onPeerStatus.publish(status, s -> logger.debug("Updated status: {}", s), onError::publish);
                }

            }

            @Override
            public void onMessage(final RTCDataChannelBuffer buffer) {
                if (buffer.binary) {
                    final var message = new Message(WebRTCPeer.this, buffer.data);
                    onMessage.publish(
                            message,
                            m -> logger.debug("Delivered binary message."),
                            onError::publish
                    );
                } else {
                    final var string = UTF_8.decode(buffer.data).toString();
                    final var message = new StringMessage(WebRTCPeer.this, string);
                    onStringMessage.publish(
                            message,
                            m -> logger.debug("Delivered string message."),
                            onError::publish
                    );
                }
            }

            @Override
            public void onBufferedAmountChange(final long previousAmount) {
                logger.debug("Data channel buffer size changed from {} -> {}",
                        previousAmount,
                        dataChannel.getBufferedAmount()
                );
            }

        };

    }

    @Override
    public Protocol getProtocol() {
        return Protocol.WEBRTC;
    }

    @Override
    public PeerPhase getPhase() {
        return findDataChannel()
                .map(dc -> switch(dc.getState()){
                    case CONNECTING -> READY;
                    case OPEN -> CONNECTED;
                    case CLOSED, CLOSING -> PeerPhase.TERMINATED;
                })
                .orElse(READY);
    }

    @Override
    public SendResult send(final ByteBuffer buffer) {
        return findDataChannel().map(dataChannel -> switch (dataChannel.getState()) {
            case CONNECTING -> NOT_READY;
            case CLOSED, CLOSING -> SendResult.TERMINATED;
            case OPEN -> {
                try {
                    dataChannel.send(new RTCDataChannelBuffer(buffer, true));
                    yield SendResult.SENT;
                } catch (final Exception e) {
                    logger.error("Failed to send data channel.", e);
                    yield SendResult.ERROR;
                }
            }
        }).orElse(NOT_READY);
    }

    @Override
    public SendResult send(final String string) {
        return findDataChannel()
                .map(dataChannel -> switch (dataChannel.getState()) {
                    case CONNECTING -> NOT_READY;
                    case CLOSED, CLOSING -> SendResult.TERMINATED;
                    case OPEN -> {
                        try {
                            final var buffer = UTF_8.encode(string);
                            dataChannel.send(new RTCDataChannelBuffer(buffer, false));
                            yield SendResult.SENT;
                        } catch (final Exception e) {
                            logger.error("Failed to send data channel.", e);
                            yield SendResult.ERROR;
                        }
                    }
                })
                .orElse(NOT_READY);
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
