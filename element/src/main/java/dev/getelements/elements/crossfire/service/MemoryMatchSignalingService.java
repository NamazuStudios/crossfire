package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.error.MatchDeletedException;
import dev.getelements.elements.crossfire.model.signal.BroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.DirectSignal;
import dev.getelements.elements.sdk.ElementRegistry;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.annotation.ElementDefaultAttribute;
import dev.getelements.elements.sdk.annotation.ElementEventConsumer;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.model.exception.ForbiddenException;
import dev.getelements.elements.sdk.model.exception.InvalidMultiMatchPhaseException;
import dev.getelements.elements.sdk.model.exception.MultiMatchNotFoundException;
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
import static dev.getelements.elements.sdk.model.match.MultiMatchStatus.ENDED;

public class MemoryMatchSignalingService implements MatchSignalingService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryMatchSignalingService.class);

    @ElementDefaultAttribute("256")
    public static final String MAX_BACKLOG_SIZE = "elements.crossfire.match.signaling.max.backlog.size";

    private final ConcurrentMap<String, MemoryMatchState> matches = new ConcurrentHashMap<>();

    private int maxBacklogSize;

    private MultiMatchDao multiMatchDao;

    private ElementRegistry elementRegistry;

    @Override
    public Optional<String> findHost() {
        return Optional.empty();
    }

    @Override
    public void send(final String matchId, final BroadcastSignal signal) {

        final var match = getSignalableMatch(matchId);
        final var profiles = getMongoMultiMatchDao().getProfiles(match.getId());

        final var exists = profiles
                .stream()
                .anyMatch(p -> p.getId().equals(signal.getProfileId()));

        if (!exists) {
            throw new ForbiddenException("Profile with id " + signal.getProfileId() + " does not exist");
        }

        matches.computeIfAbsent(match.getId(), this::newMemoryMatchState)
               .send(signal);

    }

    @Override
    public void send(final String matchId, final DirectSignal signal) {

        final var match = getSignalableMatch(matchId);
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

        matches.computeIfAbsent(match.getId(), this::newMemoryMatchState)
               .send(signal);

    }

    @Override
    public boolean join(final String matchId, final String profileId) {

        final var match = getSignalableMatch(matchId);
        final var profiles = getMongoMultiMatchDao().getProfiles(match.getId());

        final var exists = profiles
                .stream()
                .anyMatch(p -> p.getId().equals(profileId));

        if (!exists)
            throw new ForbiddenException("Profile " + profileId + " is not part of match " + matchId);

        return matches
                .computeIfAbsent(match.getId(), this::newMemoryMatchState)
                .join(profileId);

    }

    @Override
    public Subscription connect(
            final String matchId,
            final String profileId,
            final Consumer<ProtocolMessage> onMessage,
            final Consumer<Throwable> onError) {

        final var match = getSignalableMatch(matchId);
        final var profiles = getMongoMultiMatchDao().getProfiles(match.getId());

        final var exists = profiles
                .stream()
                .anyMatch(p -> p.getId().equals(profileId));

        if (!exists)
            throw new ForbiddenException("Profile " + profileId + " is not part of match " + matchId);

        return matches
                .computeIfAbsent(match.getId(), this::newMemoryMatchState)
                .connect(profileId, onMessage, onError);

    }

    private MultiMatch getSignalableMatch(final String matchId) {

        final var match = getMongoMultiMatchDao().getMultiMatch(matchId);

        if (ENDED.equals(match.getStatus())) {
            throw new MatchDeletedException("Match is not active: %s".formatted(matchId));
        }

        return match;

    }

    private MemoryMatchState newMemoryMatchState(final String matchId) {

        final var parameters = new MemoryMatchState.Parameters(
                matchId,
                getMaxBacklogSize(),
                getElementRegistry(),
                this::onAllParticipantsLeft,
                this::onnAllParticipantsDisconnected
        );

        return new MemoryMatchState(parameters);
    }

    private void endAndRemove(final String matchId) {
        try {
            final var match = getMongoMultiMatchDao().endMatch(matchId);
            matches.remove(match.getId());
        } catch (MultiMatchNotFoundException ex) {
            logger.warn("Could not end MultiMatch {} because it was not found.", matchId);
        } catch (InvalidMultiMatchPhaseException ex) {
            logger.warn("Could not end MultiMatch {} because it was in an invalid state: {}", matchId, ex.getActual());
        }
    }

    private void onAllParticipantsLeft(final MemoryMatchState memoryMatchState) {
        final var matchId = memoryMatchState.getParameters().matchId();
        logger.info("All participants left {}. Removing.", matchId);
        endAndRemove(matchId);
    }

    private void onnAllParticipantsDisconnected(final MemoryMatchState memoryMatchState) {
        final var matchId = memoryMatchState.getParameters().matchId();
        logger.info("All participants disconnected {}. Removing.", matchId);
        endAndRemove(matchId);
    }

    @Override
    public boolean leave(final String matchId, final String profileId) {
        final var state = matches.get(matchId);
        return state != null && state.leave(profileId);
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

    public ElementRegistry getElementRegistry() {
        return elementRegistry;
    }

    @Inject
    public void setElementRegistry(ElementRegistry elementRegistry) {
        this.elementRegistry = elementRegistry;
    }

    public MultiMatchDao getMongoMultiMatchDao() {
        return multiMatchDao;
    }

    @Inject
    public void setMongoMultiMatchDao(MultiMatchDao mongoMultiMatchDao) {
        this.multiMatchDao = mongoMultiMatchDao;
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
