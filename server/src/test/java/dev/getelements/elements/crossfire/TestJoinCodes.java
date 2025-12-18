
package dev.getelements.elements.crossfire;

import dev.getelements.elements.crossfire.api.model.Version;
import dev.getelements.elements.crossfire.api.model.handshake.CreateHandshakeRequest;
import dev.getelements.elements.crossfire.api.model.handshake.CreatedHandshakeResponse;
import dev.getelements.elements.crossfire.api.model.handshake.JoinCodeHandshakeRequest;
import dev.getelements.elements.crossfire.client.SignalingClientPhase;
import dev.getelements.elements.sdk.dao.ApplicationConfigurationDao;
import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.stream.IntStream;

import static dev.getelements.elements.crossfire.api.model.ProtocolMessageType.CREATED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

public class TestJoinCodes {

    private static final int TEST_PLAYER_COUNT = 8;

    public static final String TEST_JOIN_CODES = "test_join_codes";

    private static final Logger logger = LoggerFactory.getLogger(TestJoinCodes.class);

    private final TestServer server = TestServer.getInstance();

    private final WebSocketContainer webSocketContainer = ContainerProvider.getWebSocketContainer();

    private final TestContext testCreator = TestContext.create(0, server, webSocketContainer);

    private final List<TestContext> testJoiners = IntStream
            .range(1, TEST_PLAYER_COUNT)
            .mapToObj(i -> TestContext.create(i, server, webSocketContainer))
            .toList();

    private String joinCode;

    private MatchmakingApplicationConfiguration configuration;

    @BeforeClass
    public void setupConfiguration() {

        final var application = server.getApplication();
        final var configuration = new MatchmakingApplicationConfiguration();
        configuration.setName(TEST_JOIN_CODES);
        configuration.setMaxProfiles(TEST_PLAYER_COUNT);
        configuration.setDescription("Test Join Code Application");
        configuration.setParent(application);

        this.configuration = server
                .getDao(ApplicationConfigurationDao.class)
                .createApplicationConfiguration(application.getId(), configuration);

        logger.info("Configuration created.");

    }

    @DataProvider
    public Object[][] allJoiners() {
        return testJoiners
                .stream()
                .map(c -> new Object[]{c})
                .toArray(Object[][]::new);
    }

    @Test
    public void testCreateMatch() throws InterruptedException {

        final var handshake = new CreateHandshakeRequest();

        handshake.setVersion(Version.V_1_1);
        handshake.setConfiguration(configuration.getName());

        handshake.setProfileId(testCreator.profile().getId());
        handshake.setSessionKey(testCreator.creation().getSessionSecret());

        final var response = testCreator
                .signalingClient()
                .handshake(handshake, 30, SECONDS);

        assertNotNull(response);
        assertEquals(response.getType(), CREATED);
        assertNotNull(response.getMatchId());

        assertEquals(testCreator.signalingClient().getPhase(), SignalingClientPhase.SIGNALING);
        assertEquals(testCreator.signalingClient().getHandshakeResponse().getMatchId(), response.getMatchId());
        assertEquals(testCreator.signalingClient().findHandshakeResponse().get().getMatchId(), response.getMatchId());

        final var createdResponse = (CreatedHandshakeResponse) response;
        joinCode = createdResponse.getJoinCode();

    }

    @Test(dependsOnMethods = "testCreateMatch", dataProvider = "allJoiners")
    public void testJoinMatch(final TestContext joiner) throws InterruptedException {

        assertNotNull(joinCode);

        final var joinCodeHandshakeRequest = new JoinCodeHandshakeRequest();
        joinCodeHandshakeRequest.setJoinCode(joinCode);
        joinCodeHandshakeRequest.setVersion(Version.V_1_1);
        joinCodeHandshakeRequest.setProfileId(joiner.profile().getId());
        joinCodeHandshakeRequest.setSessionKey(joiner.creation().getSessionSecret());

        final var response = joiner
                .signalingClient()
                .handshake(joinCodeHandshakeRequest, 30, SECONDS);

        assertEquals(joiner.signalingClient().getPhase(), SignalingClientPhase.SIGNALING);
        assertEquals(joiner.signalingClient().getHandshakeResponse().getMatchId(), response.getMatchId());
        assertEquals(joiner.signalingClient().findHandshakeResponse().get().getMatchId(), response.getMatchId());

    }

}
