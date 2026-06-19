package dev.getelements.elements.crossfire.client;

import dev.getelements.elements.crossfire.api.model.Protocol;
import dev.getelements.elements.crossfire.api.model.error.ProtocolError;
import dev.getelements.elements.crossfire.api.model.signal.HostBroadcastSignal;
import dev.getelements.elements.crossfire.api.model.signal.Signal;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.ConcurrentDequePublisher;
import dev.getelements.elements.sdk.util.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

/**
 * Base class implementing the {@link Crossfire} state machine: signal routing, host/client
 * lifecycle, CAS state transitions, and publisher wiring. Subclasses supply only:
 * <ul>
 *   <li>{@link #connect(URI)} — transport-specific connection (WebSocket, browser, etc.)</li>
 *   <li>{@link #populateHosts} — create the set of {@link MatchHost} impls for a given set of modes</li>
 *   <li>{@link #populateClients} — create the set of {@link MatchClient} impls for a given set of modes</li>
 * </ul>
 */
public abstract class AbstractCrossfire implements Crossfire {

    private static final Logger logger = LoggerFactory.getLogger(AbstractCrossfire.class);

    private final Protocol defaultProtocol;

    private final Set<Mode> supportedModes;

    private final SignalingClient signaling;

    private final Supplier<URI> defaultUriSupplier;

    private final Subscription subscription;

    private final AtomicReference<State> state = new AtomicReference<>(State.create());

    private final Publisher<OpenStatus<MatchHost>> onHostOpenStatus = new ConcurrentDequePublisher<>();

    private final Publisher<OpenStatus<MatchClient>> onClientOpenStatus = new ConcurrentDequePublisher<>();

    protected AbstractCrossfire(
            final Protocol defaultProtocol,
            final Set<Mode> supportedModes,
            final SignalingClient signaling,
            final Supplier<URI> defaultUriSupplier) {

        this.defaultProtocol = requireNonNull(defaultProtocol, "defaultProtocol");
        this.supportedModes = Collections.unmodifiableSet(EnumSet.copyOf(supportedModes));
        this.signaling = requireNonNull(signaling, "signaling");
        this.defaultUriSupplier = requireNonNull(defaultUriSupplier, "defaultUriSupplier");

        if (supportedModes.stream().map(Mode::getProtocol).noneMatch(p -> p.equals(defaultProtocol))) {
            throw new IllegalArgumentException("defaultProtocol must be supported by at least one mode");
        }

        this.subscription = Subscription.begin()
                .chain(signaling.onSignal(this::onSignal))
                .chain(signaling.onClientError(this::onClientError));

    }

    /**
     * Opens the underlying transport connection to the server at the given URI.
     */
    @Override
    public abstract AbstractCrossfire connect(URI uri);

    /**
     * Populates {@code hosts} with one {@link MatchHost} per mode in {@code modes}. Called
     * inside the CAS loop when a HOST signal is received; implementations must not block.
     */
    protected abstract void populateHosts(Map<Protocol, MatchHost> hosts,
                                          EnumSet<Mode> modes,
                                          HostBroadcastSignal signal);

    /**
     * Populates {@code clients} with one {@link MatchClient} per mode in {@code modes}. Called
     * inside the CAS loop when a non-host HOST signal is received; implementations must not block.
     */
    protected abstract void populateClients(Map<Protocol, MatchClient> clients,
                                            EnumSet<Mode> modes,
                                            HostBroadcastSignal signal);

    // -------------------------------------------------------------------------
    // Crossfire interface — common implementations
    // -------------------------------------------------------------------------

    @Override
    public AbstractCrossfire connect() {
        return connect(defaultUriSupplier.get());
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
    public SignalingClient getSignalingClient() {
        return signaling;
    }

    @Override
    public Optional<MatchHost> findMatchHost() {
        return state.get().findMatchHost();
    }

    @Override
    public Optional<MatchClient> findMatchClient() {
        return state.get().findMatchClient();
    }

    @Override
    public Optional<MatchHost> findMatchHost(final Protocol protocol) {
        return state.get().findMatchHost(protocol);
    }

    @Override
    public Optional<MatchClient> findMatchClient(final Protocol protocol) {
        return state.get().findMatchClient(protocol);
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
            closeState(old);
        }

    }

    // -------------------------------------------------------------------------
    // Signal / state-machine internals
    // -------------------------------------------------------------------------

