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
import dev.getelements.elements.sdk.util.ConcurrentDequePublisher;
import dev.getelements.elements.sdk.util.Publisher;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.WebSocketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
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

    private final WebSocketContainer webSocketContainer;

    private Supplier<URI> defaultUriSupplier;

    private final Supplier<WebRTCMatchHost.Builder> webrtcHostBuilder;

    private final Supplier<WebRTCMatchClient.Builder> webrtcClientBuilder;

    private final AtomicReference<State> state = new AtomicReference<>(State.create());

    private final Publisher<OpenStatus<MatchHost>> onHostOpenStatus = new ConcurrentDequePublisher<>();

    private final Publisher<OpenStatus<MatchClient>> onClientOpenStatus = new ConcurrentDequePublisher<>();

    public StandardCrossfire(
            final Protocol defaultProtocol,
            final Set<Mode> supportedModes,
            final SignalingClient signaling,
            final WebSocketContainer webSocketContainer,
            final Supplier<URI> defaultUriSupplier,
            final Supplier<WebRTCMatchHost.Builder> webrtcHostBuilder,
            final Supplier<WebRTCMatchClient.Builder> webrtcClientBuilder) {

        this.webrtcHostBuilder = requireNonNull(webrtcHostBuilder, "webrtcHostBuilder");
        this.webrtcClientBuilder = requireNonNull(webrtcClientBuilder, "webrtcClientBuilder");
        this.defaultProtocol = requireNonNull(defaultProtocol, "defaultProtocol");
        this.supportedModes = Collections.unmodifiableSet(EnumSet.copyOf(supportedModes));
        this.signaling = requireNonNull(signaling, "signaling");
        this.defaultUriSupplier = requireNonNull(defaultUriSupplier, "defaultUriSupplier");
        this.webSocketContainer = requireNonNull(webSocketContainer, "webSocketContainer");

        if (supportedModes.stream().map(Mode::getProtocol).anyMatch(p -> p.equals(defaultProtocol))) {
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
            setHostState(signal, defaultMode, allModes);
        } else {
            setClientState(signal, defaultMode, allModes);
        }

    }

    private void clearClientState() {
        final var old = state.getAndUpdate(State::clear);
        old.hosts().values().forEach(MatchHost::close);
        old.clients().values().forEach(MatchClient::close);
    }

    private void setHostState(final HostBroadcastSignal signal,
                              final Mode defaultMode,
                              final EnumSet<Mode> allModes) {

        State old;
        State update;

        final var hosts = new EnumMap<Protocol, MatchHost>(Protocol.class);

        allModes.forEach(m -> {
            switch(m) {
                case WEBRTC_HOST -> hosts.put(m.getProtocol(), webrtcHostBuilder.get()
                        .withSignalingClient(signaling)
                        .build()
                );
                case SIGNALING_HOST -> hosts.put(m.getProtocol(), new SignalingMatchHost(signaling));
            }
        });

        do {
            old  = state.get();
            update = old.host(defaultMode, hosts);
        } while (!state.compareAndSet(old, update));

        // We always clean up the old state, because if the update failed as we are taking responsibility for the
        // update as it happened.
        close(old);

        // This should never happen, but we add a failsafe here anyhow. In case somebody tries to update the host
        // state concurrently and the update fails, we dispose of the newly created hosts that were not added to the
        // state.

        if (hosts == update.hosts()) {
            hosts.values()
                 .forEach(host -> {
                     onHostOpenStatus.publish(new OpenStatus<>(true, host));
                     host.start();
                });
        } else {
            hosts.values().forEach(MatchHost::close);
        }

    }

    private void setClientState(final HostBroadcastSignal signal,
                                final Mode defaultMode,
                                final EnumSet<Mode> allModes) {

        State old;
        State update;

        final var clients = new EnumMap<Protocol, MatchClient>(Protocol.class);

        allModes.forEach(m -> {
            switch(m) {
                case WEBRTC_CLIENT -> clients.put(m.getProtocol(), webrtcClientBuilder.get()
                        .withRemoteProfileId(signal.getProfileId())
                        .withSignalingClient(signaling)
                        .build()
                );
                case SIGNALING_CLIENT -> clients.put(m.getProtocol(), new SignalingMatchClient(signaling));
            }
        });


        do {
            old  = state.get();
            update = old.client(defaultMode, clients);
        } while (!state.compareAndSet(old, update));

        // We always clean up the old state, because if the update failed as we are taking responsibility for the
        // update as it happened.
        close(old);

        // This should never happen, but we add a failsafe here anyhow. In case somebody tries to update the host
        // state concurrently and the update fails, we dispose of the newly created hosts that were not added to the
        // state.

        if (clients == update.clients()) {
            clients.values()
                    .stream()
                    .peek(c -> onClientOpenStatus.publish(new OpenStatus<>(true, c)))
                    .forEach(MatchClient::connect);
        } else {
            clients.values().forEach(MatchClient::close);
        }

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
    public StandardCrossfire connect() {
        final var uri = defaultUriSupplier.get();
        return connect(uri);
    }

    @Override
    public StandardCrossfire connect(final URI uri) {

        try {
            webSocketContainer.connectToServer(getSignalingClient(), uri);
        } catch (IOException | DeploymentException e) {
            throw new SignalingClientException(e);
        }

        return this;

    }

    @Override
    public SignalingClient getSignalingClient() {
        return signaling;
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
    public Subscription onHostOpenStatus(final BiConsumer<Subscription, OpenStatus<MatchHost>> onHostOpenStatus) {
        return this.onHostOpenStatus.subscribe(onHostOpenStatus);
    }

    @Override
    public Subscription onClientOpenStatus(final BiConsumer<Subscription, OpenStatus<MatchClient>> onClientOpenStatus) {
        return this.onClientOpenStatus.subscribe(onClientOpenStatus);
    }

    @Override
    public void close() {

        final var old = state.getAndUpdate(State::terminate);

        if (old.open()) {
            signaling.close();
            subscription.unsubscribe();
            close(old);
        }

    }

    private void close(final State state) {

        state.hosts()
                .values()
                .forEach(h -> {
                    onHostOpenStatus.publish(new OpenStatus<>(false, h));
                    h.close();
                });

        state.clients()
                .values()
                .forEach(c -> {
                    onClientOpenStatus.publish(new OpenStatus<>(false, c));
                    c.close();
                });

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

        private Supplier<URI> defaultUriSupplier = () -> {

            final var env = System.getenv(URI_ENV_VARIABLE);
            final var property = System.getProperty(URI_SYSTEM_PROPERTY, env);

            if (property == null) {
                throw new SignalingClientException("No default URI specified. Set the "
                        + URI_ENV_VARIABLE + " environment variable or the "
                        + URI_SYSTEM_PROPERTY + " system property."
                );
            }

            try {
                return new URI(property);
            } catch (URISyntaxException ex) {
                throw new SignalingClientException(ex);
            }

        };

        /** A supplier used to provide the WebSocket container. */
        private Supplier<WebSocketContainer> webSocketContainerSupplier;

        /** A supplier used to provide the signaling client */
        private Supplier<SignalingClient> signalingClientSupplier = V10SignalingClient::new;

        /** A supplier for creating WebRTC match host builders. */
        private Supplier<WebRTCMatchHost.Builder> webrtcHostBuilder = WebRTCMatchHost.Builder::new;

        /** A supplier for creating WebRTC match client builders. */
        private Supplier<WebRTCMatchClient.Builder> webrtcClientBuilder = WebRTCMatchClient.Builder::new;

        /**
         * Sets the default URI supplier.
         *
         * @param uri a URI to use as the default
         * @return the current Builder instance
         */
        public Builder withDefaultUri(final URI uri) {
            return withDefaultUriSupplier(() -> uri);
        }

        /**
         * Sets the WebSocket container.
         * @param webSocketContainer the websocket container to use
         * @return the websocket container
         */
        public Builder withWebSocketContainer(final WebSocketContainer webSocketContainer) {
            return withWebSocketContainerSupplier(() -> webSocketContainer);
        }

        /**
         * Sets the WebSocket container supplier.
         *
         * @param webSocketContainerSupplier a supplier that provides a WebSocket container
         * @return the current Builder instance
         */
        public Builder withWebSocketContainerSupplier(final Supplier<WebSocketContainer> webSocketContainerSupplier) {
            requireNonNull(webSocketContainerSupplier, "WebSocket container supplier must be specified.");
            this.webSocketContainerSupplier = webSocketContainerSupplier;
            return this;
        }

        /**
         * Sets the default URI supplier.
         *
         * @param defaultUriSupplier a supplier that provides the default URI
         * @return the current Builder instance
         */
        public Builder withDefaultUriSupplier(final Supplier<URI> defaultUriSupplier) {
            requireNonNull(defaultUriSupplier, "Default URI supplier must be specified.");
            this.defaultUriSupplier = defaultUriSupplier;
            return this;
        }

        /**
         * Sets the default protocol.
         *
         * @param defaultProtocol the default protocol to use
         * @return the current Builder instance
         */
        public Builder withDefaultProtocol(final Protocol defaultProtocol) {
            requireNonNull(defaultProtocol, "Default protocol must be specified.");
            this.defaultProtocol = defaultProtocol;
            return this;
        }

        /**
         * Sets the supported modes.
         *
         * @param supportedModes the set of supported modes
         * @return the current Builder instance
         */
        public Builder withSupportedModes(final Mode ... supportedModes) {
            return withSupportedModes(Set.of(supportedModes));
        }

        /**
         * Sets the supported modes.
         *
         * @param supportedModes the set of supported modes
         * @return the current Builder instance
         */
        public Builder withSupportedModes(final Set<Mode> supportedModes) {

            requireNonNull(supportedModes, "Supported modes must be specified.");

            if (supportedModes.isEmpty()) {
                throw new IllegalArgumentException("At least one supported mode must be specified.");
            }

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
            requireNonNull(signaling, "Signaling client must be specified.");
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
            requireNonNull(webrtcHostBuilder, "WebRTC host must be specified.");
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
            requireNonNull(webrtcClientBuilder, "WebRTC client must be specified.");
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

            final var websocketContainer = webSocketContainerSupplier == null
                    ? SharedWebSocketContainer.getInstance()
                    : webSocketContainerSupplier.get();

            return new StandardCrossfire(
                    defaultProtocol,
                    supportedModes,
                    signalingClientSupplier.get(),
                    websocketContainer,
                    defaultUriSupplier,
                    webrtcHostBuilder,
                    webrtcClientBuilder
            );

        }
    }

}
