package sh.blake.niouring;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A {@code Socket} analog for working with an {@code io_uring}.
 */
public final class IoUringSocket extends AbstractIoUringChannel {
    private final IoUring ioUring;
    private final Map<Long, ByteBuffer> readBufferMap = new HashMap<>();
    private final Map<Long, ByteBuffer> writeBufferMap = new HashMap<>();
    private final String ipAddress;
    private Consumer<ByteBuffer> readHandler;
    private Consumer<ByteBuffer> writeHandler;
    private Consumer<Exception> exceptionHandler;
    private boolean writePending = false;
    private boolean readPending = false;
    private boolean closed = false;

    /**
     * Instantiates a new {@code IoUringSocket}.
     * @param ioUring    the io uring
     * @param fd         the fd
     * @param ipAddress  the IP address
     */
    IoUringSocket(IoUring ioUring, long fd, String ipAddress) {
        super(fd);
        this.ioUring = ioUring;
        this.ipAddress = ipAddress;
    }

    /**
     * Queues the buffer for sending.
     *
     * Note: do not write the same buffer instance to the same socket multiple times per {@code IoUring} execution.
     *
     * @param buffer the buffer
     */
    public void queueWrite(ByteBuffer buffer) {
        ioUring.queueWrite(this, buffer);
    }

    /**
     * Queues the buffer for reading.
     *
     * @param buffer the buffer to read into
     */
    public void queueRead(ByteBuffer buffer) {
        ioUring.queueRead(this, buffer);
    }

    /**
     * Closes the socket.
     */
    public void close() {
        if (closed) {
            return;
        }
        if (!readPending && !writePending) {
            ioUring.deregister(this);
            close(fd());
            closed = true;
        } else {
            // let's queue this for closure upon completion?
            throw new RuntimeException("Cannot close with pending I/O events"); // or can we?
        }
    }

    /**
     * Gets the {@link IoUring}.
     *
     * @return the io uring
     */
    public IoUring ioUring() {
        return ioUring;
    }

    /**
     * Checks if a write operation is currently pending.
     *
     * @return whether write is pending
     */
    public boolean isWritePending() {
        return writePending;
    }

    /**
     * Sets write pending.
     *
     * @param writePending the write pending
     */
    void setWritePending(boolean writePending) {
        this.writePending = writePending;
    }

    /**
     * Checks if a read operation is currently pending.
     *
     * @return whether read is pending
     */
    public boolean isReadPending() {
        return readPending;
    }

    /**
     * Sets read pending.
     *
     * @param readPending the read pending
     */
    void setReadPending(boolean readPending) {
        this.readPending = readPending;
    }

    /**
     * Checks if the socket has been closed.
     *
     * @return whether the socket is closed
     */
    public boolean isClosed() {
        return closed;
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
    public IoUringSocket onRead(Consumer<ByteBuffer> readHandler) {
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
    public IoUringSocket onWrite(Consumer<ByteBuffer> writeHandler) {
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
    Map<Long, ByteBuffer> readBufferMap() {
        return readBufferMap;
    }

    /**
     * Gets write buffer map.
     *
     * @return the write buffer map
     */
    Map<Long, ByteBuffer> writeBufferMap() {
        return writeBufferMap;
    }

    public String ipAddress() {
        return ipAddress;
    }

    /**
     * Sets the handler to be called when an exception is caught while handling I/O for the socket.
     *
     * @param exceptionHandler the exception handler
     * @return this instance
     */
    public IoUringSocket onException(Consumer<Exception> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    private static native void close(long fd);
}
