package dev.getelements.elements.crossfire.client.webrtc;

import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCPeerConnection;
import dev.onvoid.webrtc.RTCSessionDescription;

import java.util.Optional;

public record WebRTCPeerConnectionState(
        boolean open,
        RTCPeerConnection connection,
        RTCIceCandidate candidate,
        RTCSessionDescription description,
        RTCDataChannel channel) {

    public static WebRTCPeerConnectionState create() {
        return new WebRTCPeerConnectionState(true, null, null, null, null);
    }

    public WebRTCPeerConnectionState connect(final RTCPeerConnection connection) {
        return open
                ? new WebRTCPeerConnectionState(true, connection, candidate(), description(), channel())
                : this;
    }

    public WebRTCPeerConnectionState connect(final RTCPeerConnection connection, final RTCDataChannel channel) {
        return open
                ? new WebRTCPeerConnectionState(true, connection, candidate(), description(), channel)
                : this;
    }

    public WebRTCPeerConnectionState candidate(final RTCIceCandidate candidate) {
        return open
                ? new WebRTCPeerConnectionState(true, connection(), candidate, description(), channel())
                : this;
    }

    public WebRTCPeerConnectionState description(final RTCSessionDescription description) {
        return open
                ? new WebRTCPeerConnectionState(true, connection(), candidate(), description, channel())
                : this;
    }

    public WebRTCPeerConnectionState channel(final RTCDataChannel channel) {
        return open
                ? new WebRTCPeerConnectionState(true, connection(), candidate(), description(), channel)
                : this;
    }

    public WebRTCPeerConnectionState close() {
        return open
                ? new WebRTCPeerConnectionState(false, connection(), candidate(), description(), channel())
                : this;
    }

    public Optional<RTCDataChannel> findChannel() {
        return open() ? Optional.ofNullable(channel) : Optional.empty();
    }

    public Optional<RTCPeerConnection> findConnection() {
        return open() ? Optional.ofNullable(connection()) : Optional.empty();
    }

}
