package sh.blake.niouring.examples;

import sh.blake.niouring.IoUring;
import sh.blake.niouring.IoUringServerSocket;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ParallelHttpEchoTest {
    public static void main(String[] args) {
        int rings = Integer.parseInt(args[0]);
        int ringSize = Integer.parseInt(args[1]);

        IoUringServerSocket serverSocket = IoUringServerSocket.bind(8080)
            .onAccept(HttpEchoTest::echoHandler);

        ExecutorService threadPool = Executors.newFixedThreadPool(rings);
        for (int i = 0; i < rings; i++) {
            IoUring ring = new IoUring(ringSize)
                .onException(Exception::printStackTrace)
                .queueAccept(serverSocket);

            threadPool.execute(ring::loop);
        }
    }
}
