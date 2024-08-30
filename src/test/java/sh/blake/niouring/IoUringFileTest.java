package sh.blake.niouring;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class IoUringFileTest extends TestBase {

    @Test
    public void open_and_read_readme_should_succeed() {
        String fileName = "src/test/resources/test-file.txt";
        AtomicBoolean readSuccessfully = new AtomicBoolean(false);

        IoUringFile file = new IoUringFile(fileName);
        file.onRead(in -> {
            in.flip();
            String fileStr = StandardCharsets.UTF_8.decode(in).toString();
            if (fileStr.startsWith("Hello, world!")) {
                readSuccessfully.set(true);
            }
        });

        ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 10);
        IoUring ioUring = new IoUring(TEST_RING_SIZE)
            .onException(Exception::printStackTrace)
            .queueRead(file, buffer);

        attemptUntil(ioUring::execute, readSuccessfully::get);

        Assert.assertTrue("File read successfully", readSuccessfully.get());
    }

    @Test
    public void seek_read_readme_should_succeed() {
        String fileName = "src/test/resources/test-file.txt";
        AtomicInteger readCounter = new AtomicInteger(0);
        AtomicBoolean seekSuccessfully = new AtomicBoolean(false);
        int chunkSize = 7;
        ByteBuffer buffer = ByteBuffer.allocateDirect(chunkSize);

        IoUring ioUring = new IoUring(TEST_RING_SIZE)
            .onException(Exception::printStackTrace);

        IoUringFile file = new IoUringFile(fileName);
        file.onRead(in -> {
            in.flip();
            int count = readCounter.incrementAndGet();
            String fileStr = StandardCharsets.UTF_8.decode(in).toString();

            if (count == 1) {
                in.clear(); // reuse the buffer provided
                ioUring.queueRead(file, in, chunkSize); // read with offset
            } else if (fileStr.startsWith("world!")) {
                seekSuccessfully.set(true);
            }
        });

        ioUring.queueRead(file, buffer);
        attemptUntil(ioUring::execute, seekSuccessfully::get);

        Assert.assertTrue("File seek successfully", seekSuccessfully.get());
    }
}
