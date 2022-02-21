package sh.niouring.core.examples;

import sh.niouring.core.IoUring;
import sh.niouring.core.IoUringServerSocket;
import sh.niouring.core.IoUringSocket;
import sh.niouring.core.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class HttpEchoTest {
    private static final ByteBuffer RESPONSE_LINE_BUFFER = ByteBufferUtil.wrapDirect("HTTP/1.1 200 OK\r\n\r\n");

    public static void main(String[] args) {
        IoUringServerSocket serverSocket = IoUringServerSocket.bind(8080)
            .onAccept(HttpEchoTest::echoHandler);

        IoUring ring = new IoUring()
            .onException(Exception::printStackTrace)
            .queueAccept(serverSocket);

        ring.loop();
    }

    static void echoHandler(IoUringSocket socket) {
        socket.onRead(in -> {
            // both of these writes are zero-copy
            socket.queueWrite(RESPONSE_LINE_BUFFER);
            socket.queueWrite(in);
        });

        socket.onWrite(out -> {
            out.flip(); // reset buffer for the next write
            socket.close(); // HTTP spec says we should close now
        });

        socket.onException(ex -> {
            ex.printStackTrace();
            socket.close();
        });

        socket.queueRead(ByteBuffer.allocateDirect(1024));
    }
}
