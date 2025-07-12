package dev.getelements.elements.crossfire.guice;

import com.google.inject.PrivateModule;
import dev.getelements.elements.crossfire.protocol.Pinger;
import dev.getelements.elements.crossfire.protocol.ProtocolMessageHandler;
import dev.getelements.elements.crossfire.protocol.StandardProtocolMessageHandler;
import dev.getelements.elements.crossfire.service.MatchSignalingService;
import dev.getelements.elements.crossfire.service.MemoryMatchSignalingService;

public class CrossfireModule extends PrivateModule {

    @Override
    protected void configure() {

        expose(MatchSignalingService.class);
        expose(ProtocolMessageHandler.class);

        bind(Pinger.class);

        bind(MatchSignalingService.class)
                .to(MemoryMatchSignalingService.class)
                .asEagerSingleton();

        bind(ProtocolMessageHandler.class)
                .to(StandardProtocolMessageHandler.class);

    }

}