    private void onSignal(final Subscription subscription, final Signal signal) {
        switch (signal.getType()) {
            case HOST -> onHost((HostBroadcastSignal) signal);
            case ERROR -> onProtocolError((ProtocolError) signal);
        }
    }

    private void onHost(final HostBroadcastSignal signal) {

        final var signalState = signaling.getState();

        final var allModes = getSupportedModes()
                .stream()
                .filter(m -> m.isHost() == signalState.isHost())
                .collect(toCollection(() -> EnumSet.noneOf(Mode.class)));

        final var defaultMode = allModes.stream()
                .filter(m -> defaultProtocol.equals(m.getProtocol()))
                .findFirst()
                .orElse(null);

        if (allModes.isEmpty() || defaultMode == null) {
            clearState();
        } else if (defaultMode.isHost()) {
            setHostState(signal, defaultMode, allModes);
        } else {
            setClientState(signal, defaultMode, allModes);
        }

    }

    private void clearState() {
        final var old = state.getAndUpdate(State::clear);
        old.hosts().values().forEach(MatchHost::close);
        old.clients().values().forEach(MatchClient::close);
    }

    private void setHostState(final HostBroadcastSignal signal,
                               final Mode defaultMode,
                               final EnumSet<Mode> allModes) {

        final var hosts = new EnumMap<Protocol, MatchHost>(Protocol.class);
        populateHosts(hosts, allModes, signal);

        State old, update;
        do {
            old    = state.get();
            update = old.host(defaultMode, hosts);
        } while (!state.compareAndSet(old, update));

        closeState(old);

        if (hosts == update.hosts()) {
            hosts.values().forEach(host -> {
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

        final var clients = new EnumMap<Protocol, MatchClient>(Protocol.class);
        populateClients(clients, allModes, signal);

        State old, update;
        do {
            old    = state.get();
            update = old.client(defaultMode, clients);
        } while (!state.compareAndSet(old, update));

        closeState(old);

        if (clients == update.clients()) {
            clients.values().stream()
                    .peek(c -> onClientOpenStatus.publish(new OpenStatus<>(true, c)))
                    .forEach(MatchClient::connect);
        } else {
            clients.values().forEach(MatchClient::close);
        }

    }

    private void onProtocolError(final ProtocolError error) {
        logger.error("Protocol error: {} - {}", error.getCode(), error.getMessage());
        close();
    }

    private void onClientError(final Subscription subscription, final Throwable throwable) {
        logger.error("Client error: {}", throwable.getMessage(), throwable);
        close();
    }

    private void closeState(final State s) {

        s.hosts().values().forEach(h -> {
            onHostOpenStatus.publish(new OpenStatus<>(false, h));
            h.close();
        });

        s.clients().values().forEach(c -> {
            onClientOpenStatus.publish(new OpenStatus<>(false, c));
            c.close();
        });

    }

    // -------------------------------------------------------------------------
    // Immutable state record
    // -------------------------------------------------------------------------

    record State(boolean open,
                 Mode defaultMode,
                 Map<Protocol, MatchHost> hosts,
                 Map<Protocol, MatchClient> clients) {

        static State create() {
            return new State(true, null, Map.of(), Map.of());
        }

        State clear() {
            return open() ? new State(true, null, Map.of(), Map.of()) : this;
        }

        State host(final Mode mode, final Map<Protocol, MatchHost> hosts) {
            if (!mode.isHost()) throw new IllegalArgumentException(mode + " is not a host mode.");
            return open() ? new State(true, mode, hosts, Map.of()) : this;
        }

        State client(final Mode mode, final Map<Protocol, MatchClient> clients) {
            if (mode.isHost()) throw new IllegalArgumentException(mode + " is not a client mode.");
            return open() ? new State(true, mode, Map.of(), clients) : this;
        }

        State terminate() {
            return open() ? new State(false, null, hosts(), clients()) : this;
        }

        Optional<MatchHost> findMatchHost() {
            return defaultMode() != null ? findMatchHost(defaultMode().getProtocol()) : Optional.empty();
        }

        Optional<MatchClient> findMatchClient() {
            return defaultMode() != null ? findMatchClient(defaultMode().getProtocol()) : Optional.empty();
        }

        Optional<MatchClient> findMatchClient(final Protocol protocol) {
            return Optional.ofNullable(clients().get(protocol));
        }

        Optional<MatchHost> findMatchHost(final Protocol protocol) {
            return Optional.ofNullable(hosts().get(protocol));
        }

    }

}
