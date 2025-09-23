package dev.getelements.elements.crossfire.client.webrtc;

import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCPeerConnection;
import dev.onvoid.webrtc.RTCSessionDescription;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record WebRTCPeerConnectionState(
        boolean open,
        RTCPeerConnection connection,
        List<RTCIceCandidate> candidates,
        RTCSessionDescription description,
        RTCDataChannel channel) {

    static {
        WebRTC.load();
    }

    public static WebRTCPeerConnectionState create() {
        return new WebRTCPeerConnectionState(true, null, List.of(), null, null);
    }

    public WebRTCPeerConnectionState connect(final RTCPeerConnection connection) {

        if (connection == null) {
            throw new IllegalArgumentException("Must provide connection object.");
        }

        if (connection() != null) {
            throw new IllegalStateException("Cannot connect more than once");
        }

        return open
                ? new WebRTCPeerConnectionState(true, connection, candidates(), description(), channel())
                : this;

    }

    public WebRTCPeerConnectionState connect(final RTCPeerConnection connection, final RTCDataChannel channel) {

        if (channel == null) {
            throw new IllegalArgumentException("Must provide channel object.");
        }

        if (connection == null) {
            throw new IllegalArgumentException("Must provide connection object.");
        }

        if (connection() != null || channel() != null) {
            throw new IllegalStateException("Cannot connect more than once");
        }

        return open
                ? new WebRTCPeerConnectionState(true, connection, candidates(), description(), channel)
                : this;

    }

    public WebRTCPeerConnectionState candidate(final RTCIceCandidate candidate) {

        final var candidates = new ArrayList<>(candidates()) {{ add(candidate); }};

//        if (connection() == null) {
//            throw new IllegalStateException("No connection.");
//        }

        return open
                ? new WebRTCPeerConnectionState(true, connection(), candidates, description(), channel())
                : this;

    }

    public WebRTCPeerConnectionState description(final RTCSessionDescription description) {

//        if (connection() == null) {
//            throw new IllegalStateException("No connection.");
//        }

        return open
                ? new WebRTCPeerConnectionState(true, connection(), candidates(), description, channel())
                : this;

    }

    public WebRTCPeerConnectionState channel(final RTCDataChannel channel) {

        if (connection() == null) {
            throw new IllegalStateException("No connection.");
        }

        return open
                ? new WebRTCPeerConnectionState(true, connection(), candidates(), description(), channel)
                : this;
    }

    public WebRTCPeerConnectionState close() {
        return open
                ? new WebRTCPeerConnectionState(false, connection(), candidates(), description(), channel())
                : this;
    }

    public Optional<RTCDataChannel> findChannel() {
        return open() ? Optional.ofNullable(channel()) : Optional.empty();
    }

    public Optional<RTCPeerConnection> findConnection() {
        return open() ? Optional.ofNullable(connection()) : Optional.empty();
    }

}
