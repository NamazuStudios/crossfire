package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.error.DuplicateConnectionException;
import dev.getelements.elements.crossfire.model.error.MatchDeletedException;
import dev.getelements.elements.crossfire.model.signal.BroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.SignalWithRecipient;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.annotation.ElementEventConsumer;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.model.exception.ForbiddenException;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static dev.getelements.elements.sdk.dao.MultiMatchDao.MULTI_MATCH_DELETED;

public class MemoryMatchSignalingService implements MatchSignalingService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryMatchSignalingService.class);

    // TODO Remove and replace with a proper ping/pong response
    private final long QUEUE_TIMEOUT_SECONDS = 180;

    private final ConcurrentMap<String, MailboxesForMatch> matches = new ConcurrentHashMap<>();

    private MultiMatchDao mongoMultiMatchDao;

    @Override
    public void send(final String matchId, final BroadcastSignal signal) {
        final var match = getMongoMultiMatchDao().getMultiMatch(matchId);
        matches.computeIfAbsent(match.getId(), mid -> MailboxesForMatch.create())
                .getMailbox(signal.getProfileId());

    }

    @Override
    public void send(final String matchId, final SignalWithRecipient signal) {
        final var match = getMongoMultiMatchDao().getMultiMatch(matchId);

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

        return matches.computeIfAbsent(match.getId(), mid -> MailboxesForMatch.create())
                .getMailbox(profileId)
                .subscribe(onMessage, onError);

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

    private record Mailbox(Deque<ProtocolMessage> backlog, AtomicReference<SubscriptionRecord> subscription) {

        public static Mailbox create() {
            return new Mailbox(new ConcurrentLinkedDeque<>(), new AtomicReference<>());
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
    }

    private record MailboxesForMatch(ConcurrentMap<String, Mailbox> mailboxes) {

        public static MailboxesForMatch create() {
            return new MailboxesForMatch(new ConcurrentHashMap<>());
        }

        public Mailbox getMailbox(final String profileId) {
            return mailboxes().computeIfAbsent(profileId, pid -> Mailbox.create());
        }

        public void onError(final Throwable th) {
            mailboxes().values().forEach(m -> m.onError(th));
        }

    }

}
