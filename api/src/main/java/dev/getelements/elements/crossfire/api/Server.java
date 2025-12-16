package dev.getelements.elements.crossfire.api;

import dev.getelements.elements.crossfire.api.model.Version;

import java.util.concurrent.Future;

public interface Server {

    /**
     * Submits a runnable to the protocol message handler's executor service.
     * This is used to ensure that the runnable is executed in the context of the protocol message handler.
     *
     * @param task the runnable to submit
     */
    Future<?> submit(Runnable task);

}
