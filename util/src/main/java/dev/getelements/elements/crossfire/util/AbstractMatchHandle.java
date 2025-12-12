package dev.getelements.elements.crossfire.util;

import dev.getelements.elements.crossfire.api.MatchHandle;
import dev.getelements.elements.crossfire.api.MatchPhase;
import dev.getelements.elements.crossfire.api.MatchmakingAlgorithm;
import dev.getelements.elements.crossfire.api.MatchmakingRequest;
import dev.getelements.elements.crossfire.api.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.api.model.handshake.HandshakeRequest;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static dev.getelements.elements.crossfire.util.CancelableMatchStateRecord.create;

/**
 * Abstract implementation of a {@link MatchHandle}.
 *
 * @param <RequestT> the type of handshake request
 */
public abstract class AbstractMatchHandle<RequestT extends HandshakeRequest> implements MatchHandle<RequestT> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractMatchHandle.class);

    private final MatchmakingAlgorithm algorithm;

    private final MatchmakingRequest<RequestT> request;

    private final AtomicReference<CancelableMatchStateRecord<RequestT>> state = new AtomicReference<>(create());

    protected AbstractMatchHandle(final MatchmakingAlgorithm algorithm, final MatchmakingRequest<RequestT> request) {
        this.request = request;
        this.algorithm = algorithm;
    }

    @Override
    public void startMatcing() {

        final var state = this.state.updateAndGet(CancelableMatchStateRecord::matching);

        switch (state.phase()) {
            case MatchPhase.MATCHING -> onMatching(state);
            case MatchPhase.TERMINATED -> logger.debug("Can't start. Match was previously terminated: {}", request);
            default -> throw new ProtocolStateException("Unknown phase: " + state.phase());
        }

    }

    @Override
    public void leaveMatch() {

        final var state = this.state.getAndUpdate(CancelableMatchStateRecord::terminate);

        switch (state.phase()) {
            case MatchPhase.MATCHED -> onLeaveMatch(state);
            case MatchPhase.MATCHING -> onTerminated(state);
            case MatchPhase.TERMINATED -> logger.debug("Match handle closed. Cannot leave match: {}", request);
            default -> throw new ProtocolStateException("Unknown phase: " + state.phase());
        }

    }

    @Override
    public void endMatch() {

        final var state = this.state.get();

        switch (state.phase()) {
            case MatchPhase.MATCHED -> onEndMatch(state);
            case MatchPhase.TERMINATED -> logger.debug("Match handle closed. Cannot end match: {}", request);
            default -> throw new ProtocolStateException("Unknown phase: " + state.phase());
        }

    }

    @Override
    public void openMatch() {

        final var state = this.state.get();

        switch (state.phase()) {
            case MatchPhase.MATCHED -> onOpenMatch(state);
            case MatchPhase.TERMINATED -> logger.debug("Match handle closed. Cannot open match: {}", request);
            default -> throw new ProtocolStateException("Unknown phase: " + state.phase());
        }

    }

    @Override
    public void closeMatch() {

        final var state = this.state.get();

        switch (state.phase()) {
            case MatchPhase.MATCHED -> onCloseMatch(state);
            case MatchPhase.TERMINATED -> logger.debug("Match handle closed. Cannot close match: {}", request);
            default -> throw new ProtocolStateException("Unknown phase: " + state.phase());
        }

    }

    @Override
    public Optional<MultiMatch> findResult() {
        return Optional.of(state.get().result());
    }

    @Override
    public MatchmakingRequest<RequestT> getRequest() {
        return request;
    }

    @Override
    public MatchmakingAlgorithm getAlgorithm() {
        return algorithm;
    }

    /**
     * Sets the result of the matchmaking process. This transitions the state to {@link MatchPhase#MATCHED}. Intended
     * to be called by subclasses when a match is found to accurately handle state transitions. It is not recommended to
     * override this method.
     *
     * @param result the result
     * @return the {@link CancelableMatchStateRecord} after the update
     */
    protected CancelableMatchStateRecord<RequestT> setResult(final MultiMatch result) {

        final var state = this.state.updateAndGet(s -> s.matched(result));

        switch (state.phase()) {

            case MatchPhase.MATCHED -> {
                logger.info("Matched for request: {}", request);
                onResult(state, result);
            }

            case MatchPhase.TERMINATED -> logger.debug("Terminating matchmaking algorithm for request: {}", request);
            default -> throw new ProtocolStateException("Invalid phase: " + state.phase());

        }

        return state;

    }

    /**
     * Called to open the match.
     *
     * @param state the state result as part of the operation
     */
    protected abstract void onOpenMatch(CancelableMatchStateRecord<RequestT> state);

    /**
     * Called to close the match.
     *
     * @param state the state result as part of the operation
     * */
    protected abstract void onCloseMatch(CancelableMatchStateRecord<RequestT> state);

    /**
     * Called to end the match.
     *
     * @param state the state result as part of the operation
     */
    protected abstract void onEndMatch(CancelableMatchStateRecord<RequestT> state);

    /**
     * Called when matching has started.
     *
     * @param state the state result as part of the operation
     */
    protected abstract void onMatching(CancelableMatchStateRecord<RequestT> state);

    /**
     * Called when the match has matching has been terminated and the player has left the queue.
     *
     * @param state the state result as part of the operation
     */
    protected abstract void onLeaveMatch(CancelableMatchStateRecord<RequestT> state);

    /**
     * Called when the match has matching has been terminated.
     *
     * @param state the state result as part of the operation
     */
    protected void onTerminated(final CancelableMatchStateRecord<RequestT> state) {
        logger.debug("Terminating matchmaking for request: {}", request);
    }

    /**
     * Called when the match has been made.
     *
     * @param state the state result as part of the operation
     * @param result the resulting {@link MultiMatch}
     */
    protected abstract void onResult(CancelableMatchStateRecord<RequestT> state, MultiMatch result);

}
