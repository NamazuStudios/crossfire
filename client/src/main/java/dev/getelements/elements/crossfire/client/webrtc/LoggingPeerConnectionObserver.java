package dev.getelements.elements.crossfire.client.webrtc;

import dev.onvoid.webrtc.*;
import dev.onvoid.webrtc.media.MediaStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface LoggingPeerConnectionObserver extends PeerConnectionObserver {

    default Logger getLogger() {
        return LoggerFactory.getLogger(this.getClass());
    }

    @Override
    default void onIceCandidate(final RTCIceCandidate candidate) {
        getLogger().debug("Got ICE Candidate: {}", candidate);
    }

    @Override
    default void onSignalingChange(final RTCSignalingState state) {
        getLogger().debug("Signaling State Change: {}", state);
    }

    @Override
    default void onConnectionChange(final RTCPeerConnectionState state) {
        getLogger().debug("Connection State Change: {}", state);
    }

    @Override
    default void onIceConnectionChange(final RTCIceConnectionState state) {
        getLogger().debug("ICE Connection State: {}", state);
    }

    @Override
    default void onStandardizedIceConnectionChange(final RTCIceConnectionState state) {
        getLogger().debug("Standardized ICE Connection State: {}", state);
    }

    @Override
    default void onIceConnectionReceivingChange(boolean receiving) {
        getLogger().debug("ICE Connection Receiving: {}", receiving);
    }

    @Override
    default void onIceGatheringChange(final RTCIceGatheringState state) {
        getLogger().debug("ICE Gathering State: {}", state);
    }

    @Override
    default void onIceCandidateError(final RTCPeerConnectionIceErrorEvent event) {
        getLogger().debug("ICE Candidate Error: {}", event);
    }

    @Override
    default void onIceCandidatesRemoved(final RTCIceCandidate[] candidates) {
        getLogger().debug("ICE Candidates Removed: [{}]", Stream
                .of(candidates)
                .map(RTCIceCandidate::toString)
                .collect(Collectors.joining(", "))
        );
    }

    @Override
    default void onAddStream(final MediaStream stream) {
        getLogger().debug("Add Stream: {}", stream);
    }

    @Override
    default void onRemoveStream(MediaStream stream) {
        getLogger().debug("Remove Stream: {}", stream);
    }

    @Override
    default void onDataChannel(final RTCDataChannel dataChannel) {
        getLogger().debug("Data Channel: {}", dataChannel);
    }

    @Override
    default void onRenegotiationNeeded() {
        getLogger().debug("Renegotiation Needed");
    }

    @Override
    default void onAddTrack(final RTCRtpReceiver receiver, final MediaStream[] mediaStreams) {
        getLogger().debug("Add Track: {} [{}]", receiver, Stream.of(mediaStreams)
                .map(Object::toString)
                .collect(Collectors.joining(","))
        );
    }

    @Override
    default void onRemoveTrack(final RTCRtpReceiver receiver) {
        getLogger().debug("Remove Track: {}", receiver);
    }

    @Override
    default void onTrack(final RTCRtpTransceiver transceiver) {
        getLogger().debug("Track: {}", transceiver);
    }

}
