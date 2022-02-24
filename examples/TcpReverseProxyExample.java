package sh.blake.niouring.examples;

import sh.blake.niouring.IoUring;
import sh.blake.niouring.IoUringServerSocket;
import sh.blake.niouring.IoUringSocket;

import java.nio.ByteBuffer;

public class TcpReverseProxyExample {
    public static void main(String[] args) {
        String listenAddress = "127.0.0.1"; int fromPort = 8080;
        String forwardAddress = "127.0.0.1"; int toPort = 8000;

        IoUringServerSocket serverSocket = new IoUringServerSocket(listenAddress, fromPort);
        serverSocket.onAccept((ring, listenSocket) -> {
            ring.queueAccept(serverSocket);

            IoUringSocket forwardSocket = new IoUringSocket(forwardAddress, toPort);
            forwardSocket.onConnect(r -> {
                ByteBuffer listenBuffer = ByteBuffer.allocateDirect(1024 * 100);
                ring.queueRead(listenSocket, listenBuffer);
                listenSocket.onRead(in -> ring.queueWrite(forwardSocket, (ByteBuffer) in.flip()));
                listenSocket.onWrite(out -> ring.queueRead(forwardSocket, (ByteBuffer) out.clear()));
                listenSocket.onException(Exception::printStackTrace);
                listenSocket.onClose(forwardSocket::close);

                ByteBuffer forwardBuffer = ByteBuffer.allocateDirect(1024 * 100);
                ring.queueRead(forwardSocket, forwardBuffer);
                forwardSocket.onRead(in -> ring.queueWrite(listenSocket, (ByteBuffer) in.flip()));
                forwardSocket.onWrite(out -> ring.queueRead(listenSocket, (ByteBuffer) out.clear()));
                forwardSocket.onException(Exception::printStackTrace);
                forwardSocket.onClose(listenSocket::close);
            });

            ring.queueConnect(forwardSocket);
        });

        new IoUring()
            .onException(Exception::printStackTrace)
            .queueAccept(serverSocket)
            .loop();
    }
}
