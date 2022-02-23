package sh.blake.niouring.examples;

import sh.blake.niouring.IoUring;
import sh.blake.niouring.IoUringServerSocket;
import sh.blake.niouring.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class HttpHelloWorldServer {
    public static void main(String[] args) {
        String response = "HTTP/1.1 200 OK\r\n\r\nHello, world!";
        ByteBuffer responseBuffer = ByteBufferUtil.wrapDirect(response);

        IoUringServerSocket serverSocket = new IoUringServerSocket(8080);
        serverSocket.onAccept((ring, socket) -> {
            // queue another accept request for the next client
            ring.queueAccept(serverSocket);

            // set up the read handler and queue a read operation
            socket.onRead(in -> ring.queueWrite(socket, responseBuffer.slice()));
            ring.queueRead(socket, ByteBuffer.allocateDirect(1024));

            // HTTP spec says the server should close when done
            socket.onWrite(out -> socket.close());

            // and some really basic error handling
            socket.onException(ex -> {
                ex.printStackTrace();
                socket.close();
            });
        });

        new IoUring()
            .onException(Exception::printStackTrace)
            .queueAccept(serverSocket) // queue an accept request, onAccept will be called when a socket connects
            .loop(); // process I/O events until interrupted
    }
}
