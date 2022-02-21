package sh.niouring.core.examples;

import sh.niouring.core.IoUring;
import sh.niouring.core.IoUringServerSocket;
import sh.niouring.core.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class HttpHelloWorldTest {
    public static void main(String[] args) {
        ByteBuffer response = ByteBufferUtil.wrapDirect("HTTP/1.1 200 OK\r\n\r\nHello, world!");

        IoUringServerSocket serverSocket = IoUringServerSocket.bind(8080).onAccept(socket -> {
            socket.onRead(in -> socket.queueWrite(response)); // both read and write are zero-copy

            socket.onWrite(out -> {
                out.flip(); // reset shared response buffer for the next write
                socket.close(); // HTTP spec says we should close after responding
            });

            socket.onException(ex -> {
                ex.printStackTrace();
                socket.close();
            });

            socket.queueRead(ByteBuffer.allocateDirect(1024));
        });

        IoUring ring = new IoUring()
            .onException(Exception::printStackTrace)
            .queueAccept(serverSocket);

        ring.loop();
    }
}
