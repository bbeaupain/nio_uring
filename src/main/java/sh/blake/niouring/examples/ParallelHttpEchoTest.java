package sh.blake.niouring.examples;

import sh.blake.niouring.IoUring;
import sh.blake.niouring.IoUringServerSocket;
import sh.blake.niouring.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ParallelHttpEchoTest {
    private static final ByteBuffer RESPONSE_LINE_BUFFER = ByteBufferUtil.wrapDirect("HTTP/1.1 200 OK\r\n\r\n");

    public static void main(String[] args) {
        IoUringServerSocket serverSocket = new IoUringServerSocket(8080).onAccept((ring, socket) -> {
            socket.onRead(in -> {
                ring.queueWrite(socket, RESPONSE_LINE_BUFFER.slice());
                ring.queueWrite(socket, in);
            });
            ring.queueRead(socket, ByteBuffer.allocateDirect(1024));
            socket.onWrite(out -> socket.close());
            socket.onException(ex -> socket.close());
        });

        int rings = Integer.parseInt(args[0]);
        int ringSize = Integer.parseInt(args[1]);
        ExecutorService threadPool = Executors.newFixedThreadPool(rings);
        for (int i = 0; i < rings; i++) {
            IoUring ring = new IoUring(ringSize)
                .onException(Exception::printStackTrace)
                .queueAccept(serverSocket);

            threadPool.execute(ring::loop);
        }
    }
}
