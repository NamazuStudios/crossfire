package dev.getelements.elements.crossfire.client.webrtc;

import dev.onvoid.webrtc.PeerConnectionFactory;

/**
 * A singleton class that provides a shared instance of PeerConnectionFactory.
 */
public class SharedPeerConnectionFactory {

    static {
        WebRTC.load();
    }

    private static final PeerConnectionFactory instance = new PeerConnectionFactory();

    /**
     * Gets the shared instance.
     *
     * @return the instance
     */
    public static PeerConnectionFactory getInstance() {
        return instance;
    }

}
