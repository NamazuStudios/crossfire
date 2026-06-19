package dev.getelements.elements.crossfire.client;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;

/**
 * A default shared singleton instance of {@link WebSocketContainer}.
 */
public class SharedWebSocketContainer {

    private static final WebSocketContainer container = ContainerProvider.getWebSocketContainer();

    /**
     * Gets a shared singleton instance of {@link WebSocketContainer}.
     *
     * @return the shared instance
     */
    public static WebSocketContainer getInstance() {
        return container;
    }

}
