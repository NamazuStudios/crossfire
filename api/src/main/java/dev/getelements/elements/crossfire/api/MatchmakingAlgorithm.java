package dev.getelements.elements.crossfire.api;

import dev.getelements.elements.crossfire.api.model.error.InvalidConfigurationException;
import dev.getelements.elements.crossfire.api.model.error.UnexpectedMessageException;
import dev.getelements.elements.crossfire.api.model.handshake.HandshakeRequest;
import dev.getelements.elements.sdk.annotation.ElementPublic;

import java.lang.reflect.ParameterizedType;
import java.util.Optional;

/**
 * Represents a matchmaking algorithm that can be used to match players or profiles based on certain criteria.
 */
@ElementPublic
public interface MatchmakingAlgorithm<InitializeRequestT extends HandshakeRequest, ResumeRequestT extends HandshakeRequest> {

    /**
     * Gets the name of the matchmaking algorithm.
     *
     * @return the name
     */
    String getName();

    /**
     * Initializes the matchmaking algorithm. Note that this method is non-blocking and returns immediately and does not
     * write to the database or perform any blocking operations. Subsequent calls to the returned MatchHandle's methods
     * will actually perform the operations of the matching process.
     *
     * @param request the matchmaking request
     * @return the match handle
     */
    MatchHandle<InitializeRequestT> initialize(MatchmakingRequest<InitializeRequestT> request);

    /**
     * Allows resuming an existing matchmaking process. Note that this method is non-blocking and returns immediately
     * and does write to the database or perform any blocking operations. Subsequent calls to the returned MatchHandle's
     * methods will actually perform the operations of the matching process. This is used for scenarios in which a
     * participant has been disconnected and wishes to rejoin an existing match.
     *
     * @param request the matchmaking request
     * @return the match handle
     */
    MatchHandle<ResumeRequestT> resume(MatchmakingRequest<ResumeRequestT> request);

    /**
     * Creates a checked version of this matchmaking algorithm that only accepts specific types of handshake requests.
     * This just provides a type-safe wrapper around the existing algorithm and provides meaningful type errors at
     * runtime if the types do not match.
     *
     * @return a checked matchmaking algorithm
     * @param <CheckedInitializeRequestT> the type of the initialize request
     * @param <CheckedResumeRequestT> the type of the join request
     */
    default <CheckedInitializeRequestT extends HandshakeRequest, CheckedResumeRequestT extends HandshakeRequest>
    MatchmakingAlgorithm<CheckedInitializeRequestT, CheckedResumeRequestT> checked(
            final Class<CheckedInitializeRequestT> initializeClass,
            final Class<CheckedResumeRequestT> resumeClass) {

        final var acceptedResumeType = findResumeRequestType();
        final var acceptedInitializeType = findInitializeRequestType();

        if (acceptedResumeType.isPresent() && acceptedInitializeType.isPresent()) {

            final var acceptsResumeType = acceptedResumeType
                    .map(resumeClass::equals)
                    .orElse(false);

            final var acceptsInitializeType = acceptedInitializeType
                    .map(initializeClass::equals)
                    .orElse(false);

            if (acceptsResumeType && acceptsInitializeType) {
                // The above checks ensure that the types are correct, so this cast is safe (unless somebody is really
                // abusing the API).
                return (MatchmakingAlgorithm<CheckedInitializeRequestT, CheckedResumeRequestT>) this;
            } else {
                throw new UnexpectedMessageException("%s does not support request types: initialize=%s, resume=%s".formatted(
                        getClass().getName(),
                        initializeClass.getName(),
                        resumeClass.getName()
                ));
            }

        }

        throw new InvalidConfigurationException(
                "Unable to determine supported request types for matchmaking algorithm %s. Requested initialize=%s, resume=%s."
                .formatted(getClass().getName(), initializeClass.getName(), resumeClass.getName())
        );

    }

    /**
     * Gets the type of the find request, if available. This is used to determine if the algorithm supports a specific
     * type of request and report more meaningful error messages in logs and responses. The default implementation
     * attempts to use reflection via {@link MatchmakingAlgorithm#findRequestType(int)}.
     *
     * @return an {@link Optional} of the initialize type
     */
    default Optional<Class<? extends InitializeRequestT>> findInitializeRequestType() {
        return findRequestType(0);
    }

    /**
     * Gets the type of the find request, if available. This is used to determine if the algorithm supports a specific
     * type of request and report more meaningful error messages in logs and responses. The default implementation
     * attempts to use reflection via {@link MatchmakingAlgorithm#findRequestType(int)}.
     *
     * @return an {@link Optional} of the resume type
     */
    default Optional<Class<? extends ResumeRequestT>> findResumeRequestType() {
        return findRequestType(1);
    }

    /**
     * Utility method to support finding the request type via reflection. Supports the default implementations of
     * of {@link MatchmakingAlgorithm#findInitializeRequestType()} and
     * {@link MatchmakingAlgorithm#findResumeRequestType()}. This method inspects the class hierarchy to find the
     * generic type argument at the specified index. If it can be determined, it is returned as an Optional of the type.
     * Otherwise, an empty Optional is returned.
     *
     * This should work for types that directly implement MatchmakingAlgorithm and specify concrete types for the
     * generic parameters. However, it may not work in all cases, such as when using certain proxying mechanisms
     * or complex inheritance structures.
     *
     * @return the request type, if available
     * @param <RequestT> the request type
     */
    default <RequestT extends HandshakeRequest> Optional<Class<? extends RequestT>> findRequestType(final int index) {

        Class<?> aClass = getClass();

        do {

            for (var aGenericInterface : aClass.getGenericInterfaces() ) {

                if (!(aGenericInterface instanceof ParameterizedType parameterizedType)) {
                    continue;
                }

                final var rawType = parameterizedType.getRawType();

                if (!(MatchmakingAlgorithm.class.equals(rawType))) {
                    continue;
                }

                final var argument = parameterizedType.getActualTypeArguments()[index];

                if (argument instanceof Class<?>) {
                    return Optional.of((Class<? extends RequestT>) argument);
                }

            }

        } while ((aClass = aClass.getSuperclass()) != null);

        return Optional.empty();

    }

}
