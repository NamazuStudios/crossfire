package dev.getelements.elements.crossfire.api;

import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
import dev.getelements.elements.sdk.model.profile.Profile;

/**
 * Starts the matchmaking algorithm with the given request.
 *
 * @param <MessageT> the type of handshake request used to initiate the matchmaking process
 */
public interface MatchmakingRequest<MessageT extends HandshakeRequest> {

    /**
     * Gets the profile of the matchmaking request.
     *
     * @return the profile of the matchmaking request
     */
    Profile getProfile();

    /**
     * Gets the matchmaking application configuration.
     *
     * @return the matchmaking application configuration
     */
    MatchmakingApplicationConfiguration getApplicationConfiguration();

    /**
     * Gets the handshake request for the matchmaking algorithm.
     *
     * @return the handshake request for the matchmaking algorithm
     */
    MessageT getHandshakeRequest();

    /**
     * Gets the protocol message handler for the matchmaking request.
     *
     * @return the protocol message handler for the matchmaking request
     */
    ProtocolMessageHandler getProtocolMessageHandler();

    /**
     * Fails the matchmaking request without a throwable.
     */
    default void failure() {
        failure(null);
    }

    /**
     * Fails the matchmaking request with the given throwable.
     *
     * @param th the throwable to fail with, may be null
     */
    void failure(Throwable th);

    /**
     * Completes the matchmaking request with the given match.
     */
    void success(MatchHandle<MessageT> matchHandle);

}
