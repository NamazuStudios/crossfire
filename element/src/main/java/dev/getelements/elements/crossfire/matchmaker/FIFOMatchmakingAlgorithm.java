package dev.getelements.elements.crossfire.matchmaker;

import dev.getelements.elements.crossfire.StandardJoinMatchHandle;
import dev.getelements.elements.crossfire.api.*;
import dev.getelements.elements.crossfire.model.handshake.FindHandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.JoinHandshakeRequest;
import dev.getelements.elements.sdk.annotation.ElementServiceExport;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.dao.Transaction;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import dev.getelements.elements.sdk.model.match.MultiMatchStatus;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        return new FIFOMatchHandle(request);
    }

    @Override
    public MatchHandle<JoinHandshakeRequest> join(final MatchmakingRequest<JoinHandshakeRequest> request) {
        return new StandardJoinMatchHandle(this, request, getTransactionProvider());
    }

    public Provider<Transaction> getTransactionProvider() {
        return transactionProvider;
    }

    @Inject
    public void setTransactionProvider(final Provider<Transaction> transactionProvider) {
        this.transactionProvider = transactionProvider;
    }

    private class FIFOMatchHandle extends StandardCancelableMatchHandle<FindHandshakeRequest> {

        public FIFOMatchHandle(final MatchmakingRequest<FindHandshakeRequest> request) {
            super(FIFOMatchmakingAlgorithm.this, request, FIFOMatchmakingAlgorithm.this.getTransactionProvider());
        }

        @Override
        protected void onMatching(final CancelableMatchStateRecord<FindHandshakeRequest> state) {
            getRequest().getProtocolMessageHandler().submit(() -> {
                final var result = getTransactionProvider().get().performAndClose(txn -> {

                    final var dao = txn.getDao(MultiMatchDao.class);
                    final var profile = getRequest().getProfile();
                    final var configuration = getRequest().getApplicationConfiguration();

                    final var multiMatch =  dao
                            .findOldestAvailableMultiMatchCandidate(configuration, profile.getId(), "")
                            .orElseGet(() -> {
                                final var m = new MultiMatch();
                                m.setConfiguration(getRequest().getApplicationConfiguration());
                                m.setStatus(MultiMatchStatus.OPEN);
                                return dao.createMultiMatch(m);
                            });

                    return dao.addProfile(multiMatch.getId(), profile);

                });

                setResult(result);

            });
        }

    }

}
