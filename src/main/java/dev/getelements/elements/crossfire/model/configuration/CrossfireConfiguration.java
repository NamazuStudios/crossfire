package dev.getelements.elements.crossfire.model.configuration;

import dev.getelements.elements.crossfire.jackson.Jackson;
import dev.getelements.elements.sdk.model.application.ElementServiceReference;
import dev.getelements.elements.sdk.model.application.MatchmakingApplicationConfiguration;
import jakarta.validation.Valid;

import java.util.Map;
import java.util.Optional;

public class CrossfireConfiguration {

    public static final String METADATA_KEY = "crossfire";

    @Valid
    private ElementServiceReference matchmaker;

    public ElementServiceReference getMatchmaker() {
        return matchmaker;
    }

    public void setMatchmaker(ElementServiceReference matchmaker) {
        this.matchmaker = matchmaker;
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
