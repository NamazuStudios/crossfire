package dev.getelements.elements.crossfire.protocol.v10;

import dev.getelements.elements.crossfire.api.MatchmakingRequest;
import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.MatchedResponse;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler.MultiMatchRecord;
import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
import dev.getelements.elements.sdk.model.match.MultiMatch;
import dev.getelements.elements.sdk.model.profile.Profile;

import java.util.concurrent.atomic.AtomicReference;

class V10MatchRequest<MessageT extends HandshakeRequest> implements MatchmakingRequest<MessageT> {

    private final ProtocolMessageHandler protocolMessageHandler;

    private final AtomicReference<V10HandshakeStateRecord> state;

    private final Profile profile;

    private final MessageT handshakeRequest;

    private final MatchmakingApplicationConfiguration applicationConfiguration;

    public V10MatchRequest(
            final ProtocolMessageHandler protocolMessageHandler,
            final AtomicReference<V10HandshakeStateRecord> state,
            final Profile profile,
            final MessageT handshakeRequest,
            final MatchmakingApplicationConfiguration applicationConfiguration) {
        this.state = state;
        this.profile = profile;
        this.handshakeRequest = handshakeRequest;
        this.protocolMessageHandler = protocolMessageHandler;
        this.applicationConfiguration = applicationConfiguration;
    }

    @Override
    public Profile getProfile() {
        return profile;
    }

    @Override
    public MessageT getHandshakeRequest() {
        return handshakeRequest;
    }

    @Override
    public MatchmakingApplicationConfiguration getApplicationConfiguration() {
        return applicationConfiguration;
    }

    @Override
    public ProtocolMessageHandler getProtocolMessageHandler() {
        return protocolMessageHandler;
    }

    @Override
    public void failure(final Throwable th) {
        final var state = this.state.updateAndGet(V10HandshakeStateRecord::terminate);
        state.cancelPending();
        getProtocolMessageHandler().terminate(th);
    }

    @Override
    public void success(final MultiMatch multiMatch) {

        final var multiMatchRecord = new MultiMatchRecord(multiMatch, applicationConfiguration);
        final var state = this.state.updateAndGet(s -> s.matched(multiMatchRecord));

        switch (state.phase()) {
            case TERMINATED -> state.cancelPending();
            case MATCHED -> getProtocolMessageHandler().matched(multiMatchRecord);
        }

    }

}
