package dev.getelements.elements.crossfire.client.v10;

import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.client.SignalingClientPhase;
import dev.getelements.elements.crossfire.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.model.handshake.HandshakeResponse;
import jakarta.websocket.Session;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.getelements.elements.crossfire.client.SignalingClientPhase.*;

record V10SignalingClientState(SignalingClientPhase phase,
                               Session session,
                               HandshakeResponse handshake,
                               String host,
                               List<String> profiles) implements SignalingClient.MatchState {

    public static V10SignalingClientState create() {
        return new V10SignalingClientState(READY, null, null, null, List.of());
    }

    public V10SignalingClientState connected(final Session session) {
        return switch (phase()) {
            case TERMINATED -> this;
            case READY -> new V10SignalingClientState(CONNECTED, session, handshake(), host(), profiles());
            default -> throw new ProtocolStateException("Invalid connection phase " + phase());
        };
    }

    public V10SignalingClientState terminate() {
        return switch (phase()) {
            case TERMINATED -> this;
            default -> new V10SignalingClientState(TERMINATED, session(), handshake(), host(), profiles());
        };
    }

    public V10SignalingClientState handshaking() {
        return switch (phase()) {
            case TERMINATED -> this;
            case CONNECTED -> new V10SignalingClientState(HANDSHAKING, session(), handshake(), host(), profiles());
            default -> throw new ProtocolStateException("Invalid handshake phase " + phase());
        };
    }

    public V10SignalingClientState matched(final HandshakeResponse handshake) {
        return switch (phase()) {
            case TERMINATED -> this;
            case HANDSHAKING -> new V10SignalingClientState(SIGNALING, session(), handshake, host(), profiles());
            default -> throw new ProtocolStateException("Invalid handshake phase " + phase());
        };
    }

    public V10SignalingClientState host(final String host) {
        return switch (phase()) {
            case TERMINATED -> this;
            case SIGNALING -> new V10SignalingClientState(phase(), session(), handshake(), host, profiles());
            default -> throw new ProtocolStateException("Invalid handshake phase " + phase());
        };
    }

    public V10SignalingClientState connect(final String profile) {
        return switch (phase()) {
            case TERMINATED -> this;
            case SIGNALING -> {
                final var profiles = new ArrayList<>(profiles()) {{ add(profile); }};
                yield new V10SignalingClientState(phase(), session(), handshake(), host(), profiles);
            }
            default -> throw new ProtocolStateException("Invalid handshake phase " + phase());
        };
    }

    public V10SignalingClientState disconnect(final String profile) {
        return switch (phase()) {
            case TERMINATED -> this;
            case SIGNALING -> {
                final var profiles = new ArrayList<>(profiles()) {{ remove(profile); }};
                yield new V10SignalingClientState(phase(), session(), handshake(), host(), profiles);
            }
            default -> throw new ProtocolStateException("Invalid handshake phase " + phase());
        };
    }

    public void closeSession() throws IOException {
        if (session() != null)
            session().close();
    }

    @Override
    public SignalingClientPhase getPhase() {
        return phase();
    }

    @Override
    public String getHost() {
        return host();
    }

    @Override
    public String getProfileId() {
        return Optional
                .ofNullable(handshake())
                .flatMap(p -> Optional.ofNullable(p.getProfileId()))
                .orElse(null);
    }

    @Override
    public List<String> getProfiles() {
        return profiles();
    }

}
