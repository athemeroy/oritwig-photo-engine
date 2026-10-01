package dev.oritwig.aarproof;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
public final class ConsumerActivity extends Activity {
 @Override public void onCreate(Bundle saved) {super.onCreate(saved);TextView text=new TextView(this);text.setText("Independent AAR integration proof. Run ConsumerProof instrumentation for real offline render/export checks.");text.setTextSize(20);setContentView(text);}
}
