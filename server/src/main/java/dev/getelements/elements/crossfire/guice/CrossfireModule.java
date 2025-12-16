package dev.getelements.elements.crossfire.guice;

import com.google.inject.PrivateModule;
import dev.getelements.elements.crossfire.api.MatchmakingAlgorithm;
import dev.getelements.elements.crossfire.api.model.Version;
import dev.getelements.elements.crossfire.matchmaker.FIFOMatchmakingAlgorithm;
import dev.getelements.elements.crossfire.matchmaker.JoinCodeMatchmakingAlgorithm;
import dev.getelements.elements.crossfire.protocol.*;
import dev.getelements.elements.crossfire.protocol.v1.V10HandshakeHandler;
import dev.getelements.elements.crossfire.protocol.v1.V11HandshakeHandler;
import dev.getelements.elements.crossfire.protocol.v1.V1ProtocolMessageHandler;
import dev.getelements.elements.crossfire.protocol.v1.V1SignalingHandler;
import dev.getelements.elements.crossfire.service.ControlService;
import dev.getelements.elements.crossfire.service.MatchSignalingService;
import dev.getelements.elements.crossfire.service.MemoryMatchSignalingService;
import dev.getelements.elements.crossfire.service.StandardControlService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.inject.name.Names.named;
import static dev.getelements.elements.crossfire.matchmaker.FIFOMatchmakingAlgorithm.NAME;

public class  CrossfireModule extends PrivateModule {

    @Override
    protected void configure() {

        expose(ControlService.class);
        expose(MatchSignalingService.class);
        expose(ProtocolMessageHandler.class);
        expose(MatchmakingAlgorithm.class);
        expose(MatchmakingAlgorithm.class).annotatedWith(named(FIFOMatchmakingAlgorithm.NAME));
        expose(MatchmakingAlgorithm.class).annotatedWith(named(JoinCodeMatchmakingAlgorithm.NAME));

        bind(Pinger.class)
                .to(StandardPinger.class);

        bind(MatchSignalingService.class)
                .to(MemoryMatchSignalingService.class)
                .asEagerSingleton();

        bind(ControlService.class)
                .to(StandardControlService.class);

        bind(ProtocolMessageHandler.class)
                .to(V1ProtocolMessageHandler.class);

        bind(HandshakeHandler.class)
                .annotatedWith(named(Version.VERSION_1_0_NAME))
                .to(V10HandshakeHandler.class);

        bind(HandshakeHandler.class)
                .annotatedWith(named(Version.VERSION_1_1_NAME))
                .to(V11HandshakeHandler.class);

        bind(SignalingHandler.class)
                .to(V1SignalingHandler.class);

        bind(ExecutorService.class)
                .toProvider(Executors::newCachedThreadPool)
                .asEagerSingleton();

        bind(ScheduledExecutorService.class)
                .toProvider(Executors::newSingleThreadScheduledExecutor)
                .asEagerSingleton();

        bind(MatchmakingAlgorithm.class)
                .to(FIFOMatchmakingAlgorithm.class);

        bind(MatchmakingAlgorithm.class)
                .annotatedWith(named(FIFOMatchmakingAlgorithm.NAME))
                .to(FIFOMatchmakingAlgorithm.class);

        bind(MatchmakingAlgorithm.class)
                .annotatedWith(named(JoinCodeMatchmakingAlgorithm.NAME))
                .to(FIFOMatchmakingAlgorithm.class);

        bind(Validator.class)
                .toProvider(() -> {
                    try (var factory = Validation.buildDefaultValidatorFactory()) {
                        return factory.getValidator(); // thread-safe singleton
                    }
                })
                .asEagerSingleton();

    }

}
