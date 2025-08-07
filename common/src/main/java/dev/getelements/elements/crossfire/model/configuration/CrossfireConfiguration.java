package dev.getelements.elements.crossfire.model.configuration;

import dev.getelements.elements.crossfire.jackson.Jackson;
import dev.getelements.elements.sdk.model.application.ElementServiceReference;
import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration for the Crossfire matchmaking service.
 */
public class CrossfireConfiguration {

    public static final Logger logger = LoggerFactory.getLogger(CrossfireConfiguration.class);

    public static final String METADATA_KEY = "crossfire";

    /**
     * Defines a matchmaker service.
     */
    @Valid
    private ElementServiceReference matchmaker;

    public ElementServiceReference getMatchmaker() {
        return matchmaker;
    }

    public void setMatchmaker(ElementServiceReference matchmaker) {
        this.matchmaker = matchmaker;
    }

    /**
     * Updates the supplied matchmaking application configuration with the Crossfire configuration.
     *
     * @param matchmakingApplicationConfiguration the matchmaking application configuration to update
     * @return the metadata value present in the matchmaking application configuration before updates
     */
    public Object save(final MatchmakingApplicationConfiguration matchmakingApplicationConfiguration) {

        var metadata = matchmakingApplicationConfiguration.getMetadata();

        if (metadata == null) {
            metadata = new HashMap<>();
        } else {
            metadata = new HashMap<>(metadata);
        }

        final var crossfire = Jackson
                .getMapper()
                .convertValue(this, Map.class);

        matchmakingApplicationConfiguration.setMetadata(metadata);

        return metadata.put(METADATA_KEY, crossfire);

    }

    public static Optional<CrossfireConfiguration> from(final MatchmakingApplicationConfiguration appConfig) {

        final var metadata = appConfig.getMetadata();

        if (metadata == null || metadata.isEmpty()) {
            return Optional.empty();
        }

        final var crossfire = metadata.get(METADATA_KEY);

        if (crossfire == null) {
            return Optional.empty();
        } else if (crossfire instanceof Map<?, ?> crossfireConfigMap) {

            final var configuration = Jackson
                    .getMapper()
                    .convertValue(crossfireConfigMap, CrossfireConfiguration.class);

            return Optional.of(configuration);

        } else {
            return Optional.empty();
        }

    }

}
