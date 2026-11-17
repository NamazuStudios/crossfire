package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.api.HostOrchestrationStrategy;
import dev.getelements.elements.crossfire.api.model.ProtocolMessage;
import dev.getelements.elements.crossfire.api.model.error.DuplicateConnectionException;
import dev.getelements.elements.crossfire.api.model.error.MessageBufferOverrunException;
import dev.getelements.elements.crossfire.api.model.error.UnexpectedMessageException;
import dev.getelements.elements.crossfire.api.model.signal.*;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.Monitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class MemoryMatchState {

    private static final Logger logger = LoggerFactory.getLogger(MemoryMatchState.class);

    private MemoryMatchBacklog.SessionState host;

    private final MemoryMatchBacklog memoryMatchBacklog;

    private final Parameters parameters;

    public MemoryMatchState(final Parameters parameters) {
        this.parameters = parameters;
        this.memoryMatchBacklog = new MemoryMatchBacklog();
    }

    public void send(final DirectSignal signal) {
        switch (signal.getLifecycle()) {
            case ONCE -> memoryMatchBacklog.publish(signal);
            case SESSION, MATCH -> memoryMatchBacklog.publishAndPersist(signal);
            default -> throw new IllegalArgumentException("Unexpected value: " + signal.getLifecycle());
        }
    }

    public void send(final BroadcastSignal signal) {
        switch (signal.getLifecycle()) {
            case ONCE -> memoryMatchBacklog.publish(signal);
            case SESSION, MATCH -> memoryMatchBacklog.publishAndPersist(signal);
            default -> throw new IllegalArgumentException("Unexpected value: " + signal.getLifecycle());
        }
    }

    public Parameters getParameters() {
        return parameters;
    }

    public void error(final Throwable th) {
        memoryMatchBacklog.error(th);
    }

    /**
     * Re-runs host selection for this match against the currently connected participants, using
     * the configured strategy. A strategy may decline (return empty) to leave the current host.
     */
    public void assignHost() {
        memoryMatchBacklog.assignHost();
    }

    /**
     * Explicitly designates the given profile as host, provided it has joined the match.
     */
    public void assignHost(final String profileId) {
        memoryMatchBacklog.assignHost(profileId);
    }

    public boolean join(final String profileId) {
        return memoryMatchBacklog.join(profileId);
    }

    public boolean leave(final String profileId) {
        return memoryMatchBacklog.leave(profileId);
    }

    public Subscription connect(
            final String profileId,
            final Consumer<ProtocolMessage> onMessage,
            final Consumer<Throwable> onError) {
        return memoryMatchBacklog.connect(
                profileId,
                onMessage,
                onError
        );
    }

    private class MemoryMatchBacklog {

        private final Lock read;

        private final Lock write;

        private final Map<String, SessionState> sessionStates = new TreeMap<>();

        private final BoundedList.Builder<Signal> backlogListBuilder = new BoundedList.Builder<>();

        public MemoryMatchBacklog() {
            final var lock = new ReentrantReadWriteLock();
            this.read = lock.readLock();
            this.write = lock.writeLock();
            backlogListBuilder
                    .maxSize(parameters.matchBacklogSize())
                    .sizeSupplier(this::size)
                    .exceptionSupplier(MessageBufferOverrunException::new);
        }

        private int size() {
            return sessionStates
                    .values()
                    .stream()
                    .mapToInt(SessionState::size)
                    .sum();
        }

        public void publish(final DirectSignal signal) {
            try (var mon = Monitor.enter(read)) {

                final var recipientState = sessionStates.get(signal.getRecipientProfileId());

                if (recipientState == null) {
                    logger.warn("Dropping ONCE direct signal: recipient {} has not joined match.",
                            signal.getRecipientProfileId());
                } else if (recipientState.getSubscriptionRecord() == null) {
                    logger.warn("Dropping ONCE direct signal: recipient {} joined but is not currently "
                            + "connected (no active subscription). Sender {}, match {}.",
                            signal.getRecipientProfileId(), signal.getProfileId(), parameters.matchId());
                } else {
                    recipientState.getSubscriptionRecord().onMessage(signal);
                }

            }
        }

        public void publish(final BroadcastSignal signal) {
            try (var mon = Monitor.enter(read)) {
                doPublish(signal);
            }
        }

        private void doPublish(final BroadcastSignal signal) {
            sessionStates
                    .values()
                    .stream()
                    .filter(s -> signal.isFor(s.getProfileId()))
                    .map(SessionState::getSubscriptionRecord)
                    .filter(Objects::nonNull)
                    .forEach(subscription -> subscription.onMessage(signal));
        }

        public void publishAndPersist(final DirectSignal signal) {
            try (var mon = Monitor.enter(write)) {

                final var backlog = sessionStates.computeIfAbsent(signal.getProfileId(), SessionState::new);
                backlog.append(signal);

                final var recipientState = sessionStates.get(signal.getRecipientProfileId());

                if (recipientState == null) {
                    // Persisted in the sender's outbox; the recipient will receive it on connect.
                    logger.debug("Recipient {} not joined for SIGNAL/MATCH direct signal; deferred to connect.",
                            signal.getRecipientProfileId());
                } else {
                    Optional.ofNullable(recipientState.getSubscriptionRecord())
                            .ifPresent(subscriptionRecord -> subscriptionRecord.onMessage(signal));
                }

            }
        }

        public void publishAndPersist(final BroadcastSignal signal) {
            try (var mon = Monitor.enter(write)) {
                final var backlog = sessionStates.computeIfAbsent(signal.getProfileId(), SessionState::new);
                backlog.append(signal);
                doPublish(signal);
            }
        }

        public void error(final Throwable th) {
            try (var mon = Monitor.enter(read)) {
                sessionStates
                        .values()
                        .stream()
                        .map(SessionState::getSubscriptionRecord)
                        .filter(Objects::nonNull)
                        .forEach(subscription -> subscription.onError(th));
            }
        }

        public boolean join(final String profileId) {

            requireNonNull(profileId, "profileId cannot be null");

            try (final var mon = Monitor.enter(write)) {

                var state = sessionStates.get(profileId);

                if (state == null) {
                    state = new SessionState(profileId);
                    sessionStates.put(profileId, state);
                    return true;
                } else {
                    return false;
                }

            }

        }

        public Subscription connect(
                final String profileId,
                final Consumer<ProtocolMessage> onMessage,
                final Consumer<Throwable> onError) {

            requireNonNull(onError, "onError cannot be null");
            requireNonNull(onMessage, "onMessage cannot be null");
            requireNonNull(profileId, "profileId cannot be null");

            try (var mon = Monitor.enter(write)) {

                final var state = sessionStates.computeIfAbsent(profileId, SessionState::new);

                // We have to call this first here because it may clear out the existing session's backlog which would
                // immediately eliminate the connection message from the backlog. Since this is happening in the
                // exclusive lock, other clients trying to connect will be blocked until this is done so they will
                // either receive the old connection's backlog or the new connection's backlog but not a mix of both.

                state.disconnectExistingIfNecessary();

                final var connect = new ConnectBroadcastSignal();
                connect.setProfileId(profileId);
                state.append(connect);
                doPublish(connect);

                // This happens next in case the new subscription is the host. This ensures that the host signal will
                // be put into the queue as well when the call to "host" is made below.

                if (host == null) {

                    // The connecting profile is the first candidate even though its subscription does
                    // not exist yet (subscribe happens below, after the backlog replay). Preserves
                    // the old "first to connect becomes host" behavior exactly — when host is null
                    // no other participant is currently subscribed.
                    final var connected = new LinkedHashSet<String>();
                    connected.add(profileId);
                    sessionStates.entrySet()
                            .stream()
                            .filter(e -> !e.getKey().equals(profileId)
                                    && e.getValue().getSubscriptionRecord() != null)
                            .map(Map.Entry::getKey)
                            .forEach(connected::add);

                    parameters.hostOrchestrationStrategy()
                            .selectHost(parameters.matchId(), connected, Optional.empty())
                            .map(sessionStates::get)
                            .ifPresent(s -> {
                                s.host();
                                host = s;
                            });

                }

                // Process the full backlog for this profile which includes the connection message we just added above
                // as well as the join message that was added when the profile first joined the match in this type's
                // constructor.

                final var backlog = sessionStates
                        .values()
                        .stream()
                        .flatMap(SessionState::stream)
                        .filter(s -> s.isFor(profileId))
                        .toList();

                backlog.forEach(onMessage);

                return state.subscribe(onMessage, onError);

            }

        }

        public boolean leave(final String profileId) {

            requireNonNull(profileId, "profileId cannot be null");

            try (var mon = Monitor.enter(write)) {
                if (sessionStates.remove(profileId) != null) {

                    if (sessionStates.isEmpty()) {
                        getParameters().onAllParticipantsLeft().accept(MemoryMatchState.this);
                    }

                    return true;

                } else {
                    return false;
                }
            }

        }

        public void assignHost() {
            try (var mon = Monitor.enter(write)) {

                final var connected = sessionStates.entrySet()
                        .stream()
                        .filter(e -> e.getValue().getSubscriptionRecord() != null)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                parameters.hostOrchestrationStrategy()
                        .selectHost(parameters.matchId(), connected,
                                Optional.ofNullable(host).map(SessionState::getProfileId))
                        .map(sessionStates::get)
                        .ifPresent(s -> {
                            s.host();
                            host = s;
                        });

            }
        }

        public void assignHost(final String profileId) {
            try (var mon = Monitor.enter(write)) {
                final var state = sessionStates.get(profileId);
                if (state != null) {
                    state.host();
                    host = state;
                }
            }
        }

        /**
         * Tracks all outgoing messages for a given profileId as well as the single active subscription. Depending on
         * the message's lifecycle, messages are stored in different outboxes. The outboxes are bounded lists that
         * ensure a single Match cannot consume memory indefinitely.
         **/
        private class SessionState {

            private final String profileId;

            private final List<Signal> match = backlogListBuilder.build();

            private final List<Signal> session = backlogListBuilder.build();

            private final AtomicReference<SubscriptionRecord> subscription = new AtomicReference<>();

            private SessionState(final String profileId) {
                this.profileId = requireNonNull(profileId, "profileId cannot be null");

                final var join = new JoinBroadcastSignal();
                join.setProfileId(profileId);
                append(join);
                doPublish(join);

            }

            public String getProfileId() {
                return profileId;
            }

            public int size() {
                return match.size() + session.size();
            }

            public Stream<Signal> stream() {
                return Stream.concat(match.stream(), session.stream());
            }

            public void append(final DirectSignal signal) {

                if (!profileId.equals(signal.getProfileId())) {
                    throw new UnexpectedMessageException("Outbound Direct Message Mismatch "
                            + profileId
                            + "(this queue) != "
                            + signal.getProfileId()
                            + "(signal sender)."
                    );
                } else if (profileId.equals(signal.getRecipientProfileId())) {
                    throw new UnexpectedMessageException("Cannot mirror messages. "
                            + signal.getRecipientProfileId() + " "
                            + "is attempting to send a signal to themselves."
                    );
                }

                switch(signal.getLifecycle()) {
                    case MATCH -> match.add(signal);
                    case SESSION -> session.add(signal);
                    default -> throw new IllegalArgumentException("Unexpected value: " + signal.getLifecycle());
                }

            }

            public void append(final BroadcastSignal signal) {

                if (!profileId.equals(signal.getProfileId())) {
                    throw new UnexpectedMessageException("Outbound Direct Message Mismatch "
                            + profileId + " (this queue) != "
                            + signal.getProfileId() + " (signal sender)."
                    );
                }

                switch(signal.getLifecycle()) {
                    case MATCH -> match.add(signal);
                    case SESSION -> session.add(signal);
                    default -> throw new IllegalArgumentException("Unexpected lifecycle value: " + signal.getLifecycle());
                }

            }

            public void host() {

                // Generates and buffers the signal in the appropriate outbox
                final var signal = new HostBroadcastSignal();
                signal.setProfileId(profileId);
                append(signal);

                // Informs the whole match that there is indeed a new host.
                doPublish(signal);

            }

            public SubscriptionRecord getSubscriptionRecord() {
                return subscription.get();
            }

            public Subscription subscribe(
                    final Consumer<ProtocolMessage> onMessage,
                    final Consumer<Throwable> onError) {

                requireNonNull(onError, "onError cannot be null");
                requireNonNull(onMessage, "onMessage cannot be null");

                final var updated = subscription.updateAndGet(existing -> {
                    if (existing == null)
                        return new SubscriptionRecord(onMessage, onError, this::disconnectCleanly);
                    else
                        throw new IllegalStateException("Subscription already exists. Did you forget to disconnect the old subscription first?");
                });

                return updated.subscription();

            }

            /**
             * Cleanly disconnects the current subscription and clears it from this session state. Clean disconnects
             * do not drive any sort of error of the existing subscription because we assume that the client is
             * explicitly disconnecting.
             */
            private void disconnectCleanly() {

                // This is protected by the outer write lock because we essentially call it directly from the
                // calling code in MemoryMatchBacklog.connect() method as the subscription's onUnsubscribe handler.

                try (var mon = Monitor.enter(write)) {

                    session.clear();

                    final var existing = subscription.getAndSet(null);

                    if (existing == null) {
                        logger.debug("No existing subscription to disconnect for profileId {}.", profileId);
                    } else {

                        final var hasConnections = sessionStates.values()
                                .stream()
                                .anyMatch(s -> s.subscription.get() != null);

                        if (hasConnections) {
                            reassignHostIfNecessary();
                        } else {
                            parameters.onAllParticipantsDisconnected()
                                    .accept(MemoryMatchState.this);
                        }

                    }

                }

            }

            /**
             * Forcibly disconnects the existing subscription if one exists. This is used in scenarios where a new
             * connection for the same profileId is being established and we want to ensure that only one active
             * subscription exists at a time. This method drives an error to the existing subscription's onError
             * handler resets it for a new subscription to be established.
             */
            private void disconnectExistingIfNecessary() {

                final var existing = subscription.getAndSet(null);

                if (existing != null)
                    existing.onError(new DuplicateConnectionException());

            }

            /**
             * Re-assigns the host if the current host is this session state. This is called whenever a session state
             * is removed or its subscription is disconnected.
             */
            private void reassignHostIfNecessary() {

                if (host == this) {

                    // sessionStates iterates in profile-id order; the default strategy promotes the
                    // first still-connected remaining participant, exactly as the old hardcode did.
                    final var connected = sessionStates.entrySet()
                            .stream()
                            .filter(e -> e.getValue() != this && e.getValue().getSubscriptionRecord() != null)
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toCollection(LinkedHashSet::new));

                    host = parameters.hostOrchestrationStrategy()
                            .selectHost(parameters.matchId(), connected, Optional.of(getProfileId()))
                            .map(sessionStates::get)
                            .orElse(null);

                    if (host != null)
                        host.host();

                }

            }

        }

    }

    public record Parameters(
            String matchId,
            int matchBacklogSize,
            HostOrchestrationStrategy hostOrchestrationStrategy,
            Consumer<MemoryMatchState> onAllParticipantsLeft,
            Consumer<MemoryMatchState> onAllParticipantsDisconnected) {}

}
