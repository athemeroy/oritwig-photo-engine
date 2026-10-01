package dev.oritwig.photoproof;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.TextureView;
import dev.oritwig.telegram.photo.FilterGLThread;
import java.util.concurrent.CountDownLatch;

/** Instrumentation-only real window-surface lifecycle host. */
public final class SurfaceProofActivity extends Activity implements TextureView.SurfaceTextureListener {
    public final CountDownLatch ready = new CountDownLatch(1);
    public final CountDownLatch detached = new CountDownLatch(1);
    public volatile FilterGLThread renderer;
    public Bitmap source;
    private TextureView texture;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); source=ProofActivity.fixture(128,96,false);
        texture=new TextureView(this);texture.setSurfaceTextureListener(this);setContentView(texture);
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture surface,int width,int height) {
        renderer=new FilterGLThread(surface,source,0,false,2048,true,width,height);
        new Thread(() -> {
            renderer.setFilterGLThreadDelegate(null);
            renderer.requestRender(true,true,false);ready.countDown();
        }, "SurfaceProofSetup").start();
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface,int width,int height) { if(renderer!=null)renderer.setSurfaceTextureSize(width,height); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { if(renderer!=null)renderer.shutdown();detached.countDown();return false; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
}
