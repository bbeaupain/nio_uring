package sh.blake.niouring;

import java.util.Random;
import java.util.function.Supplier;

public class TestBase {
    public static final int TEST_RING_SIZE = 8;
    public static final int MAX_ATTEMPTS = 10;

    public int randomPort() {
        return new Random(System.currentTimeMillis()).nextInt(64510) + 1024;
    }

    void attemptUntil(Runnable runnable, Supplier<Boolean> condition) {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            if (condition.get()) {
                return;
            }
            runnable.run();
        }
    }
}
