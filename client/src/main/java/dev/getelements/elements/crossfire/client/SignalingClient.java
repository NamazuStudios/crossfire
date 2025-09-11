package dev.getelements.elements.crossfire.client;

import dev.getelements.elements.crossfire.model.Version;
import dev.getelements.elements.crossfire.model.error.TimeoutException;
import dev.getelements.elements.crossfire.model.handshake.HandshakeRequest;
import dev.getelements.elements.crossfire.model.handshake.HandshakeResponse;
import dev.getelements.elements.crossfire.model.signal.Signal;
import dev.getelements.elements.sdk.Subscription;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The Client interface to the Crossfire server. Crossfire handles the signaling and relay of data.
 */
public interface SignalingClient extends AutoCloseable {

    /**
     * Gets the protocol version.
     *
     * @return the protocol version.
     */
    Version getVersion();

    /**
     * Gets the state of the match.
     *
     * @return the state of the match
     */
    MatchState getState();

    /**
     * Returns the current phase of the client.
     *
     * @return the phase
     */
    default SignalingClientPhase getPhase() {
        return getState().getPhase();
    }

    /**
     * Sends the given signal to the server.
     *
     * @param signal the signal
     */
    void signal(Signal signal);

    /**
     * Returns the current {@link HandshakeResponse} if available. If the response is not available, it throws a
     * {@link NoSuchElementException}. Will always be available in the {@link SignalingClientPhase#SIGNALING} phase or later.
     *
     * @return the current handshake response
     */
    default HandshakeResponse getHandshakeResponse() {
        return findHandshakeResponse().orElseThrow(NoSuchElementException::new);
    }

    /**
     * Finds the current {@link HandshakeResponse} if available.
     *
     * @return an {@link Optional} containing the handshake response if it exists, otherwise empty
     */
    Optional<HandshakeResponse> findHandshakeResponse();

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
                return reference.get();
            } else {
                throw new TimeoutException("Timed out waiting on server to respond.");
            }
        } catch (TimeoutException ex) {
            close();
            throw ex;
        }

    }

    /**
     * Subscribes to the handshake response events.
     *
     * @return a subscription to the handshake event
     */
    Subscription onHandshake(BiConsumer<Subscription, HandshakeResponse> listener);

    /**
     * Subscribes to the signal events.
     *
     * @return a subscription to the signaling event
     */
    Subscription onSignal(BiConsumer<Subscription, Signal> listener);

    /**
     * Subscribes to the client error events.
     *
     * @return a subscription to the error event
     */
    Subscription onClientError(BiConsumer<Subscription, Throwable> listener);

    /**
     * Closes the client connection and releases any resources associated with it.
     */
    void close();

    /**
     * Gets the current match state.
     */
    interface MatchState {

        /**
         * Gets the phase of the signaling client.
         * @return the phase
         */
        SignalingClientPhase getPhase();

        /**
         * Gets the host profile id, or null if not available.
         *
         * @return the host
         */
        String getHost();

        /**
         * Gets the match id.
         * @return the match id
         */
        String getMatchId();

        /**
         * Gets the Profile id of the client.
         *
         * @return the profile id
         */
        String getProfileId();

        /**
         * Gets the list of all profiles in the match.
         *
         * @return the profiles
         */
        List<String> getProfiles();

        /**
         * Returns true if the current client is the host.
         *
         * @return true if host, otherwise false
         */
        default boolean isHost() {
            return Objects.equals(getHost(), getProfileId());
        }

    }

}
