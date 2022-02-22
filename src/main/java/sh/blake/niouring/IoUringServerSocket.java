package sh.blake.niouring;

import java.util.function.Consumer;

/**
 * A {@code ServerSocket} analog for working with an {@code io_uring}.
 */
public final class IoUringServerSocket extends AbstractIoUringChannel {
    private static final int DEFAULT_BACKLOG = 65535;

    private Consumer<IoUringSocket> acceptHandler;

    /**
     * Creates an {@code IoUringServerSocket} bound to an address and port with the specified backlog size.
     * @param address The address
     * @param port The port
     * @param backlog The backlog
     * @return The IoUringServerSocket
     */
    public static IoUringServerSocket bind(String address, int port, int backlog) {
        IoUringServerSocket serverSocket = new IoUringServerSocket();
        IoUringServerSocket.bind(serverSocket.fd(), address, port, backlog);
        return serverSocket;
    }
    /**
     * Creates an {@code IoUringServerSocket} bound to an address and port with the default backlog size.
     * @param address The address
     * @param port The port
     * @return The IoUringServerSocket
     */
    public static IoUringServerSocket bind(String address, int port) {
        return bind(address, port, DEFAULT_BACKLOG);
    }

    /**
     * Creates an {@code IoUringServerSocket} bound to address {@code "127.0.0.1"} on the specified port with the
     * specified backlog size.
     * @param port The port
     * @param backlog The backlog
     * @return The IoUringServerSocket
     */
    public static IoUringServerSocket bind(int port, int backlog) {
        return bind("127.0.0.1", port, backlog);
    }

    /**
     * Creates an {@code IoUringServerSocket} bound to address {@code "127.0.0.1"} on the specified port with the
     * default backlog size.
     * @param port The port
     * @return The IoUringServerSocket
     */
    public static IoUringServerSocket bind(int port) {
        return bind("127.0.0.1", port, DEFAULT_BACKLOG);
    }

    /**
     * Instantiates a new {@code IoUringServerSocket}.
     */
    public IoUringServerSocket() {
        super(create());
    }

    /**
     * Gets the accept handler.
     *
     * @return the accept handler
     */
    Consumer<IoUringSocket> acceptHandler() {
        return acceptHandler;
    }

    /**
     * Sets the accept handler.
     *
     * @param acceptHandler the accept handler
     * @return this instance
     */
    public IoUringServerSocket onAccept(Consumer<IoUringSocket> acceptHandler) {
        this.acceptHandler = acceptHandler;
        return this;
    }

    private static native long create();
    private static native int bind(long fd, String host, int port, int backlog);

    static {
        System.loadLibrary("nio_uring");
    }
}
