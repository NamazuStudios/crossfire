package dev.getelements.elements.crossfire.matchmaker;

import dev.getelements.elements.crossfire.StandardJoinMatchHandle;
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

import static dev.getelements.elements.sdk.model.match.MultiMatchStatus.FULL;
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
    public MatchHandle<FindHandshakeRequest> find(final MatchmakingRequest<FindHandshakeRequest> request) {
        return new StandardCancelableMatchHandle<>(this, request, getTransactionProvider()) {

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
                            .filter(m -> OPEN.equals(m.getStatus()))
                            .filter(m -> dao.getProfiles(m.getId()).size() < m.getConfiguration().getMaxProfiles());

                    // If any profile in the match matches the requested profile, then that profile
                    // has already joined and should attempt to rejoin
                    final var alreadyJoined = base.get()
                            .filter(m -> dao.getProfiles(m.getId())
                                    .stream()
                                    .anyMatch(p -> Objects.equals(p.getId(), request.getProfile().getId())));

                    MultiMatch result = alreadyJoined.findFirst().orElse(null);

                    //If no matches have already been joined, find an open one or create a new one
                    if(result == null) {

                        //Prioritize any matches that you've already joined so that you can rejoin
                        result = base.get()
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
                    }

                    transaction.commit();

                    result(result);

                    final var profiles = dao.getProfiles(result.getId());

                    // TODO Add a flag to indicate that the match should start automatically when full or if it should
                    //  start manually through another action or API.

                    if (profiles.size() >= result.getConfiguration().getMaxProfiles()) {
                        result.setStatus(FULL);
                        dao.updateMultiMatch(result);
                    }

                }

            }

        };
    }

    @Override
    public MatchHandle<JoinHandshakeRequest> join(final MatchmakingRequest<JoinHandshakeRequest> request) {
        return new StandardJoinMatchHandle(this, request, getTransactionProvider());
    }

    public Provider<Transaction> getTransactionProvider() {
        return transactionProvider;
    }

    @Inject
    public void setTransactionProvider(Provider<Transaction> transactionProvider) {
        this.transactionProvider = transactionProvider;
    }

}
