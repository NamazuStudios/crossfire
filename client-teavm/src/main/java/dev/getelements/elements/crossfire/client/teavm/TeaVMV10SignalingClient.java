package dev.getelements.elements.crossfire.client.teavm;

import dev.getelements.elements.crossfire.api.model.Version;
import dev.getelements.elements.crossfire.api.model.control.ControlMessage;
import dev.getelements.elements.crossfire.api.model.handshake.*;
import dev.getelements.elements.crossfire.api.model.signal.*;
import dev.getelements.elements.crossfire.client.SignalingClient;
import dev.getelements.elements.crossfire.client.SignalingClientPhase;
import dev.getelements.elements.sdk.Subscription;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static dev.getelements.elements.crossfire.client.SignalingClientPhase.*;

/**
 * Browser WebSocket implementation of {@link SignalingClient} for TeaVM targets.
 * JS is single-threaded, so no locking or atomic references are needed.
 * The underlying WebSocket is injected via {@link #connect(TeaVMWebSocket)} by
 * {@link TeaVMCrossfire#connect(java.net.URI)}.
 */
class TeaVMV10SignalingClient implements SignalingClient {

    private SignalingClientPhase phase = READY;
    private TeaVMWebSocket ws;

    private HandshakeResponse handshakeResponse;
    private String matchId;
    private String profileId;
    private String host;
    private final List<String> profiles = new ArrayList<>();
    private final List<Signal> backlog = new ArrayList<>();

    private final List<SubEntry<Signal>> signalListeners = new ArrayList<>();
    private final List<SubEntry<HandshakeResponse>> handshakeListeners = new ArrayList<>();
    private final List<SubEntry<Throwable>> errorListeners = new ArrayList<>();

    void connect(final TeaVMWebSocket ws) {
        if (phase != READY) throw new IllegalStateException("Already connected");
        this.ws = ws;
        ws.setOnOpen(e -> onOpen());
        ws.setOnMessage(e -> onMessage(TeaVMWebSocket.getMessageData(e)));
        ws.setOnClose(e -> onClose(TeaVMWebSocket.getCloseCode(e), TeaVMWebSocket.getCloseReason(e)));
        ws.setOnError(e -> onError());
    }

    private void onOpen() {
        if (phase == READY) phase = CONNECTED;
    }

    private void onMessage(final String json) {
        if (json == null) return;
        final var msg = JsJsonBuilder.parseMessage(json);
        final var typeStr = msg.getType();
        if (typeStr == null) return;
        final var type = dev.getelements.elements.crossfire.api.model.ProtocolMessageType.findType(typeStr).orElse(null);
        if (type == null) return;
        switch (phase) {
            case HANDSHAKING -> handleHandshakeMessage(type, msg);
            case SIGNALING   -> handleSignalingMessage(type, msg);
            default          -> {} // ignore spurious messages in other phases
        }
    }

    private void handleHandshakeMessage(
            final dev.getelements.elements.crossfire.api.model.ProtocolMessageType type,
            final JsMessage msg) {
        switch (type) {
            case MATCHED -> {
                final var response = new MatchedResponse();
                response.setMatchId(msg.getMatchId());
                response.setProfileId(msg.getProfileId());
                this.matchId = response.getMatchId();
                this.profileId = response.getProfileId();
                this.handshakeResponse = response;
                this.phase = SIGNALING;
                publishHandshake(response);
            }
            case CREATED -> {
                final var response = new CreatedHandshakeResponse();
                response.setMatchId(msg.getMatchId());
                response.setProfileId(msg.getProfileId());
                response.setJoinCode(msg.getJoinCode());
                this.matchId = response.getMatchId();
                this.profileId = response.getProfileId();
                this.handshakeResponse = response;
                this.phase = SIGNALING;
                publishHandshake(response);
            }
            case ERROR -> handleError(msg);
            default    -> {}
        }
    }

