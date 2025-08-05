package dev.getelements.elements.crossfire.client.v10;

import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCPeerConnection;

import java.nio.ByteBuffer;
import java.util.List;

public record V10PeerConnection(
        RTCIceCandidate candidate,
        RTCPeerConnection connection,
        List<ByteBuffer> outbound) implements AutoCloseable {

    public static V10PeerConnection create() {
        return new V10PeerConnection(null, null, List.of());
    }

    public void close() {
        connection().close();
    }

}
