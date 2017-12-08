package au.org.massive.strudel_web.tunnel;

import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

public class WebsocketProxyConfigurator extends ServerEndpointConfig.Configurator {

    private Object getAttribute(String key, HandshakeRequest request) {
        return ((HttpSession) request.getHttpSession()).getAttribute(key);
    }

    private boolean hasHttpSession(HandshakeRequest request) {
        HttpSession httpSession = (HttpSession) request.getHttpSession();

        return (httpSession != null);
    }

    @Override
    public void modifyHandshake(ServerEndpointConfig config,
                                HandshakeRequest request,
                                HandshakeResponse response) {

        if (hasHttpSession(request)) {
            Object cookies = getAttribute("cookies", request);
            if (cookies != null) {
                config.getUserProperties().put("cookies", cookies);
            }

            Object currentWebsocketTunnel = getAttribute("currentWebsocketTunnel", request);
            if (currentWebsocketTunnel != null) {
                config.getUserProperties().put("currentWebsocketTunnel", currentWebsocketTunnel);
            }

            Object currentWebsocketTunnelRemotePath = getAttribute("currentWebsocketTunnelRemotePath", request);
            if (currentWebsocketTunnelRemotePath != null) {
                config.getUserProperties().put("remotePath", currentWebsocketTunnelRemotePath);
            }
        }
    }

}
