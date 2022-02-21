package sh.blake.niouring;

import sh.blake.niouring.util.OsVersionCheck;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Primary interface for creating and working with an {@code io_uring}.
 */
public final class IoUring {
    private static final int DEFAULT_MAX_EVENTS = 512;
    private static final int EVENT_TYPE_ACCEPT = 0;
    private static final int EVENT_TYPE_READ = 1;
    private static final int EVENT_TYPE_WRITE = 2;

    private final long ring;
    private final int ringSize;
    private final Map<Long, AbstractIoUringChannel> fdToSocket = new HashMap<>();
    private Consumer<Exception> exceptionHandler;

    /**
     * Instantiates a new {@code IoUring} with {@code DEFAULT_MAX_EVENTS}.
     */
    public IoUring() {
        this(DEFAULT_MAX_EVENTS);
    }

    /**
     * Instantiates a new Io uring.
     *
     * @param ringSize the max events
     */
    public IoUring(int ringSize) {
        OsVersionCheck.verifySystemRequirements();
        this.ringSize = ringSize;
        this.ring = IoUring.create(ringSize);
    }

    /**
     * Takes over the current thread with a loop calling {@code execute()}, until interrupted.
     */
    public void loop() {
        while (!Thread.interrupted()) {
            execute();
        }
    }

    /**
     * Submits all queued I/O operations to the kernel and waits an unlimited amount of time for any to complete.
     */
    public void execute() {
        doExecute(true);
    }

    /**
     * Submits all queued I/O operations to the kernel and handles any pending completion events, returning immediately
     * if none are present.
     */
    public void executeNow() {
        doExecute(false);
    }

    private void doExecute(boolean shouldWait) {
        long cqes = IoUring.createCqes(ringSize);
        try {
            int count = IoUring.submitAndGetCqes(ring, cqes, ringSize, shouldWait);
            for (int i = 0; i < count && i < ringSize; i++) {
                try {
                    handleEventCompletion(cqes, i);
                } finally {
                    IoUring.markCqeSeen(ring, cqes, i);
                }
            }
        } catch (Exception ex) {
            if (exceptionHandler != null) {
                exceptionHandler.accept(ex);
            }
        } finally {
            IoUring.freeCqes(cqes);
        }
    }

    private void handleEventCompletion(long cqes, int i) {
        long fd = IoUring.getCqeFd(cqes, i);
        int eventType = IoUring.getCqeEventType(cqes, i);
        int result = IoUring.getCqeResult(cqes, i);
        if (eventType == EVENT_TYPE_ACCEPT) {
            handleAcceptCompletion(fd, result);
        } else {
            IoUringSocket socket = (IoUringSocket) fdToSocket.get(fd);
            if (socket == null || socket.isClosed()) {
                return;
            }
            long bufferAddress = IoUring.getCqeBufferAddress(cqes, i);
            try {
                if (eventType == EVENT_TYPE_READ) {
                    ByteBuffer buffer = socket.readBufferMap().get(bufferAddress);
                    if (buffer == null) {
                        throw new IllegalStateException("Buffer already removed");
                    }
                    socket.readBufferMap().remove(bufferAddress);
                    handleReadCompletion(socket, buffer, result);
                } else if (eventType == EVENT_TYPE_WRITE) {
                    ByteBuffer buffer = socket.writeBufferMap().get(bufferAddress);
                    if (buffer == null) {
                        throw new IllegalStateException("Buffer already removed");
                    }
                    socket.writeBufferMap().remove(bufferAddress);
                    handleWriteCompletion(socket, buffer, result);
                }
            } catch (RuntimeException ex) {
                if (socket.exceptionHandler() != null) {
                    socket.exceptionHandler().accept(ex);
                }
            }
        }
    }

