package dev.getelements.elements.crossfire.client.webrtc;

import dev.getelements.elements.sdk.util.ShutdownHooks;
import dev.onvoid.webrtc.PeerConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A singleton class that provides a shared instance of PeerConnectionFactory.
 */
public class SharedPeerConnectionFactory {

    private static final ShutdownHooks shutdown = new ShutdownHooks(SharedPeerConnectionFactory.class);

    private static final PeerConnectionFactory instance = new PeerConnectionFactory();

    static {
        shutdown.add(instance::dispose);
    }

    /**
     * Gets the shared instance.
     *
     * @return the instance
     */
    public static PeerConnectionFactory getInstance() {
        return instance;
    }

}
