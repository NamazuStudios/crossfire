package dev.getelements.elements.crossfire.client.v10;

import dev.getelements.elements.crossfire.client.Client;
import dev.getelements.elements.crossfire.jackson.JacksonEncoder;
import dev.getelements.elements.crossfire.jackson.JacksonProtocolMessageDecoder;
import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import jakarta.websocket.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicReference;

import static dev.getelements.elements.crossfire.client.ClientPhase.HANDSHAKING;
import static dev.getelements.elements.crossfire.client.ClientPhase.TERMINATED;
import static dev.getelements.elements.crossfire.client.v10.V10ClientState.create;
import static dev.getelements.elements.crossfire.model.handshake.HandshakeRequest.VERSION_1_0;
import static java.util.Objects.requireNonNull;

@ClientEndpoint(
        encoders = JacksonEncoder.class,
        decoders = JacksonProtocolMessageDecoder.class
)
public class V10Client implements Client {

    private static final Logger logger = LoggerFactory.getLogger(V10Client.class);

    private final AtomicReference<V10ClientState> state = new AtomicReference<>(create());

    @OnOpen
    public void onOpen(final Session session) throws IOException {

        final var state = this.state.updateAndGet(s -> s.connected(session));

        if (TERMINATED.equals(state.phase())) {
            state.closeSession();
        }

    }

    @OnMessage
    public void onMessage(final ProtocolMessage message) {
        logger.debug("Received message {}", message);
    }

    @OnClose
    public void onClose(final Session session,
                        final CloseReason closeReason) throws IOException {
        this.state.updateAndGet(V10ClientState::terminate);
        logger.info("Connection closed: {}", closeReason);
    }

    @OnError
    public void onError(final Session session,
                        final Throwable throwable) throws IOException {
        this.state.updateAndGet(V10ClientState::terminate);
        logger.error("An error occurred.", throwable);
    }

    @Override
    public void handshake(final HandshakeRequest request) {

        requireNonNull(request, "Handshake request must not be null");

        switch (request.getType()) {
            case FIND, JOIN -> {
                if (!VERSION_1_0.equals(request.getVersion())) {
                    throw new IllegalArgumentException("Invalid protocol version: " + request.getVersion());
                }
            }
            default -> throw new IllegalArgumentException("Invalid handshake request type: " + request.getType());
        }

        final var state = this.state.updateAndGet(V10ClientState::handshaking);

        if (HANDSHAKING.equals(state.phase())) {
            state.session().getAsyncRemote().sendObject(request);
        }

    }

    @Override
    public void close() {

        final var state = this.state.updateAndGet(V10ClientState::terminate);

        try {
            state.closeSession();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

    }

}
