package dev.getelements.elements.crossfire.matchmaker;

import dev.getelements.elements.crossfire.StandardJoinMatch;
import dev.getelements.elements.crossfire.api.*;
import dev.getelements.elements.crossfire.model.handshake.FindHandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.JoinHandshakeRequest;
import dev.getelements.elements.rt.exception.DuplicateException;
import dev.getelements.elements.sdk.annotation.ElementServiceExport;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.dao.Transaction;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import dev.getelements.elements.sdk.model.match.MultiMatchStatus;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static dev.getelements.elements.sdk.model.match.MultiMatchStatus.OPEN;

@Slf4j
@ElementServiceExport(value = MatchmakingAlgorithm.class)
@ElementServiceExport(value = MatchmakingAlgorithm.class, name = FIFOMatchmakingAlgorithm.NAME)
public class FIFOMatchmakingAlgorithm implements MatchmakingAlgorithm {

    private static final Logger logger = LoggerFactory.getLogger(FIFOMatchmakingAlgorithm.class);

    public static final String NAME = "FIFO";

    private Provider<Transaction> transactionProvider;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Match<FindHandshakeRequest> find(final MatchmakingRequest<FindHandshakeRequest> request) {
        return new StandardCancelableMatch<>(this, request, getTransactionProvider()) {

            @Override
            protected void onMatching(final CancelableMatchStateRecord<FindHandshakeRequest> state) {
                request.getProtocolMessageHandler().submit(this::find);
            }

            private void find() {
                try (final var transaction = transactionProvider.get()) {

                    final var dao = transaction.getDao(MultiMatchDao.class);

                    // TODO Fix this with properly structured query. The provided code is a placeholder because it
                    // essentially loads the entire database collection into memory to accomplish the match. This
                    // could be easily replaced with a query that filters the matches based on the configuration
                    // profile, and status.

                    final List<MultiMatch> matches = dao.getAllMultiMatches();

                    final Supplier<Stream<MultiMatch>> base = () -> matches
                            .stream()
                            // Filter out matches that do not match the configuration
                            .filter(m -> m.getConfiguration()
                                    .getId()
                                    .equals(request.getApplicationConfiguration().getId())
                            )
                            // Filter out matches that are not in the correct state
                            .filter(m -> !OPEN.equals(m.getStatus()));

                    final var existing = base.get()
                            // All profiles in the match must match the requested profile
                            .filter(m -> dao.getProfiles(m.getId())
                                    .stream()
                                    .anyMatch(p -> Objects.equals(p.getId(), request.getProfile().getId())));

                    var result = Stream.concat(existing, base.get())
                            .findFirst()
                            .orElseGet(() -> {
                                final var m = new MultiMatch();
                                m.setConfiguration(request.getApplicationConfiguration());
                                m.setStatus(MultiMatchStatus.OPEN);
                                return dao.createMultiMatch(m);
                            });

                    try {
                        // We could have a tryAddProfile method here, but for now we just catch the exception
                        // and log it.
                        result = dao.addProfile(result.getId(), request.getProfile());
                    } catch (DuplicateException e) {
                        logger.info("Failed to add profile to match: {}", result.getId(), e);
                    }

                    result(result);

                }
            }

        };
    }

    @Override
    public Match<JoinHandshakeRequest> join(final MatchmakingRequest<JoinHandshakeRequest> request) {
        return new StandardJoinMatch(this, request, getTransactionProvider());
    }

    public Provider<Transaction> getTransactionProvider() {
        return transactionProvider;
    }

    @Inject
    public void setTransactionProvider(Provider<Transaction> transactionProvider) {
        this.transactionProvider = transactionProvider;
    }

}
