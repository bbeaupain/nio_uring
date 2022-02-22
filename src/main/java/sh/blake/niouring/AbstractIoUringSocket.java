package sh.blake.niouring;

import java.util.function.Consumer;

public class AbstractIoUringSocket extends AbstractIoUringChannel {
    private final String ipAddress;
    private final int port;
    private Consumer<IoUring> connectHandler;

    public AbstractIoUringSocket(long fd, String ipAddress, int port) {
        super(fd);
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public String ipAddress() {
        return ipAddress;
    }

    public int port() {
        return port;
    }

    Consumer<IoUring> connectHandler() {
        return connectHandler;
    }

    public void onConnect(Consumer<IoUring> connectHandler) {
        this.connectHandler = connectHandler;
    }

    static native long create();
}
