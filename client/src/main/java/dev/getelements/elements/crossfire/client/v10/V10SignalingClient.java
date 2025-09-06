package dev.getelements.elements.crossfire.client.v10;

import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.jackson.JacksonEncoder;
import dev.getelements.elements.crossfire.jackson.JacksonProtocolMessageDecoder;
import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.Version;
import dev.getelements.elements.crossfire.model.error.ProtocolError;
import dev.getelements.elements.crossfire.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.model.error.UnexpectedMessageException;
import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.HandshakeResponse;
import dev.getelements.elements.crossfire.model.signal.ConnectBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.HostBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.ConcurrentDequePublisher;
import dev.getelements.elements.sdk.util.Publisher;
import jakarta.websocket.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static dev.getelements.elements.crossfire.client.SignalingClientPhase.HANDSHAKING;
import static dev.getelements.elements.crossfire.client.SignalingClientPhase.TERMINATED;
import static dev.getelements.elements.crossfire.client.v10.V10SignalingClientState.create;
import static java.util.Objects.requireNonNull;

@ClientEndpoint(
        encoders = JacksonEncoder.class,
        decoders = JacksonProtocolMessageDecoder.class
)
public class V10SignalingClient implements SignalingClient {

    private static final Logger logger = LoggerFactory.getLogger(V10SignalingClient.class);

    private final Publisher<Signal> onSignal = new ConcurrentDequePublisher<>();

    private final Publisher<Throwable> onClientError = new ConcurrentDequePublisher<>();

    private final Publisher<HandshakeResponse> onHandshake = new ConcurrentDequePublisher<>();

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
        return Optional.ofNullable(state.get().handshake());
    }

    @Override
    public void signal(final Signal signal) {

        final var state = this.state.get();

        switch (state.phase()) {
            case SIGNALING -> state.session().getAsyncRemote().sendObject(signal);
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
            case SIGNALING -> onSignalingMessage((Signal) message);
            default -> throw new UnexpectedMessageException("Unexpected message in phase " + state.phase());
        }
    }

    private void onSignalingMessage(final Signal message) {

        final var state = switch (message.getType()) {
            case HOST -> {
                final var host = (HostBroadcastSignal) message;
                yield this.state.updateAndGet(s -> s.host(host.getProfileId()));
            }
            case CONNECT -> {
                final var connect = (ConnectBroadcastSignal) message;
                yield this.state.updateAndGet(s -> s.connect(connect.getProfileId()));
            }
            case DISCONNECT -> {
                final var disconnect = (ConnectBroadcastSignal) message;
                yield this.state.updateAndGet(s -> s.disconnect(disconnect.getProfileId()));
            }
            default -> this.state.get();
        };

        switch (state.phase()) {
            case SIGNALING -> onSignal.publish(message);
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
        if (ProtocolMessage.Type.MATCHED.equals(message.getType())) {
            final var state = this.state.updateAndGet(s -> s.matched(message));

            switch (state.phase()) {
                case SIGNALING -> {
                    onHandshake.publish(message);
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
        final var state = this.state.updateAndGet(V10SignalingClientState::terminate);
        state.closeSession();
    }

    @OnClose
    public void onSessionClose(final Session session,
                               final CloseReason closeReason) throws IOException {
        this.state.updateAndGet(V10SignalingClientState::terminate);
        logger.info("Connection closed: {}", closeReason);
    }

    @OnError
    public void onSessionError(final Session session,
                               final Throwable throwable) throws IOException {
        this.state.updateAndGet(V10SignalingClientState::terminate);
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
    public void close() {

        final var state = this.state.updateAndGet(V10SignalingClientState::terminate);

        try {
            state.closeSession();
        } catch (IOException ex) {
            logger.error("Error closing session", ex);
        }

    }

}
