package dev.getelements.elements.crossfire;

import dev.getelements.elements.crossfire.client.*;
import dev.getelements.elements.crossfire.client.Peer.Message;
import dev.getelements.elements.crossfire.client.Peer.StringMessage;
import dev.getelements.elements.crossfire.model.Protocol;
import dev.getelements.elements.crossfire.model.Version;
import dev.getelements.elements.crossfire.model.handshake.FindHandshakeRequest;
import dev.getelements.elements.crossfire.model.signal.ConnectBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.HostBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.dao.ApplicationConfigurationDao;
import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
import dev.getelements.elements.sdk.model.profile.Profile;
import dev.getelements.elements.sdk.model.session.SessionCreation;
import dev.getelements.elements.sdk.model.user.User;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static dev.getelements.elements.crossfire.client.Crossfire.Mode.SIGNALING_CLIENT;
import static dev.getelements.elements.crossfire.client.SignalingClientPhase.CONNECTED;
import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.MATCHED;
import static dev.getelements.elements.sdk.model.user.User.Level.USER;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.regex.Pattern.quote;
import static org.testng.Assert.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

public class TestBasicMatchmaking {

    public static final int TEST_PLAYER_COUNT = 4;

    public static final String TEST_BASIC_MATCHMAKING = "test_basic_matchmaking";

    private static final Logger logger = LoggerFactory.getLogger(TestBasicMatchmaking.class);

    private final TestServer server = TestServer.getInstance();

    private List<TestContext> testContextList = List.of();

    private WebSocketContainer webSocketContainer;

    private MatchmakingApplicationConfiguration configuration;

    @DataProvider
    public Object[][] allContexts() {

        if (testContextList.isEmpty()) {
            Assert.fail("Test contexts are not initialized. Ensure setupContexts() succeeds called before running tests.");
        }

        return testContextList
                .stream()
                .map(c -> new Object[]{c})
                .toArray(Object[][]::new);

    }

    @DataProvider
    public Object[][] host() {

        if (testContextList.isEmpty()) {
            Assert.fail("Test contexts are not initialized. Ensure setupContexts() succeeds called before running tests.");
        }

        return testContextList
                .stream()
                .filter(c -> c.signalingClient().getState().isHost())
                .map(c -> new Object[]{c})
                .toArray(Object[][]::new);

    }

    @DataProvider
    public Object[][] client() {

        if (testContextList.isEmpty()) {
            Assert.fail("Test contexts are not initialized. Ensure setupContexts() succeeds called before running tests.");
        }

        return testContextList
                .stream()
                .filter(c -> !c.signalingClient().getState().isHost())
                .map(c -> new Object[]{c})
                .toArray(Object[][]::new);

    }

    @BeforeClass
    public void setupContainer() {
        webSocketContainer = ContainerProvider.getWebSocketContainer();
    }

    @BeforeClass(dependsOnMethods = "setupContainer")
    public void setupContexts() {
        testContextList = IntStream.range(0, TEST_PLAYER_COUNT)
                .mapToObj(i -> {

                    final var user = server.createUser(format("test_%d_user", i), USER);
                    final var profile = server.createProfile(user, format("test_%d_profile", i));
                    final var session = server.newSessionForUser(user, profile);

                    final var crossfire = new StandardCrossfire.Builder()
                            .withDefaultUri(server.getTestTestServerWsUrl())
                            .withWebSocketContainer(webSocketContainer)
                            .withDefaultProtocol(Protocol.SIGNALING)
                            .withSupportedModes(Crossfire.Mode.SIGNALING_HOST, SIGNALING_CLIENT)
                            .build()
                            .connect();

                    return new TestContext(crossfire, user, profile, session,
                            new BlockingArrayQueue<>(),
                            new BlockingArrayQueue<>(),
                            new BlockingArrayQueue<>(),
                            Subscription.begin()
                    );

                })
                .toList();
    }

    @BeforeClass
    public void setupConfiguration() {

        final var application = server.getApplication();
        final var configuration = new MatchmakingApplicationConfiguration();
        configuration.setName(TEST_BASIC_MATCHMAKING);
        configuration.setMaxProfiles(TEST_PLAYER_COUNT);
        configuration.setDescription("Test Basic Matchmaking Application");
        configuration.setParent(application);

        this.configuration = server
                .getDao(ApplicationConfigurationDao.class)
                .createApplicationConfiguration(application.getId(), configuration);

        logger.info("Configuration created.");

    }

    @Test(dataProvider = "allContexts",
          threadPoolSize = TEST_PLAYER_COUNT
    )
    public void testFindHandshake(final TestContext context) throws InterruptedException {

        assertEquals(context.signalingClient().getPhase(), CONNECTED);

        final var request = new FindHandshakeRequest();
        request.setVersion(Version.V_1_0);
        request.setConfiguration(configuration.getName());
        request.setProfileId(context.profile().getId());
        request.setSessionKey(context.creation().getSessionSecret());

        final var response = context.signalingClient().handshake(request, 30, TimeUnit.SECONDS);

        assertNotNull(response);
        assertEquals(response.getType(), MATCHED);
        assertNotNull(response.getMatchId());

        assertEquals(context.signalingClient().getPhase(), SignalingClientPhase.SIGNALING);
        assertEquals(context.signalingClient().getHandshakeResponse().getMatchId(), response.getMatchId());
        assertEquals(context.signalingClient().findHandshakeResponse().get().getMatchId(), response.getMatchId());

        logger.info("Found match: {}", response.getMatchId());

    }

