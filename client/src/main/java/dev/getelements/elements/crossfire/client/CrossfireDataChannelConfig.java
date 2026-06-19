package dev.getelements.elements.crossfire.client;

/**
 * Implementation-agnostic configuration for a WebRTC data channel.
 */
public record CrossfireDataChannelConfig(
        boolean ordered,
        boolean negotiated,
        int maxPacketLifeTime,
        int maxRetransmits,
        int id,
        String protocol
) {

    public static CrossfireDataChannelConfig defaults() {
        return new CrossfireDataChannelConfig(true, false, -1, -1, -1, null);
    }

}
