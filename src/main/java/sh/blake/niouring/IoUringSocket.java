package sh.blake.niouring;

import java.nio.ByteBuffer;
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

    protected void handleConnectCompletion(IoUring ioUring, int result) {
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

    public void onConnect(Consumer<IoUring> connectHandler) {
        this.connectHandler = connectHandler;
    }
}
