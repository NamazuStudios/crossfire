package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.crossfire.model.signal.SignalLifecycle;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.util.Monitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

public class LockingMailbox {

    private static final Logger logger = LoggerFactory.getLogger(LockingMailbox.class);

    private final Lock lock = new ReentrantLock();

    private final BoundedList.Builder<Signal> backlogListBuilder = new BoundedList.Builder<>();

    private final Map<SignalLifecycle, List<Signal>> backlog = new EnumMap<>(SignalLifecycle.class);

    private SubscriptionRecord subscription;

    public LockingMailbox(final int maxBacklogSize) {
        backlogListBuilder.maxSize(maxBacklogSize).sizeSupplier(this::backlogSize);
    }

    private int backlogSize() {
        return backlog.values().stream().mapToInt(List::size).sum();
    }

    public void send(final Signal signal) {
        try (final var mon = Monitor.enter(lock)) {
            final var lifecycle = signal.getLifecycle();
            switch (lifecycle) {

                // ONCE signals are only sent to the subscription if it exists.
                // Otherwise they are added to the backlog
                case ONCE -> {
                    if (subscription == null)
                        getBacklog(lifecycle).add(signal);
                    else
                        subscription.onMessage(signal);
                }

                // Other messages are always added to the backlog
                case MATCH, SESSION -> {

                    if (subscription == null)
                       subscription.onMessage(signal);

                    getBacklog(signal.getLifecycle()).add(signal);

                }
            }
        }
    }



    private List<Signal> getBacklog(final SignalLifecycle lifecycle) {
        return backlog.computeIfAbsent(lifecycle, k -> backlogListBuilder.build());
    }

    private record SubscriptionRecord(BiConsumer<Subscription, ProtocolMessage> onMessage,
                                      BiConsumer<Subscription, Throwable> onError,
                                      Subscription subscription) {

        public void onError(final Throwable th) {
            try {
                onError().accept(subscription(), th);
            } catch (Exception ex) {
                logger.error("Error while processing error", ex);
            }
        }

        public void onMessage(final ProtocolMessage message) {
            try {
                onMessage().accept(subscription(), message);
            } catch (Exception ex) {
                onError().accept(subscription(), ex);
            }
        }

    }

}
