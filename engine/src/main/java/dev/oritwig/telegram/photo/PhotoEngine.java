/* Original account-free lifecycle adapter around extracted Telegram renderer.
 * GPL-2.0-or-later. No replacement image processing is implemented here. */
package dev.oritwig.telegram.photo;

import android.graphics.Bitmap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Offline SDR still-image session. Caller retains source; results belong to caller.
 * All returned futures complete off the Android main thread. Close cancels queued
 * requests and releases EGL; an already-running GPU operation is not preempted.
 * Curves and filter state must not be changed while their render is pending.
 */
public final class PhotoEngine implements AutoCloseable {
    private final Bitmap source;
    private final FilterGLThread renderer;
    private final ExecutorService exports = Executors.newSingleThreadExecutor(r -> new Thread(r, "PhotoExportAdapter"));
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CompletableFuture<Void> closeResult = new CompletableFuture<>();

    public PhotoEngine(Bitmap input) { this(input, 0, 2048); }

    public PhotoEngine(Bitmap input, int orientation, int maximumSide) {
        if (input == null || input.isRecycled() || input.getWidth() < 8 || input.getHeight() < 8)
            throw new IllegalArgumentException("Images must be at least 8 pixels on both sides");
        if (orientation != 0 && orientation != 90 && orientation != 180 && orientation != 270)
            throw new IllegalArgumentException("Orientation must be 0, 90, 180, or 270");
        if (maximumSide < 8 || maximumSide > 4096 || (long) input.getWidth() * input.getHeight() > 32L * 1024 * 1024)
            throw new IllegalArgumentException("Photo proof size limit exceeded");
        double scale = Math.min(1d, (double) maximumSide / Math.max(input.getWidth(), input.getHeight()));
        if (Math.floor(Math.min(input.getWidth(), input.getHeight()) * scale) < 8)
            throw new IllegalArgumentException("Scaled image must be at least 8 pixels on both sides");
        source = input.copy(Bitmap.Config.ARGB_8888, false);
        if (source == null) throw new IllegalStateException("Unable to snapshot source bitmap");
        // Headless EGL pbuffer, otherwise the upstream still-image path.
        renderer = new FilterGLThread(null, source, orientation, false, maximumSide, true, 16, 16);
    }

    /** Original means delegate=null, bypassing adjustments. An all-zero Telegram
     * SavedFilterState includes its upstream 0.11 baseline sharpening. */
    public CompletableFuture<Bitmap> renderOriginal() { return submit(null); }

    public CompletableFuture<Bitmap> render(SavedFilterState state) {
        if (state == null) throw new IllegalArgumentException("Filter state is required");
        if (state.softenSkinValue != 0)
            throw new UnsupportedOperationException("Skin smoothing is excluded from this engine");
        if (state.blurType < 0 || state.blurType > 2 ||
            (state.blurType != 0 && (state.blurExcludePoint == null || state.blurExcludeBlurSize <= 0)))
            throw new IllegalArgumentException("Blur needs a valid type, center and positive transition size");
        return submit(FilterShaders.getFilterShadersDelegate(state));
    }

    private synchronized CompletableFuture<Bitmap> submit(FilterShaders.FilterShadersDelegate delegate) {
        CompletableFuture<Bitmap> result = new CompletableFuture<>();
        if (closed.get()) { result.completeExceptionally(new IllegalStateException("Engine is closed")); return result; }
        exports.execute(() -> {
            if (closed.get() || result.isCancelled()) { result.cancel(false); return; }
            try {
                renderer.setFilterGLThreadDelegate(delegate);
                renderer.requestRender(true, true, false);
                Bitmap bitmap = renderer.getTexture();
                if (bitmap == null) throw new IllegalStateException("EGL initialization or readback failed");
                if (!result.complete(bitmap)) bitmap.recycle();
            } catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result;
    }

    public synchronized CompletableFuture<Void> closeAsync() {
        if (closed.compareAndSet(false, true)) {
            exports.execute(() -> {
                try {
                    renderer.shutdown();
                    renderer.join(TimeUnit.SECONDS.toMillis(25));
                    if (renderer.isAlive()) throw new IllegalStateException("GL shutdown timed out");
                    source.recycle();
                    closeResult.complete(null);
                } catch (Throwable error) { closeResult.completeExceptionally(error); }
            });
            exports.shutdown();
        }
        return closeResult;
    }

    @Override public void close() { closeAsync(); }
}
