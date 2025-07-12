package dev.getelements.elements.crossfire.endpoint;

import dev.getelements.elements.crossfire.jackson.JacksonEncoder;
import dev.getelements.elements.crossfire.jackson.JacksonProtocolMessageDecoder;
import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.error.StandardProtocolError;
import dev.getelements.elements.crossfire.model.error.TimeoutException;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.sdk.Element;
import dev.getelements.elements.sdk.ElementSupplier;
import dev.getelements.elements.sdk.annotation.ElementDefaultAttribute;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static jakarta.websocket.CloseReason.CloseCodes.GOING_AWAY;
import static jakarta.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION;

@ServerEndpoint(
        value = "/match",
        encoders = JacksonEncoder.class,
        decoders = JacksonProtocolMessageDecoder.class
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
        logger.debug("Closing session due to error {}", session.getId(), th);
        final var remote = session.getAsyncRemote();
        final var error = StandardProtocolError.from(th);
        remote.sendObject(error);
        doClose(session, th);
    }

    private void doClose(final Session session, final Throwable th) {
        try {

            logger.debug("Closing session {}", session.getId(), th);

            if (th instanceof TimeoutException) {
                session.close(new CloseReason(GOING_AWAY, th.getMessage()));
            } else {
                session.close(new CloseReason(UNEXPECTED_CONDITION, th.getMessage()));
            }

        } catch (Exception ex) {
            logger.error("Error while closing session {}", session.getId(), ex);
        }
    }

}
