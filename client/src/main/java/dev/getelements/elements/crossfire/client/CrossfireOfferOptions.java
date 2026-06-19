package dev.getelements.elements.crossfire.client;

/**
 * Implementation-agnostic offer options for a WebRTC peer connection.
 */
public record CrossfireOfferOptions(
        boolean voiceActivityDetection,
        boolean iceRestart
) {

    public static CrossfireOfferOptions defaults() {
        return new CrossfireOfferOptions(true, false);
    }

}
