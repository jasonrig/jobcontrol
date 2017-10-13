package au.org.massive.strudel_web.tunnel;

import org.apache.http.Header;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.impl.cookie.NetscapeDraftSpec;

import javax.servlet.http.Cookie;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


@ServerEndpoint(value = "/api/ws", configurator = WebsocketProxyConfigurator.class)
public class WebsocketProxy extends Endpoint {




    private HTTPTunnel getTunnel(Session session) {
        return (HTTPTunnel) session.getUserProperties().get(HTTPTunnel.class.getName());
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
        System.out.println("!!! WEBSOCKET : " + uri.toString());
        return uri;
    }

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        try {
            WebsocketClientEndpoint wsClient = new WebsocketClientEndpoint(this, getRemoteURI(session), new WebsocketMessageHandler() {
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
                        System.out.println("c. Got message (1)");
                        session.getBasicRemote().sendBinary(message);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void handleMessage(String message) {
                    try {
                        System.out.println("c. Got message (2)");
                        session.getBasicRemote().sendText(message);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void handlePong(ByteBuffer message) {
                    try {
                        System.out.println("c. Got message ping (3)");
                        session.getBasicRemote().sendPong(message);
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
        System.out.println("s. Got message (1)");
        WebsocketClientEndpoint wsClient = (WebsocketClientEndpoint) session.getUserProperties().get("wsClient");
        try {
            wsClient.getUserSession().getBasicRemote().sendBinary(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        System.out.println("s. Got message (2)");
        WebsocketClientEndpoint wsClient = (WebsocketClientEndpoint) session.getUserProperties().get("wsClient");
        try {
            wsClient.getUserSession().getBasicRemote().sendText(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @OnMessage
    public void onMessage(Session session, PongMessage message) {
        System.out.println("s. Got message ping (3)");
        WebsocketClientEndpoint wsClient = (WebsocketClientEndpoint) session.getUserProperties().get("wsClient");
        try {
            wsClient.getUserSession().getBasicRemote().sendPong(message.getApplicationData());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
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
