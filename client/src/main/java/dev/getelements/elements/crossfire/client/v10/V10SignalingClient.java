package dev.getelements.elements.crossfire.client.v10;

import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.jackson.JacksonEncoder;
import dev.getelements.elements.crossfire.jackson.JacksonProtocolMessageDecoder;
import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.ProtocolMessageType;
import dev.getelements.elements.crossfire.model.Version;
import dev.getelements.elements.crossfire.model.control.ControlMessage;
import dev.getelements.elements.crossfire.model.error.ProtocolError;
import dev.getelements.elements.crossfire.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.model.error.UnexpectedMessageException;
import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.HandshakeResponse;
import dev.getelements.elements.crossfire.model.signal.HostBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.JoinBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.LeaveBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.ConcurrentDequePublisher;
import dev.getelements.elements.sdk.util.Publisher;
import jakarta.websocket.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static dev.getelements.elements.crossfire.client.SignalingClientPhase.HANDSHAKING;
import static dev.getelements.elements.crossfire.client.SignalingClientPhase.TERMINATED;
import static dev.getelements.elements.crossfire.client.v10.V10SignalingClientState.create;
import static jakarta.websocket.CloseReason.CloseCodes.NORMAL_CLOSURE;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

@ClientEndpoint(
        encoders = JacksonEncoder.class,
        decoders = JacksonProtocolMessageDecoder.class
)
public class V10SignalingClient implements SignalingClient {

    private static final Logger logger = LoggerFactory.getLogger(V10SignalingClient.class);

    private final Deque <Signal> backlog = new ConcurrentLinkedDeque<>();

    private final Publisher<Signal> onSignal = new ConcurrentDequePublisher<>();

    private final Publisher<Throwable> onClientError = new ConcurrentDequePublisher<>();

    private final Publisher<HandshakeResponse> onHandshake = new ConcurrentDequePublisher<>();

    private final CountDownLatch disconnectCountdownLatch = new CountDownLatch(1);

    private final AtomicReference<V10SignalingClientState> state = new AtomicReference<>(create());

    @Override
    public Version getVersion() {
        return Version.V_1_0;
    }

    @Override
    public MatchState getState() {
        return this.state.get();
    }

    @Override
    public Optional<HandshakeResponse> findHandshakeResponse() {
        return ofNullable(state.get().handshake());
    }

    @Override
    public Stream<Signal> backlog() {
        return backlog.stream();
    }

    @Override
    public void signal(final Signal signal) {

        final var state = this.state.get();

        switch (state.phase()) {
            case SIGNALING -> state.session().getAsyncRemote().sendObject(signal);
            default -> throw new IllegalStateException("Unexpected state: " + state.phase());
        }

    }

    @Override
    public void control(final ControlMessage control) {

        final var state = this.state.get();

        switch (state.phase()) {
            case SIGNALING -> state.session().getAsyncRemote().sendObject(control);
            default -> throw new IllegalStateException("Unexpected state: " + state.phase());
        }

    }

    @OnOpen
    public void onOpen(final Session session) throws IOException {

        final var state = this.state.updateAndGet(s -> s.connected(session));

        if (TERMINATED.equals(state.phase())) {
            state.closeSession();
        }

    }

    @OnMessage
    public void onSessionMessage(final Session session, final ProtocolMessage message) throws IOException {

        logger.debug("Received message {}", message);

        if (message.getType() == null)
            throw new UnexpectedMessageException("Message has no type: " + message);

        final var state = this.state.get();

        switch (state.phase()) {
            case SIGNALING -> onMessageSignalingPhase(state, message);
            case HANDSHAKING -> onMessageHandshakingPhase(state, message);
            case TERMINATED -> logger.debug("Dropping message in terminated phase: {}", message.getType());
            default -> throw new UnexpectedMessageException("Unexpected message in phase " + state.phase());
        }

    }

    private void onMessageSignalingPhase(final V10SignalingClientState state, final ProtocolMessage message) throws IOException {
        switch (message.getType().getCategory()) {
            case ERROR -> onErrorMessage((ProtocolError) message);
            case SIGNALING, SIGNALING_DIRECT -> onSignalingMessage((Signal) message);
            default -> throw new UnexpectedMessageException("Unexpected message in phase " + state.phase());
        }
    }

    private void onSignalingMessage(final Signal message) {

        final var state = switch (message.getType()) {
            case HOST -> {
                final var host = (HostBroadcastSignal) message;
                yield this.state.updateAndGet(s -> s.host(host));
            }
            case SIGNAL_JOIN -> {
                final var join = (JoinBroadcastSignal) message;
                yield this.state.updateAndGet(s -> s.join(join));
            }
            case SIGNAL_LEAVE -> {
                final var leave = (LeaveBroadcastSignal) message;
                yield this.state.updateAndGet(s -> s.leave(leave));
            }
            default -> this.state.updateAndGet(s -> s.signal(message));
        };

        switch (state.phase()) {
            case SIGNALING -> onSignal.publish(message,
                    m -> logger.debug("Delivered signaling message: {}", message),
                    onClientError::publish
            );
            case TERMINATED -> logger.debug("Dropping message in terminated phase: {}", message.getType());
            default -> throw new UnexpectedMessageException("Unexpected message in phase " + state.phase());
        }

    }

