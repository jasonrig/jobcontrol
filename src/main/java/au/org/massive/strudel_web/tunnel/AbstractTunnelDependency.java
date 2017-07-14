package au.org.massive.strudel_web.tunnel;

/**
 * Created by jason on 14/7/17.
 */
public abstract class AbstractTunnelDependency implements TunnelDependency {
    private static int instanceCount = 0;
    protected int id;
    private Tunnel tunnel;

    public AbstractTunnelDependency(Tunnel t) {
        instanceCount ++;
        this.id = instanceCount;
        this.tunnel = t;
    }

    public AbstractTunnelDependency() {
        this(null);
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public Tunnel getTunnel() {
        return tunnel;
    }

    public void linkTunnel(Tunnel t) {
        this.tunnel = t;
    }
}