    private void handleAcceptCompletion(long serverSocketFd, long socketFd) {
        IoUringServerSocket serverSocket = (IoUringServerSocket) fdToSocket.get(serverSocketFd);
        queueAccept(serverSocket);
        if (socketFd < 0) {
            return;
        }
        IoUringSocket socket = new IoUringSocket(this, socketFd);
        fdToSocket.put(socket.fd(), socket);
        if (serverSocket.acceptHandler() != null) {
            serverSocket.acceptHandler().accept(socket);
        }
    }

    private void handleReadCompletion(IoUringSocket socket, ByteBuffer buffer, int bytesRead) {
        socket.setReadPending(false);
        if (bytesRead < 0) {
            socket.close();
            return;
        }
        buffer.limit(buffer.position() + bytesRead);
        if (socket.readHandler() != null) {
            socket.readHandler().accept(buffer);
        }
    }

    private void handleWriteCompletion(IoUringSocket socket, ByteBuffer buffer, int bytesWritten) {
        socket.setWritePending(false);
        if (bytesWritten < 0) {
            socket.close();
            return;
        }
        int newPosition = buffer.position() + bytesWritten;
        buffer.position(newPosition);
        if (!buffer.hasRemaining()) {
            if (socket.writeHandler() != null) {
                socket.writeHandler().accept(buffer);
            }
        } else {
            socket.setWritePending(true);
            queueWrite(socket, buffer);
        }
    }

    /**
     * Queues a {@link IoUringServerSocket} for an accept operation on the next ring execution.
     *
     * @param serverSocket the server socket
     * @return this instance
     */
    public IoUring queueAccept(IoUringServerSocket serverSocket) {
        fdToSocket.put(serverSocket.fd(), serverSocket);
        IoUring.queueAccept(ring, serverSocket.fd());
        return this;
    }

    /**
     * Queues {@link IoUringSocket} for a read operation on the next ring execution.
     *
     * @param socket the socket
     * @param buffer the buffer to read into
     * @return this instance
     */
    public IoUring queueRead(IoUringSocket socket, ByteBuffer buffer) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("Buffer must be direct");
        }
        long bufferAddress = IoUring.queueRead(ring, socket.fd(), buffer, buffer.position(), buffer.limit());
        socket.readBufferMap().put(bufferAddress, buffer);
        socket.setReadPending(true);
        return this;
    }

    /**
     * Queues {@link IoUringSocket} for a write operation on the next ring execution.
     *
     * @param socket the socket
     * @return this instance
     */
    public IoUring queueWrite(IoUringSocket socket, ByteBuffer buffer) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("Buffer must be direct");
        }
        long bufferAddress = IoUring.queueWrite(ring, socket.fd(), buffer, buffer.position(), buffer.limit());
        socket.writeBufferMap().put(bufferAddress, buffer);
        socket.setWritePending(true);
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
     * Sets the handler that is called when an {@code Exception} is caught during execution.
     *
     * @param exceptionHandler the exception handler
     */
    public IoUring onException(Consumer<Exception> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    /**
     * Deregister this socket from the ring.
     *
     * @param socket the socket
     */
    void deregister(IoUringSocket socket) {
        fdToSocket.remove(socket.fd());
    }

    private static native long create(int maxEvents);
    private static native long createCqes(int count);
    private static native void freeCqes(long cqes);
    private static native int submitAndGetCqes(long ring, long cqes, int cqesSize, boolean shouldWait);
    private static native int getCqeEventType(long cqes, long cqeIndex);
    private static native long getCqeFd(long cqes, long cqeIndex);
    private static native int getCqeResult(long cqes, long cqeIndex);
    private static native long getCqeBufferAddress(long cqes, long cqeIndex);
    private static native int markCqeSeen(long ring, long cqes, long cqeIndex);
    private static native int queueAccept(long ring, long serverSocketFd);
    private static native long queueRead(long ring, long socketFd, ByteBuffer buffer, int bufferPos, int bufferLen);
    private static native long queueWrite(long ring, long socketFd, ByteBuffer buffer, int bufferPos, int bufferLen);

    static {
        System.loadLibrary("nio_uring");
    }
}
