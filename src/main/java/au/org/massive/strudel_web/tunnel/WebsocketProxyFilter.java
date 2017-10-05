package au.org.massive.strudel_web.tunnel;

import au.org.massive.strudel_web.Session;
import au.org.massive.strudel_web.jersey.Endpoint;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;

@WebFilter(filterName="WebSocketProxyAuthFilter", urlPatterns = {"/api/ws/*"})
public class WebsocketProxyFilter extends Endpoint implements Filter {
    /**
     * This filter performs some authentication and session checking to ensure
     * a valid tunnel is being requested.
     */

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        // Ensure that the user is logged in and has a valid certificate
        Session session = getSessionWithCertificateOrSendError((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse);
        if (session == null) {
            return;
        }

        try {
            String[] pathParts = ((HttpServletRequest) servletRequest).getPathInfo().split("/");
            // Get the tunnel id from the path
            Integer tunnelId = Integer.valueOf(pathParts[2]);

            String remotePath = "";
            if (pathParts.length > 3) {
                remotePath = StringUtils.join(Arrays.copyOfRange(pathParts, 3, pathParts.length), "/");
            }
            if (((HttpServletRequest) servletRequest).getQueryString().length() > 0) {
                remotePath = remotePath + "?" + ((HttpServletRequest) servletRequest).getQueryString();
            }
            servletRequest.setAttribute("remotePath", remotePath);
            // Search through all registered tunnels for that id in the user's session
            for (TunnelDependency t : session.getTunnelSessionsSet()) {
                if (t instanceof HTTPTunnel) {
                    // If a match is found and the tunnel is active, then continue processing the request
                    if (t.getId() == tunnelId && ((HTTPTunnel) t).isRunning()) {
                        session.getHttpSession().setAttribute("currentWebsocketTunnel", t);
                        session.getHttpSession().setAttribute("currentWebsocketTunnelRemotePath", remotePath);
                        servletRequest.getRequestDispatcher("/api/ws").forward(servletRequest, servletResponse);
                        //filterChain.doFilter(servletRequest, servletResponse);
                        return;
                    }
                }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            // Do nothing.
        }
        // Return 404 not found if the tunnel id does not exist for this user
        ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    @Override
    public void destroy() {

    }
}
