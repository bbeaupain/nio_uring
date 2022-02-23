package sh.blake.niouring;

public class AbstractIoUringSocket extends AbstractIoUringChannel {
    private final String ipAddress;
    private final int port;

    public AbstractIoUringSocket(int fd, String ipAddress, int port) {
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

    static native int create();
}
