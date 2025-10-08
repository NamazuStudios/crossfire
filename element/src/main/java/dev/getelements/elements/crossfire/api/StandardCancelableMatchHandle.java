package dev.getelements.elements.crossfire.api;

import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.dao.Transaction;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.getelements.elements.sdk.model.match.MultiMatchStatus.ENDED;

public abstract class StandardCancelableMatchHandle<RequestT extends HandshakeRequest> extends AbstractMatchHandle<RequestT> {

    private static final Logger logger = LoggerFactory.getLogger(StandardCancelableMatchHandle.class);

    private final Provider<Transaction> transactionProvider;

    public StandardCancelableMatchHandle(
            final MatchmakingAlgorithm algorithm,
            final MatchmakingRequest<RequestT> request,
            final Provider<Transaction> transactionProvider) {
        super(algorithm, request);
        this.transactionProvider = transactionProvider;
    }

    @Override
    protected void onTerminated(final CancelableMatchStateRecord<RequestT> state) {
        getRequest().getProtocolMessageHandler().submit(() -> doTerminate(state));
    }

    private void doTerminate(final CancelableMatchStateRecord<RequestT> state) {

        if (state.result() != null) {

            logger.info("Terminating match: {}", state.result().getId());

            try (final var transaction = getTransactionProvider().get()) {

                final var dao = transaction.getDao(MultiMatchDao.class);
                dao.removeProfile(state.result().getId(), getRequest().getProfile());

                final var profiles = dao.getProfiles(state.result().getId());

                if (profiles.isEmpty()) {
                    logger.info("Removing match: {} as it has no profiles left", state.result().getId());
                    dao.deleteMultiMatch(state.result().getId());
                } else {
                    logger.info("Match {} still has profiles, not removing", state.result().getId());
                }

            }

        } else {
            logger.info("Terminating match for request: {}", getRequest());
        }

    }

    @Override
    protected void onEnd(final CancelableMatchStateRecord<RequestT> state) {
        try (var txn = getTransactionProvider().get()) {
            final var dao = txn.getDao(MultiMatchDao.class);
            final var match = dao.getMultiMatch(state.result().getId());
            match.setStatus(ENDED);
            dao.updateMultiMatch(match);
            txn.commit();
        }
    }

    @Override
    protected void onOpen(final CancelableMatchStateRecord<RequestT> state) {
        
    }

    @Override
    protected void onMatching(final CancelableMatchStateRecord<RequestT> state) {

    }

    @Override
    protected void onResult(final CancelableMatchStateRecord<RequestT> state, final MultiMatch result) {
        getRequest().success(this);
    }

    public Provider<Transaction> getTransactionProvider() {
        return transactionProvider;
    }

}
