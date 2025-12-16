package dev.getelements.elements.crossfire;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.IntStream;

public class TestJoinCodes {

    private static final int TEST_PLAYER_COUNT = 2;

    public static final String TEST_BASIC_MATCHMAKING = "test_basic_matchmaking";

    private static final Logger logger = LoggerFactory.getLogger(TestJoinCodes.class);

    private final TestServer server = TestServer.getInstance();

    private final WebSocketContainer webSocketContainer = ContainerProvider.getWebSocketContainer();

    private final List<TestContext> testContextList = IntStream
            .range(0, TEST_PLAYER_COUNT)
            .mapToObj(i -> TestContext.create(i, server, webSocketContainer))
            .toList();

}
