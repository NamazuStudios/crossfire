package dev.getelements.elements.crossfire;

import dev.getelements.elements.crossfire.client.Peer;
import dev.getelements.elements.crossfire.client.PeerPhase;
import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.client.SignalingClientPhase;
import dev.getelements.elements.crossfire.model.Protocol;
import dev.getelements.elements.crossfire.model.Version;
import dev.getelements.elements.crossfire.model.control.LeaveControlMessage;
import dev.getelements.elements.crossfire.model.handshake.FindHandshakeRequest;
import dev.getelements.elements.crossfire.model.signal.ConnectBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.HostBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.JoinBroadcastSignal;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.sdk.dao.ApplicationConfigurationDao;
import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
import dev.getelements.elements.sdk.model.profile.Profile;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static dev.getelements.elements.crossfire.client.Peer.SendResult.SENT;
import static dev.getelements.elements.crossfire.model.ProtocolMessageType.MATCHED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.regex.Pattern.quote;
import static org.testng.Assert.*;
import static org.testng.AssertJUnit.assertNotNull;

public class TestBasicMatchmaking {

    public static final int TEST_PLAYER_COUNT = 4;

    public static final String TEST_BASIC_MATCHMAKING = "test_basic_matchmaking";

    private static final Logger logger = LoggerFactory.getLogger(TestBasicMatchmaking.class);

    private TestServer server;

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
    public void setupServer() {
         server = TestServer.getInstance();
    }

    @BeforeClass
    public void setupContainer() {
        webSocketContainer = ContainerProvider.getWebSocketContainer();
    }

    @BeforeClass(dependsOnMethods = {"setupServer", "setupContainer"})
    public void setupContexts() {
        testContextList = IntStream.range(0, TEST_PLAYER_COUNT)
                .mapToObj(i -> TestContext.create(i, server, webSocketContainer))
                .toList();
    }

    @BeforeClass(dependsOnMethods = {"setupServer"})
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

        assertEquals(context.signalingClient().getPhase(), SignalingClientPhase.CONNECTED);

        final var request = new FindHandshakeRequest();
        request.setVersion(Version.V_1_0);
        request.setConfiguration(configuration.getName());
        request.setProfileId(context.profile().getId());
        request.setSessionKey(context.creation().getSessionSecret());

        final var response = context.signalingClient().handshake(request, 30, SECONDS);

        assertNotNull(response);
        assertEquals(response.getType(), MATCHED);
        assertNotNull(response.getMatchId());

        assertEquals(context.signalingClient().getPhase(), SignalingClientPhase.SIGNALING);
        assertEquals(context.signalingClient().getHandshakeResponse().getMatchId(), response.getMatchId());
        assertEquals(context.signalingClient().findHandshakeResponse().get().getMatchId(), response.getMatchId());

