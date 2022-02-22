package sh.blake.niouring;

import java.util.function.BiConsumer;

/**
 * A {@code ServerSocket} analog for working with an {@code io_uring}.
 */
public final class IoUringServerSocket extends AbstractIoUringSocket {
    private static final int DEFAULT_BACKLOG = 65535;

    private BiConsumer<IoUring, IoUringSocket> acceptHandler;
    private String address;
    private int port;

    /**
     * Instantiates a new {@code IoUringServerSocket}.
     */
    public IoUringServerSocket(String address, int port, int backlog) {
        super(AbstractIoUringSocket.create(), address, port);
        IoUringServerSocket.bind(fd(), address, port, backlog);
    }

    /**
     * Instantiates a new {@code IoUringServerSocket}.
     */
    public IoUringServerSocket(String address, int port) {
        this(address, port, DEFAULT_BACKLOG);
    }

    /**
     * Instantiates a new {@code IoUringServerSocket}.
     */
    public IoUringServerSocket(int port) {
        this("127.0.0.1", port, DEFAULT_BACKLOG);
    }

    /**
     * Gets the accept handler.
     *
     * @return the accept handler
     */
    BiConsumer<IoUring, IoUringSocket> acceptHandler() {
        return acceptHandler;
    }

    /**
     * Sets the accept handler.
     *
     * @param acceptHandler the accept handler
     * @return this instance
     */
    public IoUringServerSocket onAccept(BiConsumer<IoUring, IoUringSocket> acceptHandler) {
        this.acceptHandler = acceptHandler;
        return this;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    private static native int bind(long fd, String host, int port, int backlog);

    static {
        System.loadLibrary("nio_uring");
    }
}