    private void handleSignalingMessage(
            final dev.getelements.elements.crossfire.api.model.ProtocolMessageType type,
            final JsMessage msg) {
        switch (type) {
            case HOST -> {
                final var signal = new HostBroadcastSignal();
                signal.setProfileId(msg.getProfileId());
                this.host = signal.getProfileId();
                addToBacklog(signal);
                publishSignal(signal);
            }
            case SIGNAL_JOIN -> {
                final var signal = new JoinBroadcastSignal();
                signal.setProfileId(msg.getProfileId());
                profiles.add(signal.getProfileId());
                addToBacklog(signal);
                publishSignal(signal);
            }
            case SIGNAL_LEAVE -> {
                final var signal = new LeaveBroadcastSignal();
                signal.setProfileId(msg.getProfileId());
                profiles.removeIf(pid -> pid.equals(signal.getProfileId()));
                removeSessionBacklogFor(signal.getProfileId());
                addToBacklog(signal);
                publishSignal(signal);
            }
            case CONNECT -> {
                final var signal = new ConnectBroadcastSignal();
                signal.setProfileId(msg.getProfileId());
                addToBacklog(signal);
                publishSignal(signal);
            }
            case DISCONNECT -> {
                final var signal = new DisconnectBroadcastSignal();
                signal.setProfileId(msg.getProfileId());
                addToBacklog(signal);
                publishSignal(signal);
            }
            case STRING_BROADCAST -> {
                final var signal = new StringBroadcastSignal();
                signal.setProfileId(msg.getProfileId());
                signal.setPayload(msg.getPayload());
                parseLifecycle(msg.getLifecycle(), signal::setLifecycle);
                addToBacklog(signal);
                publishSignal(signal);
            }
            case BINARY_BROADCAST -> {
                final var signal = new BinaryBroadcastSignal();
                signal.setProfileId(msg.getProfileId());
                if (msg.getPayload() != null) signal.setPayload(Base64.getDecoder().decode(msg.getPayload()));
                parseLifecycle(msg.getLifecycle(), signal::setLifecycle);
                addToBacklog(signal);
                publishSignal(signal);
            }
            case STRING_RELAY -> {
                final var signal = new StringRelayDirectSignal();
                signal.setProfileId(msg.getProfileId());
                signal.setRecipientProfileId(msg.getRecipientProfileId());
                signal.setPayload(msg.getPayload());
                parseLifecycle(msg.getLifecycle(), signal::setLifecycle);
                addToBacklog(signal);
                publishSignal(signal);
            }
            case BINARY_RELAY -> {
                final var signal = new BinaryRelayDirectSignal();
                signal.setProfileId(msg.getProfileId());
                signal.setRecipientProfileId(msg.getRecipientProfileId());
                if (msg.getPayload() != null) signal.setPayload(Base64.getDecoder().decode(msg.getPayload()));
                parseLifecycle(msg.getLifecycle(), signal::setLifecycle);
                addToBacklog(signal);
                publishSignal(signal);
            }
            case ERROR -> handleError(msg);
            default    -> {}
        }
    }

    private void parseLifecycle(final String s, final Consumer<SignalLifecycle> setter) {
        if (s == null) return;
        try { setter.accept(SignalLifecycle.valueOf(s)); } catch (IllegalArgumentException ignored) {}
    }

    private void addToBacklog(final Signal signal) {
        if (SignalLifecycle.MATCH.equals(signal.getLifecycle())) backlog.add(signal);
    }

    private void removeSessionBacklogFor(final String leavingProfileId) {
        backlog.removeIf(signal -> {
            if (!SignalLifecycle.SESSION.equals(signal.getLifecycle())) return false;
            return switch (signal.getType().getCategory()) {
                case SIGNALING         -> leavingProfileId.equals(((BroadcastSignal) signal).getProfileId());
                case SIGNALING_DIRECT  -> leavingProfileId.equals(((DirectSignal) signal).getProfileId());
                default -> false;
            };
        });
    }

    private void handleError(final JsMessage msg) {
        final var ex = new RuntimeException("Protocol error [" + msg.getCode() + "]: " + msg.getMessage());
        publishError(ex);
        close();
    }

    private void onClose(final int code, final String reason) {
        if (phase != TERMINATED) phase = TERMINATED;
    }

    private void onError() {
        final var ex = new RuntimeException("WebSocket transport error");
        publishError(ex);
        close();
    }

    // -------------------------------------------------------------------------
    // SignalingClient — outgoing
    // -------------------------------------------------------------------------

    @Override
    public Version getVersion() {
        return Version.V_1_0;
    }

    @Override
    public MatchState getState() {
        final var snap_matchId    = matchId;
        final var snap_profileId  = profileId;
        final var snap_host       = host;
        final var snap_profiles   = List.copyOf(profiles);
        final var snap_phase      = phase;
        return new MatchState() {
            @Override public SignalingClientPhase getPhase()     { return snap_phase;    }
            @Override public String               getHost()      { return snap_host;     }
            @Override public String               getMatchId()   { return snap_matchId;  }
            @Override public String               getProfileId() { return snap_profileId;}
            @Override public List<String>         getProfiles()  { return snap_profiles; }
        };
    }

    @Override
    public Stream<Signal> backlog() {
        return List.copyOf(backlog).stream();
    }

    @Override
    public void handshake(final HandshakeRequest request) {
        if (phase != CONNECTED) throw new IllegalStateException("Handshake requires CONNECTED phase, currently " + phase);
        phase = HANDSHAKING;
        ws.send(serializeHandshake(request));
    }

    @Override
    public void signal(final Signal signal) {
        if (phase != SIGNALING) throw new IllegalStateException("Signal requires SIGNALING phase, currently " + phase);
        ws.send(serializeSignal(signal));
    }

    @Override
    public void control(final ControlMessage control) {
        if (phase != SIGNALING) throw new IllegalStateException("Control requires SIGNALING phase, currently " + phase);
        final var obj = JsJsonBuilder.newObj();
        JsJsonBuilder.set(obj, "type", control.getType().name());
        ws.send(JsJsonBuilder.stringify(obj));
    }

