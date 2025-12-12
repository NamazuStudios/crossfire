package dev.getelements.elements.crossfire.protocol;

import dev.getelements.elements.crossfire.api.MatchHandle;
import dev.getelements.elements.crossfire.api.Server;
import dev.getelements.elements.crossfire.api.model.ProtocolMessage;
import dev.getelements.elements.crossfire.api.model.error.ProtocolStateException;
import dev.getelements.elements.sdk.annotation.ElementPublic;
import dev.getelements.elements.sdk.annotation.ElementServiceExport;
import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import dev.getelements.elements.sdk.model.profile.Profile;
import jakarta.websocket.PongMessage;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Handles all protocol messages.
 */
@ElementPublic
@ElementServiceExport
public interface ProtocolMessageHandler extends Server {

    /**
     * The current connection phase.
     */
    ConnectionPhase getPhase();

    /**
     * Gets the current {@link AuthRecord}, throwing an exception if it is not present. Will always be present
     * in the {@link ConnectionPhase#SIGNALING} phase or later.
     *
     * @return the authentication record
     */
    default AuthRecord getAuthRecord() {
        return findAuthRecord().orElseThrow(NoSuchElementException::new);
    }

    /**
     * Gets the current {@link MultiMatchRecord}, throwing an exception if it is not present. Will always be present
     * in the {@link ConnectionPhase#SIGNALING} phase or later.
     *
     * @return the multi-match record
     */
    default MultiMatchRecord getMultiMatchRecord() {
        return findMatchRecord().orElseThrow(NoSuchElementException::new);
    }

    /**
     * Finds the current {@link AuthRecord}, if it is present. Will always be present in the
     * {@link ConnectionPhase#SIGNALING} phase or later.
     *
     * @return an {@link Optional} containing the authentication record if present, otherwise empty
     */
    Optional<AuthRecord> findAuthRecord();

    /**
     * Finds the current {@link MultiMatchRecord}, if it is present. Will always be present in the
     * {@link ConnectionPhase#SIGNALING} phase or later.
     *
     * @return an {@link Optional} containing the authentication record if present, otherwise empty
     */
    Optional<MultiMatchRecord> findMatchRecord();

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
     * Handles all protocol errors.
     *
     * @param session the session
     * @param error the protocol message request
     * @throws ProtocolStateException in the event that the connection phase is not ready or has been terminated
     */
    default void onError(final jakarta.websocket.Session session, final Throwable error) {
        terminate(error);
    }

    /**
     * Atomically and in a thread safe manner matches the profile to the session. This will switch the connection phase
     * to SIGNALING if this method and the call to {@link #authenticated(AuthRecord)} also succeeds.
     *
     * @param match the match
     */
    void matched(MultiMatchRecord match);

    /**
     * Atomically and in a thread safe manner authenticates the session. This will switch the connection phase to
     * SIGNALING if this method and the call to {@link #matched(MultiMatchRecord)} also succeeds.
     *
     * @param authRecord the connection phase
     * @throws ProtocolStateException in the event that the connection phase is not HANDSHAKE
     */
    void authenticated(AuthRecord authRecord);

    /**
     * Terminates the connection. This will switch the connection phase to TERMINATED. This method should be called when
     * the session is closed or when the connection is terminated. Error conditions also terminate the connection
     * automatically.
     */
    void terminate();

    /**
     * Terminates the connection. This will switch the connection phase to TERMINATED. This method should be called when
     * the session is closed or when the connection is terminated. Error conditions also terminate the connection.
     */
    void terminate(Throwable th);

    /**
     * Represents an authentication record.
     * Contains the profile and the session.
     *
     * @param profile the {@link Profile} that was authenticated
     * @param session the {@link dev.getelements.elements.sdk.model.session.Session} that was created
     */
    @ElementPublic
    record AuthRecord(
            Profile profile,
            dev.getelements.elements.sdk.model.session.Session session
    ) {}

    /**
     * Represents an authentication record.
     * Contains the profile and the session.
     *
     * @param matchHandle the {@link MultiMatch} that was matched
     * @param configuration the {@link MatchmakingApplicationConfiguration} used for the match
     */
    @ElementPublic
    record MultiMatchRecord(
            MatchHandle<?> matchHandle,
            MatchmakingApplicationConfiguration configuration
    ) {

        public String getId() {
            return matchHandle().getResult().getId();
        }

    }

}
