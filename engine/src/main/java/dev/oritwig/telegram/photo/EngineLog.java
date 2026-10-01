/* Original Android logging adapter, GPL-2.0-or-later. */
package dev.oritwig.telegram.photo;

import android.util.Log;

final class EngineLog {
    static final boolean ENABLED = true;
    static void e(String message) { Log.e("TelegramPhotoEngine", message); }
    static void e(Throwable error) { Log.e("TelegramPhotoEngine", "Renderer failure", error); }
    static void e(Throwable error, boolean ignored) { e(error); }
    private EngineLog() {}
}
