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

public class WebsocketClientEndpoint extends Endpoint {

    private Session userSession;
    private WebsocketMessageHandler messageHandler;
    private boolean inhibitCloseEvent = false;

    public WebsocketClientEndpoint(URI endpointURI, WebsocketMessageHandler messageHandler, List<Cookie> sessionCookies) throws IOException, DeploymentException {
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
        container.connectToServer(this, config, endpointURI);


        this.messageHandler = messageHandler;
    }

    public void stop() throws IOException {
        inhibitCloseEvent = true;
        userSession.close();
    }

    public Session getUserSession() {
        return this.userSession;
    }

    @Override
    public void onOpen(Session userSession, EndpointConfig config) {
        this.userSession = userSession;
        this.userSession.addMessageHandler(new MessageHandler.Whole<String>() {

            @Override
            public void onMessage(String s) {
                messageHandler.handleMessage(s);
            }
        });

        this.userSession.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer byteBuffer) {
                messageHandler.handleMessage(byteBuffer);
            }
        });

        this.userSession.addMessageHandler(new MessageHandler.Whole<PongMessage>() {
            @Override
            public void onMessage(PongMessage pongMessage) {
                messageHandler.handlePong(pongMessage);
            }
        });
    }

    @Override
    public void onClose(Session userSession, CloseReason closeReason) {
        this.userSession = null;
        if (!inhibitCloseEvent) {
            messageHandler.handleClose();
        }
        inhibitCloseEvent = false;
    }
}
