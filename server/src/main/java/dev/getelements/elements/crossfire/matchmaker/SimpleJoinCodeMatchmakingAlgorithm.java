package dev.getelements.elements.crossfire.matchmaker;

import dev.getelements.elements.crossfire.api.JoinCodeMatchmakingAlgorithm;
import dev.getelements.elements.crossfire.api.MatchHandle;
import dev.getelements.elements.crossfire.api.MatchmakingRequest;
import dev.getelements.elements.crossfire.api.model.handshake.CreateHandshakeRequest;
import dev.getelements.elements.crossfire.api.model.handshake.CreatedHandshakeResponse;
import dev.getelements.elements.crossfire.api.model.handshake.HandshakeResponse;
import dev.getelements.elements.crossfire.api.model.handshake.JoinCodeHandshakeRequest;
import dev.getelements.elements.crossfire.util.CancelableMatchStateRecord;
import dev.getelements.elements.crossfire.util.StandardCancelableMatchHandle;
import dev.getelements.elements.sdk.annotation.ElementDefaultAttribute;
import dev.getelements.elements.sdk.annotation.ElementServiceExport;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.dao.Transaction;
import dev.getelements.elements.sdk.dao.UniqueCodeDao;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import dev.getelements.elements.sdk.model.match.MultiMatchStatus;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;

/**
 * A simple implementation of the JoinCodeMatchmakingAlgorithm that uses simple join codes to determine matches.
 */
@ElementServiceExport(value = JoinCodeMatchmakingAlgorithm.class)
@ElementServiceExport(value = JoinCodeMatchmakingAlgorithm.class, name = SimpleJoinCodeMatchmakingAlgorithm.NAME)
public class SimpleJoinCodeMatchmakingAlgorithm implements JoinCodeMatchmakingAlgorithm {

    public static final String NAME = "SIMPLE_JOIN_CODE";

    @ElementDefaultAttribute(value = "4", description = "The length of the join code to be generated.")
    public static final String JOIN_CODE_LENGTH = "dev.getelements.elements.crossfire.matchmaker.join.code.length";

    @ElementDefaultAttribute(value = "2000", description = "The maximum number of attempts to generate a unique join code.")
    public static final String MAX_ATTEMPTS = "dev.getelements.elements.crossfire.matchmaker.join.code.max.attempts";

    private int maxAttempts;

    private int joinCodeLength;

    private Provider<Transaction> transactionProvider;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public MatchHandle<CreateHandshakeRequest> initialize(final MatchmakingRequest<CreateHandshakeRequest> request) {
        return new JoinCodeMatchHandle(request);
    }

    @Override
    public MatchHandle<JoinCodeHandshakeRequest> resume(final MatchmakingRequest<JoinCodeHandshakeRequest> request) {
        return new StandardJoinCodeMatchHandle(this, request, getTransactionProvider());
    }

    public Provider<Transaction> getTransactionProvider() {
        return transactionProvider;
    }

    @Inject
    public void setTransactionProvider(Provider<Transaction> transactionProvider) {
        this.transactionProvider = transactionProvider;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    @Inject
    public void setMaxAttempts(@Named(MAX_ATTEMPTS) int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getJoinCodeLength() {
        return joinCodeLength;
    }

    @Inject
    public void setJoinCodeLength(@Named(JOIN_CODE_LENGTH) int joinCodeLength) {
        this.joinCodeLength = joinCodeLength;
    }

    private class JoinCodeMatchHandle extends StandardCancelableMatchHandle<CreateHandshakeRequest> {

        public JoinCodeMatchHandle(final MatchmakingRequest<CreateHandshakeRequest> request) {
            super(SimpleJoinCodeMatchmakingAlgorithm.this, request, SimpleJoinCodeMatchmakingAlgorithm.this.getTransactionProvider());
        }

        @Override
        public HandshakeResponse newHandshakeResponse() {
            final var response = new CreatedHandshakeResponse();
            response.setMatchId(getResult().getId());
            response.setJoinCode(getResult().getJoinCode().getId());
            response.setProfileId(getRequest().getProfile().getId());
            return response;
        }

        @Override
        protected void onMatching(final CancelableMatchStateRecord<CreateHandshakeRequest> state) {
            getRequest().getServer().submit(() -> {
                final var result = getTransactionProvider().get().performAndClose(txn -> {

                    final var dao = txn.getDao(MultiMatchDao.class);
                    final var configuration = getRequest().getApplicationConfiguration();

                    final var parameters = new UniqueCodeDao.GenerationParameters(
                        configuration.getTimeoutSeconds(),
                        configuration.getLingerSeconds(),
                        getJoinCodeLength(),
                        getMaxAttempts(),
                        getRequest().getProfile().getUser(),
                        getRequest().getProfile()
                    );

                    var match = new MultiMatch();
                    match.setConfiguration(getRequest().getApplicationConfiguration());
                    match.setStatus(MultiMatchStatus.OPEN);

                    match = dao.createMultiMatch(match, parameters);
                    return dao.addProfile(match.getId(), getRequest().getProfile());

                });

                setResult(result);

            });
        }

    }

}
