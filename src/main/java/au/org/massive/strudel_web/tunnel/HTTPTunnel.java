package au.org.massive.strudel_web.tunnel;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by jason on 13/7/17.
 */
public abstract class HTTPTunnel extends AbstractTunnelDependency implements Tunnel {
    private static final String USER_AGENT = "Strudel Web Proxy";
    private String root;
    private boolean isSecure;
    protected String scheme;


    public HTTPTunnel(String root, boolean isSecure) {
        super();
        this.root = root;
        this.scheme = "http";
        this.isSecure = isSecure;
    }

    public URL getURL(String path) throws MalformedURLException {
        String separator1 = "";
        if (!this.root.startsWith("/")) {
            separator1 = "/";
        }
        String separator2 = "";
        if (!path.startsWith("/") && !root.endsWith("/")) {
            separator1 = "/";
        }
        return new URL(this.getScheme()+"://"+this.getRemoteHost()+":" + String.valueOf(this.getLocalPort()) + separator1 + this.root + separator2 + path);
    }

    private String getScheme() {
        if (this.isSecure) {
            return this.scheme + "s";
        } else {
            return this.scheme;
        }
    }

    @Override
    public int getLocalPort() {
        return this.getTunnel().getLocalPort();
    }

    @Override
    public int getRemotePort() {
        return this.getTunnel().getRemotePort();
    }

    @Override
    public String getRemoteHost() {
        return this.getTunnel().getRemoteHost();
    }

    @Override
    public void stopTunnel() {
        this.getTunnel().stopTunnel();
    }

    @Override
    public boolean isRunning() {
        return this.getTunnel().isRunning();
    }

    public void doRequest(HttpServletRequest req, HttpServletResponse res, String path, String method) throws IOException {
        HttpURLConnection con = (HttpURLConnection) getURL(path).openConnection();
        if (method != null) {
            con.setRequestMethod(method);
        }
        con.setRequestProperty("User-Agent", USER_AGENT);
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        ServletOutputStream out = res.getOutputStream();

        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            out.write(inputLine.getBytes());
        }
        in.close();
        out.flush();
    }

    @Override
    public boolean equals(Object o) {
        try {
            return o instanceof GuacamoleSession && getURL("").toString().equals(o.toString());
        } catch (MalformedURLException e) {
            return false;
        }
    }
}
