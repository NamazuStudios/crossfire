package dev.getelements.elements.crossfire.client.webrtc;

import dev.getelements.elements.crossfire.api.model.Version;
import dev.getelements.elements.crossfire.api.model.control.ControlMessage;
import dev.getelements.elements.crossfire.api.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.api.model.handshake.HandshakeResponse;
import dev.getelements.elements.crossfire.api.model.signal.ConnectBroadcastSignal;
import dev.getelements.elements.crossfire.api.model.signal.DisconnectBroadcastSignal;
import dev.getelements.elements.crossfire.api.model.signal.DirectSignal;
import dev.getelements.elements.crossfire.api.model.signal.Signal;
import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.client.SignalingClientPhase;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.ConcurrentDequePublisher;
import dev.getelements.elements.sdk.util.Publisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * In-process signaling hub for stripped-down WebRTC peer connection tests. Routes signals between
 * stub clients without any WebSocket, Mongo, or server involvement.
 */
public class StubSignalingHub {

    private final String matchId;
    private final String hostProfileId;
    private final List<String> allProfiles;
    private final ConcurrentMap<String, StubClient> clients = new ConcurrentHashMap<>();

    public StubSignalingHub(final String matchId,
                            final String hostProfileId,
                            final List<String> allProfiles) {
        this.matchId = matchId;
        this.hostProfileId = hostProfileId;
        this.allProfiles = List.copyOf(allProfiles);
    }

    /**
     * Creates and registers a {@link SignalingClient} stub for the given profile ID.
     */
    public SignalingClient connect(final String profileId) {
        final var client = new StubClient(profileId);
        clients.put(profileId, client);
        return client;
    }

    /**
     * Broadcasts a {@link ConnectBroadcastSignal} to all other registered clients, simulating
     * the server announcing that {@code joiningProfileId} has joined the match.
     */
    public void announceJoin(final String joiningProfileId) {
        final var signal = new ConnectBroadcastSignal();
        signal.setProfileId(joiningProfileId);
        route(joiningProfileId, signal);
    }

    /**
     * Broadcasts a {@link DisconnectBroadcastSignal} to all other registered clients, simulating
     * the server announcing that {@code leavingProfileId} has left the match.
     */
    public void announceDeparture(final String leavingProfileId) {
        final var signal = new DisconnectBroadcastSignal();
        signal.setProfileId(leavingProfileId);
        route(leavingProfileId, signal);
    }

    private void route(final String senderProfileId, final Signal signal) {
        if (signal instanceof DirectSignal direct) {
            final var recipient = clients.get(direct.getRecipientProfileId());
            if (recipient != null) {
                recipient.deliver(signal);
            }
        } else {
            clients.forEach((profileId, client) -> {
                if (!profileId.equals(senderProfileId)) {
                    client.deliver(signal);
                }
            });
        }
    }

    private class StubClient implements SignalingClient {

        private final String profileId;
        private final Publisher<Signal> signalPublisher = new ConcurrentDequePublisher<>();
        private final Publisher<Throwable> errorPublisher = new ConcurrentDequePublisher<>();
        private final List<Signal> backlog = new ArrayList<>();

        StubClient(final String profileId) {
            this.profileId = profileId;
        }

        void deliver(final Signal signal) {
            synchronized (backlog) {
                backlog.add(signal);
            }
            signalPublisher.publish(signal);
        }

        @Override
        public Version getVersion() {
            return Version.V_1_0;
        }

        @Override
        public MatchState getState() {
            return new MatchState() {
                @Override public SignalingClientPhase getPhase() { return SignalingClientPhase.SIGNALING; }
                @Override public String getHost() { return hostProfileId; }
                @Override public String getMatchId() { return matchId; }
                @Override public String getProfileId() { return profileId; }
                @Override public List<String> getProfiles() { return allProfiles; }
            };
        }

        @Override
        public Stream<Signal> backlog() {
            synchronized (backlog) {
                return List.copyOf(backlog).stream();
            }
        }

        @Override
        public void signal(final Signal signal) {
            route(profileId, signal);
        }

        @Override
        public void control(final ControlMessage control) {
            // not needed for WebRTC peer tests
        }

        @Override
        public Optional<HandshakeResponse> findHandshakeResponse() {
            return Optional.empty();
        }

        @Override
        public void handshake(final HandshakeRequest request) {
            // not needed for WebRTC peer tests
        }

        @Override
        public Subscription onHandshake(final BiConsumer<Subscription, HandshakeResponse> listener) {
            return Subscription.begin();
        }

        @Override
        public Subscription onSignal(final BiConsumer<Subscription, Signal> listener) {
            return signalPublisher.subscribe(listener);
        }

        @Override
        public Subscription onClientError(final BiConsumer<Subscription, Throwable> listener) {
            return errorPublisher.subscribe(listener);
        }

        @Override
        public Optional<DisconnectStatus> waitForDisconnect(final long time, final TimeUnit units) {
            return Optional.empty();
        }

        @Override
        public void close() {
            signalPublisher.clear();
            errorPublisher.clear();
        }

    }

}