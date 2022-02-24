package sh.blake.niouring;

/**
 * An {@link AbstractIoUringChannel} representing a network socket.
 */
public class AbstractIoUringSocket extends AbstractIoUringChannel {
    private final String ipAddress;
    private final int port;

    /**
     * Creates a new {@code AbstractIoUringSocket} instance.
     * @param fd The file descriptor
     * @param ipAddress The IP address
     * @param port The port
     */
    AbstractIoUringSocket(int fd, String ipAddress, int port) {
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

    static {
        System.loadLibrary("nio_uring");
    }
}
