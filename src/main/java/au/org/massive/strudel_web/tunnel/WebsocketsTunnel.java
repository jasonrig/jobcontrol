package au.org.massive.strudel_web.tunnel;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by jason on 13/7/17.
 */
public abstract class WebsocketsTunnel extends HTTPTunnel {

    public WebsocketsTunnel(Tunnel parentTunnel, String path, boolean isSecure) {
        super(path, isSecure);
        this.scheme = "ws";
    }

}
