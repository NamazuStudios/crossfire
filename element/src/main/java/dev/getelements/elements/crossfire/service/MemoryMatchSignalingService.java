package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.error.DuplicateConnectionException;
import dev.getelements.elements.crossfire.model.error.MatchDeletedException;
import dev.getelements.elements.crossfire.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.model.signal.BroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.DirectSignal;
import dev.getelements.elements.crossfire.model.signal.SignalLifecycle;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.annotation.ElementDefaultAttribute;
import dev.getelements.elements.sdk.annotation.ElementEventConsumer;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.model.exception.ForbiddenException;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import dev.getelements.elements.sdk.model.profile.Profile;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static dev.getelements.elements.sdk.dao.MultiMatchDao.MULTI_MATCH_DELETED;
import static org.eclipse.jgit.lib.ObjectChecker.object;

public class MemoryMatchSignalingService implements MatchSignalingService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryMatchSignalingService.class);

    @ElementDefaultAttribute("256")
    public static final String MAX_BACKLOG_SIZE = "elements.crossfire.match.signaling.max.backlog.size";

    private final ConcurrentMap<String, MatchRecord> matches = new ConcurrentHashMap<>();

    private int maxBacklogSize;

    private MultiMatchDao mongoMultiMatchDao;

    @Override
    public void send(final String matchId, final BroadcastSignal signal) {

        final var match = getMongoMultiMatchDao().getMultiMatch(matchId);
        final var profiles = getMongoMultiMatchDao().getProfiles(match.getId());

        final var exists = profiles
                .stream()
                .anyMatch(p -> p.getId().equals(signal.getProfileId()));

        if (!exists) {
            throw new ForbiddenException("Profile with id " + signal.getProfileId() + " does not exist");
        }

        matches.computeIfAbsent(match.getId(), mid -> MatchRecord.create(getMaxBacklogSize()))
               .send(profiles, signal);

    }

    @Override
    public void send(final String matchId, final DirectSignal signal) {

        final var match = getMongoMultiMatchDao().getMultiMatch(matchId);
        final var profiles = getMongoMultiMatchDao().getProfiles(match.getId());

        final var senderExists = profiles
                .stream()
                .anyMatch(p -> p.getId().equals(signal.getProfileId()));

        final var recipientExists = profiles
                .stream()
                .anyMatch(p -> p.getId().equals(signal.getProfileId()));

        if (!senderExists) {
            throw new ForbiddenException("Profile with id " + signal.getProfileId() + " does not exist");
        } else if (!recipientExists) {
            throw new ForbiddenException("Recipient profile with id " + signal.getRecipientProfileId() + " does not exist");
        }

        matches.computeIfAbsent(match.getId(), mid -> MatchRecord.create(getMaxBacklogSize()))
                .getMailbox(signal.getRecipientProfileId())
                .send(signal);

    }

    @Override
    public Subscription subscribe(
            final String matchId,
            final String profileId,
            final BiConsumer<Subscription, ProtocolMessage> onMessage,
            final BiConsumer<Subscription, Throwable> onError) {

        final var match = getMongoMultiMatchDao().getMultiMatch(matchId);
        final var profiles = getMongoMultiMatchDao().getProfiles(match.getId());

        final var exists = profiles
                .stream()
                .anyMatch(p -> p.getId().equals(profileId));

        if (!exists) {
            throw new ForbiddenException("Profile " + profileId + " is not part of match " + matchId);
        }

        return matches.computeIfAbsent(match.getId(), mid -> MatchRecord.create(getMaxBacklogSize()))
                .getMailbox(profileId)
                .subscribe(onMessage, onError);

    }

    public int getMaxBacklogSize() {
        return maxBacklogSize;
    }

    @Inject
    public void setMaxBacklogSize(@Named(MAX_BACKLOG_SIZE) int maxBacklogSize) {
        this.maxBacklogSize = maxBacklogSize;
    }

    public MultiMatchDao getMongoMultiMatchDao() {
        return mongoMultiMatchDao;
    }

    @Inject
    public void setMongoMultiMatchDao(MultiMatchDao mongoMultiMatchDao) {
        this.mongoMultiMatchDao = mongoMultiMatchDao;
    }

    @ElementEventConsumer(MULTI_MATCH_DELETED)
    public void onMatchDeleted(final MultiMatch multiMatch) {

        final var existing = matches.remove(multiMatch.getId());

        if (existing == null) {
            logger.debug("No match mailboxes for match {}", multiMatch.getId());
        } else {
            final var ex = new MatchDeletedException();
            existing.onError(ex);
        }

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

    private record MailboxRecord(BacklogRecord backlog,
                                 AtomicReference<SubscriptionRecord> subscription) {

        public static MailboxRecord create(final BoundedList.Builder<ProtocolMessage> builder) {
            return new MailboxRecord(BacklogRecord.create(builder), new AtomicReference<>());
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

        public void send(final ProtocolMessage signal) {
            subscription().get().onMessage(signal);
        }

    }

    private record MatchRecord(
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

        public void send(final List<Profile> profiles, final BroadcastSignal signal) {
            profiles.stream()
                    .map(Profile::getId)
                    .map(this::getMailbox)
                    .forEach(mb -> mb.send(signal));
        }

    }

    private record BacklogRecord(BoundedList.Builder<ProtocolMessage> builder,
                                 AtomicReferenceArray<List<ProtocolMessage>> backlog) {

        public static BacklogRecord create(final BoundedList.Builder<ProtocolMessage> builder) {
            return new BacklogRecord(builder, new AtomicReferenceArray<>(SignalLifecycle.values().length));
        }

        public List<ProtocolMessage> bufferFor(final SignalLifecycle lifecycle) {
            return backlog().updateAndGet(lifecycle.ordinal(), existing -> existing == null
                    ? builder().build()
                    : existing
            );
        }

        public void forEach(final Consumer<ProtocolMessage> consumer) {
            IntStream.of(0, backlog().length())
                    .mapToObj(i -> backlog().get(i))
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .forEach(consumer);
        }

    }

}
