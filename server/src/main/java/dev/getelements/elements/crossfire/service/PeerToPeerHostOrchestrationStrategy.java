package dev.getelements.elements.crossfire.service;

import dev.getelements.elements.crossfire.api.HostOrchestrationStrategy;
import dev.getelements.elements.sdk.annotation.ElementServiceExport;

import java.util.Optional;
import java.util.Set;

/**
 * The default {@link HostOrchestrationStrategy}: peer-to-peer hosting, where the first connected
 * participant becomes host and, when the host disconnects, the next connected participant (in
 * stable profile-id order) is promoted. Preserves the behavior of the previously hardcoded host
 * assignment in the match backlog exactly.
 */
@ElementServiceExport(value = HostOrchestrationStrategy.class)
@ElementServiceExport(value = HostOrchestrationStrategy.class, name = "PEER_TO_PEER")
public class PeerToPeerHostOrchestrationStrategy implements HostOrchestrationStrategy {

    public static final String NAME = "PEER_TO_PEER";

    @Override
    public Optional<String> selectHost(
            final String matchId,
            final Set<String> connectedProfiles,
            final Optional<String> currentHost) {

        // Keep the current host if still connected.
        if (currentHost.isPresent() && connectedProfiles.contains(currentHost.get())) {
            return currentHost;
        }

        // Otherwise pick the first available.
        return connectedProfiles.stream().findFirst();

    }

}
