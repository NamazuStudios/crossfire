package dev.getelements.elements.crossfire.matchmaker;

import dev.getelements.elements.crossfire.api.MatchHandle;
import dev.getelements.elements.crossfire.api.MatchmakingAlgorithm;
import dev.getelements.elements.crossfire.api.MatchmakingRequest;
import dev.getelements.elements.crossfire.api.Server;
import dev.getelements.elements.crossfire.api.model.handshake.CreateHandshakeRequest;
import dev.getelements.elements.crossfire.api.model.handshake.HandshakeResponse;
import dev.getelements.elements.crossfire.api.model.handshake.JoinCodeHandshakeRequest;

import dev.getelements.elements.crossfire.api.MatchPhase;
import dev.getelements.elements.crossfire.api.model.Version;
import dev.getelements.elements.crossfire.util.CancelableMatchStateRecord;
import dev.getelements.elements.sdk.dao.MultiMatchDao;
import dev.getelements.elements.sdk.dao.UniqueCodeDao.GenerationParameters;
import dev.getelements.elements.sdk.dao.Transaction;
import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
import dev.getelements.elements.sdk.model.exception.MultiMatchNotFoundException;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import dev.getelements.elements.sdk.model.match.MultiMatchStatus;
import dev.getelements.elements.sdk.model.Pagination;
import dev.getelements.elements.sdk.model.profile.Profile;
import dev.getelements.elements.sdk.model.user.User;
import jakarta.inject.Provider;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.UUID.randomUUID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Test for the crossfire#20 mitigation in StandardJoinCodeMatchHandle: when the
 * transactional join-code lookup transiently misses a just-committed join code, the handle
 * must fall back to a single out-of-transaction read (after which the guest still joins),
 * while a dead join code propagates {@link MultiMatchNotFoundException} immediately with the
 * original failure semantics. There is intentionally no retry loop and no thread blocking.
 */
public class StandardJoinCodeMatchHandleRetryTest {

    private Profile profile;

    private JoinCodeHandshakeRequest handshakeRequest;

    private MultiMatchDaoStub transactionDao;

    private MultiMatchDaoStub mainDao;

    private AtomicInteger transactionCount;

    private List<String> profilesAdded;

    private StandardJoinCodeMatchHandle handle;

    @BeforeMethod
    public void setUp() {

        profile = new Profile();
        profile.setId(randomUUID().toString());

        final var user = new User();
        user.setId(randomUUID().toString());
        profile.setUser(user);

        final var match = new MultiMatch();
        match.setId(randomUUID().toString());
        match.setStatus(MultiMatchStatus.OPEN);
        match.setConfiguration(new MatchmakingApplicationConfiguration());

        transactionDao = new MultiMatchDaoStub(match);
        mainDao = new MultiMatchDaoStub(match);

        transactionCount = new AtomicInteger();

        profilesAdded = new ArrayList<>();

        transactionDao.setFindMultiMatchByJoinCode(index -> Optional.empty());
        transactionDao.setGetProfiles(matchId -> new ArrayList<>());
        transactionDao.setAddProfile((matchId, joiningProfile) -> { profilesAdded.add(joiningProfile.getId()); return match; });

        mainDao.setFindMultiMatchByJoinCode(index -> Optional.of(match));
        mainDao.setGetProfiles(matchId -> new ArrayList<>());
        mainDao.setAddProfile((matchId, joiningProfile) -> { profilesAdded.add(joiningProfile.getId()); return match; });

        handshakeRequest = new JoinCodeHandshakeRequest();
        handshakeRequest.setJoinCode("TEST");

        handle = new StandardJoinCodeMatchHandle(
                new MatchmakingAlgorithmStub(),
                new MatchmakingRequestStub(profile, handshakeRequest),
                () -> new TransactionStub(transactionDao),
                () -> mainDao);

    }

    @Test
    public void testTransactionalLookupJoinsDirectly() {

        // Transaction attempt sees the committed code (no anomaly present): the guest joins
        // on the transactional path alone, with no fallback read.

        transactionDao.setFindMultiMatchByJoinCode(index -> Optional.of(transactionDao.match));

        simulateJoinAttempt();

        assertEquals(profilesAdded.size(), 1, "Guest must have been added exactly once");
        assertEquals(transactionDao.reads.get(), 1, "Exactly one transactional read attempt expected");
        assertEquals(mainDao.reads.get(), 0, "No out-of-transaction fallback read expected");

    }

    @Test
    public void testFallbackReadJoinsWhenTransactionalAttemptMisses() {

        // The first (and only) transactional read misses the committed code (stale snapshot);
        // the out-of-transaction fallback read observes the committed code and the guest joins
        // in a fresh transaction. No retries, no sleeps, single fallback read.

        simulateJoinAttempt();

        assertEquals(profilesAdded.size(), 1, "Guest must have joined via the fallback path");
        assertEquals(transactionDao.reads.get(), 1, "Exactly one transactional read attempt expected");
        assertEquals(mainDao.reads.get(), 1, "Exactly one out-of-transaction fallback read expected");

    }

    @Test
    public void testDeadJoinCodeStillSurfacesNotFound() {

        mainDao.setFindMultiMatchByJoinCode(index -> Optional.empty());

        try {
            simulateJoinAttempt();
            fail("Expected MultiMatchNotFoundException for a dead join code");
        } catch (MultiMatchNotFoundException e) {
            // expected propagation — same failure semantics as the original implementation
        }

        assertEquals(profilesAdded.size(), 0);

    }

