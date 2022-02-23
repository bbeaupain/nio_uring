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
    private static final int EVENT_TYPE_CONNECT = 3;

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
            return count;
        } catch (Exception ex) {
            if (exceptionHandler != null) {
                exceptionHandler.accept(ex);
            }
        } finally {
            IoUring.freeCqes(cqes);
        }
        return -1;
    }

    private void handleEventCompletion(long cqes, int i) {
        long fd = IoUring.getCqeFd(cqes, i);
        int eventType = IoUring.getCqeEventType(cqes, i);
        if (eventType == EVENT_TYPE_ACCEPT) {
            int result = IoUring.getCqeResult(cqes, i);
            IoUringServerSocket serverSocket = (IoUringServerSocket) fdToSocket.get(fd);
            String ipAddress = IoUring.getCqeIpAddress(cqes, i);
            handleAcceptCompletion(serverSocket, result, ipAddress);
        } else {
            AbstractIoUringChannel channel = fdToSocket.get(fd);
            if (channel == null || channel.isClosed()) {
                return;
            }
            long bufferAddress = IoUring.getCqeBufferAddress(cqes, i);
            try {
                if (eventType == EVENT_TYPE_CONNECT) {
                    handleConnectCompletion((IoUringSocket) channel);
                } else if (eventType == EVENT_TYPE_READ) {
                    int result = IoUring.getCqeResult(cqes, i);
                    ByteBuffer buffer = channel.readBufferMap().get(bufferAddress);
                    if (buffer == null) {
                        throw new IllegalStateException("Buffer already removed");
                    }
                    channel.readBufferMap().remove(bufferAddress);
                    handleReadCompletion(channel, buffer, result);
                } else if (eventType == EVENT_TYPE_WRITE) {
                    int result = IoUring.getCqeResult(cqes, i);
                    ByteBuffer buffer = channel.writeBufferMap().get(bufferAddress);
                    if (buffer == null) {
                        throw new IllegalStateException("Buffer already removed");
                    }
                    channel.writeBufferMap().remove(bufferAddress);
                    handleWriteCompletion(channel, buffer, result);
                }
            } catch (RuntimeException ex) {
                if (channel.exceptionHandler() != null) {
                    channel.exceptionHandler().accept(ex);
                }
            } finally {
                if (channel.isClosed() && fdToSocket.get(fd).equals(channel)) {
                    deregister(channel);
                }
            }
        }
    }

    private void handleConnectCompletion(IoUringSocket socket) {
        if (socket.connectHandler() != null) {
            socket.connectHandler().accept(this);
        }
    }

    private void handleAcceptCompletion(IoUringServerSocket serverSocket, long channelFd, String ipAddress) {
        if (channelFd < 0) {
            return;
        }
        IoUringSocket channel = new IoUringSocket(channelFd, ipAddress, serverSocket.getPort());
        fdToSocket.put(channel.fd(), channel);
        if (serverSocket.acceptHandler() != null) {
            serverSocket.acceptHandler().accept(this, channel);
        }
    }

    private void handleReadCompletion(AbstractIoUringChannel channel, ByteBuffer buffer, int bytesRead) {
        if (bytesRead < 0) {
            channel.close();
            return;
        }
        buffer.limit(buffer.position() + bytesRead);
        if (channel.readHandler() != null) {
            channel.readHandler().accept(buffer);
        }
    }

    private void handleWriteCompletion(AbstractIoUringChannel channel, ByteBuffer buffer, int bytesWritten) {
        if (bytesWritten < 0) {
            channel.close();
            return;
        }
        int newPosition = buffer.position() + bytesWritten;
        buffer.position(newPosition);
        if (!buffer.hasRemaining()) {
            if (channel.writeHandler() != null) {
                channel.writeHandler().accept(buffer);
            }
        } else {
            queueWrite(channel, buffer);
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
        long bufferAddress = IoUring.queueRead(ring, channel.fd(), buffer, buffer.position(), buffer.limit());
        channel.readBufferMap().put(bufferAddress, buffer);
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
        long bufferAddress = IoUring.queueWrite(ring, channel.fd(), buffer, buffer.position(), buffer.limit());
        channel.writeBufferMap().put(bufferAddress, buffer);
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
    private static native long createCqes(int count);
    private static native void freeCqes(long cqes);
    private static native int submitAndGetCqes(long ring, long cqes, int cqesSize, boolean shouldWait);
    private static native int getCqeEventType(long cqes, long cqeIndex);
    private static native long getCqeFd(long cqes, long cqeIndex);
    private static native int getCqeResult(long cqes, long cqeIndex);
    private static native long getCqeBufferAddress(long cqes, long cqeIndex);
    private static native String getCqeIpAddress(long cqes, long cqeIndex);
    private static native int markCqeSeen(long ring, long cqes, long cqeIndex);
    private static native int queueAccept(long ring, long serverSocketFd);
    private static native int queueConnect(long ring, long socketFd, String ipAddress, int port);
    private static native long queueRead(long ring, long channelFd, ByteBuffer buffer, int bufferPos, int bufferLen);
    private static native long queueWrite(long ring, long channelFd, ByteBuffer buffer, int bufferPos, int bufferLen);

    static {
        System.loadLibrary("nio_uring");
    }
}
