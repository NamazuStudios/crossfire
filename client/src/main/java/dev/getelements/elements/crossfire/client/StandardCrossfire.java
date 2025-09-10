package dev.getelements.elements.crossfire.client;

import dev.getelements.elements.crossfire.client.signaling.SignalingMatchClient;
import dev.getelements.elements.crossfire.client.signaling.SignalingMatchHost;
import dev.getelements.elements.crossfire.client.v10.V10SignalingClient;
import dev.getelements.elements.crossfire.client.webrtc.WebRTCMatchClient;
import dev.getelements.elements.crossfire.client.webrtc.WebRTCMatchHost;
import dev.getelements.elements.crossfire.model.Protocol;
import dev.getelements.elements.crossfire.model.error.ProtocolError;
import dev.getelements.elements.crossfire.model.signal.HostBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.SimpleLazyValue;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static dev.getelements.elements.crossfire.model.Protocol.WEBRTC;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

public class StandardCrossfire implements Crossfire {

    private static final Logger logger = LoggerFactory.getLogger(StandardCrossfire.class);

    private final Protocol defaultProtocol;

    private final Set<Mode> supportedModes;

    private final Subscription subscription;

    private final SignalingClient signaling;

    private final Supplier<WebRTCMatchHost.Builder> webrtcHostBuilder;

    private final Supplier<WebRTCMatchClient.Builder> webrtcClientBuilder;

    private final AtomicReference<State> state = new AtomicReference<>(State.create());

    public StandardCrossfire(
            final Protocol defaultProtocol,
            final Set<Mode> supportedModes,
            final SignalingClient signaling,
            final Supplier<WebRTCMatchHost.Builder> webrtcHostBuilder,
            final Supplier<WebRTCMatchClient.Builder> webrtcClientBuilder) {

        this.webrtcHostBuilder = requireNonNull(webrtcHostBuilder);
        this.webrtcClientBuilder = requireNonNull(webrtcClientBuilder);
        this.defaultProtocol = requireNonNull(defaultProtocol, "defaultProtocol");
        this.supportedModes = Collections.unmodifiableSet(EnumSet.copyOf(supportedModes));
        this.signaling = requireNonNull(signaling, "signaling");

        if (supportedModes.stream().anyMatch(m -> getMode().getProtocol().equals(defaultProtocol))) {
            this.subscription = Subscription.begin()
                    .chain(signaling.onSignal(this::onSignal))
                    .chain(signaling.onClientError(this::onClientError));
        } else {
            throw new IllegalArgumentException("defaultProtocol must be supported by at least one mode");
        }

    }

    private void onSignal(final Subscription subscription, final Signal signal) {
        switch (signal.getType()) {
            case HOST -> onHost(subscription, (HostBroadcastSignal) signal);
            case ERROR -> onProtocolError(subscription, (ProtocolError) signal);
        }
    }

    private void onHost(final Subscription subscription,
                        final HostBroadcastSignal signal) {

        final var signalState = signaling.getState();

        final var allModes = getSupportedModes()
                .stream()
                .filter(m -> m.isHost() == signalState.isHost())
                .collect(toCollection(() -> EnumSet.noneOf(Mode.class)));

        final var defaultMode = allModes
                .stream()
                .filter(m -> defaultProtocol.equals(m.getProtocol()))
                .findFirst()
                .orElse(null);

        if (allModes.isEmpty() || defaultMode == null) {
            clearClientState();
        } else if (defaultMode.isHost()) {
            setHostState(defaultMode, allModes);
        } else {
            setClientState(defaultMode, allModes);
        }

    }

    private void clearClientState() {
        final var old = state.getAndUpdate(State::clear);
        old.hosts().values().forEach(MatchHost::close);
        old.clients().values().forEach(MatchClient::close);
    }

