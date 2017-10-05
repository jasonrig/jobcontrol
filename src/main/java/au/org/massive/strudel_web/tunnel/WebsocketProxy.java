package au.org.massive.strudel_web.tunnel;

import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;


@ServerEndpoint(value = "/api/ws", configurator = WebsocketProxyConfigurator.class)
public class WebsocketProxy extends Endpoint {

    private WebSocketClient wsClient;

    private HTTPTunnel getTunnel(Session session) {
        return (HTTPTunnel) session.getUserProperties().get(HTTPTunnel.class.getName());
    }

    private String getRemotePath(Session session) {
        return (String) session.getUserProperties().get("remotePath");
    }

    private URI getRemoteURI(Session session) {
        HTTPTunnel t = getTunnel(session);
        String scheme = "ws";
        if (t.isSecure()) {
            scheme = "wss";
        }
        URI uri;
        try {
            uri = new URI(t.getURL(getRemotePath(session)).toString().replaceFirst("http", "ws"));
        } catch (URISyntaxException | MalformedURLException e) {
            throw new RuntimeException(e);
        }

        return uri;
    }

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {

        wsClient = new WebSocketClient();
        try {
            wsClient.start();
            ClientUpgradeRequest req = new ClientUpgradeRequest();
            // Add headers/cookies to req
            wsClient.connect(new WebsocketClientSocket(), getRemoteURI(session), req);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                wsClient.stop();
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        try {
            wsClient.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
