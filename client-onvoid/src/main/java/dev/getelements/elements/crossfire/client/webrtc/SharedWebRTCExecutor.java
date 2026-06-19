package dev.getelements.elements.crossfire.client.webrtc;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JVM singleton single-threaded executor for all WebRTC API calls.
 *
 * libwebrtc's PeerConnectionFactory owns an internal signaling thread. Calling
 * createPeerConnection(), setRemoteDescription(), addIceCandidate(), etc. from
 * multiple concurrent Java threads (e.g. Jetty WebSocket IO threads) races against
 * each other and against libwebrtc's own callback threads. Routing every WebRTC
 * operation through this executor serialises them onto one thread, eliminating
 * the concurrent-access path that triggers RTC_DCHECK failures.
 */
public class SharedWebRTCExecutor {

    static {
        WebRTC.load();
    }

    private static final SharedWebRTCExecutor instance = new SharedWebRTCExecutor();

    public static SharedWebRTCExecutor getInstance() {
        return instance;
    }

    private final Executor executor;

    private SharedWebRTCExecutor() {
        executor = Executors.newSingleThreadExecutor(r -> {
            final var t = new Thread(r, "webrtc-signal");
            t.setDaemon(true);
            return t;
        });
    }

    public Executor getExecutor() {
        return executor;
    }

}