    private void setHostState(final Mode defaultMode,
                              final EnumSet<Mode> allModes) {

        State old;
        State update;

        final var hosts = new SimpleLazyValue<>(() -> {

            final var result = new EnumMap<Protocol, MatchHost>(Protocol.class);

            allModes.forEach(m -> {
                switch(m) {
                    case WEBRTC_HOST -> result.put(m.getProtocol(), webrtcHostBuilder.get()
                            .withSignalingClient(signaling)
                            .build()
                    );
                    case SIGNALING_HOST -> result.put(m.getProtocol(), new SignalingMatchHost(signaling));
                }
            });

            return result;

        });

        do {
            old  = state.get();
        } while (state.compareAndSet(old, update = old.host(defaultMode, hosts.get())));

        // We always clean up the old state, because if the update failed as we are taking responsibility for the
        // update as it happened.

        old.hosts().values().forEach(MatchHost::close);
        old.clients().values().forEach(MatchClient::close);

        // This should never happen, but we add a failsafe here anyhow. In case somebody tries to update the host
        // state concurrently and the update fails, we dispose of the newly created hosts that were not added to the
        // state.

        final var updated = update.hosts();

        hosts.getOptional()
            .filter(h -> h != updated)
            .ifPresent(h -> h.values().forEach(MatchHost::close));

    }

    private void setClientState(final Mode defaultMode,
                                final EnumSet<Mode> allModes) {

        State old;
        State update;

        final var clients = new SimpleLazyValue<>(() -> {

            final var result = new EnumMap<Protocol, MatchClient>(Protocol.class);

            allModes.forEach(m -> {
                switch(m) {
                    case WEBRTC_CLIENT -> result.put(m.getProtocol(), webrtcClientBuilder.get()
                            .withSignalingClient(signaling)
                            .build()
                    );
                    case SIGNALING_CLIENT -> result.put(m.getProtocol(), new SignalingMatchClient(signaling));
                }
            });

            return result;

        });

        do {
            old  = state.get();
            update = old.client(defaultMode, clients.get());
        } while (state.compareAndSet(old, update));

        // We always clean up the old state, because if the update failed as we are taking responsibility for the
        // update as it happened.

        old.hosts().values().forEach(MatchHost::close);
        old.clients().values().forEach(MatchClient::close);

        // This should never happen, but we add a failsafe here anyhow. In case somebody tries to update the host
        // state concurrently and the update fails, we dispose of the newly created hosts that were not added to the
        // state.

        final var updated = update.clients();

        clients.getOptional()
                .filter(h -> h != updated)
                .ifPresent(h -> h.values().forEach(MatchClient::close));

    }

    private void onProtocolError(final Subscription subscription,
                                 final ProtocolError signal) {
        logger.error("Protocol error: {} - {}", signal.getCode(), signal.getMessage());
        close();
    }

    private void onClientError(final Subscription subscription,
                               final Throwable throwable) {
        logger.error("Client error: {}", throwable.getMessage(), throwable);
        close();
    }

    @Override
    public Mode getMode() {
        return state.get().defaultMode();
    }

    @Override
    public Set<Mode> getSupportedModes() {
        return supportedModes;
    }

    @Override
    public void connect(final URI uri) {

        final var container = ContainerProvider.getWebSocketContainer();

        try {
            container.connectToServer(getSignalingClient(), uri);
        } catch (IOException | DeploymentException e) {
            throw new SignalingClientException(e);
        }

    }

    @Override
    public SignalingClient getSignalingClient() {
        return null;
    }

    @Override
    public Optional<MatchHost> findMatchHost() {
        final var state = this.state.get();
        return state.findMatchHost();
    }

    @Override
    public Optional<MatchClient> findMatchClient() {
        final var state = this.state.get();
        return state.findMatchClient();
    }

    @Override
    public Optional<MatchHost> findMatchHost(final Protocol protocol) {
        final var state = this.state.get();
        return state.findMatchHost(protocol);
    }

    @Override
    public Optional<MatchClient> findMatchClient(final Protocol protocol) {
        final var state = this.state.get();
        return state.findMatchClient(protocol);
    }

    @Override
    public void close() {

        final var old = state.getAndUpdate(State::terminate);

        if (old.open()) {
            subscription.unsubscribe();
        }

    }

