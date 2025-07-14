package dev.getelements.elements.crossfire.protocol;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.error.ProtocolStateException;
import dev.getelements.elements.sdk.annotation.ElementServiceExport;
import dev.getelements.elements.sdk.model.profile.Profile;
import jakarta.websocket.PongMessage;

import java.io.IOException;

/**
 * Handles all protocol messages.
 */
@ElementServiceExport
public interface ProtocolMessageHandler {

    /**
     * Gets the current authentication record.
     *
     * @return the authentication record
     */
    AuthRecord getAuthRecord();

    /**
     * The current connection phase.
     */
    ConnectionPhase getPhase();

    /**
     * Starts the protocol message handler.
     *
     * @param session the session
     * @throws IOException
     */
    void start(jakarta.websocket.Session session) throws IOException;

    /**
     * Stops the protocol message handler.
     *
     * @param session the sesion
     * @throws IOException any IO exception if there was a problem writing
     */
    void stop(jakarta.websocket.Session session) throws IOException;

    /**
     * Handles all pong messages messages.
     *
     * @param session the session
     * @param message the protocol message request
     */
    void onMessage(jakarta.websocket.Session session, PongMessage message) throws IOException;

    /**
     * Handles all protocol messages.
     *
     * @param session the session
     * @param message the protocol message request
     * @throws ProtocolStateException in the event that the connection phase is not ready or has been terminated
     */
    void onMessage(jakarta.websocket.Session session, ProtocolMessage message) throws IOException;

    /**
     * Atomically and in a thread safe manner authenticates the session. This will switch the connection phase to
     * SIGNALING and will be able to receive signaling messages.
     *
     * @param authRecord the connection phase
     * @throws ProtocolStateException in the event that the connection phase is not HANDSHAKE
     */
    void auth(AuthRecord authRecord);

    /**
     * Represents an authentication record.
     * Contains the profile and the session.
     *
     * @param profile
     * @param session
     */
    record AuthRecord(
            Profile profile,
            dev.getelements.elements.sdk.model.session.Session session
    ) {}

}
