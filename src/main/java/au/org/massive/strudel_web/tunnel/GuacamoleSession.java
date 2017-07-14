package au.org.massive.strudel_web.tunnel;

import au.org.massive.strudel_web.ssh.AbstractSSHClient;

/**
 * Parameters required for the Guacamole database
 *
 * @author jrigby
 */
public abstract class GuacamoleSession extends AbstractTunnelDependency {
    private String name;
    private int localPort;
    private String remoteHost;
    private int remotePort;
    private String protocol;
    private String password;

    public GuacamoleSession() {
        super();
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLocalPort() {
        return localPort;
    }

    public void setLocalPort(int port) {
        this.localPort = port;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int port) {
        this.remotePort = port;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return getProtocol()+"://"+ getRemoteHost()+":"+ getRemotePort()+"/";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GuacamoleSession && toString().equals(o.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
