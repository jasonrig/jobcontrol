package au.org.massive.strudel_web.tunnel;

import java.io.IOException;
import java.util.*;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import au.org.massive.strudel_web.Session;
import au.org.massive.strudel_web.ssh.ForkedSSHClient;

/**
 * Manages the lifecycle of a Tunnel session
 *
 * @author jrigby
 */
public class TunnelManager implements ServletContextListener {

    private static Map<Integer, TunnelDependency> sshTunnels; // <Local Port, Tunnel>
    private static Timer tunnelCleaner;

    public TunnelManager() {
        sshTunnels = new HashMap<>();
        tunnelCleaner = new Timer(true);
    }

    @Override
    public void contextDestroyed(ServletContextEvent arg0) {
        for (Integer port : sshTunnels.keySet()) {
            stopTunnel(port);
        }
        tunnelCleaner.cancel(); // Avoid tomcat memory leaks
    }

    @Override
    public void contextInitialized(ServletContextEvent arg0) {
        final long FIVE_SECONDS = 5000;
        tunnelCleaner.schedule(new TimerTask() {

            @Override
            public void run() {
                final LinkedList<Integer> toStop = new LinkedList<>();
                for (Integer port : sshTunnels.keySet()) {
                    Tunnel t = sshTunnels.get(port).getTunnel();
                    if (!t.isRunning()) {
                        toStop.add(port);
                    }
                }
                for (Integer port : toStop) {
                    stopTunnel(port);
                }
            }

        }, FIVE_SECONDS, FIVE_SECONDS);
    }


    /**
     * Starts a Guacamole session
     *
     * @param desktopName name to assign to desktop
     * @param vncPassword password to access VNC session
     * @param viaGateway  the remote SSH server gateway
     * @param remoteHost  the target of the tunnel
     * @param remotePort  the remote port of the tunnel
     * @param session     current session object
     * @return a TunnelDependency with active tunnel
     */
    public static TunnelDependency startGuacamoleSession(String desktopName, String vncPassword, String viaGateway, String remoteHost, int remotePort, Session session) {

        final Set<TunnelDependency> tunnelSessionSet = session.getTunnelSessionsSet();

        GuacamoleSession guacSession = new GuacamoleSession() {
            @Override
            public void onTunnelStop() {
                tunnelSessionSet.remove(this);
            }
        };

        guacSession.setName(desktopName);
        guacSession.setPassword(vncPassword);
        guacSession.setRemoteHost(remoteHost.equals("localhost") ? viaGateway : remoteHost);
        guacSession.setProtocol("vnc");
        guacSession.setRemotePort(remotePort);

        // Avoid creating duplicate guacamole tunnels
        if (tunnelSessionSet.contains(guacSession)) {
            for (TunnelDependency s : tunnelSessionSet) {
                if (s.equals(guacSession)) {
                    ((GuacamoleSession) s).setName(desktopName);
                    ((GuacamoleSession) s).setPassword(vncPassword);
                    return s;
                }
            }
        }

        Tunnel t = null;
        try {
            t = startTunnel(viaGateway, remoteHost, remotePort, session);
            guacSession.setLocalPort(t.getLocalPort());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return registerTunnel(session, t, guacSession);
    }

    public static TunnelDependency startHttpTunnel(String viaGateway, String remoteHost, String root, boolean isSecure, int remotePort, Session session) {

        final Set<TunnelDependency> tunnelSessionSet = session.getTunnelSessionsSet();

        HTTPTunnel httpTunnel = new HTTPTunnel(root, isSecure) {
            @Override
            public void onTunnelStop() {
                tunnelSessionSet.remove(this);
            }
        };

        // Avoid creating duplicate http tunnels
        if (tunnelSessionSet.contains(httpTunnel)) {
            for (TunnelDependency s : tunnelSessionSet) {
                if (s.equals(httpTunnel)) {
                    return s;
                }
            }
        }

        Tunnel t = null;
        try {
            t = startTunnel(viaGateway, remoteHost, remotePort, session);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return registerTunnel(session, t, httpTunnel);
    }

    private static TunnelDependency registerTunnel(Session session, Tunnel tunnel, AbstractTunnelDependency tunnelDependency) {

        final Set<TunnelDependency> tunnelSessionSet = session.getTunnelSessionsSet();
        tunnelDependency.linkTunnel(tunnel);
        sshTunnels.put(tunnel.getLocalPort(), tunnelDependency);
        tunnelSessionSet.add(tunnelDependency);

        return tunnelDependency;
    }

    public static void stopSession(TunnelDependency tunnelSession) {
        Integer port = tunnelSession.getTunnel().getLocalPort();
        if (sshTunnels.containsKey(port)) {
            stopTunnel(port);
        }
    }

    private static Tunnel startTunnel(String viaGateway, String remoteHost, int remotePort, Session session) throws IOException {
        ForkedSSHClient sshClient = new ForkedSSHClient(session.getCertificate(), viaGateway, remoteHost);
        return sshClient.startTunnel(remotePort, 0);
    }

    private static void stopTunnel(int port) {
        TunnelDependency t = sshTunnels.get(port);

        t.onTunnelStop();
        if (t.getTunnel().isRunning()) {
            t.getTunnel().stopTunnel();
        }

        sshTunnels.remove(port);
    }
}
