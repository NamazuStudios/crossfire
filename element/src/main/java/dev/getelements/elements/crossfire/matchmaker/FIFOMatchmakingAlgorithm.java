package dev.getelements.elements.crossfire.matchmaker;

import dev.getelements.elements.crossfire.StandardJoinMatchHandle;
import dev.getelements.elements.crossfire.api.*;
import dev.getelements.elements.crossfire.model.handshake.FindHandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.JoinHandshakeRequest;
import dev.getelements.elements.dao.mongo.match.MongoMultiMatchDao;
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

                final var result = getTransactionProvider().get().performAndClose(txn -> {

                    final var dao = txn.getDao(MongoMultiMatchDao.class);
                    final var profile = request.getProfile();
                    final var configuration = request.getApplicationConfiguration();

                    final var multiMatch =  dao
                            .findOldestAvailableMultiMatchCandidate(configuration, profile.getId(), "")
                            .orElseGet(() -> {
                                final var m = new MultiMatch();
                                m.setConfiguration(request.getApplicationConfiguration());
                                m.setStatus(MultiMatchStatus.OPEN);
                                return dao.createMultiMatch(m);
                            });

                    return dao.addProfile(multiMatch.getId(), profile);

                });

                result(result);

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
