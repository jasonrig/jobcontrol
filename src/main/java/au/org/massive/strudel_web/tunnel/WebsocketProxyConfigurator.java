package au.org.massive.strudel_web.tunnel;

import au.org.massive.strudel_web.Session;

import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

public class WebsocketProxyConfigurator extends ServerEndpointConfig.Configurator {

    private Object getAttribute(String key, HandshakeRequest request) {
        return ((HttpSession) request.getHttpSession()).getAttribute(key);
    }

    @Override
    public void modifyHandshake(ServerEndpointConfig config,
                                HandshakeRequest request,
                                HandshakeResponse response) {

        HttpSession httpSession = (HttpSession) request.getHttpSession();

        if (httpSession != null) {
            config.getUserProperties().put(HTTPTunnel.class.getName(), getAttribute("currentWebsocketTunnel", request));
            config.getUserProperties().put("remotePath", getAttribute("currentWebsocketTunnelRemotePath", request));
        }
    }

}
