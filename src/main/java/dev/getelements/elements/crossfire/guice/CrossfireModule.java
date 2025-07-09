package dev.getelements.elements.crossfire.guice;

import com.google.inject.PrivateModule;
import dev.getelements.elements.crossfire.MatchSignalingService;
import dev.getelements.elements.crossfire.MemoryMatchSignalingService;

public class CrossfireModule extends PrivateModule {

    @Override
    protected void configure() {

        expose(MatchSignalingService.class);

        bind(MatchSignalingService.class)
                .to(MemoryMatchSignalingService.class)
                .asEagerSingleton();

    }

}
