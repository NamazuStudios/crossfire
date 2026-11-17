package dev.getelements.elements.crossfire.matchmaker;

import com.mongodb.MongoException;
import dev.getelements.elements.crossfire.api.MatchmakingAlgorithm;
import dev.getelements.elements.crossfire.api.MatchmakingRequest;
import dev.getelements.elements.crossfire.api.model.handshake.JoinCodeHandshakeRequest;
import dev.getelements.elements.crossfire.util.CancelableMatchStateRecord;
import dev.getelements.elements.crossfire.util.StandardCancelableMatchHandle;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.dao.Transaction;
import dev.getelements.elements.sdk.model.exception.MultiMatchNotFoundException;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import dev.getelements.elements.sdk.model.profile.Profile;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StandardJoinCodeMatchHandle extends StandardCancelableMatchHandle<JoinCodeHandshakeRequest> {

    private static final Logger logger = LoggerFactory.getLogger(StandardJoinCodeMatchHandle.class);

    /**
     * The {@link MultiMatchDao} bound directly to this Element's own (non transaction scoped) Datastore.
     * Committing a transaction makes its writes immediately visible to out-of-transaction reads, so the
     * fallback read (see {@link #locateAndJoin(String)}) using this DAO resolves committed join codes even
     * in the transient window the transactional path may miss.
     */
    private Provider<MultiMatchDao> multiMatchDaoProvider;

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

        // Concurrent JOIN_CODE guests each mutate the same match document (one addProfile per
        // transaction). Under a burst (e.g. a code dropped into Twitch chat) those transactions
        // collide with MongoDB WriteConflict (112, errorLabel TransientTransactionError) —
        // harness testing on Elements 3.9.0-rc-9 measured an 80% join failure rate for a
        // simultaneous 7-guest burst. MongoDB mandates retrying such errors; the addProfile is
        // idempotent (the in-match check re-reads on every attempt), so a bounded retry is safe.
        // Backoff is short and capped — unlike the lookup retry deliberately absent above, this
        // covers a genuine transient conflict, not an unbounded snapshot-staleness window.
        long backoffMillis = TRANSIENT_TXN_BACKOFF_INITIAL_MS;

        for (int attempt = 1; ; attempt++) {

            try {
                setResult(locateAndJoin(joinCode));
                return;
            } catch (final MongoException e) {

                final var transientConflict = e.hasErrorLabel("TransientTransactionError");
                final var unknownCommit = e.hasErrorLabel("UnknownTransactionCommitResult");

                if ((!transientConflict && !unknownCommit) || attempt >= MAX_TRANSIENT_TXN_RETRIES) {
                    throw e;
                }

                logger.warn(
                        "Transient MongoDB error joining match by join code {} (attempt {}/{}). " +
                        "Retrying in {}ms: {}",
                        joinCode, attempt, MAX_TRANSIENT_TXN_RETRIES, backoffMillis, e.getMessage()
                );

                try {
                    Thread.sleep(backoffMillis);
                } catch (final InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while retrying transient MongoDB error", ie);
                }

                backoffMillis = Math.min(backoffMillis * 2, TRANSIENT_TXN_BACKOFF_MAX_MS);

            }

        }

    }

    private static final int MAX_TRANSIENT_TXN_RETRIES = 8;
    private static final long TRANSIENT_TXN_BACKOFF_INITIAL_MS = 25L;
    private static final long TRANSIENT_TXN_BACKOFF_MAX_MS = 500L;

    /**
     * <p>Locates the match for the supplied join code and joins the requesting profile to it.</p>
     *
     * <p>The lookup is first attempted transactionally — the correct, consistent path. However, mongod
     * may serve a transaction read from a snapshot which does not yet include a commit made moments
     * earlier by an unrelated party whose pooled logical session had just committed, yielding a transient
     * {@link MultiMatchNotFoundException} even though the join code is committed and visible to ordinary
     * (out-of-transaction) reads. See NamazuStudios/crossfire#20 and the upstream MongoDB filing recorded
     * in NamazuStudios/elements#207.</p>
     *
     * <p>On that first miss, a single out-of-transaction read observes committed state directly
     * (out-of-transaction reads were verified clean in every condition tested, including committed
     * codes missed by transactional reads for up to ~20s after the commit). If the code is genuinely
     * dead or never existed, that read rethrows the {@link MultiMatchNotFoundException} exactly as the
     * transactional path would have, so failure semantics for invalid codes are unchanged and remain
     * immediate.</p>
     *
     * <p>Note there is intentionally no retry loop: a bounded retry with backoff could cover only a
     * fraction of the observed window (and would block this connection's executor thread for the
     * latency of each sleep), while the fallback resolves the miss deterministically on the first
     * attempt.</p>
     */
    private MultiMatch locateAndJoin(final String joinCode) {

        try (final var transaction = getTransactionProvider().get()) {

            final var dao = transaction.getDao(MultiMatchDao.class);
            final var multiMatch = attemptToJoin(dao, joinCode);

            transaction.commit();

            return multiMatch;

        } catch (MultiMatchNotFoundException e) {

            logger.debug(
                    "Transactional join-code lookup did not find join code {}. Reason: {}.",
                    joinCode, e.getMessage() == null ? "not found" : e.getMessage());

            // Most likely a transaction snapshot which predated the join code's own commit. An
            // out-of-transaction read observes committed state directly, so give it one shot before
            // declaring the code not found. If this truly is a dead (or never-existed) code, this
            // read rethrows the MultiMatchNotFoundException exactly as the original single-attempt
            // implementation did.

            final var multiMatch = getMultiMatchDaoProvider().get().getMultiMatchByJoinCode(joinCode);
            joinInNewTransaction(multiMatch);
            return multiMatch;

        }

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

}
