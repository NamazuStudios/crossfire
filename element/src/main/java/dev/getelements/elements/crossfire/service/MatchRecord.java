package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.model.signal.BroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.DirectSignal;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.model.profile.Profile;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

record MatchRecord(
        BacklogRecord backlog,
        ConcurrentMap<String, MailboxRecord> mailboxes,
        BoundedList.Builder<ProtocolMessage> builder) {

    public static MatchRecord create(final int maxBacklogSize) {

        final var builder = BoundedList.<ProtocolMessage>builder()
                .maxSize(maxBacklogSize)
                .backingListSupplier(CopyOnWriteArrayList::new)
                .exceptionSupplier(() -> new ProtocolStateException("Exceeded max backlog size: " + maxBacklogSize));

        return new MatchRecord(BacklogRecord.create(builder), new ConcurrentHashMap<>(), builder);

    }

    public MailboxRecord getMailbox(final String profileId) {
        return mailboxes().computeIfAbsent(profileId, pid -> MailboxRecord.create(builder()));
    }

    public void onError(final Throwable th) {
        mailboxes().values().forEach(m -> m.onError(th));
    }

    public void send(final DirectSignal signal) {

    }

    public void send(final List<Profile> profiles, final BroadcastSignal signal) {
        profiles.stream()
                .map(Profile::getId)
                .map(this::getMailbox)
                .forEach(mb -> mb.send(signal));
    }

    public Subscription subscribe(final BiConsumer<Subscription, ProtocolMessage> onMessage,
                                  final BiConsumer<Subscription, Throwable> onError) {

        return null;
    }

}
