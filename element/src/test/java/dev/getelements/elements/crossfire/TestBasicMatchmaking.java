package dev.getelements.elements.crossfire;

import dev.getelements.elements.crossfire.client.Crossfire;
import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.client.StandardCrossfire;
import dev.getelements.elements.crossfire.model.Version;
import dev.getelements.elements.crossfire.model.handshake.FindHandshakeRequest;
import dev.getelements.elements.sdk.dao.ApplicationConfigurationDao;
import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
import dev.getelements.elements.sdk.model.profile.Profile;
import dev.getelements.elements.sdk.model.session.SessionCreation;
import dev.getelements.elements.sdk.model.user.User;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static dev.getelements.elements.crossfire.client.SignalingClientPhase.CONNECTED;
import static dev.getelements.elements.crossfire.client.SignalingClientPhase.SIGNALING;
import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.MATCHED;
import static dev.getelements.elements.sdk.model.user.User.Level.USER;
import static java.lang.String.format;
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
                            .build()
                            .connect();

                    return new TestContext(crossfire, user, profile, session);

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

    @Test(dataProvider = "allContexts")
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

        assertEquals(context.signalingClient().getPhase(), SIGNALING);
        assertEquals(context.signalingClient().getHandshakeResponse().getMatchId(), response.getMatchId());
        assertEquals(context.signalingClient().findHandshakeResponse().get().getMatchId(), response.getMatchId());

        logger.info("Found match: {}", response.getMatchId());

    }

    @Test(dependsOnMethods = "testFindHandshake")
    public void testTestAllJoinedSameMatch() {

    }

    public record TestContext(
            Crossfire crossfire,
            User user,
            Profile profile,
            SessionCreation creation
    ) {

        public SignalingClient signalingClient() {
            return crossfire.getSignalingClient();
        }

    }

}
