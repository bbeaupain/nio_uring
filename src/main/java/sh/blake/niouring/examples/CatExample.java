package sh.blake.niouring.examples;

import sh.blake.niouring.IoUring;
import sh.blake.niouring.IoUringFile;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class CatExample {
    public static void main(String[] args) {
        IoUringFile file = new IoUringFile(args[0]);
        file.onRead(in -> {
            System.out.println(StandardCharsets.UTF_8.decode(in));
            file.close();
        });
        new IoUring()
            .queueRead(file, ByteBuffer.allocateDirect(8192))
            .execute(); // process at least one I/O event (blocking until complete)
    }
}
