package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.crossfire.model.signal.SignalLifecycle;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;
import java.util.stream.IntStream;

record BacklogRecord(BoundedList.Builder<ProtocolMessage> builder,
                     AtomicReferenceArray<List<ProtocolMessage>> backlog) {

    public static BacklogRecord create(final BoundedList.Builder<ProtocolMessage> builder) {
        return new BacklogRecord(builder, new AtomicReferenceArray<>(SignalLifecycle.values().length));
    }

    public void buffer(final Signal signal) {

        final var backlog = backlog().updateAndGet(signal.getLifecycle().ordinal(),
                existing -> existing == null
                        ? builder().build()
                        : existing
        );

        backlog.add(signal);

    }

    public void forEach(final Consumer<ProtocolMessage> consumer) {
        IntStream.of(0, backlog().length())
                .mapToObj(i -> backlog().get(i))
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .forEach(consumer);
    }

}
