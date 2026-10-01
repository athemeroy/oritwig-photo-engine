/* Tiny offline extraction proof; intentionally not a complete photo editor. */
package dev.oritwig.photoproof;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.oritwig.telegram.photo.PhotoEngine;
import dev.oritwig.telegram.photo.SavedFilterState;
import java.io.File;
import java.io.FileOutputStream;

public final class ProofActivity extends Activity {
    private PhotoEngine engine;
    private Bitmap shown;
    private TextView status;
    private ImageView preview;
    private Button original, enhanced;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(24, 48, 24, 24);
        TextView title = new TextView(this); title.setText("Telegram photo engine extraction proof"); title.setTextSize(22);
        layout.addView(title);
        status = new TextView(this); status.setText("Offline synthetic fixture. This is a test host, not a finished editor.");
        layout.addView(status);
        preview = new ImageView(this); preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        layout.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        original = new Button(this); original.setText("Render original"); layout.addView(original);
        enhanced = new Button(this); enhanced.setText("Render enhanced + curves"); layout.addView(enhanced);
        setContentView(layout);
        Bitmap fixture = fixture(320, 240, false);
        engine = new PhotoEngine(fixture); fixture.recycle();
        original.setOnClickListener(v -> render(false)); enhanced.setOnClickListener(v -> render(true));
        render(false);
    }
    private void render(boolean changed) {
        original.setEnabled(false); enhanced.setEnabled(false); status.setText("Rendering through extracted Telegram GLES and calcCDT…");
        SavedFilterState state = new SavedFilterState(); state.enhanceValue = 70; state.exposureValue = 15;
        state.curvesToolValue.redCurve.midtonesLevel = 65;
        (changed ? engine.render(state) : engine.renderOriginal()).whenComplete((bitmap, error) -> {
            File exported = new File(getFilesDir(), changed ? "enhanced.png" : "original.png");
            if (error == null) {
                try (FileOutputStream stream = new FileOutputStream(exported)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new IllegalStateException("PNG encoder failed");
                } catch (Exception failure) { error = failure; }
            }
            final Throwable problem = error;
            runOnUiThread(() -> {
                if (isDestroyed()) { if (bitmap != null) bitmap.recycle(); return; }
                if (problem != null) status.setText("Proof failed: " + problem);
                else { Bitmap old = shown; shown = bitmap; preview.setImageBitmap(bitmap); if (old != null) old.recycle(); status.setText((changed ? "Adjusted" : "Original") + " · " + bitmap.getWidth() + "×" + bitmap.getHeight() + " · exported private PNG"); }
                original.setEnabled(true); enhanced.setEnabled(true);
            });
        });
    }
    @Override public void onDestroy() { if (engine != null) engine.close(); super.onDestroy(); }
    public static Bitmap fixture(int width, int height, boolean alpha) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];
        for (int y=0; y<height; y++) for(int x=0; x<width; x++) {
            int r=24 + 200*x/(width-1), g=20+210*y/(height-1), b=((x/8+y/8)%2==0 ? 50 : 190);
            if(x<width/8 && y<height/8) {r=235;g=30;b=20;}
            if(x>width*7/8 && y>height*7/8) {r=20;g=35;b=230;}
            pixels[y*width+x]=Color.argb(alpha ? (x<width/2 ? 64 : 192):255,r,g,b);
        }
        bitmap.setPixels(pixels,0,width,0,0,width,height); return bitmap;
    }
}
