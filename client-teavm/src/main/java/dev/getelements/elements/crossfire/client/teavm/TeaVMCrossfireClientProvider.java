package dev.getelements.elements.crossfire.client.teavm;

import dev.getelements.elements.crossfire.client.Crossfire;
import dev.getelements.elements.crossfire.client.CrossfireClientProvider;

/**
 * {@link CrossfireClientProvider} for TeaVM browser targets.
 */
public class TeaVMCrossfireClientProvider implements CrossfireClientProvider {

    @Override
    public Crossfire.Builder newBuilder() {
        return new TeaVMCrossfire.Builder();
    }

}
