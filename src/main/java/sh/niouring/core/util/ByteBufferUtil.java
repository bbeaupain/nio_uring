package sh.niouring.core.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Utility methods for byte buffers.
 */
public class ByteBufferUtil {

    /**
     * Wrap a direct byte buffer.
     *
     * @param data the data
     * @return the byte buffer
     */
    public static ByteBuffer wrapDirect(byte[] data) {
        return (ByteBuffer) ByteBuffer.allocateDirect(data.length).put(data).flip();
    }

    /**
     * Wrap direct byte buffer.
     *
     * @param utf8 the utf8 string
     * @return the byte buffer
     */
    public static ByteBuffer wrapDirect(String utf8) {
        byte[] data = utf8.getBytes(StandardCharsets.UTF_8);
        return (ByteBuffer) ByteBuffer.allocateDirect(data.length).put(data).flip();
    }

    /**
     * Copies a non-direct buffer to a new direct byte buffer, returning the argued buffer immediately if it is already
     * direct.
     *
     * @param buffer the buffer
     * @return the direct byte buffer
     */
    public static ByteBuffer wrapDirect(ByteBuffer buffer) {
        if (buffer.isDirect()) {
            return buffer;
        }
        buffer.flip();
        return (ByteBuffer) ByteBuffer.allocateDirect(buffer.remaining()).put(buffer).flip();
    }
}
