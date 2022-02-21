package sh.blake.niouring;

/**
 * The type {@code AbstractIoUringSocket}.
 */
public abstract class AbstractIoUringChannel {
    private final long fd;

    /**
     * Instantiates a new {@code AbstractIoUringSocket}.
     *
     * @param fd the fd
     */
    AbstractIoUringChannel(long fd) {
        this.fd = fd;
    }

    /**
     * Gets the file descriptor.
     *
     * @return the long
     */
    long fd() {
        return fd;
    }
}