    record State(boolean open,
                 Mode defaultMode,
                 Map<Protocol, MatchHost> hosts,
                 Map<Protocol, MatchClient> clients) {

        public static State create() {
            return new State(true, null, Map.of(), Map.of());
        }

        public State clear() {
            return open() ? new State(true, null, Map.of(), Map.of()) : this;
        }

        public State host(final Mode mode, final Map<Protocol, MatchHost> hosts) {

            if (!mode.isHost())
                throw new IllegalArgumentException(mode + " is not a host mode.");

            return open() ? new State(true, mode, hosts, Map.of()) : this;

        }

        public State client(final Mode mode, final Map<Protocol, MatchClient> clients) {

            if (mode.isHost())
                throw new IllegalArgumentException(mode + " is not a client mode.");

            return open() ? new State(true, mode, Map.of(), clients) : this;

        }

        public State terminate() {
            return open() ? new State(false, null, hosts(), clients()) : this;
        }

        public Optional<MatchHost> findMatchHost() {
            return findMatchHost(defaultMode().getProtocol());
        }

        public Optional<MatchClient> findMatchClient() {
            return findMatchClient(defaultMode().getProtocol());
        }

        public Optional<MatchClient> findMatchClient(final Protocol protocol) {
            return Optional.ofNullable(clients().get(protocol));
        }

        public Optional<MatchHost> findMatchHost(final Protocol protocol) {
            return Optional.ofNullable(hosts().get(protocol));
        }

    }

    /**
     * Builder class for creating instances of {@link StandardCrossfire}.
     */
    public static class Builder {

        /** The default protocol used for communication. */
        private Protocol defaultProtocol = WEBRTC;

        /** The set of supported modes for the crossfire instance. */
        private Set<Mode> supportedModes = EnumSet.allOf(Mode.class);

        /** A supplier used to provide the signaling client */
        private Supplier<SignalingClient> signalingClientSupplier = V10SignalingClient::new;

        /** A supplier for creating WebRTC match host builders. */
        private Supplier<WebRTCMatchHost.Builder> webrtcHostBuilder = WebRTCMatchHost.Builder::new;

        /** A supplier for creating WebRTC match client builders. */
        private Supplier<WebRTCMatchClient.Builder> webrtcClientBuilder = WebRTCMatchClient.Builder::new;

        /**
         * Sets the default protocol.
         *
         * @param defaultProtocol the default protocol to use
         * @return the current Builder instance
         */
        public Builder withDefaultProtocol(final Protocol defaultProtocol) {
            this.defaultProtocol = defaultProtocol;
            return this;
        }

        /**
         * Sets the supported modes.
         *
         * @param supportedModes the set of supported modes
         * @return the current Builder instance
         */
        public Builder withSupportedModes(final Set<Mode> supportedModes) {
            this.supportedModes = supportedModes;
            return this;
        }

        /**
         * Sets the signaling client.
         *
         * @param signaling the signaling client
         * @return the current Builder instance
         */
        public Builder withSignalingClientSupplier(final SignalingClient signaling) {
            this.signalingClientSupplier = () -> signaling;
            return this;
        }

        /**
         * Sets the WebRTC match host builder supplier.
         *
         * @param webrtcHostBuilder the supplier for WebRTC match host builders
         * @return the current Builder instance
         */
        public Builder withWebRTCHostBuilder(final Supplier<WebRTCMatchHost.Builder> webrtcHostBuilder) {
            this.webrtcHostBuilder = webrtcHostBuilder;
            return this;
        }

        /**
         * Sets the WebRTC match client builder supplier.
         *
         * @param webrtcClientBuilder the supplier for WebRTC match client builders
         * @return the current Builder instance
         */
        public Builder withWebRTCClientBuilder(final Supplier<WebRTCMatchClient.Builder> webrtcClientBuilder) {
            this.webrtcClientBuilder = webrtcClientBuilder;
            return this;
        }

        /**
         * Builds and returns a new {@link StandardCrossfire} instance.
         *
         * @return a new StandardCrossfire instance
         * @throws IllegalArgumentException if any required field is null
         */
        public StandardCrossfire build() {

            if (signalingClientSupplier == null) {
                throw new IllegalStateException("No signaling client set.");
            }

            return new StandardCrossfire(
                    defaultProtocol,
                    supportedModes,
                    signalingClientSupplier.get(),
                    webrtcHostBuilder,
                    webrtcClientBuilder
            );

        }
    }

}
