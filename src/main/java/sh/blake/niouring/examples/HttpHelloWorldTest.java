package sh.blake.niouring.examples;

import sh.blake.niouring.IoUring;
import sh.blake.niouring.IoUringServerSocket;
import sh.blake.niouring.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class HttpHelloWorldTest {
    public static void main(String[] args) {
        IoUringServerSocket serverSocket = IoUringServerSocket.bind(8080).onAccept(socket -> {
            socket.onRead(in -> {
                String response = "HTTP/1.1 200 OK\r\n\r\nHello, world!";
                ByteBuffer buffer = ByteBufferUtil.wrapDirect(response);
                socket.queueWrite(buffer);
            });
            socket.queueRead(ByteBuffer.allocateDirect(1024));
            socket.onWrite(out -> socket.close());
            socket.onException(ex -> socket.close());
        });
        new IoUring()
            .onException(Exception::printStackTrace)
            .queueAccept(serverSocket)
            .loop();
    }
}
