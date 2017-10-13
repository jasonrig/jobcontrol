package au.org.massive.strudel_web.tunnel;

import org.apache.http.Header;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.impl.cookie.NetscapeDraftSpec;

import javax.servlet.http.Cookie;
import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@ClientEndpoint
public class WebsocketClientEndpoint {

    private Session userSession;
    private WebsocketMessageHandler messageHandler;
    public boolean inhibitCloseEvent = false;

    public WebsocketClientEndpoint(Endpoint e, URI endpointURI, WebsocketMessageHandler messageHandler, List<Cookie> sessionCookies) throws IOException, DeploymentException {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().configurator(new ClientEndpointConfig.Configurator() {
            @Override
            public void beforeRequest(Map<String, List<String>> headers) {
                super.beforeRequest(headers);

                BasicCookieStore cs = new BasicCookieStore();

                for (Cookie c : sessionCookies) {
                    BasicClientCookie newCookie = new BasicClientCookie(c.getName(), c.getValue());
                    cs.addCookie(newCookie);
                }
                NetscapeDraftSpec cookieSpec = new NetscapeDraftSpec();
                List<Header> cookieHeaders = cookieSpec.formatCookies(cs.getCookies());
                for (Header h : cookieHeaders) {
                    headers.put(h.getName(), Arrays.asList(h.getValue()));
                }

                headers.put("Origin", Arrays.asList("http://"+headers.get("Host").get(0)));
            }
        }).build();
        System.out.println("Connecting to " + endpointURI.toString());
        container.connectToServer(this, config, endpointURI);
        System.out.println("Connected");
        this.messageHandler = messageHandler;
    }

    public void stop() throws IOException {
        inhibitCloseEvent = true;
        userSession.close();
    }

    public Session getUserSession() {
        return this.userSession;
    }

    @OnOpen
    public void onOpen(Session userSession, EndpointConfig config) {
        System.out.println("Client OnOpen!");
        this.userSession = userSession;
    }

    @OnClose
    public void onClose(Session userSession, CloseReason closeReason) {
        System.out.println("Client OnClose!");
        this.userSession = null;
        if (!inhibitCloseEvent) {
            messageHandler.handleClose();
        }
        inhibitCloseEvent = false;
    }

    @OnMessage
    public void onMessage(ByteBuffer message) {
        messageHandler.handleMessage(message);
    }

    @OnMessage
    public void onMessage(String message) {
        messageHandler.handleMessage(message);
    }

    @OnMessage
    public void onMessage(PongMessage message) {
        messageHandler.handlePong(message.getApplicationData());
    }
}
