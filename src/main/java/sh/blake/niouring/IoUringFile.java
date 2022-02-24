package sh.blake.niouring;

/**
 * An {@link AbstractIoUringChannel} implementation for file operations.
 */
public class IoUringFile extends AbstractIoUringChannel {

    /**
     * Instantiates a new {@code IoUringFile}.
     *
     * @param path The path to the file
     */
    public IoUringFile(String path) {
        super(IoUringFile.open(path));
    }

    private static native int open(String path);

    static {
        System.loadLibrary("nio_uring");
    }
}
