package dev.getelements.elements.crossfire.api;

import dev.getelements.elements.crossfire.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static dev.getelements.elements.crossfire.api.CancelableMatchStateRecord.create;

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
    public void start() {

        final var state = this.state.updateAndGet(CancelableMatchStateRecord::matching);

        switch (state.phase()) {

            case MATCHING -> {
                logger.debug("Starting matchmaking algorithm for request: {}", request);
                onMatching(state);
            }

            case TERMINATED -> logger.debug("Terminating matchmaking algorithm for request: {}", request);

            default -> throw new ProtocolStateException("Unknown phase: " + state.phase());

        }

    }

    @Override
    public void leave() {

        final var state = this.state.updateAndGet(CancelableMatchStateRecord::terminate);

        switch (state.phase()) {

            case MATCHING -> {
                logger.debug("Starting matchmaking algorithm for request: {}", request);
                onTerminated(state);
            }

            case TERMINATED -> logger.debug("Terminating matchmaking algorithm for request: {}", request);

            default -> throw new ProtocolStateException("Unknown phase: " + state.phase());

        }

    }

    protected CancelableMatchStateRecord<RequestT> result(final MultiMatch result) {

        final var state = this.state.updateAndGet(s -> s.matched(result));

        switch (state.phase()) {

            case MATCHING -> {
                logger.info("Matched for request: {}", request);
                onResult(state, result);
            }

            case TERMINATED -> logger.debug("Terminating matchmaking algorithm for request: {}", request);
            default -> throw new ProtocolStateException("Invalid phase: " + state.phase());

        }

        return state;

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

    protected abstract void onMatching(CancelableMatchStateRecord<RequestT> state);

    protected abstract void onTerminated(CancelableMatchStateRecord<RequestT> state);

    protected abstract void onResult(CancelableMatchStateRecord<RequestT> state, MultiMatch result);

}
