package sh.blake.niouring.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class NativeLibraryLoader {
    private static boolean loadAttempted = false;

    public static synchronized void load() {
        OsVersionCheck.verifySystemRequirements();
        try {
            if (loadAttempted) {
                return;
            }
            loadAttempted = true;

            try (InputStream inputStream = NativeLibraryLoader.class.getResourceAsStream("/libnio_uring.so")) {
                if (inputStream == null) {
                    throw new IOException("Native library not found");
                }
                File tempFile = File.createTempFile("libnio_uring", ".tmp");

                byte[] buffer = new byte[8192];
                try (FileOutputStream fileOutputStream = new FileOutputStream(tempFile)) {
                    while (inputStream.available() > 0) {
                        int bytesRead = inputStream.read(buffer);
                        fileOutputStream.write(buffer, 0, bytesRead);
                    }
                }

                System.load(tempFile.getAbsolutePath());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            System.loadLibrary("nio_uring");
        }
    }
}
