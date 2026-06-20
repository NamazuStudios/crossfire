package dev.getelements.elements.crossfire.client.teavm.signaling;

import dev.getelements.elements.sdk.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Simple ArrayList-backed event publisher for TeaVM browser targets.
 * No concurrency primitives — JS is single-threaded.
 */
public final class TeaVMPublisher<T> {

    private final List<Entry<T>> entries = new ArrayList<>();

    public Subscription subscribe(final BiConsumer<Subscription, T> listener) {
        final var entry = new Entry<>(listener);
        entries.add(entry);
        final Subscription sub = () -> entries.remove(entry);
        entry.subscription = sub;
        return sub;
    }

    public void publish(final T value) {
        for (var e : List.copyOf(entries)) e.listener.accept(e.subscription, value);
    }

    public void clear() {
        entries.clear();
    }

    private static final class Entry<T> {
        final BiConsumer<Subscription, T> listener;
        Subscription subscription;
        Entry(final BiConsumer<Subscription, T> l) { listener = l; }
    }
}
