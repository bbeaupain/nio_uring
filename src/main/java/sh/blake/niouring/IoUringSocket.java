package sh.blake.niouring;

import java.util.function.Consumer;

/**
 * A {@code Socket} analog for working with an {@code io_uring}.
 */
public final class IoUringSocket extends AbstractIoUringSocket {
    private Consumer<IoUring> connectHandler;

    public IoUringSocket(String ipAddress, int port) {
        super(AbstractIoUringSocket.create(), ipAddress, port);
    }

    IoUringSocket(int fd, String ipAddress, int port) {
        super(fd, ipAddress, port);
    }

    Consumer<IoUring> connectHandler() {
        return connectHandler;
    }

    public void onConnect(Consumer<IoUring> connectHandler) {
        this.connectHandler = connectHandler;
    }
}
