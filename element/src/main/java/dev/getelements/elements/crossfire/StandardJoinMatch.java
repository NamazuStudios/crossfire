package dev.getelements.elements.crossfire;

import dev.getelements.elements.crossfire.api.CancelableMatchStateRecord;
import dev.getelements.elements.crossfire.api.MatchmakingAlgorithm;
import dev.getelements.elements.crossfire.api.MatchmakingRequest;
import dev.getelements.elements.crossfire.api.StandardCancelableMatch;
import dev.getelements.elements.crossfire.model.handshake.JoinHandshakeRequest;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.dao.Transaction;
import dev.getelements.elements.sdk.model.exception.MultiMatchNotFoundException;
import jakarta.inject.Provider;

public class StandardJoinMatch extends StandardCancelableMatch<JoinHandshakeRequest> {

    public StandardJoinMatch(
            final MatchmakingAlgorithm algorithm,
            final MatchmakingRequest<JoinHandshakeRequest> request,
            final Provider<Transaction> transactionProvider) {
        super(algorithm, request, transactionProvider);
    }

    @Override
    protected void onMatching(final CancelableMatchStateRecord<JoinHandshakeRequest> state) {
        getRequest().getProtocolMessageHandler().submit(this::doFind);
    }

    private void doFind() {
        try (final var transaction = getTransactionProvider().get()) {

            final var dao = transaction.getDao(MultiMatchDao.class);
            final var handshakeRequest = getRequest().getHandshakeRequest();

            final var result = dao
                    .findMultiMatch(handshakeRequest.getMatchId())
                    .filter(m -> dao.getProfiles(m.getId())
                            .stream()
                            .anyMatch(p -> p.getId().equals(getRequest().getProfile().getId()))
                    )
                    .orElseThrow(MultiMatchNotFoundException::new);

            result(result);

        }
    }

}
