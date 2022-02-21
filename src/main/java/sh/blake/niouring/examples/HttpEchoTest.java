package sh.blake.niouring.examples;

import sh.blake.niouring.IoUringSocket;
import sh.blake.niouring.IoUring;
import sh.blake.niouring.IoUringServerSocket;
import sh.blake.niouring.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class HttpEchoTest {
    private static final ByteBuffer RESPONSE_LINE_BUFFER = ByteBufferUtil.wrapDirect("HTTP/1.1 200 OK\r\n\r\n");

    public static void main(String[] args) {
        IoUringServerSocket serverSocket = IoUringServerSocket.bind(8080)
            .onAccept(HttpEchoTest::echoHandler);
        new IoUring()
            .onException(Exception::printStackTrace)
            .queueAccept(serverSocket)
            .loop();
    }

    static void echoHandler(IoUringSocket socket) {
        socket.onRead(in -> {
            socket.queueWrite(RESPONSE_LINE_BUFFER.slice());
            socket.queueWrite(in);
        });
        socket.queueRead(ByteBuffer.allocateDirect(1024));
        socket.onWrite(out -> socket.close());
        socket.onException(ex -> socket.close());
    }
}
