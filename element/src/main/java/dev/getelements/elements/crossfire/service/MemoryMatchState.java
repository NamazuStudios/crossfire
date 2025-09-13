package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.error.DuplicateConnectionException;
import dev.getelements.elements.crossfire.model.error.MessageBufferOverrunException;
import dev.getelements.elements.crossfire.model.error.UnexpectedMessageException;
import dev.getelements.elements.crossfire.model.signal.*;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.Monitor;
import java8.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static dev.getelements.elements.crossfire.model.signal.SignalLifecycle.SESSION;
import static java.util.Objects.requireNonNull;

public class MemoryMatchState {

    private static final Logger logger = LoggerFactory.getLogger(MemoryMatchState.class);

    private MemoryMatchBacklog.SessionState host;

    private final MemoryMatchBacklog memoryMatchBacklog;

    public MemoryMatchState(final int maxBacklogSize) {
        this(maxBacklogSize, new ReentrantReadWriteLock());
    }

    public MemoryMatchState(final int maxBacklogSize, final ReadWriteLock readWriteLock) {
        this(maxBacklogSize, readWriteLock.readLock(), readWriteLock.writeLock());
    }

    public MemoryMatchState(final int maxBacklogSize,
                            final Lock read,
                            final Lock write) {
        memoryMatchBacklog = new MemoryMatchBacklog(maxBacklogSize, read, write);
    }

    public void send(final DirectSignal signal) {
        switch (signal.getLifecycle()) {
            case ONCE -> memoryMatchBacklog.publish(signal);
            case SESSION, MATCH -> memoryMatchBacklog.publishAndPersist(signal);
            default -> throw new IllegalArgumentException("Unexpected value: " + signal.getLifecycle());
        }
    }

    public void send(
            final Stream<String> profileIds,
            final BroadcastSignal signal) {
        switch (signal.getLifecycle()) {
            case ONCE -> memoryMatchBacklog.publish(signal);
            case SESSION, MATCH -> memoryMatchBacklog.publishAndPersist(profileIds, signal);
            default -> throw new IllegalArgumentException("Unexpected value: " + signal.getLifecycle());
        }
    }

    public void error(final Throwable th) {
        memoryMatchBacklog.error(th);
    }

    public Subscription join(
            final String profileId,
            final Consumer<ProtocolMessage> onMessage,
            final Consumer<Throwable> onError) {
        return memoryMatchBacklog.join(
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

        public MemoryMatchBacklog(final int maxBacklogSize,
                                  final Lock read,
                                  final Lock write) {
            this.read = read;
            this.write = write;
            backlogListBuilder
                    .maxSize(maxBacklogSize)
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
                Optional.ofNullable(sessionStates.get(signal.getRecipientProfileId()))
                        .map(SessionState::getSubscriptionRecord)
                        .ifPresent(subscriptionRecord -> subscriptionRecord.onMessage(signal));
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
                    .filter(s -> !s.getProfileId().equals(signal.getProfileId()))
                    .map(SessionState::getSubscriptionRecord)
                    .filter(Objects::nonNull)
                    .forEach(subscription -> subscription.onMessage(signal));
        }

        public void publishAndPersist(final DirectSignal signal) {
            try (var mon = Monitor.enter(write)) {

                final var backlog = sessionStates.computeIfAbsent(signal.getProfileId(), SessionState::new);
                backlog.append(signal);

                Optional.ofNullable(sessionStates.get(signal.getRecipientProfileId()))
                        .map(SessionState::getSubscriptionRecord)
                        .ifPresent(subscriptionRecord -> subscriptionRecord.onMessage(signal));

            }
        }

        public void publishAndPersist(final Stream<String> profileIds, final BroadcastSignal signal) {
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

        public Subscription join(
                final String profileId,
                final Consumer<ProtocolMessage> onMessage,
                final Consumer<Throwable> onError) {

            try (var mon = Monitor.enter(write)) {

                requireNonNull(onError, "onError cannot be null");
                requireNonNull(onMessage, "onMessage cannot be null");
                requireNonNull(profileId, "profileId cannot be null");

                final var state = sessionStates.computeIfAbsent(profileId, SessionState::new);

                if (host == null) {
                    state.host();
                    host = state;
                }

                // Process the full backlog for this profile
                final var backlog = sessionStates
                        .values()
                        .stream()
                        .flatMap(SessionState::stream)
                        .filter(s -> switch (s.getType().getCategory()) {
                            case SIGNALING -> true;
                            case SIGNALING_DIRECT -> ((DirectSignal) s).getRecipientProfileId().equals(profileId);
                            default -> false;
                        })
                        .toList();

                backlog.forEach(onMessage);

                return state.subscribe(onMessage, onError);

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
                final var connect = new ConnectBroadcastSignal();
                connect.setProfileId(profileId);
                append(connect);
                doPublish(connect);
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
                signal.setLifecycle(SESSION);
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

                final var updated = new SubscriptionRecord(onMessage, onError, this::unsubscribe);
                final var existing = subscription.getAndSet(updated);

                if (existing != null)
                    existing.onError(new DuplicateConnectionException());

                return updated.subscription();

            }

            private void unsubscribe() {
                try (var mon = Monitor.enter(write)) {

                    session.clear();
                    subscription.set(null);

                    if (host == this)
                        host = MemoryMatchBacklog.this.sessionStates
                                .values()
                                .stream()
                                .filter(s -> s != this && s.subscription.get() != null)
                                .findFirst()
                                .orElse(null);

                    if (host != null)
                        host.host();

                }
            }

        }

    }

}
