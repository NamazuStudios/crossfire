package dev.getelements.elements.crossfire;

import dev.getelements.elements.sdk.ElementRegistry;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.ConcurrentLockedPublisher;
import dev.getelements.elements.sdk.util.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.SECONDS;

public class MemoryMatchSignalingService implements MatchSignalingService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryMatchSignalingService.class);

    // TODO Remove and replace with a proper ping/pong response
    private final long QUEUE_TIMEOUT_SECONDS = 180;

    private final Map<String, MessageRelayQueue> queues = new ConcurrentHashMap<>();

    private final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor();

    private static final MemoryMatchSignalingService instance = new MemoryMatchSignalingService();

    /**
     * Gets the single shared instance.
     *
     * @return the single shared instance.
     * @deprecated To be replaced with a lookup in {@link ElementRegistry}
     */
    @Deprecated
    public static MemoryMatchSignalingService getInstance() {
        return instance;
    }

    @Override
    public boolean pingMatch(final String matchId) {

        final var queue = queues.get(matchId);

        if (queue == null) {
            logger.warn("No Such match {}", matchId);
            return false;
        }

        final var lock = queue.getLock();
        lock.lock();

        try {
            queue.resetTimeout();
            return true;
        } finally {
            lock.unlock();
        }

    }

    @Override
    public void addSessionDescription(
            final String matchId,
            final String profileId,
            final String sdpMessage) {

        final var queue = queues.computeIfAbsent(matchId, k -> new MessageRelayQueue());
        final var lock = queue.getLock();
        lock.lock();

        try {
            queue.publish(profileId, sdpMessage);
        } finally {
            lock.unlock();
        }

    }

    @Override
    public Subscription subscribeToUpdates(
            final String matchId,
            final String profileId,
            final Consumer<String> sdpMessageConsumer,
            final Consumer<Throwable> sdpErrorConsumer) {

        final var queue = queues.computeIfAbsent(matchId, k -> new MessageRelayQueue());
        final var lock = queue.getLock();
        lock.lock();

        try {
            return queue.subscribe(profileId, sdpMessageConsumer, sdpErrorConsumer);
        } finally {
            lock.unlock();
        }

    }

    private class MessageRelayQueue {

        private ScheduledFuture<?> timeoutFuture = timeoutScheduler.schedule(
                this::timeout,
                QUEUE_TIMEOUT_SECONDS,
                SECONDS
        );

        private final Lock lock = new ReentrantLock();

        private final List<SdpMessage> backlog = new ArrayList<>();

        private final Publisher<SdpMessage> messagePublisher = new ConcurrentLockedPublisher<>(lock);

        private final Publisher<Throwable> exceptionPublisher = new ConcurrentLockedPublisher<>(lock);

        public Lock getLock() {
            return lock;
        }

        public void publish(final String profileId,
                            final String sdpMessage) {
            final var message = new SdpMessage(profileId, sdpMessage);
            resetTimeout();
            backlog.addLast(message);
            messagePublisher.publish(message);
        }

        public Subscription subscribe(final String profileId,
                                      final Consumer<String> sdpMessageConsumer,
                                      final Consumer<Throwable> sdpErrorConsumer) {

            resetTimeout();

            backlog.stream()
                    .filter(msg -> !msg.originator().equals(profileId))
                    .map(SdpMessage::payload)
                    .forEach(sdpMessageConsumer);

            final var subscription =  Subscription.begin()
                    .chain(() -> backlog.removeIf(msg -> msg.originator().equals(profileId)))
                    .chain(exceptionPublisher.subscribe(sdpErrorConsumer))
                    .chain(messagePublisher.subscribe(m -> {
                        if (!m.originator().equals(profileId)) {
                            sdpMessageConsumer.accept(m.payload());
                        }
                    }));

            return () -> {
                try {
                    lock.lock();
                    subscription.unsubscribe();
                } finally {
                    lock.unlock();
                }
            };

        }

        private void timeout() {

            lock.lock();

            try {
                exceptionPublisher.publish(new TimeoutException("Queued timed out."));
            } finally {
                exceptionPublisher.clear();
                messagePublisher.clear();
                lock.unlock();
            }

        }

        private void resetTimeout() {
            timeoutFuture.cancel(false);
            timeoutFuture = timeoutScheduler.schedule(
                    this::timeout,
                    QUEUE_TIMEOUT_SECONDS,
                    SECONDS
            );
        }

    }

    private record SdpMessage(Object originator, String payload) {}

}
