package dev.getelements.elements.crossfire.protocol.v10;


import dev.getelements.elements.crossfire.model.ProtocolMessage;
import dev.getelements.elements.crossfire.model.error.*;
import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.MatchedResponse;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.crossfire.model.signal.SignalWithRecipient;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static dev.getelements.elements.crossfire.model.error.ProtocolError.Code.INVALID_MESSAGE;
import static dev.getelements.elements.crossfire.protocol.ConnectionPhase.SIGNALING;
import static dev.getelements.elements.crossfire.protocol.ConnectionPhase.TERMINATED;
import static jakarta.websocket.CloseReason.CloseCodes.*;

public class V10ProtocolMessageHandler implements ProtocolMessageHandler {

    public static final String UNKNOWN_SESSION = "<unknown session id>";

    @ElementDefaultAttribute("100")
    public static final String MAX_BUFFER_SIZE = "dev.getelements.elements.crossfire.protocol.max.buffer.size";

    private static final Logger logger = LoggerFactory.getLogger(V10ProtocolMessageHandler.class);

    public static final Map<Class<? extends Throwable>, Function<Throwable, CloseReason>> EXPECTED_EXCEPTIONS = Map.of(
            TimeoutException.class, th -> new CloseReason(GOING_AWAY, th.getMessage()),
            ProtocolStateException.class, th -> new CloseReason(VIOLATED_POLICY, th.getMessage()),
            UnexpectedMessageException.class, th -> new CloseReason(VIOLATED_POLICY, th.getMessage()),
            MessageBufferOverrunException.class,  th -> new CloseReason(VIOLATED_POLICY, th.getMessage()),
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

    private HandshakeHandler handshakeHandler;

    private SignalingHandler signalingHandler;

    private final AtomicReference<V10ConnectionStateRecord> state = new AtomicReference<>(V10ConnectionStateRecord.create());

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
        final var result = state.updateAndGet(existing -> existing.start(session));
        pinger.start(session);
        getHandshakeHandler().start(this, session);
        logger.debug("{}: Connection started for session {}", result.phase(), session.getId());
    }

    @Override
    public void stop(final Session session) throws IOException {
        final var result = state.updateAndGet(V10ConnectionStateRecord::terminate);
        logger.debug("{}: Stopping protocol message handler {}.", result.phase(), result.sessionId());
    }

    @Override
    public void onMessage(final Session session, final PongMessage message) throws IOException {
        getPinger().onPong(session, message);
    }

    @Override
    public void onMessage(final Session session, final ProtocolMessage message) throws IOException {

        final var violations = getValidator().validate(message);

        if (violations.isEmpty()) {

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

            final var reason = new CloseReason(NOT_CONSISTENT, "Invalid Message.");
            doTerminate(reason, null);

        }

    }

