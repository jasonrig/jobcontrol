package au.org.massive.strudel_web.tunnel;

import au.org.massive.strudel_web.Session;
import org.apache.commons.text.StrSubstitutor;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Created by jason on 13/7/17.
 */
public abstract class HTTPTunnel extends AbstractTunnelDependency implements Tunnel {
    private String alias;
    private String root;
    private boolean isSecure;
    protected String scheme;


    public HTTPTunnel(String alias, String root, boolean isSecure) {
        super();

        this.alias = alias;

        Map<String, String> proxyProps = new HashMap<>();
        proxyProps.put("id", String.valueOf(this.id));

        root = new StrSubstitutor(proxyProps, "_", "_").replace(root);

        this.root = root;
        this.scheme = "http";
        this.isSecure = isSecure;
    }

    public boolean isSecure() {
        return this.isSecure;
    }

    public String getAlias() {
        return this.alias;
    }

    public URL getURL(String path, String scheme) throws MalformedURLException {
        String separator1 = "";
        if (!this.root.startsWith("/")) {
            separator1 = "/";
        }
        String separator2 = "";
        if (!path.startsWith("/") && !root.endsWith("/")) {
            separator2 = "/";
        }
        return new URL(scheme+"://localhost:" + String.valueOf(this.getLocalPort()) + separator1 + this.root + separator2 + path);
    }

    public URL getURL(String path) throws MalformedURLException {
        return getURL(path, this.getScheme());
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

    public void doRequest(HttpServletRequest req, HttpServletResponse res, String path, String method, Session session) throws IOException, InterruptedException {

        if (path.startsWith("api/kernels/")) {
            try {
                WebsocketProxyFilter.forwardRequestOr404(req, res, session, getId(), path);
            } catch (ServletException e) {
                e.printStackTrace();
            }
            return;
        }

        CookieManager manager = new CookieManager();
        manager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(manager);

        HttpURLConnection con = (HttpURLConnection) getURL(path + "?" + req.getQueryString()).openConnection();

        if (method == null) {
            method = "GET";
        }

        switch (method.toUpperCase()) {
            case "GET":
              con.setDoInput(true);
              break;
            case "POST":
            case "PUT":
                con.setDoOutput(true);
        }
        con.setRequestMethod(method);

        final Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String k = headerNames.nextElement();
            String v = req.getHeader(k);
            con.setRequestProperty(k, v);
        }

        boolean hasBody = req.getContentLength() > -1;


        OutputStream clientOutput = res.getOutputStream();

        if (hasBody) {
            final OutputStream remoteServerOutput = con.getOutputStream();
            final InputStream clientRequest = req.getInputStream();

            ConnectionThread connectionThread1 = new ConnectionThread(clientRequest, remoteServerOutput);
            connectionThread1.start();
            connectionThread1.join();
            remoteServerOutput.close();
            clientRequest.close();
        }

        // Forward the response headers
        Map<String, List<String>> conHeaders = con.getHeaderFields();
        final List<String> ignoreHeaders = Arrays.asList("null", "Server", "Set-Cookie");
        for (Map.Entry<String, List<String>> kv : conHeaders.entrySet()) {
            if (ignoreHeaders.contains(kv.getKey())) {
                continue;
            } else {
                for (String v : kv.getValue()) {
                    String key = kv.getKey();
                    if (key != null) {
                        res.addHeader(key, v);
                    }
                }
            }
        }

        for (HttpCookie c : manager.getCookieStore().getCookies()) {
            res.addHeader("Set-Cookie", c.getName()+"="+c.getValue()+"; Path=/");
        }

        res.setStatus(con.getResponseCode());
        InputStream remoteServerResponse = getServerResponseStream(con);
        ConnectionThread connectionThread = new ConnectionThread(remoteServerResponse, clientOutput);
        connectionThread.start();
        connectionThread.join();
        clientOutput.close();

    }

    private InputStream getServerResponseStream(HttpURLConnection con) {
        InputStream remoteServerInput;
        try {
             remoteServerInput = con.getInputStream();
        } catch (IOException e) {
             remoteServerInput = con.getErrorStream();
        }
        return remoteServerInput;
    }

    @Override
    public boolean equals(Object o) {
        try {
            return o instanceof HTTPTunnel && getURL("").toString().equals(o.toString());
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private class ConnectionThread extends Thread {
        private OutputStream w;
        private InputStream r;

        public ConnectionThread(InputStream r, OutputStream w) {
            this.w = w;
            this.r = r;
        }

        @Override
        public void run() {
            try {
                byte[] buf = new byte[1024];
                int length = 0;
                while ((length = r.read(buf)) != -1) {
                    w.write(buf, 0, length);
                }
                w.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
