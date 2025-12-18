package dev.getelements.elements.crossfire.protocol.v1;


import dev.getelements.elements.crossfire.api.model.ProtocolMessage;
import dev.getelements.elements.crossfire.api.model.Version;
import dev.getelements.elements.crossfire.api.model.control.ControlMessage;
import dev.getelements.elements.crossfire.api.model.error.*;
import dev.getelements.elements.crossfire.api.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.api.model.handshake.MatchedResponse;
import dev.getelements.elements.crossfire.api.model.signal.BroadcastSignal;
import dev.getelements.elements.crossfire.api.model.signal.DirectSignal;
import dev.getelements.elements.crossfire.protocol.*;
import dev.getelements.elements.sdk.annotation.ElementDefaultAttribute;
import dev.getelements.elements.sdk.model.exception.BaseException;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.websocket.CloseReason;
import jakarta.websocket.PongMessage;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static dev.getelements.elements.crossfire.api.model.error.ProtocolError.Code.INVALID_MESSAGE;
import static dev.getelements.elements.crossfire.protocol.ConnectionPhase.SIGNALING;
import static dev.getelements.elements.crossfire.protocol.ConnectionPhase.TERMINATED;
import static jakarta.websocket.CloseReason.CloseCodes.*;

public class V1ProtocolMessageHandler implements ProtocolMessageHandler {

    public static final String UNKNOWN_SESSION = "<unknown session id>";

    @ElementDefaultAttribute("100")
    public static final String MAX_BUFFER_SIZE = "dev.getelements.elements.crossfire.protocol.max.buffer.size";

    private static final Logger logger = LoggerFactory.getLogger(V1ProtocolMessageHandler.class);

    public static final Map<Class<? extends Throwable>, Function<Throwable, CloseReason>> EXPECTED_EXCEPTIONS = Map.of(
            TimeoutException.class, th -> new CloseReason(GOING_AWAY, th.getMessage()),
            ProtocolStateException.class, th -> new CloseReason(VIOLATED_POLICY, th.getMessage()),
            UnexpectedMessageException.class, th -> new CloseReason(VIOLATED_POLICY, th.getMessage()),
            MessageBufferOverrunException.class, th -> new CloseReason(VIOLATED_POLICY, th.getMessage()),
            BaseException.class,                 th -> new CloseReason(
                    switch (((BaseException)th).getCode()) {
                        case UNKNOWN -> UNEXPECTED_CONDITION;
                        case OVERLOAD -> TRY_AGAIN_LATER;
                        default -> VIOLATED_POLICY;
                    },
                    th.getMessage()
            )
    );

    private int maxBufferSize;

    private Pinger pinger;

    private Validator validator;

    private ExecutorService executorService;

    private HandshakeHandler v10HandshakeHandler;

    private HandshakeHandler v11HandshakeHandler;

    private SignalingHandler signalingHandler;

    private final AtomicReference<V1ConnectionStateRecord> state = new AtomicReference<>(V1ConnectionStateRecord.create());

    @Override
    public ConnectionPhase getPhase() {
        return state.get().phase();
    }

    @Override
    public Optional<AuthRecord> findAuthRecord() {
        return Optional.ofNullable(state.get().auth());
    }

    @Override
    public Optional<MultiMatchRecord> findMatchRecord() {
        return Optional.ofNullable(state.get().match());
    }

    @Override
    public void start(final Session session) throws IOException {
        perform(() -> {
            final var result = state.updateAndGet(existing -> existing.start(session));
            pinger.start(session);
            getV10HandshakeHandler().start(this, session);
            getV11HandshakeHandler().start(this, session);
            logger.debug("{}: Connection started for session {}", result.phase(), session.getId());
        });
    }