        logger.info("Found match: {}", response.getMatchId());

    }

    @Test(dependsOnMethods = "testAllPlayersJoined")
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
          dependsOnMethods = "testTestAllJoinedSameMatch"
    )
    public void testAllConnectedAndHostAssigned(final TestContext context) throws InterruptedException {

        String actualHostId = null;

        final var remaining = testContextList
                .stream()
                .filter(tc -> tc != context)
                .map(TestContext::profile)
                .map(Profile::getId)
                .toList();

        final var remainingJoins = new TreeSet<>(remaining);
        final var remainingConnects = new TreeSet<>(remaining);

        final var signals = new ArrayList<Signal>();

        while (actualHostId == null || !remainingJoins.isEmpty() || !remainingConnects.isEmpty()) {

            final var signal = context.signals().poll(1, SECONDS);

            if (signal == null) {
                // This is here to allow for setting breakpoints when debugging
                continue;
            }

            logger.info("Signal received: {} from context {}.", signal.getType(), context.profile().getId());

            signals.add(signal);

            switch (signal.getType()) {
                case HOST -> actualHostId = signal.as(HostBroadcastSignal.class).getProfileId();
                case CONNECT -> {
                    final var profileId = signal.as(ConnectBroadcastSignal.class).getProfileId();
                    remainingConnects.remove(profileId);
                }
                case SIGNAL_JOIN -> {
                    final var profileId = signal.as(JoinBroadcastSignal.class).getProfileId();
                    remainingJoins.remove(profileId);
                }
            }

        }

        final var state = context.signalingClient().getState();

        final var expectedHostId = state.getHost();
        assertEquals(actualHostId, expectedHostId, "Host mismatch.");

        if (state.getProfileId().equals(state.getHost())) {
            assertTrue(state.isHost(), "Expected host flag set.");
        } else {
            assertFalse(state.isHost(), "Expected host flag set.");
        }

        signals.forEach(s -> logger.info(
                "Signal dequeued: {} from context {}.",
                s.getType(),
                context.profile().getId())
        );

    }

    @Test(dataProvider = "host",
          dependsOnMethods = "testAllConnectedAndHostAssigned"
    )
    public void testHostSendMessage(final TestContext context) {

        final var crossfire = context.crossfire();

        for (final var protocol : crossfire.getSupportedProtocols()) {

            final var hostOptional = context
                    .crossfire()
                    .findMatchHost(protocol);

            assertTrue(hostOptional.isPresent(), "Expected host to be present for: " + protocol);

            final List<Peer> peers;
            final var host = hostOptional.get();

            try (final var queue = host.newPeerQueue()) {
                peers = queue.waitForAllPeers(PeerPhase.CONNECTED).toList();
                assertEquals(peers.size(), TEST_PLAYER_COUNT - 1);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted waiting for peers.");
                return;
            }

            final var hostProfileId = context
                    .crossfire()
                    .getSignalingClient()
                    .getState()
                    .getHost();

            for (final var peer : peers) {

                assertNotEquals(hostProfileId, peer.getProfileId());

                final var message = new TestMessage(
                        protocol,
                        TestAction.HOST_SEND_MESSAGE,
                        context.profile().getId(),
                        peer.getProfileId()
                );

                assertEquals(peer.send(message.toString()), SENT);
                assertEquals(peer.send(message.toBinary()), SENT);

                logger.info("Host sent message to {}: {}", peer.getProfileId(), message);

            }

        }

    }

    @Test(dataProvider = "client",
          dependsOnMethods = "testHostSendMessage"
    )
    public void testClientReplyMessageBinary(final TestContext context) throws InterruptedException {

        final var crossfire = context.crossfire();
        final var signalingClientState = crossfire.getSignalingClient().getState();

        final var protocols = new TreeSet<>(crossfire.getSupportedProtocols());

        while (!protocols.isEmpty()) {

            logger.info("Receiving binary messages for {} (from host {}).",
                    signalingClientState.getProfileId(),
                    signalingClientState.getHost()
            );

            final var receivedMessage = context.messages().take();
            final var receivedTestMessage = TestMessage.from(receivedMessage.data());

            assertTrue(protocols.remove(receivedTestMessage.protocol()));
            assertEquals(receivedTestMessage.action(), TestAction.HOST_SEND_MESSAGE);
            assertEquals(receivedTestMessage.profileId(), signalingClientState.getHost());
            assertEquals(receivedTestMessage.recipientProfileId(), signalingClientState.getProfileId());

            final var clientOptional = crossfire.findMatchClient(receivedTestMessage.protocol());
            assertTrue(clientOptional.isPresent(), "Expected client to be present for: " + receivedTestMessage.protocol());

            final var client = clientOptional.get();
            final var responseTestMessage = new TestMessage(
                    receivedTestMessage.protocol(),
                    TestAction.CLIENT_REPLY_MESSAGE,
                    signalingClientState.getProfileId(),
                    signalingClientState.getHost()
            );

            assertTrue(client.findPeer().isPresent(), "Expected client to be present for: " + receivedTestMessage.protocol());
            assertEquals(client.findPeer().get().send(responseTestMessage.toBinary()), SENT);

        }

    }

    @Test(dataProvider = "client",
          dependsOnMethods = "testHostSendMessage"
    )
    public void testClientReplyMessageString(final TestContext context) throws InterruptedException {

        final var crossfire = context.crossfire();
        final var signalingClientState = crossfire.getSignalingClient().getState();

        final var protocols = new TreeSet<>(crossfire.getSupportedProtocols());

        while (!protocols.isEmpty()) {

            logger.info("Receiving string messages for {} (from host {}).",
                    signalingClientState.getProfileId(),
                    signalingClientState.getHost()
            );

            final var receivedMessage = context.stringMessages().take();
            final var receivedTestMessage = TestMessage.from(receivedMessage.data());

            assertTrue(protocols.remove(receivedTestMessage.protocol()));
            assertEquals(receivedTestMessage.action(), TestAction.HOST_SEND_MESSAGE);
            assertEquals(receivedTestMessage.profileId(), signalingClientState.getHost());
            assertEquals(receivedTestMessage.recipientProfileId(), signalingClientState.getProfileId());

            final var clientOptional = crossfire.findMatchClient(receivedTestMessage.protocol());
            assertTrue(clientOptional.isPresent(), "Expected client to be present for: " + receivedTestMessage.protocol());

            final var client = clientOptional.get();
            final var responseTestMessage = new TestMessage(
                    receivedTestMessage.protocol(),
                    TestAction.CLIENT_REPLY_MESSAGE,
                    signalingClientState.getProfileId(),
                    signalingClientState.getHost()
            );

            assertTrue(client.findPeer().isPresent(), "Expected client to be present for: " + receivedTestMessage.protocol());
            client.findPeer().get().send(responseTestMessage.toString());

        }

    }

    @Test(dataProvider = "host",
          dependsOnMethods = {"testClientReplyMessageBinary", "testClientReplyMessageString"}
    )
    public void testHostReceiveSignalBinary(final TestContext context) throws InterruptedException {

        final var count = (long) context
                .crossfire()
                .getSupportedProtocols()
                .size() * (TEST_PLAYER_COUNT - 1);

        for (int i = 0; i < count - 1; ++i) {
            final var msg = context.messages().take();
            final var testMessage = TestMessage.from(msg.data());
            assertEquals(testMessage.protocol(), msg.peer().getProtocol());
            assertEquals(testMessage.action(), TestAction.CLIENT_REPLY_MESSAGE);
            assertEquals(testMessage.profileId(), msg.peer().getProfileId());
            assertEquals(testMessage.recipientProfileId(), context.signalingClient().getState().getHost());
        }

    }

    @Test(dataProvider = "host",
          dependsOnMethods = {"testClientReplyMessageBinary", "testClientReplyMessageString"}
    )
    public void testHostReceiveSignalString(final TestContext context) throws InterruptedException {

        final var count = (long) context
                .crossfire()
                .getSupportedProtocols()
                .size() * (TEST_PLAYER_COUNT - 1);

        for (int i = 0; i < count; ++i) {
            final var msg = context.stringMessages().take();
            final var testMessage = TestMessage.from(msg.data());
            assertEquals(testMessage.protocol(), msg.peer().getProtocol());
            assertEquals(testMessage.action(), TestAction.CLIENT_REPLY_MESSAGE);
            assertEquals(testMessage.profileId(), msg.peer().getProfileId());
            assertEquals(testMessage.recipientProfileId(), context.signalingClient().getState().getHost());
        }

    }

    @Test(groups = "testLeaveMatch",
          dataProvider = "allContexts",
          dependsOnMethods = "testHostReceiveSignalString"
    )
    public void testLeaveMatch(final TestContext context) throws InterruptedException {

        final var leave = new LeaveControlMessage();
        leave.setProfileId(context.profile().getId());
        context.crossfire().getSignalingClient().control(leave);

        final var status = context.crossfire().getSignalingClient().waitForDisconnect();
        assertFalse(status.error(), "Closed with error: " + status.message());

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

        public static TestMessage from(final ByteBuffer buffer) {
            final var string = UTF_8.decode(buffer).toString();
            return from(string);
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

        public ByteBuffer toBinary() {
            final var string = toString();
            final var bytes = string.getBytes(UTF_8);
            return ByteBuffer.wrap(bytes);
        }

    }

    /**
     * Enumeration of test actions taken.
     */
    private enum TestAction {

        /**
         * Host sends a message to all clients.
         */
        HOST_SEND_MESSAGE,

        /**
         * Client replies to host message.
         */
        CLIENT_REPLY_MESSAGE

    }



}
