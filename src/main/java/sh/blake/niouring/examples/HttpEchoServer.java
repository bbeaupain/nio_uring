package sh.blake.niouring.examples;

import sh.blake.niouring.IoUring;
import sh.blake.niouring.IoUringServerSocket;
import sh.blake.niouring.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class HttpEchoServer {
    private static final ByteBuffer RESPONSE_LINE_BUFFER = ByteBufferUtil.wrapDirect("HTTP/1.1 200 OK\r\n\r\n");

    public static void main(String[] args) {
        IoUringServerSocket serverSocket = new IoUringServerSocket(8080);
        serverSocket.onAccept((ring, socket) -> {
            ring.queueAccept(serverSocket);

            socket.onRead(in -> {
                ring.queueWrite(socket, RESPONSE_LINE_BUFFER.slice());
                ring.queueWrite(socket, in);
            });
            ring.queueRead(socket, ByteBuffer.allocateDirect(1024));

            socket.onWrite(out -> socket.close());
            socket.onException(ex -> socket.close());
        });

        new IoUring()
            .onException(Exception::printStackTrace)
            .queueAccept(serverSocket)
            .loop();
    }
}
