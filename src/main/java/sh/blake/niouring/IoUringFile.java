package sh.blake.niouring;

public class IoUringFile extends AbstractIoUringChannel {

    /**
     * Instantiates a new {@code AbstractIoUringSocket}.
     *
     * @param path The path to the file
     */
    public IoUringFile(String path) {
        super(IoUringFile.open(path));
    }

    private static native long open(String path);

    static {
        System.loadLibrary("nio_uring");
    }
}
