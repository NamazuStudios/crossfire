package dev.getelements.elements.crossfire.api;

import dev.getelements.elements.sdk.annotation.ElementPublic;
import dev.getelements.elements.sdk.annotation.ElementServiceExport;

import java.util.Optional;
import java.util.Set;

/**
 * Selects the host participant for a match's signaling state.
 *
 * Implementations are resolved per {@link dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration}
 * via an {@link dev.getelements.elements.sdk.model.application.ElementServiceReference} stored in the
 * configuration's {@code hostOrchestration} field. When no reference is configured, the bundled
 * peer-to-peer strategy (first connected participant, promotion on disconnect) is used.
 *
 * Implementations must be stateless with respect to a single match: they may be shared across
 * all matches of a configuration and are invoked while holding the match state's write lock, so
 * they must not block or perform I/O.
 */
@ElementPublic
@ElementServiceExport
public interface HostOrchestrationStrategy {

    /**
     * Selects the host profile ID from the set of currently connected participants.
     *
     * Called both for initial host assignment (when no host is currently assigned, typically as
     * the first participant connects) and for reassignment after the current host has
     * disconnected.
     *
     * @param matchId the ID of the match
     * @param connectedProfiles profile IDs of the participants considered connected; the order
     *                          is stable and reflects connection order where the caller can
     *                          provide it (a linked set)
     * @param currentHost the profile ID of the current host, or empty if none is assigned
     * @return the profile ID to designate as host, or empty if no host should be assigned
     */
    Optional<String> selectHost(
            String matchId,
            Set<String> connectedProfiles,
            Optional<String> currentHost);

}
