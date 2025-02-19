package dev.getelements.elements.crossfire;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.eclipse.jetty.server.Server;

public class Standalone {

    public static void main(final String[] args) throws Exception {

        final var server = new Server(8080);
        final var context = new ServletContextHandler();

        JakartaWebSocketServletContainerInitializer.configure(context, null);
        context.addServlet(SdpInitializerServlet.class, "/*");

        context.setContextPath("/");
        server.setHandler(context);
        server.start();
        server.join();

    }


}
