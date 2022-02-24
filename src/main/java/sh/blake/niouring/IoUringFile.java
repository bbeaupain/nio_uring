package sh.blake.niouring;

import sh.blake.niouring.util.NativeLibraryLoader;

import java.io.IOException;
import java.net.URISyntaxException;

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
        try {
            NativeLibraryLoader.load("/libnio_uring.so");
        } catch (IOException | URISyntaxException ex) {
            ex.printStackTrace();
            System.loadLibrary("nio_uring");
        }
    }
}
