package dev.getelements.elements.crossfire.client;

/**
 * Java SPI entry point for creating {@link Crossfire} instances. Register implementations
 * via {@code META-INF/services/dev.getelements.elements.crossfire.client.CrossfireClientProvider}.
 */
public interface CrossfireClientProvider {

    /**
     * Returns a new {@link Crossfire.Builder} for this implementation.
     */
    Crossfire.Builder newBuilder();

}
