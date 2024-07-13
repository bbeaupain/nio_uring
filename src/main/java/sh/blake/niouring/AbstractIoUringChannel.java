package sh.blake.niouring;

import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import sh.blake.niouring.util.NativeLibraryLoader;
import sh.blake.niouring.util.ReferenceCounter;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * The type {@code AbstractIoUringSocket}.
 */
public abstract class AbstractIoUringChannel {
    private final int fd;
    private final LongObjectHashMap<ReferenceCounter<ByteBuffer>> readBufferMap = new LongObjectHashMap<>();
    private final LongObjectHashMap<ReferenceCounter<ByteBuffer>> writeBufferMap = new LongObjectHashMap<>();
    private boolean closed = false;
    private Consumer<ByteBuffer> readHandler;
    private Consumer<ByteBuffer> writeHandler;
    private Consumer<Exception> exceptionHandler;
    private Runnable closeHandler;

    /**
     * Instantiates a new {@code AbstractIoUringSocket}.
     *
     * @param fd the fd
     */
    AbstractIoUringChannel(int fd) {
        this.fd = fd;
    }

    protected void handleReadCompletion(ByteBuffer buffer, int bytesRead) {
        if (bytesRead < 0) {
            close();
            return;
        }
        buffer.position(buffer.position() + bytesRead);
        if (readHandler() != null) {
            readHandler().accept(buffer);
        }
    }

    protected void handleWriteCompletion(ByteBuffer buffer, int bytesWritten) {
        if (bytesWritten < 0) {
            close();
            return;
        }
        buffer.position(buffer.position() + bytesWritten);
        if (writeHandler() != null) {
            writeHandler().accept(buffer);
        }
    }

    /**
     * Closes the socket.
     */
    public void close() {
        if (closed) {
            return;
        }
        AbstractIoUringChannel.close(fd);
        closed = true;
        if (closeHandler != null) {
            closeHandler.run();
        }
    }

    /**
     * Gets the file descriptor.
     *
     * @return the long
     */
    int fd() {
        return fd;
    }

    /**
     * Checks if a write operation is currently pending.
     *
     * @return whether write is pending
     */
    public boolean isWritePending() {
        return !writeBufferMap.isEmpty();
    }

    /**
     * Checks if a read operation is currently pending.
     *
     * @return whether read is pending
     */
    public boolean isReadPending() {
        return !readBufferMap.isEmpty();
    }

    /**
     * Gets the read handler.
     *
     * @return the read handler
     */
    Consumer<ByteBuffer> readHandler() {
        return readHandler;
    }

    /**
     * Sets the handler to be called when a read operation completes.
     *
     * @param readHandler the read handler
     * @return this instance
     */
    public AbstractIoUringChannel onRead(Consumer<ByteBuffer> readHandler) {
        this.readHandler = readHandler;
        return this;
    }

    /**
     * Gets the write handler.
     *
     * @return the write handler
     */
    Consumer<ByteBuffer> writeHandler() {
        return writeHandler;
    }

    /**
     * Sets the handler to be called when a write operation completes.
     *
     * @param writeHandler the write handler
     * @return this instance
     */
    public AbstractIoUringChannel onWrite(Consumer<ByteBuffer> writeHandler) {
        this.writeHandler = writeHandler;
        return this;
    }

    /**
     * Gets the exception handler.
     *
     * @return the exception handler
     */
    Consumer<Exception> exceptionHandler() {
        return exceptionHandler;
    }

    /**
     * Gets read buffer map.
     *
     * @return the read buffer map
     */
    LongObjectHashMap<ReferenceCounter<ByteBuffer>> readBufferMap() {
        return readBufferMap;
    }

    /**
     * Gets write buffer map.
     *
     * @return the write buffer map
     */
    LongObjectHashMap<ReferenceCounter<ByteBuffer>> writeBufferMap() {
        return writeBufferMap;
    }

    /**
     * Sets the handler to be called when an exception is caught while handling I/O for the socket.
     *
     * @param exceptionHandler the exception handler
     * @return this instance
     */
    public AbstractIoUringChannel onException(Consumer<Exception> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    Runnable closeHandler() {
        return closeHandler;
    }

    /**
     * Sets the handler to be called when the channel is closed.
     * @param closeHandler The close handler
     * @return this instance
     */
    public AbstractIoUringChannel onClose(Runnable closeHandler) {
        this.closeHandler = closeHandler;
        return this;
    }

    /**
     * Check if the channel is closed.
     *
     * @return true if the channel has been closed
     */
    public boolean isClosed() {
        return closed;
    }

    void setClosed(boolean closed) {
        this.closed = closed;
    }

    /**
     * Check if the channel is open.
     * @return true if the channel has not been closed
     */
    public boolean isOpen() {
        return !closed;
    }

    private static native void close(int fd);

    static {
        NativeLibraryLoader.load();
    }
}
