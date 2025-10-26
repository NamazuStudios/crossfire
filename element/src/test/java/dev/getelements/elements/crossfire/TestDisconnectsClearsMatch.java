package dev.getelements.elements.crossfire;

import dev.getelements.elements.sdk.dao.ApplicationConfigurationDao;
import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;

import java.util.List;

public class TestDisconnectsClearsMatch {

    private static final Logger logger = LoggerFactory.getLogger(TestDisconnectsClearsMatch.class);

    public static final int TEST_PLAYER_COUNT = 4;

    public static String TEST_DISCONNECT_CLEARS_MATCH = "test_disconnect_clears_match";

    private TestServer server;

    private List<TestBasicMatchmaking.TestContext> testContextList = List.of();

    private WebSocketContainer webSocketContainer;

    private MatchmakingApplicationConfiguration configuration;

    @BeforeClass
    public void setupServer() {
        server = TestServer.getInstance();
    }

    @BeforeClass
    public void setupContainer() {
        webSocketContainer = ContainerProvider.getWebSocketContainer();
    }

    @BeforeClass(dependsOnMethods = {"setupServer", "setupContexts"})
    public void setupConfiguration() {

        final var application = server.getApplication();
        final var configuration = new MatchmakingApplicationConfiguration();
        configuration.setName(TEST_DISCONNECT_CLEARS_MATCH);
        configuration.setMaxProfiles(TEST_PLAYER_COUNT);
        configuration.setDescription("Test Basic Matchmaking Application");
        configuration.setParent(application);

        this.configuration = server
                .getDao(ApplicationConfigurationDao.class)
                .createApplicationConfiguration(application.getId(), configuration);

        logger.info("Configuration created.");

    }

}
