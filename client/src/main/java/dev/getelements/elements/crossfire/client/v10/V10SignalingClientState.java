package dev.getelements.elements.crossfire.client.v10;

import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.client.SignalingClientPhase;
import dev.getelements.elements.crossfire.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.model.handshake.HandshakeResponse;
import dev.getelements.elements.crossfire.model.signal.*;
import jakarta.websocket.Session;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.getelements.elements.crossfire.client.SignalingClientPhase.*;
import static dev.getelements.elements.crossfire.model.signal.SignalLifecycle.SESSION;
import static java.util.Collections.unmodifiableList;
import static java.util.Optional.ofNullable;

record V10SignalingClientState(SignalingClientPhase phase,
                               Session session,
                               HandshakeResponse handshake,
                               String host,
                               List<String> profiles,
                               List<Signal> backlog) implements SignalingClient.MatchState {

    public V10SignalingClientState {
        backlog = backlog == null ? List.of() : unmodifiableList(backlog);
        profiles = profiles == null ? List.of() : unmodifiableList(profiles);
    }

    public static V10SignalingClientState create() {
        return new V10SignalingClientState(READY, null, null, null, List.of(), List.of());
    }

    public V10SignalingClientState connected(final Session session) {
        return switch (phase()) {
            case TERMINATED -> this;
            case READY -> new V10SignalingClientState(CONNECTED, session, handshake(), host(), profiles(), backlog());
            default -> throw new ProtocolStateException("Invalid connection phase " + phase());
        };
    }

    public V10SignalingClientState terminate() {
        return switch (phase()) {
            case TERMINATED -> this;
            default -> new V10SignalingClientState(TERMINATED, session(), handshake(), host(), profiles(), backlog());
        };
    }

    public V10SignalingClientState handshaking() {
        return switch (phase()) {
            case TERMINATED -> this;
            case CONNECTED -> new V10SignalingClientState(HANDSHAKING, session(), handshake(), host(), profiles(), backlog());
            default -> throw new ProtocolStateException("Invalid handshake phase " + phase());
        };
    }

    public V10SignalingClientState matched(final HandshakeResponse handshake) {
        return switch (phase()) {
            case TERMINATED -> this;
            case HANDSHAKING -> new V10SignalingClientState(SIGNALING, session(), handshake, host(), profiles(), backlog());
            default -> throw new ProtocolStateException("Invalid handshake phase " + phase());
        };
    }

    public V10SignalingClientState host(final HostBroadcastSignal host) {
        return switch (phase()) {
            case TERMINATED -> this;
            case SIGNALING -> new V10SignalingClientState(phase(), session(), handshake(), host.getProfileId(), profiles(), backlog());
            default -> throw new ProtocolStateException("Invalid handshake phase " + phase());
        };
    }

    public V10SignalingClientState join(final JoinBroadcastSignal signal) {
        return switch (phase()) {
            case TERMINATED -> this;
            case SIGNALING -> {
                final var backlog = new ArrayList<>(backlog()) {{ add(signal); }};
                final var profiles = new ArrayList<>(profiles()) {{ add(signal.getProfileId()); }};
                yield new V10SignalingClientState(phase(), session(), handshake(), host(), profiles, backlog);
            }
            default -> throw new ProtocolStateException("Invalid handshake phase " + phase());
        };
    }

    public V10SignalingClientState leave(final LeaveBroadcastSignal disconnect) {
        return switch (phase()) {
            case TERMINATED -> this;
            case SIGNALING -> {

                final var backlog = new ArrayList<>(backlog()) {{ removeIf(signal ->
                        SESSION.equals(signal.getLifecycle()) && switch (signal.getType().getCategory()) {

                            case SIGNALING -> ((BroadcastSignal) signal)
                                    .getProfileId()
                                    .equals(disconnect.getProfileId());

                            case SIGNALING_DIRECT -> ((DirectSignal) signal)
                                    .getProfileId()
                                    .equals(disconnect.getProfileId());

                            default -> throw new IllegalStateException("Unexpected signal type " + signal.getType());

                        });
                }};

                final var profiles = new ArrayList<>(profiles()) {{
                    removeIf(profileId -> profileId.equals(disconnect.getProfileId()));
                }};

                yield new V10SignalingClientState(phase(), session(), handshake(), host(), profiles, backlog);

            }
            default -> throw new ProtocolStateException("Invalid handshake phase " + phase());
        };
    }

    public V10SignalingClientState signal(final Signal signal) {
        return switch (phase()) {
            case TERMINATED -> this;
            case SIGNALING ->  switch (signal.getLifecycle()) {
                case ONCE -> this;
                case MATCH, SESSION -> {
                    final var backlog = new ArrayList<>(backlog()) {{ add(signal); }};
                    yield new V10SignalingClientState(phase(), session(), handshake(), host(), profiles(), backlog);
                }
            };
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
    public String getMatchId() {
        return Optional
                .ofNullable(handshake())
                .flatMap(p -> ofNullable(p.getMatchId()))
                .orElse(null);
    }

    @Override
    public String getProfileId() {
        return ofNullable(handshake())
                .flatMap(p -> ofNullable(p.getProfileId()))
                .orElse(null);
    }

    @Override
    public List<String> getProfiles() {
        return profiles();
    }

}
