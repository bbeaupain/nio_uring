package sh.blake.niouring.examples;

import sh.blake.niouring.IoUring;
import sh.blake.niouring.IoUringFile;
import sh.blake.niouring.IoUringServerSocket;
import sh.blake.niouring.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class HttpReadmeFileServer {
    public static void main(String[] args) {
        IoUring ioUring = new IoUring().onException(Exception::printStackTrace);

        IoUringFile readmeFile = new IoUringFile("README.md");
        readmeFile.onRead(readmeBuffer -> {
            readmeFile.close();

            ByteBuffer responseLine = ByteBufferUtil.wrapDirect(
                "HTTP/1.1 200 OK\r\n" +
                "Content-Length: " + readmeBuffer.remaining() + "\r\n\r\n"
            );

            IoUringServerSocket serverSocket = new IoUringServerSocket(8080);
            serverSocket.onAccept((ring, socket) -> {
                ring.queueAccept(serverSocket);

                socket.onRead(in -> {
                    ring.queueWrite(socket, responseLine.slice());
                    ring.queueWrite(socket, readmeBuffer.slice());
                });
                ring.queueRead(socket, ByteBuffer.allocateDirect(1024));

                socket.onWrite(out -> socket.close());
                socket.onException(ex -> {
                    ex.printStackTrace();
                    socket.close();
                });
            });

            ioUring.queueAccept(serverSocket);
        });

        ioUring
            .queueRead(readmeFile, ByteBuffer.allocateDirect(8192))
            .loop();
    }
}
