package dev.getelements.elements.crossfire.guice;

import com.google.inject.PrivateModule;
import dev.getelements.elements.crossfire.protocol.*;
import dev.getelements.elements.crossfire.protocol.v10.V10HandshakeHandler;
import dev.getelements.elements.crossfire.protocol.v10.V10ProtocolMessageHandler;
import dev.getelements.elements.crossfire.protocol.v10.V10SignalingHandler;
import dev.getelements.elements.crossfire.service.MatchSignalingService;
import dev.getelements.elements.crossfire.service.MemoryMatchSignalingService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class  CrossfireModule extends PrivateModule {

    @Override
    protected void configure() {

        expose(MatchSignalingService.class);
        expose(ProtocolMessageHandler.class);

        bind(Pinger.class)
                .to(StandardPinger.class);

        bind(MatchSignalingService.class)
                .to(MemoryMatchSignalingService.class)
                .asEagerSingleton();

        bind(ProtocolMessageHandler.class)
                .to(V10ProtocolMessageHandler.class);

        bind(HandshakeHandler.class)
                .to(V10HandshakeHandler.class);

        bind(SignalingHandler.class)
                .to(V10SignalingHandler.class);

        bind(ExecutorService.class)
                .toProvider(Executors::newCachedThreadPool)
                .asEagerSingleton();

        bind(Validator.class)
                .toProvider(() -> {
                    try (var factory = Validation.buildDefaultValidatorFactory()) {
                        return factory.getValidator(); // thread-safe singleton
                    }
                })
                .asEagerSingleton();

    }

}