    private void simulateJoinAttempt() {
        handle.startMatching();
    }

    private static class MultiMatchDaoStub implements MultiMatchDao {

        final MultiMatch match;

        Function<Integer, Optional<MultiMatch>> findMultiMatchByJoinCode;

        Function<String, List<Profile>> getProfiles;

        BiFunction<String, Profile, MultiMatch> addProfile;

        final AtomicInteger reads = new AtomicInteger();

        MultiMatchDaoStub(final MultiMatch match) {
            this.match = match;
        }

        void setFindMultiMatchByJoinCode(final Function<Integer, Optional<MultiMatch>> behavior) {
            this.findMultiMatchByJoinCode = behavior;
        }

        void setGetProfiles(final Function<String, List<Profile>> behavior) {
            this.getProfiles = behavior;
        }

        void setAddProfile(final BiFunction<String, Profile, MultiMatch> behavior) {
            this.addProfile = behavior;
        }

        @Override
        public MultiMatch getMultiMatchByJoinCode(final String joinCode) {
            reads.incrementAndGet();
            return findMultiMatchByJoinCode.apply(reads.get()).orElseThrow(MultiMatchNotFoundException::new);
        }

        @Override
        public Optional<MultiMatch> findMultiMatchByJoinCode(final String joinCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Profile> getProfiles(final String multiMatchId) {
            return getProfiles.apply(multiMatchId);
        }

        @Override
        public MultiMatch addProfile(final String multiMatchId, final Profile profile) {
            return addProfile.apply(multiMatchId, profile);
        }

        @Override
        public List<MultiMatch> getAllMultiMatches(final String query) { return List.of(); }

        @Override
        public Pagination<MultiMatch> getMultiMatches(final int offset, final int count, final String search) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<MultiMatch> findMultiMatch(final String multiMatchId) { return Optional.empty(); }

        @Override
        public Optional<MultiMatch> findOldestAvailableMultiMatchCandidate(
                final MatchmakingApplicationConfiguration configuration,
                final String profileId,
                final String query) { return Optional.empty(); }

        @Override
        public MultiMatch removeProfile(final String multiMatchId, final Profile profile) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MultiMatch createMultiMatch(final MultiMatch multiMatch) { throw new UnsupportedOperationException(); }

        @Override
        public MultiMatch createMultiMatch(final MultiMatch multiMatch, GenerationParameters generationParameters) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MultiMatch updateMultiMatch(final String matchId, final MultiMatch configuration) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MultiMatch openMatch(final String matchId) { throw new UnsupportedOperationException(); }

        @Override
        public MultiMatch closeMatch(final String matchId) { throw new UnsupportedOperationException(); }

        @Override
        public MultiMatch endMatch(final String matchId) { throw new UnsupportedOperationException(); }

        @Override
        public MultiMatch refreshMatch(final String matchId) { throw new UnsupportedOperationException(); }

        @Override
        public void deleteAllMultiMatches() { }

        @Override
        public boolean tryDeleteMultiMatch(final String matchId) { return false; }

    }

    private static class TransactionStub implements Transaction {

        private final MultiMatchDao dao;

        TransactionStub(final MultiMatchDao dao) { this.dao = dao; }

        @Override
        @SuppressWarnings("unchecked")
        public <DaoT> DaoT getDao(final Class<DaoT> daoClass) {
            if (!MultiMatchDao.class.equals(daoClass)) {
                throw new IllegalArgumentException("Unexpected dao: " + daoClass);
            }
            return (DaoT) dao;
        }

        @Override public void commit() { }
        @Override public void start() { }
        @Override public void rollback() { }
        @Override public void close() { }
        @Override public boolean isActive() { return false; }

        @Override
        public <T> Function<Transaction, T> wrap(final Function<Transaction, T> original) {
            return original;
        }

    }

    private static class MatchmakingAlgorithmStub
            implements MatchmakingAlgorithm<JoinCodeHandshakeRequest, JoinCodeHandshakeRequest> {
        @Override public String getName() { return "STUB"; }
        @Override public MatchHandle<JoinCodeHandshakeRequest> initialize(MatchmakingRequest<JoinCodeHandshakeRequest> request) { throw new UnsupportedOperationException(); }
        @Override public MatchHandle<JoinCodeHandshakeRequest> resume(MatchmakingRequest<JoinCodeHandshakeRequest> request) { throw new UnsupportedOperationException(); }
    }

    private static class MatchmakingRequestStub implements MatchmakingRequest<JoinCodeHandshakeRequest> {

        private final Profile profile;
        private final JoinCodeHandshakeRequest handshakeRequest;

        MatchmakingRequestStub(final Profile profile, final JoinCodeHandshakeRequest handshakeRequest) {
            this.profile = profile;
            this.handshakeRequest = handshakeRequest;
        }

        @Override public Profile getProfile() { return profile; }
        @Override public MatchmakingApplicationConfiguration getApplicationConfiguration() { return new MatchmakingApplicationConfiguration(); }
        @Override public JoinCodeHandshakeRequest getHandshakeRequest() { return handshakeRequest; }
        @Override public Server getServer() { return task -> { task.run(); return null; }; }
        @Override public void failure() { }
        @Override public void failure(final Throwable throwable) { }
        @Override public void success(final MatchHandle<JoinCodeHandshakeRequest> matchHandle) { }

    }

}
