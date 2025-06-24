package dev.getelements.elements.crossfire.guice;

import _internal.com.google.inject.PrivateModule;
import dev.getelements.elements.crossfire.MemoryMatchSignalingService;
import dev.getelements.elements.crossfire.MatchSignalingService;

public class CrossfireModule extends PrivateModule {

    @Override
    protected void configure() {
        bind(MatchSignalingService.class)
                .to(MemoryMatchSignalingService.class)
                .asEagerSingleton();
    }

}
