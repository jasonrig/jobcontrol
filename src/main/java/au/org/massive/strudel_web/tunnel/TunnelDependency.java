package au.org.massive.strudel_web.tunnel;

/**
 * Created by jason on 13/7/17.
 */
public interface TunnelDependency {
    void onTunnelStop();

    Tunnel getTunnel();

    int getId();
}
