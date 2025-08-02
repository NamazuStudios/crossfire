package dev.getelements.elements.crossfire.client.v10;

import dev.getelements.elements.crossfire.client.Client;
import dev.getelements.elements.crossfire.jackson.JacksonEncoder;
import dev.getelements.elements.crossfire.jackson.JacksonProtocolMessageDecoder;
import dev.getelements.elements.crossfire.model.ProtocolMessage;
import jakarta.websocket.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static dev.getelements.elements.crossfire.client.ClientPhase.TERMINATED;
import static dev.getelements.elements.crossfire.client.v10.V10ClientState.create;

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
                        final CloseReason closeReason) {
        logger.info("Connection closed: " + closeReason);
    }

    @OnError
    public void onError(final Session session,
                        final Throwable throwable) {
        logger.error("An error occurred", throwable);
    }

    @Override
    public void close() {

    }

}
