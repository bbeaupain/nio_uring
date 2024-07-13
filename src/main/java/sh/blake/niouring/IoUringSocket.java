package sh.blake.niouring;

import java.util.function.Consumer;

/**
 * A {@code Socket} analog for working with an {@code io_uring}.
 */
public class IoUringSocket extends AbstractIoUringSocket {
    private Consumer<IoUring> connectHandler;

    /**
     * Instantiates a new {@code IoUringSocket}.
     *
     * @param ipAddress the ip address
     * @param port      the port
     */
    public IoUringSocket(String ipAddress, int port) {
        super(AbstractIoUringSocket.create(), ipAddress, port);
    }

    /**
     * Instantiates a new {@code IoUringSocket}.
     *
     * @param fd        the fd
     * @param ipAddress the ip address
     * @param port      the port
     */
    IoUringSocket(int fd, String ipAddress, int port) {
        super(fd, ipAddress, port);
    }

    void handleConnectCompletion(IoUring ioUring, int result) {
        if (result != 0) {
            // TODO: better error messages, users don't have access to errno
            throw new RuntimeException("Connection result was: " + result);
        }
        if (connectHandler != null) {
            connectHandler.accept(ioUring);
        }
    }

    Consumer<IoUring> connectHandler() {
        return connectHandler;
    }

    /**
     * Set the connect handler.
     *
     * @return this instance
     */
    public IoUringSocket onConnect(Consumer<IoUring> connectHandler) {
        this.connectHandler = connectHandler;
        return this;
    }
}
