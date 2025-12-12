package dev.getelements.elements.crossfire.client.webrtc;

import dev.getelements.elements.crossfire.client.*;
import dev.getelements.elements.crossfire.api.model.Protocol;
import dev.getelements.elements.crossfire.api.model.signal.CandidateDirectSignal;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.ConcurrentDequePublisher;
import dev.getelements.elements.sdk.util.Publisher;
import dev.onvoid.webrtc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

import static dev.getelements.elements.crossfire.client.Peer.SendResult.NOT_READY;
import static dev.getelements.elements.crossfire.client.PeerPhase.CONNECTED;
import static dev.getelements.elements.crossfire.client.PeerPhase.READY;
import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class WebRTCPeer implements Peer, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(WebRTCPeer.class);

    static {
        WebRTC.load();
    }

    /**
     * Converts a {@link CandidateDirectSignal} to an {@link RTCIceCandidate}.
     *
     * @param signal the candidate signal
     * @return the RTC ICE candidate
     */
    public static RTCIceCandidate fromCandidateSignal(final CandidateDirectSignal signal) {
        return new RTCIceCandidate(
                signal.getMid(),
                signal.getMidIndex(),
                signal.getCandidate()
        );
    }

    private final SignalingClient signaling;

    private final Publisher<PeerStatus> onPeerStatus;

    /**
     * The message publisher which will relay binary messages received from the data channel.
     */
    private final Publisher<Message> onMessage = new ConcurrentDequePublisher<>();

    /**
     * The message publisher which will relay string messages received from the data channel.
     */
    private final Publisher<StringMessage> onStringMessage = new ConcurrentDequePublisher<>();

    /**
     * The error publisher which will relay any errors encountered during the lifecycle of this peer.
     */
    protected final Publisher<Throwable> onError = new ConcurrentDequePublisher<>();

    /**
     * The current state of the peer connection.
     */
    protected final AtomicReference<WebRTCPeerConnectionState> peerConnectionState = new AtomicReference<>(WebRTCPeerConnectionState.create());

    public WebRTCPeer(
            final SignalingClient signaling,
            final Publisher<PeerStatus> onPeerStatus) {
        this.signaling = signaling;
        this.onPeerStatus = onPeerStatus;
    }

    /**
     * Closes this peer and releases any resources associated with it.
     */
    @Override
    public void close() {

        final var old = peerConnectionState.getAndUpdate(WebRTCPeerConnectionState::close);

        if (old.open()) {

            close(old);
            logger.debug("Releasing native resources for peer {}", getProfileId());

            old.findChannel().ifPresentOrElse(
                    RTCDataChannel::close,
                    () -> logger.debug("No data channel to close for peer {}", getProfileId())
            );

            old.findConnection().ifPresentOrElse(
                    RTCPeerConnection::close,
                    () -> logger.debug("No peer connection to close for peer {}", getProfileId())
            );

        } else {
            logger.debug("Peer already closed {}", getProfileId());
        }

    }

    /**
     * Closes the peer from the supplied state. This is called by {@link #close()} after the state has been
     * atomically updated to closed. It will only be called once.
     *
     * @param state the state
     */
    protected abstract void close(final WebRTCPeerConnectionState state);

    /**
     * Gets the local profile id of this peer.
     *
     * @return the local profile id
     */
    protected abstract String getLocalProfileId();

    /**
     * Finds the {@link RTCDataChannel} associated with the peer. If available, it will return the datachannel.
     *
     * @return the {@link Optional} containing the data channel
     */
    protected abstract Optional<RTCDataChannel> findDataChannel();

    /**
     * Called when the {@link WebRTCPeerConnectionState} has changed. This can be used to perform any necessary
     * changes to the peer after the successful atomic state change happens. The base implementation will check
     * check that both
     *
     * @param existing the old or existing state
     * @param replacement the updated or replacement state
     */
    protected void onStateChange(final WebRTCPeerConnectionState existing,
                                 final WebRTCPeerConnectionState replacement) {

        boolean start = true;

        if (!replacement.open()) {
            start = false;
            logger.debug("{} not starting ICE: Closed.", getClass().getSimpleName());
        }

        if (replacement.connection() == null) {
            start = false;
            logger.debug("{} not starting ICE: No connection.", getClass().getSimpleName());
        }

        if (replacement.description() == null) {
            start = false;
            logger.debug("{} not starting ICE: No session description.", getClass().getSimpleName());
        }

        if (!start) {
            return;
        }

        if (Objects.equals(existing.description(), replacement.description())) {
            logger.debug("{} not starting ICE: Session description unchanged.", getClass().getSimpleName());
            addCandidates(replacement);
        } else {
            logger.debug("{} starting ICE.", getClass().getSimpleName());
            startICE(replacement);
        }

    }

    /**
     * Starts ICE (Internet Connectivity Establishment) for the peer connection. This will typically involve creating
     * an offer or answer and setting the local description along with all applicable ICE candidates. Intended to be
     * called by {@link #updateAndGet(UnaryOperator)}.
     *
     * @param state the peer connection state
     */
    protected abstract void startICE(WebRTCPeerConnectionState state);

    /**
     * Adds the supplied candidates to the connection.
     *
     * @param state the peer connection state
     */
    protected void addCandidates(final WebRTCPeerConnectionState state) {

        final var connection = state.connection();

        state.candidates().forEach(candidate -> {

            logger.debug("Adding the candidates for remote peer {}:\n{}.",
                    getProfileId(),
                    candidate
            );

            connection.addIceCandidate(candidate);

        });

    }

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

                final var state = dataChannel.getState();

                final var status = switch (state) {
                    // We don't handle the READY state here because the peer will be ready before the data connection
                    // is ready. We consider it ready as soon as ICE starts for the sake of WEB RTC, we we just look
                    // for either CONNECTING or OPEN.
                    case OPEN -> new PeerStatus(CONNECTED, WebRTCPeer.this);
                    case CLOSED -> new PeerStatus(PeerPhase.TERMINATED, WebRTCPeer.this);
                    default -> null;
                };

                logger.debug("Data channel state changed {}: {}", state, status);

                if (status == null) {
                    logger.debug("State change does not require status change: {}", state);
                } else {
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

                    logger.debug("recv: bin={}, pos={}, lim={}, cap={}, rem={}",
                            buffer.binary,
                            buffer.data.position(),
                            buffer.data.limit(),
                            buffer.data.capacity(),
                            buffer.data.remaining()
                    );

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

    /**
     * Sends a signal to the other peer with the given ICE candidate.
     *
     * @param candidate the ICE candidate
     * @param localProfileId the local profile id
     * @param remoteProfileId the remote profile id
     */
    protected void signalCandidate(
            final RTCIceCandidate candidate,
            final String localProfileId,
            final String remoteProfileId) {

        final var signal = new CandidateDirectSignal();
        signal.setProfileId(localProfileId);
        signal.setRecipientProfileId(remoteProfileId);
        signal.setMid(candidate.sdpMid);
        signal.setCandidate(candidate.sdp);
        signal.setMidIndex(candidate.sdpMLineIndex);

        logger.debug("Sent {} ICE CANDIDATE {} -> {}:\n{}",
                getClass().getSimpleName(),
                signal.getProfileId(),
                signal.getRecipientProfileId(),
                signal.getCandidate()
        );

        signaling.signal(signal);

    }

    /**
     * Called to handle the remote signal received from the {@link SignalingClient}.
     *
     * @param signal the signal
     */
    protected void onSignalCandidate(final CandidateDirectSignal signal) {
        if (getProfileId().equals(signal.getProfileId())) {

            logger.debug("{} got CANDIDATE From {}.\n{}",
                    getClass().getSimpleName(),
                    signal.getProfileId(),
                    signal.getCandidate()
            );

            final var candidate = fromCandidateSignal(signal);
            updateAndGet(s -> s.candidate(candidate));

        } else {
            logger.warn("Dropping CANDIDATE from {} intended for {} (not relevant to this peer {}).",
                    signal.getProfileId(),
                    signal.getRecipientProfileId(),
                    getProfileId()
            );
        }
    }

    /**
     * Updates the current {@link WebRTCPeerConnectionState} using the given operation. This will also apply any
     * necessary changes to the underlying {@link RTCPeerConnection}.
     *
     * @param operation the operation to apply
     */
    protected WebRTCPeerConnectionState updateAndGet(final UnaryOperator<WebRTCPeerConnectionState> operation) {

        WebRTCPeerConnectionState existing;
        WebRTCPeerConnectionState replacement;

        do {
            existing = peerConnectionState.get();
            replacement = operation.apply(existing);
        } while (!peerConnectionState.compareAndSet(existing, replacement));

        onStateChange(existing, replacement);

        return replacement;

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

                    final var array = new byte[buffer.remaining()];
                    buffer.duplicate().get(array);

                    final var wrapped = ByteBuffer.wrap(array);
                    dataChannel.send(new RTCDataChannelBuffer(wrapped, true));

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
                            final var buffer = ByteBuffer.wrap(string.getBytes(UTF_8));
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

    protected class ConnectionObserver implements LoggingPeerConnectionObserver {

        private final Logger logger;

        public ConnectionObserver(final Class<? extends WebRTCPeer> peerClass) {
            this.logger = LoggerFactory.getLogger(peerClass);
        }

        @Override
        public Logger getLogger() {
            return logger;
        }

        @Override
        public void onIceCandidate(final RTCIceCandidate candidate) {
            signalCandidate(candidate, getLocalProfileId(), getProfileId());
        }

        @Override
        public void onIceCandidateError(final RTCPeerConnectionIceErrorEvent event) {

            logger.error("ICE candidate error: {} for remote {}",
                    event.getErrorText(),
                    getProfileId()
            );

            onError.publish(new PeerException(event.getErrorText()));
            close();

        }

        @Override
        public void onSignalingChange(final RTCSignalingState state) {

            getLogger().debug("Peer signaling state changed {} for {}", state, getProfileId());

            switch (state) {
                case STABLE -> onPeerStatus.publish(new PeerStatus(READY, WebRTCPeer.this));
            }

        }

    }

}