    @Test(dependsOnMethods = "testFindHandshake")
    public void testTestAllJoinedSameMatch() {

        final var uniqueMatchIds = testContextList.stream()
                .map(TestContext::signalingClient)
                .map(SignalingClient::getState)
                .map(SignalingClient.MatchState::getMatchId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        assertEquals(uniqueMatchIds.size(), 1, "All clients should have joined the same match");

    }

    @Test(dataProvider = "allContexts",
          dependsOnMethods = "testTestAllJoinedSameMatch",
          threadPoolSize = TEST_PLAYER_COUNT
    )
    public void testAllConnectedAndHostAssigned(final TestContext context) throws InterruptedException {

        String actualHostId = null;

        final var expectedProfileIds = testContextList
                .stream()
                .filter(tc -> tc != context)
                .map(TestContext::profile)
                .map(Profile::getId)
                .collect(Collectors.toSet());

        final var signals = new ArrayList<Signal>();

        while (actualHostId == null && !expectedProfileIds.isEmpty()) {

            final var signal = context.signals().take();
            logger.info("Signal received: {} from context {}.", signal.getType(), context.profile().getId());

            signals.add(signal);

            switch (signal.getType()) {
                case HOST -> actualHostId = ((HostBroadcastSignal)signal).getProfileId();
                case CONNECT -> expectedProfileIds.remove(((ConnectBroadcastSignal)signal).getProfileId());
            }

        }

        final var expectedHostId = context
                .signalingClient()
                .getState()
                .getHost();

        assertEquals(actualHostId, expectedHostId, "Host mismatch.");
        signals.forEach(s -> logger.info("Signal dequeued: {} from context {}.", s.getType(), context.profile().getId()));

    }

    @Test(dataProvider = "host",
          dependsOnMethods = "testAllConnectedAndHostAssigned",
          threadPoolSize = TEST_PLAYER_COUNT
    )
    public void testHostSendSignal(final TestContext context) {
        final var crossfire = context.crossfire();

        for (final var protocol : crossfire.getSupportedProtocols()) {
            final var host = context.crossfire().findMatchHost(protocol);

        }
    }

    @Test(dataProvider = "host",
            dependsOnMethods = "testAllConnectedAndHostAssigned",
            threadPoolSize = TEST_PLAYER_COUNT
    )
    public void testClientReplySignal(final TestContext context) {

    }

    public record TestContext(
            Crossfire crossfire,
            User user,
            Profile profile,
            SessionCreation creation,
            BlockingQueue<Signal> signals,
            BlockingQueue<Message> messages,
            BlockingQueue<StringMessage> stringMessages,
            Subscription subscription
    ) {

        public TestContext {

            crossfire
                    .getSignalingClient()
                    .onSignal((sub, signal) -> {
                        signals().add(signal);
                        logger.info("Received signal {} for profile {}.", signal.getType(), profile().getId());
                    });

            subscription = Subscription.begin()
                    .chain(subscription)
                    .chain(crossfire.onClientOpenStatus(this::onClientOpened));

        }

        private void onClientOpened(final Subscription clientSubscription,
                                    final Crossfire.OpenStatus<MatchClient> matchClient) {
            if (matchClient.open()) {
                matchClient.object().onPeerStatus(this::onPeerStatus);
            } else {
                clientSubscription.unsubscribe();
            }
        }

        private void onPeerStatus(final Subscription peerSubscription, final PeerStatus peerStatus) {
            switch (peerStatus.phase()) {
                case READY -> {
                    peerStatus.peer().onMessage(((s, m) -> messages.add(m)));
                    peerStatus.peer().onStringMessage(((s, m) -> stringMessages.add(m)));
                }
                case CONNECTED -> logger.info("Connected remote peer: {}", peerStatus.peer().getProfileId());
                case TERMINATED -> peerSubscription.unsubscribe();
            }
        }

        public SignalingClient signalingClient() {
            return crossfire().getSignalingClient();
        }

    }

    private record TestMessage(Protocol protocol,
                               TestAction action,
                               String profileId,
                               String recipientProfileId) {

        public TestMessage {
            requireNonNull(protocol);
            requireNonNull(action);
            requireNonNull(profileId);
        }

        public static TestMessage from(final String string) {

            final var components = string.split(quote(":"));

            return switch (components.length) {
                case 3 -> new TestMessage(
                        Protocol.valueOf(components[0]),
                        TestAction.valueOf(components[1]),
                        components[2],
                        null
                );
                case 4 -> new TestMessage(
                        Protocol.valueOf(components[0]),
                        TestAction.valueOf(components[1]),
                        components[2],
                        components[3]
                );
                default -> throw new IllegalArgumentException("Invalid component count: "
                        + components.length
                        + " in "
                        + string
                );
            };

        }

        public String toString() {
            return Stream.of(protocol, action, profileId, recipientProfileId)
                    .filter(Objects::nonNull)
                    .map(Objects::toString)
                    .collect(Collectors.joining(":"));
        }

    }

    private enum TestAction {
        BROADCAST_MESSAGE,
        HOST_SEND_MESSAGE,
        CLIENT_REPLY_MESSAGE,
    }

}
