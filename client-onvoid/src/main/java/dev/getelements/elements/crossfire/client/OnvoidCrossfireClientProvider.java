package dev.getelements.elements.crossfire.client;

/**
 * {@link CrossfireClientProvider} backed by the onvoid JNI WebRTC library.
 */
public class OnvoidCrossfireClientProvider implements CrossfireClientProvider {

    @Override
    public Crossfire.Builder newBuilder() {
        return new OnvoidCrossfire.Builder();
    }

}