    @Override
    public void stop(final Session session) throws IOException {
        final var result = state.updateAndGet(V1ConnectionStateRecord::terminate);
        getPinger().stop();
        getV10HandshakeHandler().stop(this, session);
        getV11HandshakeHandler().stop(this, session);
        getSignalingHandler().stop(this, session);
        terminate();
        logger.debug("{}: Stopping protocol message handler {}.", result.phase(), result.sessionId());
    }

    @Override
    public void onMessage(final Session session, final PongMessage message) throws IOException {
        perform(() -> getPinger().onPong(session, message));
    }

    @Override
    public void onMessage(final Session session, final ProtocolMessage message) throws IOException {
        perform(() -> {

            final var violations = getValidator().validate(message);

            if (message.isServerOnly()) {

                final var error = new StandardProtocolError();
                error.setCode(INVALID_MESSAGE.toString());
                error.setMessage("Invalid message: " + message.getType() + " is server-only. Rejecting.");
                session.getAsyncRemote().sendObject(error);

                final var reason = new CloseReason(NOT_CONSISTENT, "Invalid Message.");
                doTerminate(reason, null);

            } else if (violations.isEmpty()) {

                final var state = this.state.get();
                logger.debug("{}: Session {} received protocol message {}", state, session.getId(), message.getType());

                switch (state.phase()) {
                    case READY -> onMessageReadyPhase(state, session, message);
                    case HANDSHAKE -> onMessageHandshakePhase(state, session, message);
                    case SIGNALING -> onSignalingMessage(state, session, message);
                    default -> throw invalid(state, message);
                }

            } else {

                final var error = new StandardProtocolError();
                error.setCode(INVALID_MESSAGE.toString());
                error.setMessage("Invalid message: " + violations
                        .stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.joining("\n"))
                );

                session.getAsyncRemote().sendObject(error);

                final var reason = new CloseReason(NOT_CONSISTENT, "Invalid Message.");
                doTerminate(reason, null);

            }

        });
    }

    private void onMessageReadyPhase(
            final V1ConnectionStateRecord state,
            final Session session,
            final ProtocolMessage message) {

        final var handshake = (HandshakeRequest) message;

        switch (message.getType().getCategory()) {

            // In the READY state, the handshake messages will begin the handshake phase. A client may only attempt
            // to start the handshake once at the READY phase. It should be the first message sent by the client,
            // and any signaling messages sent before the handshake will be buffered.

            case HANDSHAKE -> {

                final var result = this.state.updateAndGet(V1ConnectionStateRecord::handshake);

                switch (result.phase()) {
                    case HANDSHAKE -> {

                        // The state was updated to HANDSHAKE, so we can now process the handshake request.
                        switch (handshake.getType().getVersion()) {
                            case V_1_0 -> getV10HandshakeHandler().onMessage(this, session, handshake);
                            case V_1_1 -> getV11HandshakeHandler().onMessage(this, session, handshake);
                            default -> throw invalid(state, message);
                        };

                        // Now that we entered the HANDSHAKE phase, we can process the handshake request. The above line
                        // ensures that the state is updated to HANDSHAKE before processing the message and that
                        // multiple handshake requests are not allowed in the READY phase.

                        logger.debug("{}: Started handshake {} for session {}.",
                                result,
                                message.getType(),
                                session.getId()
                        );


                    }

                    // We got the termination request while in the READY phase. We just ignore the message and do not
                    // allow the protocol to continue. Just log and drop the message.

                    case TERMINATED -> logger.debug("{}: Session {} already terminated, ignoring handshake request.",
                            result.phase(),
                            session.getId()
                    );

                    default -> throw invalid(state, message);

                }

            }

            // In the READY state, signaling messages will be sent to the signaling handler for later processing.
            case SIGNALING, SIGNALING_DIRECT -> bufferInbound(message);

            // In the READY state, all other message types are invalid and will throw an exception.
            default -> throw invalid(state, message);

        };

    }

    private void onMessageHandshakePhase(
            final V1ConnectionStateRecord state,
            final Session session,
            final ProtocolMessage message) {
        switch (message.getType().getCategory()) {
            // In the HANDSHAKE phase, the handshake messages will be buffered until the handshake is complete.
            case SIGNALING, SIGNALING_DIRECT -> bufferInbound(message);
            // All other message types in the HANDSHAKE phase are invalid and will throw an exception.
            default -> throw invalid(state, message);
        }
    }

    public void bufferInbound(final ProtocolMessage message) {

        final var result = this.state.updateAndGet(existing ->
                switch (existing.phase()) {
                    case SIGNALING -> existing;
                    default -> throw invalid(existing, message);
                }
        );

        if (SIGNALING.equals(result.phase())) {

            // The state of the protocol changed while buffering the message, so we take the message and process it
            // as if it was received in the SIGNALING phase.
            onMessageHandshakePhase(result, result.session(), message);

            logger.debug("{}: Skipping buffer for inbound message {} for session {} in phase.",
                    result.phase(),
                    message.getType(),
                    result.sessionId()
            );

        } else {
            logger.debug("{}: Buffered inbound message {} for session {} in phase.",
                    result.phase(),
                    message.getType(),
                    result.sessionId()
            );
        }

    }

    private void onSignalingMessage(
            final V1ConnectionStateRecord state,
            final Session session,
            final ProtocolMessage message) {
        switch (message.getType().getCategory()) {
            // Messages in the signaling category will be processed by the signaling handler based on their type.
            case CONTROL -> getSignalingHandler().onMessageControl(this, session, (ControlMessage) message);
            case SIGNALING -> getSignalingHandler().onMessage(this, session, (BroadcastSignal) message);
            case SIGNALING_DIRECT -> getSignalingHandler().onMessageDirect(this, session, (DirectSignal) message);
            // All other message types in the SIGNALING phase are invalid and will throw an exception.
            default -> throw invalid(state, message);
        }
    }

    @Override
    public Future<?> submit(final Runnable task) {
        return getExecutorService().submit(() -> perform(task));
    }

    private void perform(final Runnable task) {
        try {
            task.run();
        } catch (final Throwable th) {

            final var state = this.state.get();

            logger.error("{}: Error executing task {} for session {}: {}",
                    state.phase(),
                    task.getClass().getName(),
                    state.sessionId(),
                    th.getMessage(),
                    th
            );

            terminate(th);

        }
    }

    @Override
    public void matched(final MultiMatchRecord multiMatchRecord) {

        final var update = state.updateAndGet(state -> state.matched(multiMatchRecord));

        logger.debug("{}: Matched session {} to match {}.   ",
                update.phase(),
                update.sessionId(),
                multiMatchRecord.getId()
        );

        if (SIGNALING.equals(update.phase())) {
            startSignaling(update);
        }

    }

    @Override
    public void authenticated(final AuthRecord authRecord) {

        final var update = state.updateAndGet(state -> state.authenticated(authRecord));

        logger.debug("{}: Authenticated session {} for {}",
                update.phase(),
                update.sessionId(),
                authRecord.profile().getId()
        );

        if (SIGNALING.equals(update.phase())) {
            startSignaling(update);
        }

    }

    private void startSignaling(final V1ConnectionStateRecord state) {

        logger.debug("Starting signaling for session {} in match {}.",
                state.sessionId(),
                state.match().getId()
        );

        final var response = state
                .match()
                .matchHandle()
                .newHandshakeResponse();

        state.session()
                .getAsyncRemote()
                .sendObject(response);

        getSignalingHandler().start(this, state.session(), state.match(), state.auth());

    }

    @Override
    public void terminate() {
        final var reason = new CloseReason(NORMAL_CLOSURE, "Closing session normally.");
        doTerminate(reason, null);
    }

    @Override
    public void terminate(final Throwable th) {

        final var reason = EXPECTED_EXCEPTIONS.entrySet()
                .stream()
                .filter(entry -> entry.getKey().isInstance(th))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(t -> new CloseReason(UNEXPECTED_CONDITION, t.getMessage()))
                .apply(th);

        doTerminate(reason, th);

    }

    private void log(
            final V1ConnectionStateRecord state,
            final CloseReason closeReason,
            final Throwable th) {

        final BiConsumer<String, Object[]> method = switch (closeReason.getCloseCode()) {
            case GOING_AWAY, VIOLATED_POLICY -> logger::debug;
            default -> logger::error;
        };

        method.accept(
                "{}: Closing session {} due to {}: {}",
                new Object[]{
                        state.phase(),
                        state.sessionId(),
                        closeReason.getCloseCode().getCode(),
                        closeReason.getReasonPhrase(),
                        th
                }
        );

    }

    private void doTerminate(final CloseReason reason, final Throwable th) {

        V1ConnectionStateRecord curent;

        do {

            curent = state.get();

            if (curent.phase() == TERMINATED) {
                logger.debug("{}: Session {} already terminated, ignoring error.",
                        curent.phase(),
                        curent.sessionId()
                );
                return;
            }

        } while (!state.compareAndSet(curent, curent.terminate()));

        final var result = state.updateAndGet(V1ConnectionStateRecord::terminate);
        final var session = result.session();

        if (th == null) {
            logger.debug("{}: Closing session {} due to {}: {}",
                    result.phase(),
                    result.sessionId(),
                    reason.getCloseCode().getCode(),
                    reason.getReasonPhrase()
            );
        } else if (session != null) {
            final var error = StandardProtocolError.from(th);
            session.getAsyncRemote().sendObject(error);
            log(result, reason, th);
        } else {
            log(result, reason, th);
        }

        getPinger().stop();
        getV10HandshakeHandler().stop(this, session);
        getSignalingHandler().stop(this, session);

        try {
            if (session != null)
                session.close(reason);
        } catch (final IOException ex) {
            logger.error("{}: Failed to close session {} due to {}: {}",
                    result.phase(),
                    result.sessionId(),
                    reason.getCloseCode().getCode(),
                    reason.getReasonPhrase(),
                    ex
            );
        }

    }

    private ProtocolStateException invalid(final V1ConnectionStateRecord phase, final ProtocolMessage message) {

        logger.error("{}: Not ready to accept {}({}} messages in phase.",
                phase.phase(),
                message.getType().getCategory(),
                message.getType()
        );

        return new ProtocolStateException(
                "Unexpected message type " +
                message.getType() +
                " in phase " +
                phase.phase()
        );

    }

    public Pinger getPinger() {
        return pinger;
    }

    @Inject
    public void setPinger(Pinger pinger) {
        this.pinger = pinger;
    }

    public Validator getValidator() {
        return validator;
    }

    @Inject
    public void setValidator(Validator validator) {
        this.validator = validator;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    @Inject
    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public HandshakeHandler getV10HandshakeHandler() {
        return v10HandshakeHandler;
    }

    @Inject
    public void setV10HandshakeHandler(@Named(Version.VERSION_1_0_NAME) HandshakeHandler v10HandshakeHandler) {
        this.v10HandshakeHandler = v10HandshakeHandler;
    }

    public HandshakeHandler getV11HandshakeHandler() {
        return v11HandshakeHandler;
    }

    @Inject
    public void setV11HandshakeHandler(@Named(Version.VERSION_1_1_NAME) HandshakeHandler v11HandshakeHandler) {
        this.v11HandshakeHandler = v11HandshakeHandler;
    }

    public SignalingHandler getSignalingHandler() {
        return signalingHandler;
    }

    @Inject
    public void setSignalingHandler(SignalingHandler signalingHandler) {
        this.signalingHandler = signalingHandler;
    }

    public int getMaxBufferSize() {
        return maxBufferSize;
    }

    @Inject
    public void setMaxBufferSize(@Named(MAX_BUFFER_SIZE) final int maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
    }

}
