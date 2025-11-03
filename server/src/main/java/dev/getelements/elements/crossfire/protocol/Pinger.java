package dev.getelements.elements.crossfire.protocol;

import dev.getelements.elements.sdk.annotation.ElementPublic;
import jakarta.websocket.PongMessage;
import jakarta.websocket.Session;

import java.io.IOException;

/**
 * Pinger interface for managing WebSocket ping/pong messages. This continously pings the remote endpoint to ensure the
 * connection remains active and responsive. Terminates the connection if no pong response is received within the
 * configured interval.
 */
@ElementPublic
public interface Pinger {

    /**
     * Starts the pinger for the given session. This method will find a periodic ping to the remote endpoint
     * @param session the session to start the pinger for
     */
    void start(Session session);

    /**
     * Stops the pinger for the current session. This method will stop any periodic pinging and clean up resources.
     */
    void stop();

    /**
     * Indicates that a pong message has been received from the remote endpoint. This method is called when a pong
     * message is received in response to a ping. It can be used to reset any timeout or keep-alive mechanisms that are
     * in place.
     *
     * @param session the session that received the pong message
     * @param message the pong message received from the remote endpoint
     */
    void onPong(Session session, PongMessage message);

}
