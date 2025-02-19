package dev.getelements.elements.crossfire;

import com.google.inject.PrivateModule;

public class MemorySdpRelayServiceModule extends PrivateModule {

    @Override
    protected void configure() {
        expose(SdpRelayService.class);
        bind(SdpRelayService.class)
                .to(MemorySdpRelayService.class)
                .asEagerSingleton();
    }

}
