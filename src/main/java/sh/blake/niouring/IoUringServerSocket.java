package sh.blake.niouring;

import sh.blake.niouring.util.NativeLibraryLoader;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A {@code ServerSocket} analog for working with an {@code io_uring}.
 */
public final class IoUringServerSocket extends AbstractIoUringSocket {
    private static final int DEFAULT_BACKLOG = 65535;

    private BiConsumer<IoUring, IoUringSocket> acceptHandler;

    /**
     * Instantiates a new {@code IoUringServerSocket}.
     *
     * @param address The address to bind to
     * @param port The port to bind to
     * @param backlog The backlog size
     */
    public IoUringServerSocket(String address, int port, int backlog) {
        super(AbstractIoUringSocket.create(), address, port);
        IoUringServerSocket.bind(fd(), address, port, backlog);
    }

    /**
     * Instantiates a new {@code IoUringServerSocket} with a default backlog size of {@code DEFAULT_BACKLOG}.
     *
     * @param address The address to bind to
     * @param port The port to bind to
     */
    public IoUringServerSocket(String address, int port) {
        this(address, port, DEFAULT_BACKLOG);
    }

    /**
     * Instantiates a new {@code IoUringServerSocket} bound to "127.0.0.1" on the specified port with the default
     * backlog size of {@code DEFAULT_BACKLOG}.
     *
     * @param port The port to bind to
     */
    public IoUringServerSocket(int port) {
        this("127.0.0.1", port, DEFAULT_BACKLOG);
    }

    IoUringSocket handleAcceptCompletion(IoUring ioUring, IoUringServerSocket serverSocket, int channelFd, String ipAddress) {
        if (channelFd < 0) {
            return null;
        }
        IoUringSocket channel = new IoUringSocket(channelFd, ipAddress, serverSocket.port());
        if (serverSocket.acceptHandler() != null) {
            serverSocket.acceptHandler().accept(ioUring, channel);
        }
        return channel;
    }

    @Override
    public IoUringServerSocket onRead(Consumer<ByteBuffer> buffer) {
        throw new UnsupportedOperationException("Server socket cannot read");
    }

    @Override
    public IoUringServerSocket onWrite(Consumer<ByteBuffer> buffer) {
        throw new UnsupportedOperationException("Server socket cannot write");
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

    private static native void bind(long fd, String host, int port, int backlog);

    static {
        NativeLibraryLoader.load();
    }
}
