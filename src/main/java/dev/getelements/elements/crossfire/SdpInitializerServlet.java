package dev.getelements.elements.crossfire;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.server.ServerContainer;

public class SdpInitializerServlet extends HttpServlet {

    @Override
    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        final var container = (ServerContainer)getServletContext().getAttribute(ServerContainer.class.getName());

        try {
            container.addEndpoint(SdpRelayEndpoint.class);
        } catch (DeploymentException e) {
            throw new ServletException(e);
        }

    }

}
