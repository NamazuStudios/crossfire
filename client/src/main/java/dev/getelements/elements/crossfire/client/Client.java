package dev.getelements.elements.crossfire.client;

import dev.getelements.elements.crossfire.model.error.ProtocolError;
import dev.getelements.elements.crossfire.model.error.TimeoutException;
import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.HandshakeResponse;
import dev.getelements.elements.sdk.Subscription;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface Client extends AutoCloseable {

    /**
     * Returns the current phase of the client.
     *
     * @return the phase
     */
    ClientPhase getPhase();

    /**
     * Sends a new {@link HandshakeRequest} to the server to initiate the handshake process.
     *
     * @param request the handshake request to send
     */
    void handshake(HandshakeRequest request);

    /**
     * Sends a new {@link HandshakeRequest} to the server to initiate the handshake process.
     *
     * @param request the handshake request to send
     * @param responseConsumer the consumer to handle the response
     */
    default void handshake(final HandshakeRequest request, final Consumer<HandshakeResponse> responseConsumer) {

        final var subscription = onHandshake((s, r) -> {
            s.unsubscribe();
            responseConsumer.accept(r);
        });

        try {
            handshake(request);
        } catch (Exception ex) {
            subscription.unsubscribe();
            throw ex;
        }

    }

    /**
     * Performs a handshake and waits for the response for a specified amount of time.
     *
     * @param request the {@link HandshakeRequest} to send
     * @param time the time to wait
     * @param timeUnit the time unit for the wait
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    default HandshakeResponse handshake(
            final HandshakeRequest request,
            final long time,
            final TimeUnit timeUnit) throws InterruptedException {

        final var latch = new CountDownLatch(1);
        final var reference = new AtomicReference<HandshakeResponse>();

        handshake(request, response -> {
            reference.set(response);
            latch.countDown();
        });

        try {

            if (latch.await(time, timeUnit)) {
                throw new TimeoutException("Timed out waiting on server to respond.");
            }

            return reference.get();

        } catch (TimeoutException ex) {
            close();
            throw ex;
        }

    }

    /**
     * Subscribes to the error events.
     *
     * @return the current phase
     */
    Subscription onError(BiConsumer<Subscription, ProtocolError> listener);

    /**
     * Subscribes to the handshake response events.
     *
     * @return the current phase
     */
    Subscription onHandshake(BiConsumer<Subscription, HandshakeResponse> listener);

    /**
     * Closes the client connection and releases any resources associated with it.
     */
    void close();

}