    private void onMessageReadyPhase(
            final V10ConnectionStateRecord state,
            final Session session,
            final ProtocolMessage message) {

        switch (message.getType().getCategory()) {

            // In the READY state, the handshake messages will begin the handshake phase. A client may only attempt
            // to start the handshake once at the READY phase. It should be the first message sent by the client,
            // and any signaling messages sent before the handshake will be buffered.

            case HANDSHAKE -> {

                final var result = this.state.updateAndGet(V10ConnectionStateRecord::handshake);

                switch (result.phase()) {
                    case HANDSHAKE -> {

                        // The state was updated to HANDSHAKE, so we can now process the handshake request.
                        getHandshakeHandler().onMessage(
                                this,
                                session,
                                (HandshakeRequest) message
                        );

                        // Now that we entered the HANDSHAKE phase, we can process the handshake request. The above line
                        // ensures that the state is updated to HANDSHAKE before processing the message and that multiple
                        // handshake requests are not allowed in the READY phase.

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
            final V10ConnectionStateRecord state,
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

        final var result = this.state.updateAndGet(existing -> switch (existing.phase()) {

            case SIGNALING -> existing;

            case READY, WAITING, HANDSHAKE -> {

                if (existing.ib().size() >= getMaxBufferSize()) {
                    throw new MessageBufferOverrunException("Inbound buffer size exceeded: " + existing.ib().size());
                }

                yield existing.bufferInbound(message);

            }

            default -> throw invalid(existing, message);

        });

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
            final V10ConnectionStateRecord state,
            final Session session,
            final ProtocolMessage message) {
        switch (message.getType().getCategory()) {
            // Messages in the signaling category will be processed by the signaling handler based on their type.
            case SIGNALING -> getSignalingHandler().onMessage(this, session, (Signal) message);
            case SIGNALING_DIRECT -> getSignalingHandler().onMessageDirect(this, session, (SignalWithRecipient) message);
            // All other message types in the SIGNALING phase are invalid and will throw an exception.
            default -> throw invalid(state, message);
        }
    }

    @Override
    public Future<?> submit(final Runnable task) {
        return getExecutorService().submit(() -> {

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

        });

    }

    @Override
    public void send(final ProtocolMessage message) {

        final var result = state.updateAndGet(existing -> switch (existing.phase()) {

            case SIGNALING -> existing;

            case READY, WAITING, HANDSHAKE -> {

                if (existing.ob().size() >= getMaxBufferSize()) {
                    throw new MessageBufferOverrunException("Inbound buffer size exceeded: " + existing.ib().size());
                }

                yield existing.bufferOutbound(message);

            }

            default -> throw invalid(existing, message);

        });

        if (SIGNALING.equals(result.phase())) {

            result.session().getAsyncRemote().sendObject(message);

            logger.debug("{}: Skipping buffer for outbound message {} for session {} in phase.",
                    result.phase(),
                    message.getType(),
                    result.sessionId()
            );

        } else {
            logger.debug("{}: Buffered outbound message {} for session {} in phase.",
                    result.phase(),
                    message.getType(),
                    result.sessionId()
            );
        }

    }

    @Override
    public void matched(final MultiMatchRecord multiMatchRecord) {

        V10ConnectionStateRecord update;
        V10ConnectionStateRecord existing;

        do {
            existing = state.get();
        } while (!state.compareAndSet(existing, update = existing.matched(multiMatchRecord)));

        logger.debug("{}: Matched session {} to match {}.   ",
                update.phase(),
                update.sessionId(),
                multiMatchRecord.getId()
        );

        if (SIGNALING.equals(update.phase())) {
            final var response = new MatchedResponse();
            response.setMatchId(update.match().getId());
            update.session().getAsyncRemote().sendObject(response);
            processBacklog(existing);
            getSignalingHandler().start(this, update.session());
        }

    }

    @Override
    public void authenticated(final AuthRecord authRecord) {

        V10ConnectionStateRecord update;
        V10ConnectionStateRecord existing;

        do {
            existing = state.get();
        } while (!state.compareAndSet(existing, update = existing.authenticated(authRecord)));

        logger.debug("{}: Authenticated session {} for {}",
                update.phase(),
                update.sessionId(),
                authRecord.session().getProfile().getId()
        );

        if (SIGNALING.equals(update.phase())) {
            final var response = new MatchedResponse();
            response.setMatchId(update.match().getId());
            update.session().getAsyncRemote().sendObject(response);
            processBacklog(existing);
            getSignalingHandler().start(this, update.session());
        }

    }

    private void processBacklog(final V10ConnectionStateRecord state) {

        final var inbound = state.ib() == null ? List.<ProtocolMessage>of() : state.ib();
        final var outbound = state.ob() == null ? List.<ProtocolMessage>of() : state.ob();

        logger.debug("{}: Processing backlog for session {}", state.phase(), state.sessionId());
        inbound.forEach(message -> onSignalingMessage(state, state.session(), message));

        logger.debug("{}: Processed inbound backlog for session {}. Count: {}",
                state.phase(),
                state.sessionId(),
                inbound.size()
        );

        final var remote = state.session().getAsyncRemote();
        outbound.forEach(remote::sendObject);

        logger.debug("{}: Processed outbound backlog for session {}. Count: {}",
                state.phase(),
                state.sessionId(),
                outbound.size()
        );

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
            final V10ConnectionStateRecord state,
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

        V10ConnectionStateRecord curent;

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

        final var result = state.updateAndGet(V10ConnectionStateRecord::terminate);
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
        getHandshakeHandler().stop(this, session);
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

    private ProtocolStateException invalid(final V10ConnectionStateRecord phase, final ProtocolMessage message) {

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

    public HandshakeHandler getHandshakeHandler() {
        return handshakeHandler;
    }

    @Inject
    public void setHandshakeHandler(HandshakeHandler handshakeHandler) {
        this.handshakeHandler = handshakeHandler;
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
