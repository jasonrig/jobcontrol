package au.org.massive.strudel_web.tunnel;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.PathParam;
import java.io.*;
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
            separator2 = "/";
        }
        return new URL(this.getScheme()+"://localhost:" + String.valueOf(this.getLocalPort()) + separator1 + this.root + separator2 + path);
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

    public void doRequest(HttpServletRequest req, HttpServletResponse res, String path, String method) throws IOException, InterruptedException {

        String reqUrlBase = req.getRequestURL().toString().replaceAll("/$", "");

        HttpURLConnection con = (HttpURLConnection) getURL(path + "?" + req.getQueryString()).openConnection();
        con.setRequestProperty("User-Agent", USER_AGENT);

        if (method != null) {
            con.setRequestMethod(method);
        } else {
            con.setRequestMethod("GET");
        }

        boolean hasBody = req.getContentLength() > -1;

        final BufferedReader remoteServerInput = new BufferedReader(new InputStreamReader(con.getInputStream()));
        final BufferedWriter clientOutput = new BufferedWriter(new OutputStreamWriter(res.getOutputStream()));

        Transform<String> htmlTransformer = new Transform<String>() {
            private final String jsInterceptCode = "<script>" +
                    "(function(open) {\n"+
                    "\n"+
                    "    XMLHttpRequest.prototype.open = function(method, url, async, user, pass) {\n"+
                    "\n"+
                    "        var parser = document.createElement('a');\n" +
                    "        parser.href = url;\n" +
                    "        url = '"+ reqUrlBase + "' + parser.pathname + parser.search;\n" +
                    "        open.call(this, method, url, async, user, pass);\n"+
                    "    };\n"+
                    "\n"+
                    "})(XMLHttpRequest.prototype.open);" +
                    "</script>";
            private boolean transformComplete = false;
            @Override
            public String doTransform(String data) {
                if (!transformComplete) {
                    if (data.toLowerCase().contains("<head>")) {
                        transformComplete = true;
                        data = data.replaceFirst("<head>", "<head><base href=\"" + reqUrlBase + "/\">"+jsInterceptCode);
                    }
                }

                return data
                        .replaceAll("href=\"/", "href=\"")
                        .replaceAll("src=\"/", "src=\"");
            }
        };

        if (hasBody) {
            con.setDoOutput(true);
            final BufferedWriter remoteServerOutput = new BufferedWriter(new OutputStreamWriter(con.getOutputStream()));
            final BufferedReader clientInput = new BufferedReader(new InputStreamReader(req.getInputStream()));
            BidirectionalConnection bidirectionalConnection = new BidirectionalConnection(clientInput, clientOutput, remoteServerInput, remoteServerOutput, null, htmlTransformer);
            bidirectionalConnection.start();
            bidirectionalConnection.join();
            remoteServerOutput.close();
            clientInput.close();
        } else {
            ConnectionThread connectionThread = new ConnectionThread(remoteServerInput, clientOutput, htmlTransformer);
            connectionThread.start();
            connectionThread.join();
        }

        remoteServerInput.close();
        clientOutput.close();

    }

    @Override
    public boolean equals(Object o) {
        try {
            return o instanceof GuacamoleSession && getURL("").toString().equals(o.toString());
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private interface Transform<T> {
        public T doTransform(T data);
    }

    private class ConnectionThread extends Thread {
        private BufferedWriter w;
        private BufferedReader r;
        private Transform<String> transformer;

        public ConnectionThread(BufferedReader r, BufferedWriter w, Transform<String> transformer) {
            this.w = w;
            this.r = r;
            if (transformer == null) {
                this.transformer = new Transform<String>() {
                    @Override
                    public String doTransform(String data) {
                        return data;
                    }
                };
            } else {
                this.transformer = transformer;
            }
        }

        @Override
        public void run() {
            String data;
            try {
                while ((data = r.readLine()) != null) {
                    w.write(transformer.doTransform(data));
                }
                w.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class BidirectionalConnection extends Thread {
        private ConnectionThread con1;
        private ConnectionThread con2;

        public BidirectionalConnection(BufferedReader r1, BufferedWriter w1, BufferedReader r2, BufferedWriter w2, Transform<String> t1, Transform<String> t2) {
            con1 = new ConnectionThread(r1, w2, t1);
            con2 = new ConnectionThread(r2, w1, t2);
        }

        @Override
        public void run() {
            con1.start();
            con2.start();

            try {
                con1.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            try {
                con2.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
