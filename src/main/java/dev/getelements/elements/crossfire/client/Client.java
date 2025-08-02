package dev.getelements.elements.crossfire.client;

public interface Client extends AutoCloseable {

    /**
     * Closes the client connection and releases any resources associated with it.
     */
    void close();

}
