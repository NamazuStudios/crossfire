package dev.getelements.elements.crossfire.api;

import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.dao.Transaction;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class StandardCancelableMatch<RequestT extends HandshakeRequest> extends AbstractMatch<RequestT> {

    private static final Logger logger = LoggerFactory.getLogger(StandardCancelableMatch.class);

    private final Provider<Transaction> transactionProvider;

    public StandardCancelableMatch(
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
            }

        } else {
            logger.info("Terminating match for request: {}", getRequest());
        }

    }

    @Override
    protected void onResult(final CancelableMatchStateRecord<RequestT> state, final MultiMatch result) {
        getRequest().success(result);
    }

    public Provider<Transaction> getTransactionProvider() {
        return transactionProvider;
    }

}
