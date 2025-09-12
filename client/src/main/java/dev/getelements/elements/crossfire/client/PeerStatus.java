package dev.getelements.elements.crossfire.client;

/**
 * Indicates a change in status for a particular peer.
 *
 * @param phase the phase
 * @param peer the peer itself
 */
public record PeerStatus(PeerPhase phase, Peer peer) {}
