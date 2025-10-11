package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.error.MatchDeletedException;
import dev.getelements.elements.crossfire.model.signal.BroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.DirectSignal;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.annotation.ElementDefaultAttribute;
import dev.getelements.elements.sdk.annotation.ElementEventConsumer;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.model.exception.ForbiddenException;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import static dev.getelements.elements.sdk.dao.MultiMatchDao.MULTI_MATCH_DELETED;

public class MemoryMatchSignalingService implements MatchSignalingService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryMatchSignalingService.class);

    @ElementDefaultAttribute("256")
    public static final String MAX_BACKLOG_SIZE = "elements.crossfire.match.signaling.max.backlog.size";

    private final ConcurrentMap<String, MemoryMatchState> matches = new ConcurrentHashMap<>();

    private int maxBacklogSize;

    private MultiMatchDao mongoMultiMatchDao;

    @Override
    public Optional<String> findHost() {
        return Optional.empty();
    }

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

        matches.computeIfAbsent(match.getId(), mid -> new MemoryMatchState(getMaxBacklogSize()))
               .send(signal);

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
                .anyMatch(p -> p.getId().equals(signal.getRecipientProfileId()));

        if (!senderExists) {
            throw new ForbiddenException("Profile with id " + signal.getProfileId() + " does not exist");
        } else if (!recipientExists) {
            throw new ForbiddenException("Recipient profile with id " + signal.getRecipientProfileId() + " does not exist");
        }

        matches.computeIfAbsent(match.getId(), mid -> new MemoryMatchState(getMaxBacklogSize()))
               .send(signal);

    }

    @Override
    public boolean join(final String matchId, final String profileId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Subscription connect(
            final String matchId,
            final String profileId,
            final Consumer<ProtocolMessage> onMessage,
            final Consumer<Throwable> onError) {

        final var match = getMongoMultiMatchDao().getMultiMatch(matchId);
        final var profiles = getMongoMultiMatchDao().getProfiles(match.getId());

        final var exists = profiles
                .stream()
                .anyMatch(p -> p.getId().equals(profileId));

        if (!exists)
            throw new ForbiddenException("Profile " + profileId + " is not part of match " + matchId);

        return matches
                .computeIfAbsent(match.getId(), mid -> new MemoryMatchState(getMaxBacklogSize()))
                .join(profileId, onMessage, onError);

    }

    @Override
    public void leave(final String matchId, final String profileId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void assignHost(final String matchId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void assignHost(final String matchId, final String profileId) {
        throw new UnsupportedOperationException();
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
            existing.error(ex);
        }

    }

}
