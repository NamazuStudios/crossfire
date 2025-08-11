package dev.getelements.elements.crossfire;

import dev.getelements.elements.crossfire.client.Client;
import dev.getelements.elements.crossfire.client.v10.V10Client;
import dev.getelements.elements.crossfire.model.handshake.FindHandshakeRequest;
import dev.getelements.elements.sdk.dao.ApplicationConfigurationDao;
import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
import dev.getelements.elements.sdk.model.profile.Profile;
import dev.getelements.elements.sdk.model.session.SessionCreation;
import dev.getelements.elements.sdk.model.user.User;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.WebSocketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static dev.getelements.elements.crossfire.model.ProtocolMessage.Type.MATCHED;
import static dev.getelements.elements.crossfire.model.handshake.HandshakeRequest.VERSION_1_0;
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
        testContextList = IntStream.range(0, 1)
                .mapToObj(i -> {

                    final var client = new V10Client();
                    final var user = server.createUser(format("test_%d_user", i), USER);
                    final var profile = server.createProfile(user, format("test_%d_profile", i));
                    final var session = server.newSessionForUser(user, profile);

                    try {
                        webSocketContainer.connectToServer(client, server.getTestTestServerWsUrl());
                        return new TestContext(user, profile, client, session);
                    } catch (DeploymentException | IOException e) {
                        Assert.fail("Failed to connect client", e);
                        return null;
                    }

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

        logger.info("Configuration created");

    }

    @Test(dataProvider = "allContexts")
    public void testFindHandshake(final TestContext context) throws InterruptedException {

        final var request = new FindHandshakeRequest();
        request.setVersion(VERSION_1_0);
        request.setConfiguration(configuration.getName());
        request.setProfileId(context.profile().getId());
        request.setSessionKey(context.creation().getSessionSecret());

        final var response = context.client().handshake(request, 30, TimeUnit.SECONDS);
        assertNotNull(response);
        assertEquals(response.getType(), MATCHED);
        assertNotNull(response.getMatchId());

    }

    public record TestContext(
            User user,
            Profile profile,
            Client client,
            SessionCreation creation
    ) {}

}

