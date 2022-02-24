package sh.blake.niouring;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class IoUringFileTest extends TestBase {

    @Test
    public void open_and_read_readme_should_succeed() {
        String fileName = "README.md";
        AtomicBoolean readSuccessfully = new AtomicBoolean(false);

        IoUringFile file = new IoUringFile(fileName);
        file.onRead(in -> {
            in.flip();
            String fileStr = StandardCharsets.UTF_8.decode(in).toString();
            if (fileStr.startsWith("# nio_uring")) {
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
}
