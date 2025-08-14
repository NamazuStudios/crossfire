package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.error.DuplicateConnectionException;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.sdk.Subscription;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

record MailboxRecord(
        BacklogRecord backlog,
        AtomicReference<SubscriptionRecord> subscription) {

    public static MailboxRecord create(final BoundedList.Builder<ProtocolMessage> builder) {
        return new MailboxRecord(
                BacklogRecord.create(builder),
                new AtomicReference<>()
        );
    }

    public Subscription subscribe(
            final BiConsumer<Subscription, ProtocolMessage> onMessage,
            final BiConsumer<Subscription, Throwable> onError) {

        final var update = new SubscriptionRecord(onMessage, onError, () -> subscription().set(null));
        final var existing = subscription().getAndSet(update);

        if (existing == null) {
            backlog().forEach(update::onMessage);
        } else {
            final var ex = new DuplicateConnectionException();
            existing.onError(ex);
        }

        return update.subscription();

    }

    public void onError(final Throwable th) {

        final var s = subscription().get();

        if (s != null)
            s.onError(th);

    }

    public void send(final Signal signal) {

        final var subscription = subscription().get();

        switch (signal.getLifecycle()) {

            // Signal sent once are only buffered until a subscription is made.
            case ONCE -> {
                if (subscription == null)
                    backlog().buffer(signal);
                else
                    subscription.onMessage(signal);
            }

            // Signals with MATCH and SESSION lifecycles are buffered always.
            case MATCH, SESSION -> {

                backlog().buffer(signal);

                if (subscription != null)
                    subscription.onMessage(signal);

            }

            // All other states are unexpected and this is here for future proofing.
            default -> throw new IllegalStateException("Unexpected value: " + signal.getLifecycle());

        }
    }

}
