package dev.getelements.elements.crossfire.matchmaker;

import dev.getelements.elements.crossfire.api.MatchmakingAlgorithm;
import dev.getelements.elements.crossfire.api.MatchmakingRequest;
import dev.getelements.elements.crossfire.api.model.handshake.JoinCodeHandshakeRequest;
import dev.getelements.elements.sdk.annotation.ElementDefaultAttribute;
import dev.getelements.elements.crossfire.util.CancelableMatchStateRecord;
import dev.getelements.elements.crossfire.util.StandardCancelableMatchHandle;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.dao.Transaction;
import dev.getelements.elements.sdk.model.exception.MultiMatchNotFoundException;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import dev.getelements.elements.sdk.model.profile.Profile;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StandardJoinCodeMatchHandle extends StandardCancelableMatchHandle<JoinCodeHandshakeRequest> {

    private static final Logger logger = LoggerFactory.getLogger(StandardJoinCodeMatchHandle.class);

    /**
     * Bounded number of transactional attempts used to locate a match by its join code.
     * mongod may serve a transaction read from a snapshot which does not yet include a commit
     * made moments earlier by an unrelated party (session/other client), yielding a transient
     * {@link MultiMatchNotFoundException} even though the join code is committed and visible to
     * ordinary (out-of-transaction) reads. See NamazuStudios/crossfire#20 and the linked upstream
     * MongoDB filing recorded in the Crossfire design notebook.
     */
    @ElementDefaultAttribute("5")
    public static final String JOIN_CODE_LOOKUP_ATTEMPTS = "dev.getelements.elements.crossfire.matchmaker.join.code.lookup.attempts";

    /**
     * Milliseconds to wait between failed transactional join-code lookup attempts.
     */
    @ElementDefaultAttribute("250")
    public static final String JOIN_CODE_LOOKUP_BACKOFF_MILLIS = "dev.getelements.elements.crossfire.matchmaker.join.code.lookup.backoff.millis";

    /**
     * The {@link MultiMatchDao} bound directly to this Element's own (non transaction scoped) Datastore.
     * Committing a transaction makes its writes immediately visible to out-of-transaction reads, so a
     * final fallback read using this DAO resolves committed join codes even in the transient window the
     * transactional path may miss.
     */
    private Provider<MultiMatchDao> multiMatchDaoProvider;

    private int joinCodeLookupAttempts;

    private long joinCodeLookupBackoffMillis;

    public StandardJoinCodeMatchHandle(
            final MatchmakingAlgorithm<?,?> algorithm,
            final MatchmakingRequest<JoinCodeHandshakeRequest> request,
            final Provider<Transaction> transactionProvider,
            final Provider<MultiMatchDao> multiMatchDaoProvider) {
        super(algorithm, request, transactionProvider);
        this.multiMatchDaoProvider = multiMatchDaoProvider;
    }

    @Override
    protected void onMatching(final CancelableMatchStateRecord<JoinCodeHandshakeRequest> state) {

        getRequest().getServer().submit(this::doFind);
    }

    private void doFind() {

        final var joinCode = getRequest().getHandshakeRequest().getJoinCode();

        final var multiMatchByJoinCode = locateAndJoin(joinCode);

        setResult(multiMatchByJoinCode);

    }

    private MultiMatch locateAndJoin(final String joinCode) {

        final var failure = new StringBuilder();

        for (int attempt = 0; attempt < getJoinCodeLookupAttempts(); ++attempt) {

            if (attempt > 0) {
                try {
                    Thread.sleep(getJoinCodeLookupBackoffMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted during join code lookup.", e);
                }
            }

            try (final var transaction = getTransactionProvider().get()) {

                final var dao = transaction.getDao(MultiMatchDao.class);
                final var multiMatch = attemptToJoin(dao, joinCode);

                transaction.commit();

                return multiMatch;

            } catch (MultiMatchNotFoundException e) {

                logger.debug(
                        "Attempt {}/{} did not find join code {}. Reason: {}.",
                        attempt + 1, getJoinCodeLookupAttempts(), joinCode, e.getMessage() == null ? "not found" : e.getMessage());

                failure.append(e.getMessage() == null ? e.getClass().getName() : e.getMessage());

                if (attempt > 0) failure.append("; ");

            }

        }

        // All transactional attempts missed the join code, most likely against a snapshot which
        // predated the join code's own commit. An out-of-transaction read observes committed
        // state directly, so give it one shot before declaring the code not found. If this
        // truly is a dead (or never-existed) code, this read rethrows the MultiMatchNotFoundException
        // exactly as the original single-attempt implementation did.

        logger.debug(
                "Join code lookup exhausted {} transactional attempts. Attempting out-of-transaction read for join code {}.",
                getJoinCodeLookupAttempts(), joinCode);

        final var multiMatch = getMultiMatchDaoProvider().get().getMultiMatchByJoinCode(joinCode);
        joinInNewTransaction(multiMatch);
        return multiMatch;

    }

    private MultiMatch attemptToJoin(final MultiMatchDao dao, final String joinCode) {

        final var handshakeRequest = getRequest().getHandshakeRequest();
        final var multiMatchByJoinCode = dao.getMultiMatchByJoinCode(joinCode);

        final var profileId = getRequest().getProfile().getId();

        final var inMatch = dao
                .getProfiles(multiMatchByJoinCode.getId())
                .stream()
                .map(Profile::getId)
                .anyMatch(pid -> pid.equals(profileId));

        if (!inMatch) {
            dao.addProfile(multiMatchByJoinCode.getId(), getRequest().getProfile());
        }

        return multiMatchByJoinCode;

    }

    /**
     * Joins the supplied {@link MultiMatch} within a fresh transaction, mirroring the behavior of
     * {@link #attemptToJoin(MultiMatchDao, String)} as it would have been reached from a committed,
     * out-of-transaction read of the join code.
     */
    private void joinInNewTransaction(final MultiMatch multiMatch) {

        try (final var transaction = getTransactionProvider().get()) {

            final var dao = transaction.getDao(MultiMatchDao.class);

            final var profileId = getRequest().getProfile().getId();

            final var inMatch = dao
                    .getProfiles(multiMatch.getId())
                    .stream()
                    .map(Profile::getId)
                    .anyMatch(pid -> pid.equals(profileId));

            if (!inMatch) {
                dao.addProfile(multiMatch.getId(), getRequest().getProfile());
            }

            transaction.commit();

        }

    }

    public Provider<MultiMatchDao> getMultiMatchDaoProvider() {
        return multiMatchDaoProvider;
    }

    public int getJoinCodeLookupAttempts() {
        return joinCodeLookupAttempts;
    }

    @Inject
    public void setJoinCodeLookupAttempts(@Named(JOIN_CODE_LOOKUP_ATTEMPTS) int joinCodeLookupAttempts) {
        this.joinCodeLookupAttempts = joinCodeLookupAttempts;
    }

    public long getJoinCodeLookupBackoffMillis() {
        return joinCodeLookupBackoffMillis;
    }

    @Inject
    public void setJoinCodeLookupBackoffMillis(@Named(JOIN_CODE_LOOKUP_BACKOFF_MILLIS) long joinCodeLookupBackoffMillis) {
        this.joinCodeLookupBackoffMillis = joinCodeLookupBackoffMillis;
    }

}
