package dev.getelements.elements.crossfire;

import dev.getelements.elements.crossfire.client.*;
import dev.getelements.elements.crossfire.client.webrtc.WebRTCMatchClient;
import dev.getelements.elements.crossfire.client.webrtc.WebRTCMatchHost;
import dev.getelements.elements.crossfire.api.model.Protocol;
import dev.getelements.elements.crossfire.api.model.signal.BroadcastSignal;
import dev.getelements.elements.crossfire.api.model.signal.DirectSignal;
import dev.getelements.elements.crossfire.api.model.signal.Signal;
import dev.getelements.elements.sdk.Subscription;
import dev.getelements.elements.sdk.model.profile.Profile;
import dev.getelements.elements.sdk.model.session.SessionCreation;
import dev.getelements.elements.sdk.model.user.User;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCIceTransportPolicy;
import jakarta.websocket.WebSocketContainer;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;

import static dev.getelements.elements.crossfire.client.Crossfire.Mode.SIGNALING_CLIENT;
import static dev.getelements.elements.crossfire.client.Crossfire.Mode.SIGNALING_HOST;
import static dev.getelements.elements.sdk.model.user.User.Level.USER;

public record TestContext(
        Crossfire crossfire,
        User user,
        Profile profile,
        SessionCreation creation,
        BlockingQueue<Signal> signals,
        BlockingQueue<Peer.Message> messages,
        BlockingQueue<Peer.StringMessage> stringMessages,
        Subscription subscription
) {

    private static final Logger logger = LoggerFactory.getLogger(TestContext.class);

    public TestContext {

        crossfire
                .getSignalingClient()
                .onSignal((sub, signal) -> {
                    signals().add(signal);
                    switch (signal.getType().getCategory()) {
                        case SIGNALING ->
                                logger.info("Received broadcast signal {} from {} for profile {}.",
                                        signal.getType(),
                                        ((BroadcastSignal) signal).getProfileId(),
                                        profile().getId()
                                );
                        case SIGNALING_DIRECT ->
                                logger.info("Received direct signal {} from {} addressed to {} for profile {}.",
                                        signal.getType(),
                                        ((DirectSignal) signal).getProfileId(),
                                        ((DirectSignal) signal).getRecipientProfileId(),
                                        profile().getId()
                                );
                    }
                });

        subscription = Subscription.begin()
                .chain(subscription)
                .chain(crossfire.onHostOpenStatus(this::onHostOpened))
                .chain(crossfire.onClientOpenStatus(this::onClientOpened));

    }

    public static TestContext create(final int i,
                                     final TestServer server,
                                     final WebSocketContainer webSocketContainer) {
        final var user = server.createUser("test_%d_user".formatted(i) , USER);
        final var profile = server.createProfile(user, "test_%d_profile".formatted(i));
        final var session = server.newSessionForUser(user, profile);

        final var crossfire = new StandardCrossfire.Builder()
                .withDefaultUri(server.getTestTestServerWsUrl())
                .withWebSocketContainer(webSocketContainer)
                // The WebRTC Clients have some issues with memory management so those tests are disabled
                // for now until we can figure out the memory corruption issues with the Java WebRTC
                // client library. This does a full signaling test and we can manually run the WebRTC
                // tests to ensure that connectivity is working.
//                            .withDefaultProtocol(Protocol.WEBRTC)
//                            .withSupportedModes(WEBRTC_HOST, WEBRTC_CLIENT)
                .withDefaultProtocol(Protocol.SIGNALING)
                .withSupportedModes(SIGNALING_HOST, SIGNALING_CLIENT)
                .withWebRTCHostBuilder(() -> new WebRTCMatchHost.Builder()
                        .withPeerConfigurationProvider(profileId -> {
                            final var rtcConfiguration = new RTCConfiguration();
                            rtcConfiguration.iceTransportPolicy = RTCIceTransportPolicy.NO_HOST;
                            return rtcConfiguration;
                        })
                )
                .withWebRTCClientBuilder(() -> new WebRTCMatchClient.Builder()
                        .withPeerConfigurationProvider(profileId -> {
                            final var rtcConfiguration = new RTCConfiguration();
                            rtcConfiguration.iceTransportPolicy = RTCIceTransportPolicy.NO_HOST;
                            return rtcConfiguration;
                        })
                )
                .build()
                .connect();

        return new TestContext(crossfire, user, profile, session,
                new BlockingArrayQueue<>(),
                new BlockingArrayQueue<>(),
                new BlockingArrayQueue<>(),
                Subscription.begin()
        );

    }

    private void onHostOpened(final Subscription hostSubscription,
                              final Crossfire.OpenStatus<MatchHost> matchClient) {
        if (matchClient.open()) {
            matchClient.object().onPeerStatus(this::onPeerStatus);
        } else {
            hostSubscription.unsubscribe();
        }
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
                peerStatus.peer().onMessage(((s, m) -> {

                    final var localProfileId = crossfire().getSignalingClient().getState().getProfileId();

                    logger.info("Received binary message {} -> {}",
                            m.peer().getProfileId(),
                            localProfileId
                    );

                    if (messages.add(m))
                        logger.info("Added message: {}", localProfileId);
                    else
                        logger.info("Failed to add message: {}", localProfileId);

                }));
                peerStatus.peer().onStringMessage(((s, m) -> {

                    final var localProfileId = crossfire().getSignalingClient().getState().getProfileId();

                    logger.info("Received string message {} -> {}",
                            m.peer().getProfileId(),
                            localProfileId
                    );

                    if (stringMessages.add(m))
                        logger.info("Added string message: {}.", localProfileId);
                    else
                        logger.info("Failed string to add message: {}", localProfileId);

                }));
            }
            case CONNECTED -> logger.info("Connected remote peer: {}", peerStatus.peer().getProfileId());
            case TERMINATED -> peerSubscription.unsubscribe();
        }
    }

    public SignalingClient signalingClient() {
        return crossfire().getSignalingClient();
    }

}
