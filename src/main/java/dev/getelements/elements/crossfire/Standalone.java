package dev.getelements.elements.crossfire;

import com.google.inject.Guice;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.eclipse.jetty.server.Server;

public class Standalone {

    public static void main(final String[] args) throws Exception {

        final var server = new Server(8080);
        final var context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        final var injector = Guice.createInjector(new MemorySdpRelayServiceModule());

        final var configurator = new ServerEndpointConfig.Configurator() {

            @Override
            public <T> T getEndpointInstance(final Class<T> endpointClass) {
                return injector.getInstance(endpointClass);
            }

        };

        final var endpoint = SdpRelayEndpoint.class.getAnnotation(ServerEndpoint.class);

        JakartaWebSocketServletContainerInitializer.configure(
                context,
                (cxt, container) -> ServerEndpointConfig.Builder
                            .create(SdpRelayEndpoint.class, endpoint.value())
                            .configurator(configurator)
                    .build()
        );

        server.start();
        server.join();

    }


}
