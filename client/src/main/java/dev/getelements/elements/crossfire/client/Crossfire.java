package dev.getelements.elements.crossfire.client;

import dev.getelements.elements.crossfire.model.Protocol;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

import static dev.getelements.elements.crossfire.model.Protocol.SIGNALING;
import static dev.getelements.elements.crossfire.model.Protocol.WEBRTC;

/**
 * Main interface for the Crossfire client, providing access to signaling and match clients and hosts. Crossfires can
 * transfer data between peers using WebRTC or the signaling server. Note, that Crossfire provides no bridge between
 * signaling and WebRTC. Therefore, the host/client be aware of the protocol used by the other peer. In some cases it
 * may make sense to combine both protocols, e.g., using WebRTC for low-latency communication and the signaling server
 * or it may make sense to switch.
 */
public interface Crossfire extends AutoCloseable {

    /**
     * The environment variable that can be used to set the default URI for the Crossfire instance.
     */
    String URI_ENV_VARIABLE = "ELEMENTS_CROSSFIRE_URI";

    /**
     * The system property that can be used to set the default URI for the Crossfire instance.
     */
    String URI_SYSTEM_PROPERTY = "dev.getelements.elements.crossfire.client.uri";

    /**
     * Gets the current mode of the Crossfire instance. Because the signaling layer may support multiple modes, the
     * mode may be null if no mode is active, or may change in response to signaling messages.
     *
     * @return the mode
     */
    Mode getMode();

    /**
     * Gets the supported modes of the Crossfire instance. Crossfire will operate only within the modes returned by this
     * and explicitly defined in the configuration.
     *
     * @return the supported modes
     */
    Set<Mode> getSupportedModes();

    /**
     * Connects the signaling client to the server using the default URI. The default implementation uses environment
     * variables and system properties to determine the URI. Implementations may override this method to provide an
     * alternative way to determine the default URI. If no default URI is available, this method throws an exception
     * indicating that the URI must be provided (e.g., via {@link #connect(URI)}).
     *
     * @return this Crossfire instance
     */
    Crossfire connect();

    /**
     * Connects the signaling client to the server. This must be called before any other operation and many operations
     * will not be available until after the handshake is complete.
     *
     * @return this Crossfire instance
     * @throws SignalingClientException if there is an error establishing the connection
     */
    Crossfire connect(URI uri);

    /**
     * Gets the signaling client for the Crossfire instance.
     *
     * @return the signaling client
     */
    SignalingClient getSignalingClient();

    /**
     * Finds the default match host, if available. The match host may may not be connected as determined by the
     * signaling server.
     *
     * @return the default match host, if available
     */
    Optional<MatchHost> findMatchHost();

    /**
     * Finds the default match client, if avilable. The match client may may not be connected as determined by the
     * signaling server.
     *
     * @return the match client
     */
    Optional<MatchClient> findMatchClient();

    /**
     * Gets the {@link MatchHost} for the {@link Protocol}.
     *
     * @param protocol the protocol
     * @return the {@link Optional} of the {@link MatchHost}
     */
    Optional<MatchHost> findMatchHost(Protocol protocol);

    /**
     * Gets the {@link MatchHost} for the {@link Protocol}.
     *
     * @param protocol the protocol
     * @return the {@link Optional} of the {@link MatchHost}
     */
    Optional<MatchClient> findMatchClient(Protocol protocol);

    /**
     * Closes the Crossfire instance, including all clients and hosts.
     */
    void close();

    /**
     * Defines the mode of the Crossfire instance.
     */
    enum Mode {

        /**
         * WebRTC host mode.
         */
        WEBRTC_HOST(WEBRTC, true),

        /**
         * WebRTC client mode.
         */
        WEBRTC_CLIENT(WEBRTC, false),

        /**
         * Signaling host mode.
         */
        SIGNALING_HOST(SIGNALING, true),

        /**
         * Signaling client mode.
         */
        SIGNALING_CLIENT(SIGNALING, false);

        private final boolean host;

        private final Protocol protocol;

        Mode(final Protocol protocol, boolean host) {
            this.host = host;
            this.protocol = protocol;
        }

        /**
         * Indicates if the mode is a host mode.
         *
         * @return true if the mode is a host mode, false otherwise
         */
        public boolean isHost() {
            return host;
        }

        /**
         * Returns the protocol used by this mode.
         *
         * @return the protocol
         */
        public Protocol getProtocol() {
            return protocol;
        }

    }

}
