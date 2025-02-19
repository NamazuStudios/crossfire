package dev.getelements.elements.crossfire;

import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.annotation.ElementServiceImplementation;
import dev.getelements.elements.sdk.util.ConcurrentLockedPublisher;
import dev.getelements.elements.sdk.util.Publisher;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.SECONDS;

@Singleton
@ElementServiceImplementation(SdpRelayService.class)
public class MemorySdpRelayService implements SdpRelayService {

    private final long QUEUE_TIMEOUT_SECONDS = 360;

    private final Map<String, MessageRelayQueue> queues = new ConcurrentHashMap<>();

    private final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor();

    private static final MemorySdpRelayService instance = new MemorySdpRelayService();

    public static MemorySdpRelayService getInstance() {
        return instance;
    }

    @Override
    public void addSessionDescription(
            final String matchId,
            final String sdpMessage) {

        final var queue = queues.computeIfAbsent(matchId, k -> new MessageRelayQueue());
        final var lock = queue.getLock();
        lock.lock();

        try {
            queue.publish(sdpMessage);
        } finally {
            lock.unlock();
        }

    }

    @Override
    public Subscription subscribeToUpdates(
            final String matchId,
            final Consumer<String> sdpMessageConsumer,
            final Consumer<Throwable> sdpErrorConsumer) {

        final var queue = queues.computeIfAbsent(matchId, k -> new MessageRelayQueue());
        final var lock = queue.getLock();
        lock.lock();

        try {
            return queue.subscribe(sdpMessageConsumer, sdpErrorConsumer);
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

        private final List<String> backlog = new ArrayList<>();

        private final Publisher<String> messaggePublisher = new ConcurrentLockedPublisher<>(lock);

        private final Publisher<Throwable> exceptionPublisher = new ConcurrentLockedPublisher<>(lock);

        public Lock getLock() {
            return lock;
        }

        public void publish(final String sdpMessage) {
            backlog.addLast(sdpMessage);
            messaggePublisher.publish(sdpMessage);
        }

        public Subscription subscribe(final Consumer<String> sdpMessageConsumer,
                                      final Consumer<Throwable> sdpErrorConsumer) {
            backlog.forEach(sdpMessageConsumer);
            return Subscription.begin()
                    .chain(messaggePublisher.subscribe(sdpMessageConsumer))
                    .chain(exceptionPublisher.subscribe(sdpErrorConsumer));
        }

        private void timeout() {

            lock.lock();

            try {
                exceptionPublisher.publish(new TimeoutException("Queued timed out."));
            } finally {
                exceptionPublisher.clear();
                messaggePublisher.clear();
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

}
