package dev.getelements.elements.crossfire.api;

import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.dao.Transaction;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    protected void onEndMatch(final CancelableMatchStateRecord<RequestT> state) {
        getRequest()
                .getProtocolMessageHandler()
                .submit(() -> getTransactionProvider().get().performAndCloseV(txn -> {
                    final var dao = txn.getDao(MultiMatchDao.class);
                    dao.endMatch(state.result().getId());
                }));
    }

    @Override
    protected void onCloseMatch(final CancelableMatchStateRecord<RequestT> state) {
        getRequest()
                .getProtocolMessageHandler()
                .submit(() -> getTransactionProvider().get().performAndCloseV(txn -> {
                    final var dao = txn.getDao(MultiMatchDao.class);
                    dao.closeMatch(state.result().getId());
                }));
    }

    @Override
    protected void onLeaveMatch(final CancelableMatchStateRecord<RequestT> state) {
        getRequest()
                .getProtocolMessageHandler()
                .submit(() -> getTransactionProvider().get().performAndCloseV(txn -> {

                    final var dao = txn.getDao(MultiMatchDao.class);
                    final var result = dao.removeProfile(state.result().getId(), getRequest().getProfile());

                    if (result.getCount() == 0) {
                        // We are the last profile, delete the match. The database will not need a dangling or empty
                        // match hanging around in the queue.
                        dao.deleteMultiMatch(result.getId());
                    }

                }));
    }

    @Override
    protected void onOpenMatch(final CancelableMatchStateRecord<RequestT> state) {
        getTransactionProvider().get().performAndCloseV(txn -> {
            final var dao = txn.getDao(MultiMatchDao.class);
            final var match = dao.getMultiMatch(state.result().getId());
            dao.openMatch(match.getId());
        });
    }

    @Override
    protected void onResult(final CancelableMatchStateRecord<RequestT> state, final MultiMatch result) {
        getRequest().success(this);
    }

    public Provider<Transaction> getTransactionProvider() {
        return transactionProvider;
    }

}
