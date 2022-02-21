package sh.niouring.core.util;

public class OsVersionCheck {
    private static final String OS = "Linux";
    private static final int MAJOR_VERSION = 5;
    private static final int MINOR_VERSION = 1;

    public static void verifySystemRequirements() {
        String os = System.getProperty("os.name");
        if (!os.contains(OS)) {
            failed();
        }

        String version = System.getProperty("os.version");
        String[] versionTokens = version.split("\\.");

        int versionMajor = Integer.parseInt(versionTokens[0]);
        if (versionMajor < MAJOR_VERSION) {
            failed();
        }

        int versionMinor = Integer.parseInt(versionTokens[1]);
        if (versionMinor < MINOR_VERSION && versionMajor == MAJOR_VERSION) {
            failed();
        }
    }

    private static void failed() {
        throw new RuntimeException("Linux >= 5.1 required");
    }
}
