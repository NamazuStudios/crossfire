package dev.getelements.elements.crossfire.endpoint;

import dev.getelements.elements.crossfire.common.jackson.FailDecoder;
import dev.getelements.elements.crossfire.common.jackson.JacksonEncoder;
import dev.getelements.elements.crossfire.common.jackson.JacksonProtocolMessageDecoder;
import dev.getelements.elements.crossfire.api.model.ProtocolMessage;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.sdk.Element;
import dev.getelements.elements.sdk.ElementSupplier;
import dev.getelements.elements.sdk.annotation.ElementDefaultAttribute;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@ServerEndpoint(
        value = "/match",
        encoders = JacksonEncoder.class,
        decoders = { JacksonProtocolMessageDecoder.class, FailDecoder.class }
)
public class MatchSignalingEndpoint {

    @ElementDefaultAttribute("crossfire")
    public static final String APP_SERVE_PREFIX = "dev.getelements.elements.app.serve.prefix";

    private static final Logger logger = LoggerFactory.getLogger(MatchSignalingEndpoint.class);

    private final Element element = ElementSupplier
            .getElementLocal(MatchSignalingEndpoint.class)
            .get();

    private final ProtocolMessageHandler handler = element
            .getServiceLocator()
            .getInstance(ProtocolMessageHandler.class);

    @OnOpen
    public void onOpen(final Session session) throws IOException {
        logger.debug("Session {} opened.", session.getId());
        handler.start(session);
    }

    @OnMessage
    public void onMessage(
            final Session session,
            final PongMessage pongMessage) throws IOException {
        logger.debug("Pong message received for session {}.", session.getId());
        handler.onMessage(session, pongMessage);
    }

    @OnMessage
    public void onMessage(
            final Session session,
            final ProtocolMessage message) throws IOException {
        logger.debug("Received protocol message {} for session {}.", message.getType(), session.getId());
        handler.onMessage(session, message);
    }

    @OnClose
    public void onClose(final Session session) throws IOException {
        logger.debug("Session {} closed.", session.getId());
        handler.stop(session);
    }

    @OnError
    public void onError(final Session session, final Throwable th) {
        logger.debug("Encountered error in session {}", session.getId(), th);
        handler.onError(session, th);
    }

}
