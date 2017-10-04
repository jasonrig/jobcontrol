package au.org.massive.strudel_web.tunnel;

import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import java.net.URI;


public class WebsocketProxy extends Endpoint {

    private String target;

    public WebsocketProxy(String target) {
        this.target = target;
    }

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        WebSocketClient wsClient = new WebSocketClient();
        try {
            wsClient.start();
            URI targetUri = new URI(this.target);
            ClientUpgradeRequest req = new ClientUpgradeRequest();
            // Add headers/cookies to req
            wsClient.connect(, targetUri, req);
            session.getUserProperties().put("target", wsClient);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                wsClient.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
