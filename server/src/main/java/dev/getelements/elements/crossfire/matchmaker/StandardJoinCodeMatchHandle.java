package dev.getelements.elements.crossfire.matchmaker;

import dev.getelements.elements.crossfire.api.MatchmakingAlgorithm;
import dev.getelements.elements.crossfire.api.MatchmakingRequest;
import dev.getelements.elements.crossfire.api.model.handshake.JoinCodeHandshakeRequest;
import dev.getelements.elements.crossfire.util.CancelableMatchStateRecord;
import dev.getelements.elements.crossfire.util.StandardCancelableMatchHandle;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.dao.Transaction;
import dev.getelements.elements.sdk.model.profile.Profile;
import jakarta.inject.Provider;

public class StandardJoinCodeMatchHandle extends StandardCancelableMatchHandle<JoinCodeHandshakeRequest> {

    public StandardJoinCodeMatchHandle(
            final MatchmakingAlgorithm<?,?> algorithm,
            final MatchmakingRequest<JoinCodeHandshakeRequest> request,
            final Provider<Transaction> transactionProvider) {
        super(algorithm, request, transactionProvider);
    }

    @Override
    protected void onMatching(final CancelableMatchStateRecord<JoinCodeHandshakeRequest> state) {

        getRequest().getServer().submit(this::doFind);
    }

    private void doFind() {
        try (final var transaction = getTransactionProvider().get()) {

            final var dao = transaction.getDao(MultiMatchDao.class);
            final var handshakeRequest = getRequest().getHandshakeRequest();
            final var multiMatchByJoinCode = dao.getMultiMatchByJoinCode(handshakeRequest.getJoinCode());

            final var profileId = getRequest().getProfile().getId();

            final var inMatch = dao
                    .getProfiles(multiMatchByJoinCode.getId())
                    .stream()
                    .map(Profile::getId)
                    .anyMatch(pid -> pid.equals(profileId));

            if (!inMatch) {
                dao.addProfile(multiMatchByJoinCode.getId(), getRequest().getProfile());
            }

            setResult(multiMatchByJoinCode);

        }
    }

}
