/* Original narrow JNI adapter; kernel is Telegram's calcCDT. GPL-2.0-or-later. */
package dev.oritwig.telegram.photo;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

public final class NativeEnhancement {
    public static final int OUTPUT_BYTES = 16 * 256 * 4;
    public static final int WORK_BYTES = 16 * 2 * 4 * 257;
    private static final AtomicLong completed = new AtomicLong();
    static { System.loadLibrary("telegram_photo_enhancement"); }
    private NativeEnhancement() {}
    public static void calcCDT(ByteBuffer hsv, int width, int height, ByteBuffer output, ByteBuffer work) {
        if (hsv == null || output == null || work == null || output.isReadOnly() || work.isReadOnly())
            throw new IllegalArgumentException("Writable output/work buffers are required");
        calculate(hsv, width, height, output, work);
        completed.incrementAndGet();
    }
    public static long completedCalls() { return completed.get(); }
    private static native void calculate(ByteBuffer hsv, int width, int height, ByteBuffer output, ByteBuffer work);
}
