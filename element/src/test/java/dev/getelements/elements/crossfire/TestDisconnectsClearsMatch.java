//package dev.getelements.elements.crossfire;
//
//import dev.getelements.elements.crossfire.client.SignalingClientPhase;
//import dev.getelements.elements.crossfire.model.Version;
//import dev.getelements.elements.crossfire.model.handshake.FindHandshakeRequest;
//import dev.getelements.elements.sdk.dao.ApplicationConfigurationDao;
//import dev.getelements.elements.sdk.dao.MultiMatchDao;
//import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
//import dev.getelements.elements.sdk.model.match.MultiMatchStatus;
//import jakarta.websocket.ContainerProvider;
//import jakarta.websocket.WebSocketContainer;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.testng.Assert;
//import org.testng.annotations.BeforeClass;
//import org.testng.annotations.DataProvider;
//import org.testng.annotations.Test;
//
//import java.util.List;
//import java.util.stream.IntStream;
//
//import static dev.getelements.elements.crossfire.model.ProtocolMessageType.MATCHED;
//import static java.util.concurrent.TimeUnit.SECONDS;
//import static org.testng.Assert.assertEquals;
//import static org.testng.AssertJUnit.assertNotNull;
//
// TODO: Clear up this test once we can figure out why it is not launching.
//public class TestDisconnectsClearsMatch {
//
//    private static final Logger logger = LoggerFactory.getLogger(TestDisconnectsClearsMatch.class);
//
//    public static final int TEST_PLAYER_COUNT = 4;
//
//    public static String TEST_DISCONNECT_CLEARS_MATCH = "test_disconnect_clears_match";
//
//    private TestServer server;
//
//    private List<TestContext> testContextList = List.of();
//
//    private WebSocketContainer webSocketContainer;
//
//    private MatchmakingApplicationConfiguration configuration;
//
//    @DataProvider
//    public Object[][] allContexts() {
//
//        if (testContextList.isEmpty()) {
//            Assert.fail("Test contexts are not initialized. Ensure setupContexts() succeeds called before running tests.");
//        }
//
//        return testContextList
//                .stream()
//                .map(c -> new Object[]{c})
//                .toArray(Object[][]::new);
//
//    }
//
//    @BeforeClass
//    public void setupServer() {
//        server = TestServer.getInstance();
//    }
//
//    @BeforeClass
//    public void setupContainer() {
//        webSocketContainer = ContainerProvider.getWebSocketContainer();
//    }
//
//    @BeforeClass(dependsOnMethods = {"setupServer", "setupContainer"})
//    public void setupContexts() {
//        testContextList = IntStream.range(0, TEST_PLAYER_COUNT)
//                .mapToObj(i -> TestContext.create(i, server, webSocketContainer))
//                .toList();
//    }
//
//    @BeforeClass(dependsOnMethods = {"setupServer", "setupContexts"})
//    public void setupConfiguration() {
//
//        final var application = server.getApplication();
//        final var configuration = new MatchmakingApplicationConfiguration();
//        configuration.setName(TEST_DISCONNECT_CLEARS_MATCH);
//        configuration.setMaxProfiles(TEST_PLAYER_COUNT);
//        configuration.setDescription("Test Basic Matchmaking Application");
//        configuration.setParent(application);
//
//        this.configuration = server
//                .getDao(ApplicationConfigurationDao.class)
//                .createApplicationConfiguration(application.getId(), configuration);
//
//        logger.info("Configuration created.");
//
//    }
//
//    @Test(dataProvider = "allContexts")
//    public void connect(final TestContext context) throws InterruptedException {
//
//        assertEquals(context.signalingClient().getPhase(), SignalingClientPhase.CONNECTED);
//
//        final var request = new FindHandshakeRequest();
//        request.setVersion(Version.V_1_0);
//        request.setConfiguration(configuration.getName());
//        request.setProfileId(context.profile().getId());
//        request.setSessionKey(context.creation().getSessionSecret());
//
//        final var response = context.signalingClient().handshake(request, 30, SECONDS);
//
//        assertNotNull(response);
//        assertEquals(response.getType(), MATCHED);
//        assertNotNull(response.getMatchId());
//
//        assertEquals(context.signalingClient().getPhase(), SignalingClientPhase.SIGNALING);
//        assertEquals(context.signalingClient().getHandshakeResponse().getMatchId(), response.getMatchId());
//        assertEquals(context.signalingClient().findHandshakeResponse().get().getMatchId(), response.getMatchId());
//
//        logger.info("Found match: {}", response.getMatchId());
//
//    }
//
//    @Test(dataProvider = "allContexts", dependsOnMethods = "connect")
//    public void disconnect(final TestContext context) throws Exception {
//        context.signalingClient().close();
//        context.signalingClient().waitForDisconnect();
//    }
//
//    @Test(dataProvider = "allContexts", dependsOnMethods = "disconnect")
//    public void checkAllMatchesDestroyed(final TestContext context) throws Exception {
//        final var dao = server.getDao(MultiMatchDao.class);
//        final var matchId = context.signalingClient().getHandshakeResponse().getMatchId();
//        final var multiMatch = dao.getMultiMatch(matchId);
//        assertEquals(multiMatch.getStatus(), MultiMatchStatus.ENDED);
//    }
//
//}