    @Override
    public Optional<HandshakeResponse> findHandshakeResponse() {
        return Optional.ofNullable(handshakeResponse);
    }

    @Override
    public Optional<DisconnectStatus> waitForDisconnect(final long time, final TimeUnit units) {
        throw new UnsupportedOperationException("Blocking wait not supported in TeaVM browser target");
    }

    @Override
    public void close() {
        if (phase != TERMINATED) {
            phase = TERMINATED;
            if (ws != null) {
                ws.close();
                ws = null;
            }
            signalListeners.clear();
            handshakeListeners.clear();
            errorListeners.clear();
        }
    }

    // -------------------------------------------------------------------------
    // Subscriptions
    // -------------------------------------------------------------------------

    @Override
    public Subscription onSignal(final BiConsumer<Subscription, Signal> listener) {
        return addListener(signalListeners, listener);
    }

    @Override
    public Subscription onHandshake(final BiConsumer<Subscription, HandshakeResponse> listener) {
        return addListener(handshakeListeners, listener);
    }

    @Override
    public Subscription onClientError(final BiConsumer<Subscription, Throwable> listener) {
        return addListener(errorListeners, listener);
    }

    private <T> Subscription addListener(final List<SubEntry<T>> list, final BiConsumer<Subscription, T> listener) {
        final var entry = new SubEntry<>(listener);
        list.add(entry);
        final Subscription sub = () -> list.remove(entry);
        entry.subscription = sub;
        return sub;
    }

    private void publishSignal(final Signal signal) {
        for (var entry : List.copyOf(signalListeners)) entry.listener.accept(entry.subscription, signal);
    }

    private void publishHandshake(final HandshakeResponse response) {
        for (var entry : List.copyOf(handshakeListeners)) entry.listener.accept(entry.subscription, response);
    }

    private void publishError(final Throwable error) {
        for (var entry : List.copyOf(errorListeners)) entry.listener.accept(entry.subscription, error);
    }

    // -------------------------------------------------------------------------
    // JSON serialization helpers
    // -------------------------------------------------------------------------

    private static String serializeHandshake(final HandshakeRequest request) {
        final var obj = JsJsonBuilder.newObj();
        JsJsonBuilder.set(obj, "type",       request.getType().name());
        JsJsonBuilder.set(obj, "version",    request.getVersion() == null ? null : request.getVersion().name());
        JsJsonBuilder.set(obj, "profileId",  request.getProfileId());
        JsJsonBuilder.set(obj, "sessionKey", request.getSessionKey());
        if (request instanceof FindHandshakeRequest fhr) {
            JsJsonBuilder.set(obj, "configuration", fhr.getConfiguration());
        } else if (request instanceof JoinHandshakeRequest jhr) {
            JsJsonBuilder.set(obj, "matchId", jhr.getMatchId());
        } else if (request instanceof JoinCodeHandshakeRequest jchr) {
            JsJsonBuilder.set(obj, "joinCode", jchr.getJoinCode());
        } else if (request instanceof CreateHandshakeRequest chr) {
            JsJsonBuilder.set(obj, "configuration", chr.getConfiguration());
        }
        return JsJsonBuilder.stringify(obj);
    }

    private static String serializeSignal(final Signal signal) {
        final var obj = JsJsonBuilder.newObj();
        JsJsonBuilder.set(obj, "type", signal.getType().name());
        if (signal instanceof BroadcastSignal bs) {
            JsJsonBuilder.set(obj, "profileId",  bs.getProfileId());
            JsJsonBuilder.set(obj, "lifecycle",  bs.getLifecycle() == null ? null : bs.getLifecycle().name());
        }
        if (signal instanceof DirectSignal ds) {
            JsJsonBuilder.set(obj, "profileId",          ds.getProfileId());
            JsJsonBuilder.set(obj, "recipientProfileId", ds.getRecipientProfileId());
            JsJsonBuilder.set(obj, "lifecycle",          ds.getLifecycle() == null ? null : ds.getLifecycle().name());
        }
        switch (signal.getType()) {
            case STRING_BROADCAST -> JsJsonBuilder.set(obj, "payload", ((StringBroadcastSignal)  signal).getPayload());
            case STRING_RELAY     -> JsJsonBuilder.set(obj, "payload", ((StringRelayDirectSignal) signal).getPayload());
            case BINARY_BROADCAST -> JsJsonBuilder.set(obj, "payload",
                    Base64.getEncoder().encodeToString(((BinaryBroadcastSignal)  signal).getPayload()));
            case BINARY_RELAY     -> JsJsonBuilder.set(obj, "payload",
                    Base64.getEncoder().encodeToString(((BinaryRelayDirectSignal) signal).getPayload()));
            default -> {}
        }
        return JsJsonBuilder.stringify(obj);
    }

    // -------------------------------------------------------------------------
    // Internal helper
    // -------------------------------------------------------------------------

    private static final class SubEntry<T> {
        final BiConsumer<Subscription, T> listener;
        Subscription subscription;
        SubEntry(final BiConsumer<Subscription, T> listener) { this.listener = listener; }
    }
}
