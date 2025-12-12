package dev.getelements.elements.crossfire.util;

import dev.getelements.elements.crossfire.api.MatchPhase;
import dev.getelements.elements.crossfire.api.model.error.ProtocolStateException;
import dev.getelements.elements.crossfire.api.model.handshake.HandshakeRequest;
import dev.getelements.elements.sdk.model.match.MultiMatch;

import static dev.getelements.elements.crossfire.api.MatchPhase.*;

public record CancelableMatchStateRecord<RequestT extends HandshakeRequest>(MatchPhase phase, MultiMatch result) {

    public static
    <RequestT extends HandshakeRequest>
    CancelableMatchStateRecord<RequestT> create() {
        return new CancelableMatchStateRecord<>(MatchPhase.READY, null);
    }

    public CancelableMatchStateRecord<RequestT> matching() {
        return switch (phase()) {
            case TERMINATED -> this;
            case READY -> new CancelableMatchStateRecord<>(MATCHING, null);
            default -> throw new ProtocolStateException("Unexpected phase: " + phase());
        };
    }

    public CancelableMatchStateRecord<RequestT> matched(final MultiMatch result) {
        return switch (phase()) {
            case TERMINATED -> this;
            case MATCHING -> new CancelableMatchStateRecord<>(MATCHED, result);
            default -> throw new ProtocolStateException("Unexpected phase: " + phase());
        };
    }

    public CancelableMatchStateRecord<RequestT> terminate() {
        return switch (phase()) {
            case TERMINATED -> this;
            default -> new CancelableMatchStateRecord<>(TERMINATED, result);
        };
    }

}
