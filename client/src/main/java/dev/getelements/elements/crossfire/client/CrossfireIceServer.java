package dev.getelements.elements.crossfire.client;

import java.util.List;

/**
 * Implementation-agnostic representation of a WebRTC ICE server (STUN or TURN).
 */
public record CrossfireIceServer(
        List<String> urls,
        String username,
        String password,
        String hostname,
        CrossfireTlsCertPolicy tlsCertPolicy
) {

    /**
     * Returns the default Google public STUN servers as a single {@link CrossfireIceServer}.
     */
    public static List<CrossfireIceServer> googleDefaults() {
        return List.of(new CrossfireIceServer(
                List.of(
                        "stun:stun.l.google.com:19302",
                        "stun:stun1.l.google.com:19302",
                        "stun:stun2.l.google.com:19302",
                        "stun:stun3.l.google.com:19302",
                        "stun:stun4.l.google.com:19302"
                ),
                null, null, null, CrossfireTlsCertPolicy.SECURE
        ));
    }

}
