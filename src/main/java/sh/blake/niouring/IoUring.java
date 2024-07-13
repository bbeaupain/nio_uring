package sh.blake.niouring;

import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import sh.blake.niouring.util.ReferenceCounter;
import sh.blake.niouring.util.NativeLibraryLoader;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Primary interface for creating and working with an {@code io_uring}.
 */
public final class IoUring {
    private static final int DEFAULT_MAX_EVENTS = 1024;
    private static final int EVENT_TYPE_ACCEPT = 0;
    private static final int EVENT_TYPE_READ = 1;
    private static final int EVENT_TYPE_WRITE = 2;
    private static final int EVENT_TYPE_CONNECT = 3;
    private static final int EVENT_TYPE_CLOSE = 4;

    private final long ring;
    private final int ringSize;
    private final IntObjectHashMap<AbstractIoUringChannel> fdToSocket = new IntObjectHashMap<>();
    private Consumer<Exception> exceptionHandler;
    private boolean closed = false;
    private final long cqes;
    private final ByteBuffer resultBuffer;

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
        this.ringSize = ringSize;
        this.ring = IoUring.create(ringSize);
        this.cqes = IoUring.createCqes(ringSize);
        this.resultBuffer = ByteBuffer.allocateDirect(ringSize * 17);
    }

    /**
     * Closes the io_uring.
     */
    public void close() {
        if (closed) {
            throw new IllegalStateException("io_uring closed");
        }
        closed = true;
        IoUring.close(ring);
    }

    /**
     * Takes over the current thread with a loop calling {@code execute()}, until closed.
     */
    public void loop() {
        while (!closed) {
            execute();
        }
    }

    /**
     * Submits all queued I/O operations to the kernel and waits an unlimited amount of time for any to complete.
     */
    public int execute() {
        return doExecute(true);
    }

    /**
     * Submits all queued I/O operations to the kernel and handles any pending completion events, returning immediately
     * if none are present.
     */
    public int executeNow() {
        return doExecute(false);
    }

    private int doExecute(boolean shouldWait) {
        if (closed) {
            throw new IllegalStateException("io_uring closed");
        }
        try {
            int count = IoUring.submitAndGetCqes(ring, resultBuffer, cqes, ringSize, shouldWait);
            if (count == -1) {
                throw new IllegalStateException("submitAndGetCqes returned -1");
            }
            for (int i = 0; i < count && i < ringSize; i++) {
                try {
                    handleEventCompletion(cqes, resultBuffer, i);
                } finally {
                    IoUring.markCqeSeen(ring, cqes, i);
                }
            }
            return count;
        } catch (Exception ex) {
            if (exceptionHandler != null) {
                exceptionHandler.accept(ex);
            }
        } finally {
            resultBuffer.clear();
        }
        return -1;
    }

    private void handleEventCompletion(long cqes, ByteBuffer results, int i) {
        int result = results.getInt();
        int fd = results.getInt();
        int eventType = results.get();

        if (eventType == EVENT_TYPE_ACCEPT) {
            IoUringServerSocket serverSocket = (IoUringServerSocket) fdToSocket.get(fd);
            String ipAddress = IoUring.getCqeIpAddress(cqes, i);
            IoUringSocket socket = serverSocket.handleAcceptCompletion(this, serverSocket, result, ipAddress);
            if (socket != null) {
                fdToSocket.put(socket.fd(), socket);
            }
        } else {
            AbstractIoUringChannel channel = fdToSocket.get(fd);
            if (channel == null || channel.isClosed()) {
                return;
            }
            try {
                if (eventType == EVENT_TYPE_CONNECT) {
                    ((IoUringSocket) channel).handleConnectCompletion(this, result);
                } else if (eventType == EVENT_TYPE_READ) {
                    long bufferAddress = results.getLong();
                    ReferenceCounter<ByteBuffer> refCounter = channel.readBufferMap().get(bufferAddress);
                    ByteBuffer buffer = refCounter.ref();
                    if (buffer == null) {
                        throw new IllegalStateException("Buffer already removed");
                    }
                    if (refCounter.deincrementReferenceCount() == 0) {
                        channel.readBufferMap().remove(bufferAddress);
                    }
                    channel.handleReadCompletion(buffer, result);
                } else if (eventType == EVENT_TYPE_WRITE) {
                    long bufferAddress = results.getLong();
                    ReferenceCounter<ByteBuffer> refCounter = channel.writeBufferMap().get(bufferAddress);
                    ByteBuffer buffer = refCounter.ref();
                    if (buffer == null) {
                        throw new IllegalStateException("Buffer already removed");
                    }
                    if (refCounter.deincrementReferenceCount() == 0) {
                        channel.writeBufferMap().remove(bufferAddress);
                    }
                    channel.handleWriteCompletion(buffer, result);
                } else if (eventType == EVENT_TYPE_CLOSE) {
                    channel.setClosed(true);
                    channel.closeHandler().run();
                }
            } catch (Exception ex) {
                if (channel.exceptionHandler() != null) {
                    channel.exceptionHandler().accept(ex);
                }
            } finally {
                if (channel.isClosed() && channel.equals(fdToSocket.get(fd))) {
                    deregister(channel);
                }
            }
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
     * Queues a {@link IoUringServerSocket} for a connect operation on the next ring execution.
     *
     * @param socket the socket channel
     * @return this instance
     */
    public IoUring queueConnect(IoUringSocket socket) {
        fdToSocket.put(socket.fd(), socket);
        IoUring.queueConnect(ring, socket.fd(), socket.ipAddress(), socket.port());
        return this;
    }

    /**
     * Queues {@link IoUringSocket} for a read operation on the next ring execution.
     *
     * @param channel the channel
     * @param buffer the buffer to read into
     * @return this instance
     */
    public IoUring queueRead(AbstractIoUringChannel channel, ByteBuffer buffer) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("Buffer must be direct");
        }
        fdToSocket.put(channel.fd(), channel);
        long bufferAddress = IoUring.queueRead(ring, channel.fd(), buffer, buffer.position(), buffer.limit() - buffer.position());
        ReferenceCounter<ByteBuffer> refCounter = channel.readBufferMap().get(bufferAddress);
        if (refCounter == null) {
            refCounter = new ReferenceCounter<>(buffer);
            channel.readBufferMap().put(bufferAddress, refCounter);
        }
        refCounter.incrementReferenceCount();
        return this;
    }

    /**
     * Queues {@link IoUringSocket} for a write operation on the next ring execution.
     *
     * @param channel the channel
     * @return this instance
     */
    public IoUring queueWrite(AbstractIoUringChannel channel, ByteBuffer buffer) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("Buffer must be direct");
        }
        fdToSocket.put(channel.fd(), channel);
        long bufferAddress = IoUring.queueWrite(ring, channel.fd(), buffer, buffer.position(), buffer.limit() - buffer.position());
        ReferenceCounter<ByteBuffer> refCounter = channel.writeBufferMap().get(bufferAddress);
        if (refCounter == null) {
            refCounter = new ReferenceCounter<>(buffer);
            channel.writeBufferMap().put(bufferAddress, refCounter);
        }
        refCounter.incrementReferenceCount();
        return this;
    }

    public IoUring queueClose(AbstractIoUringChannel channel) {
        IoUring.queueClose(ring, channel.fd());
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
     * Deregister this channel from the ring.
     *
     * @param channel the channel
     */
    void deregister(AbstractIoUringChannel channel) {
        fdToSocket.remove(channel.fd());
    }

    private static native long create(int maxEvents);
    private static native void close(long ring);
    private static native long createCqes(int count);
    private static native void freeCqes(long cqes);
    private static native int submitAndGetCqes(long ring, ByteBuffer buffer, long cqes, int cqesSize, boolean shouldWait);
    private static native byte getCqeEventType(long cqes, int cqeIndex);
    private static native int getCqeFd(long cqes, int cqeIndex);
    private static native int getCqeResult(long cqes, int cqeIndex);
    private static native long getCqeBufferAddress(long cqes, int cqeIndex);
    private static native String getCqeIpAddress(long cqes, int cqeIndex);
    private static native void markCqeSeen(long ring, long cqes, int cqeIndex);
    private static native void queueAccept(long ring, int serverSocketFd);
    private static native void queueConnect(long ring, int socketFd, String ipAddress, int port);
    private static native long queueRead(long ring, int channelFd, ByteBuffer buffer, int bufferPos, int bufferLen);
    private static native long queueWrite(long ring, int channelFd, ByteBuffer buffer, int bufferPos, int bufferLen);
    private static native void queueClose(long ring, int channelFd);

    static {
        NativeLibraryLoader.load();
    }
}
