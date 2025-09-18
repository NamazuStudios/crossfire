package dev.getelements.elements.crossfire.client.webrtc;

import dev.onvoid.webrtc.RTCIceServer;

import java.util.ArrayList;
import java.util.Set;

/**
 * Constants to hold Google's public ICE servers.
 */
public class GoogleICEServers {

    /**
     * The lsit of Google's managed ICE servers.
     */
    public static final Set<String> SERVER_URL_STRINGS = Set.of(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302",
        "stun:stun2.l.google.com:19302",
        "stun:stun3.l.google.com:19302",
        "stun:stun4.l.google.com:19302"
    );

    /**
     * Gets the default ICE Servers as an instance of {@link RTCIceServer}.
     * @return the default ICE servers
     */
    public static RTCIceServer getDefault() {

        final var server = new RTCIceServer();

        server.urls = SERVER_URL_STRINGS
                .stream()
                .toList();

        return server;

    }

}
