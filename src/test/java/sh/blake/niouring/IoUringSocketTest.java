package sh.blake.niouring;

import org.junit.Assert;
import org.junit.Test;
import sh.blake.niouring.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class IoUringSocketTest extends TestBase {

    @Test
    public void test_create_server_and_connect_should_succeed() {
        int port = randomPort();

        AtomicBoolean accepted = new AtomicBoolean(false);
        IoUringServerSocket serverSocket = new IoUringServerSocket(port);
        serverSocket.onAccept((ring, socket) -> {
            accepted.set(true);
            serverSocket.close();
        });
        serverSocket.onException(Exception::printStackTrace);

        AtomicBoolean connected = new AtomicBoolean(false);
        IoUringSocket socket = new IoUringSocket("127.0.0.1", port);
        socket.onException(Exception::printStackTrace);
        socket.onConnect(ring -> {
            connected.set(true);
            socket.close();
        });

        IoUring ioUring = new IoUring(TEST_RING_SIZE)
            .onException(Exception::printStackTrace)
            .queueAccept(serverSocket)
            .queueConnect(socket);

        attemptUntil(ioUring::execute, () -> accepted.get() && connected.get());

        ioUring.close();

        Assert.assertTrue("Server accepted connection", accepted.get());
        Assert.assertTrue("Client connected", connected.get());
    }

    @Test
    public void test_create_server_and_connect_with_wrong_port_should_produce_exception() {
        int port = randomPort();

        AtomicBoolean accepted = new AtomicBoolean(false);
        AtomicBoolean exceptionProduced = new AtomicBoolean(false);

        IoUringServerSocket serverSocket = new IoUringServerSocket(port);
        serverSocket.onAccept((ring, socket) -> accepted.set(true));
        serverSocket.onException(Exception::printStackTrace);

        IoUringSocket socket = new IoUringSocket("127.0.0.1", port + 1);
        socket.onException(ex -> exceptionProduced.set(true));

        IoUring ioUring = new IoUring(TEST_RING_SIZE)
            .onException(Exception::printStackTrace)
            .queueAccept(serverSocket)
            .queueConnect(socket);

        attemptUntil(ioUring::execute, exceptionProduced::get);

        ioUring.close();
        serverSocket.close();
        socket.close();

        Assert.assertFalse("Server accepted connection", accepted.get());
        Assert.assertTrue("Client to connect (wrong port)", exceptionProduced.get());
    }

    @Test
    public void test_create_server_connect_and_send_data_should_be_received() {
        int port = randomPort();
        String message = "Test over port " + randomPort();

        AtomicBoolean serverAccepted = new AtomicBoolean(false);
        AtomicBoolean serverSent = new AtomicBoolean(false);
        AtomicBoolean clientConnected = new AtomicBoolean(false);
        AtomicBoolean clientReceived = new AtomicBoolean(false);

        IoUringServerSocket serverSocket = new IoUringServerSocket(port);
        serverSocket.onException(Exception::printStackTrace);
        serverSocket.onAccept((ring, socket) -> {
            ByteBuffer testBuffer = ByteBufferUtil.wrapDirect(message);
            socket.onWrite(out -> socket.close());
            ring.queueWrite(socket, testBuffer);
            serverAccepted.set(true);
            serverSent.set(true);
        });

        IoUringSocket socket = new IoUringSocket("127.0.0.1", port);
        socket.onException(Exception::printStackTrace);
        socket.onConnect(ring -> {
            ring.queueRead(socket, ByteBuffer.allocateDirect(32));
            clientConnected.set(true);
        });
        socket.onRead(in -> {
            in.flip();
            String payload = StandardCharsets.UTF_8.decode(in).toString();
            if (payload.equals(message)) {
                clientReceived.set(true);
            }
            socket.close();
        });

        IoUring ioUring = new IoUring(TEST_RING_SIZE)
            .onException(Exception::printStackTrace)
            .queueAccept(serverSocket)
            .queueConnect(socket);

        attemptUntil(ioUring::execute, () ->
            serverAccepted.get() && clientConnected.get() && serverSent.get() && clientReceived.get());

        ioUring.close();

        Assert.assertTrue("Server accepted connection", serverAccepted.get());
        Assert.assertTrue("Server sent data", serverSent.get());
        Assert.assertTrue("Client connected", clientConnected.get());
        Assert.assertTrue("Client received data", clientReceived.get());
    }
}
