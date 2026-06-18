package dev.getelements.elements.crossfire.client.webrtc;

import dev.getelements.elements.crossfire.client.MatchClient;
import dev.getelements.elements.crossfire.client.PeerPhase;
import dev.onvoid.webrtc.RTCConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertTrue;

/**
 * Stripped-down WebRTC peer connection test. No Mongo, no WebSocket, no Crossfire server.
 * Signals are routed in-process via {@link StubSignalingHub} using BlockingQueue-style delivery.
 *
 * Reproduces the four-player host+3-client topology used in TestDisconnectsClearsMatch to isolate
 * WebRTC JNI lifecycle issues from the server stack.
 */
public class TestWebRTCDirectPeer {

    private static final Logger logger = LoggerFactory.getLogger(TestWebRTCDirectPeer.class);

    private static final int PEER_COUNT = 3;
    private static final int CONNECT_TIMEOUT_SECONDS = 30;

    private static final String MATCH_ID   = "test-match-stub";
    private static final String HOST_ID    = "profile-host";
    private static final List<String> CLIENT_IDS = List.of(
            "profile-client-1",
            "profile-client-2",
            "profile-client-3"
    );
    private static final List<String> ALL_IDS = List.of(
            HOST_ID,
            CLIENT_IDS.get(0),
            CLIENT_IDS.get(1),
            CLIENT_IDS.get(2)
    );

    private StubSignalingHub hub;
    private WebRTCMatchHost host;
    private List<MatchClient> clients;

    @BeforeClass
    public void setUp() {

        hub = new StubSignalingHub(MATCH_ID, HOST_ID, ALL_IDS);

        // Create the host — subscribes to signals immediately in its constructor.
        host = new WebRTCMatchHost.Builder()
                .withSignalingClient(hub.connect(HOST_ID))
                .withPeerConfigurationProvider(pid -> new RTCConfiguration())
                .build();

        // Create all clients — each creates a WebRTCAnsweringPeer that subscribes to signals.
        clients = CLIENT_IDS.stream()
                .map(id -> (MatchClient) new WebRTCMatchClient.Builder()
                        .withRemoteProfileId(HOST_ID)
                        .withSignalingClient(hub.connect(id))
                        .withPeerConfigurationProvider(pid -> new RTCConfiguration())
                        .build())
                .toList();

        // Set up peer connections on each answering peer BEFORE the host is told about them.
        // This ensures the connection is non-null when the SDP offer arrives.
        clients.forEach(MatchClient::connect);

    }

    @Test
    public void allPeersReachConnected() throws InterruptedException {

        // Expect CONNECTED from the host's perspective: one per client.
        final var connectedLatch = new CountDownLatch(PEER_COUNT);
        final var connectedCount = new AtomicInteger();

        host.onPeerStatus((sub, status) -> {
            logger.info("Host peer status: {} for {}", status.phase(), status.peer().getProfileId());
            if (status.phase() == PeerPhase.CONNECTED) {
                connectedCount.incrementAndGet();
                connectedLatch.countDown();
            }
        });

        // Start the host: creates an offering peer for each known profile and sends SDP offers.
        host.start();

        final var allConnected = connectedLatch.await(CONNECT_TIMEOUT_SECONDS, SECONDS);

        logger.info("Connected peer count: {}/{}", connectedCount.get(), PEER_COUNT);

        assertTrue(allConnected,
                "Timed out waiting for all " + PEER_COUNT + " peers to reach CONNECTED. " +
                "Got " + connectedCount.get() + "/" + PEER_COUNT);

    }

    /**
     * Force the race: broadcast DisconnectBroadcastSignal for every client concurrently from
     * separate threads while the peers are still actively connected. This mirrors what the real
     * server does when multiple players close their WebSocket connections simultaneously.
     */
    @Test(dependsOnMethods = "allPeersReachConnected")
    public void concurrentDisconnectDoesNotCrash() throws InterruptedException {

        final var executor = Executors.newFixedThreadPool(PEER_COUNT);
        final var startGate = new CountDownLatch(1);
        final var doneLatch = new CountDownLatch(PEER_COUNT);

        CLIENT_IDS.forEach(id -> executor.submit(() -> {
            try {
                startGate.await();
                logger.info("Broadcasting disconnect for {}", id);
                hub.announceDeparture(id);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        }));

        startGate.countDown();
        final var completed = doneLatch.await(CONNECT_TIMEOUT_SECONDS, SECONDS);
        executor.shutdown();

        assertTrue(completed, "Disconnect signals did not complete within timeout");
        logger.info("Concurrent disconnect completed without crash.");

    }

    /**
     * Force the other race: announce all clients joining simultaneously from separate threads,
     * so the host receives concurrent ConnectBroadcastSignals and calls createPeerConnection()
     * on the SharedPeerConnectionFactory from multiple threads at the same time.
     *
     * In the real server, ConnectBroadcastSignals for all players arrive on separate WebSocket
     * threads. host.start() in the previous test is sequential — this test is the real scenario.
     */
    @Test
    public void concurrentConnectDoesNotCrash() throws InterruptedException {

        // Fresh hub and participants for this test.
        final var freshHub = new StubSignalingHub(MATCH_ID + "-concurrent", HOST_ID, ALL_IDS);

        final var freshHost = new WebRTCMatchHost.Builder()
                .withSignalingClient(freshHub.connect(HOST_ID))
                .withPeerConfigurationProvider(pid -> new RTCConfiguration())
                .build();

        final var freshClients = CLIENT_IDS.stream()
                .map(id -> (MatchClient) new WebRTCMatchClient.Builder()
                        .withRemoteProfileId(HOST_ID)
                        .withSignalingClient(freshHub.connect(id))
                        .withPeerConfigurationProvider(pid -> new RTCConfiguration())
                        .build())
                .toList();

        freshClients.forEach(MatchClient::connect);

        final var connectedLatch = new CountDownLatch(PEER_COUNT);
        freshHost.onPeerStatus((sub, status) -> {
            if (status.phase() == PeerPhase.CONNECTED) connectedLatch.countDown();
        });

        // Announce all clients joining simultaneously from separate threads.
        final var executor = Executors.newFixedThreadPool(PEER_COUNT);
        final var startGate = new CountDownLatch(1);
        final var announcedLatch = new CountDownLatch(PEER_COUNT);

        CLIENT_IDS.forEach(id -> executor.submit(() -> {
            try {
                startGate.await();
                logger.info("Announcing join for {}", id);
                freshHub.announceJoin(id);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                announcedLatch.countDown();
            }
        }));

        startGate.countDown();
        announcedLatch.await(CONNECT_TIMEOUT_SECONDS, SECONDS);
        executor.shutdown();

        final var allConnected = connectedLatch.await(CONNECT_TIMEOUT_SECONDS, SECONDS);
        logger.info("Concurrent connect: {}/{} peers reached CONNECTED", PEER_COUNT - connectedLatch.getCount(), PEER_COUNT);

        try {
            assertTrue(allConnected,
                    "Timed out waiting for peers after concurrent announcements. " +
                    "Got " + (PEER_COUNT - connectedLatch.getCount()) + "/" + PEER_COUNT);
        } finally {
            freshClients.forEach(MatchClient::close);
            freshHost.close();
        }

    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        if (clients != null) clients.forEach(MatchClient::close);
        if (host != null) host.close();
    }

}
