package au.org.massive.strudel_web.tunnel;

import javax.servlet.http.Cookie;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.List;


@ServerEndpoint(value = "/api/ws", configurator = WebsocketProxyConfigurator.class)
public class WebsocketProxy {

    private HTTPTunnel getTunnel(Session session) {
        return (HTTPTunnel) session.getUserProperties().get("currentWebsocketTunnel");
    }

    private String getRemotePath(Session session) {
        return (String) session.getUserProperties().get("remotePath");
    }

    private List<Cookie> getCookies(Session session) {
        return (List<Cookie>) session.getUserProperties().get("cookies");
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

    @OnOpen
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        try {
            WebsocketClientEndpoint wsClient = new WebsocketClientEndpoint(getRemoteURI(session), new WebsocketMessageHandler() {
                @Override
                public void handleClose() {
                    try {
                        session.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void handleMessage(ByteBuffer message) {
                    try {
                        session.getBasicRemote().sendBinary(message);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void handleMessage(String message) {
                    try {
                        System.out.println(message);
                        session.getBasicRemote().sendText(message);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void handlePong(PongMessage message) {
                    try {
                        session.getBasicRemote().sendPong(message.getApplicationData());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, getCookies(session));

            session.getUserProperties().put("wsClient", wsClient);
        } catch (IOException | DeploymentException e) {
            try {
                e.printStackTrace();
                session.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    @OnMessage
    public void onMessage(Session session, ByteBuffer message) {
        WebsocketClientEndpoint wsClient = (WebsocketClientEndpoint) session.getUserProperties().get("wsClient");
        try {
            wsClient.getUserSession().getBasicRemote().sendBinary(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        WebsocketClientEndpoint wsClient = (WebsocketClientEndpoint) session.getUserProperties().get("wsClient");
        try {
            wsClient.getUserSession().getBasicRemote().sendText(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @OnMessage
    public void onMessage(Session session, PongMessage message) {
        WebsocketClientEndpoint wsClient = (WebsocketClientEndpoint) session.getUserProperties().get("wsClient");
        try {
            wsClient.getUserSession().getBasicRemote().sendPong(message.getApplicationData());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        WebsocketClientEndpoint wsClient = (WebsocketClientEndpoint) session.getUserProperties().get("wsClient");
        try {
            if (wsClient != null) {
                wsClient.stop();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
