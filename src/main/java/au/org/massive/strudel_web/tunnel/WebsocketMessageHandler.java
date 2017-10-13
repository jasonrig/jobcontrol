package au.org.massive.strudel_web.tunnel;

import java.nio.ByteBuffer;

public interface WebsocketMessageHandler {
    public void handleClose();
    public void handleMessage(ByteBuffer message);
    public void handleMessage(String message);
    public void handlePong(ByteBuffer message);
}
