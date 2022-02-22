package sh.blake.niouring;

/**
 * A {@code Socket} analog for working with an {@code io_uring}.
 */
public final class IoUringSocket extends AbstractIoUringSocket {
    public IoUringSocket(String ipAddress, int port) {
        super(AbstractIoUringSocket.create(), ipAddress, port);
    }

    IoUringSocket(long fd, String ipAddress, int port) {
        super(fd, ipAddress, port);
    }
}