    private void onMessageHandshakingPhase(final V10SignalingClientState state, final ProtocolMessage message) throws IOException {
        switch (message.getType().getCategory()) {
            case ERROR -> onErrorMessage((ProtocolError) message);
            case HANDSHAKE -> onHandshakeMessage((HandshakeResponse) message);
            default -> throw new UnexpectedMessageException("Unexpected message in phase " + state.phase());
        }
    }

    private void onHandshakeMessage(final HandshakeResponse message) {
        if (ProtocolMessageType.MATCHED.equals(message.getType())) {
            final var state = this.state.updateAndGet(s -> s.matched(message));

            switch (state.phase()) {
                case SIGNALING -> {

                    onHandshake.publish(
                            message,
                            m -> logger.debug("Delivered handshake response: {}", message),
                            onClientError::publish
                    );

                    logger.info("Handshake successful, matched with ID: {}", message.getMatchId());

                }
                case TERMINATED -> logger.info("Handshake successful, but session is terminated. Dropping.");
                default -> throw new ProtocolStateException("Unexpected message in phase " + state.phase());
            }

        } else {
            logger.error("Unexpected handshake message: {}", message.getType());
            throw new UnexpectedMessageException("Unexpected handshake message: " + message.getType());
        }
    }

    private void onErrorMessage(final ProtocolError message) throws IOException {

        final var status = new DisconnectStatus(
                message.getMessage(),
                message.getCode(),
                true
        );

        final var state = this.state.updateAndGet(s -> s.terminate(status));
        state.closeSession();
        disconnectCountdownLatch.countDown();

    }

    @OnClose
    public void onSessionClose(final Session session,
                               final CloseReason closeReason) throws IOException {

        final var status = new DisconnectStatus(
                closeReason.getReasonPhrase() == null ? "" : closeReason.getReasonPhrase(),
                closeReason.getCloseCode().toString(),
                !NORMAL_CLOSURE.equals(closeReason.getCloseCode())
        );

        this.state.updateAndGet(s -> s.terminate(status));
        disconnectCountdownLatch.countDown();
        logger.info("Connection closed: {}", closeReason);

    }

    @OnError
    public void onSessionError(final Session session,
                               final Throwable throwable) throws IOException {

        final var status = new DisconnectStatus(
                throwable.getMessage(),
                throwable.getClass().getSimpleName(),
                true
        );

        this.state.updateAndGet(s -> s.terminate(status));
        disconnectCountdownLatch.countDown();
        logger.error("An error occurred.", throwable);
        onClientError.publish(throwable);

    }

    @Override
    public void handshake(final HandshakeRequest request) {

        requireNonNull(request, "Handshake request must not be null");

        switch (request.getType()) {
            case FIND, JOIN -> {
                if (!Version.V_1_0.equals(request.getVersion())) {
                    throw new IllegalArgumentException("Invalid protocol version: " + request.getVersion());
                }
            }
            default -> throw new IllegalArgumentException("Invalid handshake request type: " + request.getType());
        }

        final var state = this.state.updateAndGet(V10SignalingClientState::handshaking);

        if (HANDSHAKING.equals(state.phase()))
            state.session().getAsyncRemote().sendObject(request);

    }

    @Override
    public Subscription onClientError(BiConsumer<Subscription, Throwable> listener) {
        return onClientError.subscribe(listener);
    }

    @Override
    public Subscription onSignal(final BiConsumer<Subscription, Signal> listener) {
        return onSignal.subscribe(listener);
    }

    @Override
    public Subscription onHandshake(final BiConsumer<Subscription, HandshakeResponse> listener) {
        return onHandshake.subscribe(listener);
    }

    @Override
    public Optional<DisconnectStatus> waitForDisconnect(final long time, final TimeUnit units) throws InterruptedException {
        if (disconnectCountdownLatch.await(time, units)) {
            return Optional.of(state.get().disconnectStatus());
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void close() {

        final var status = new DisconnectStatus("Closed by user.", "CLOSED", false);
        final var state = this.state.updateAndGet(s -> s.terminate(status));
        disconnectCountdownLatch.countDown();

        try {
            state.closeSession();
            onSignal.clear();
            onHandshake.clear();
            onClientError.clear();
        } catch (IOException ex) {
            logger.error("Error closing session", ex);
        }

    }

}
