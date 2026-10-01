package dev.oritwig.photoproof;

import android.app.Instrumentation;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.os.Bundle;
import dev.oritwig.telegram.photo.NativeEnhancement;
import dev.oritwig.telegram.photo.PhotoEngine;
import dev.oritwig.telegram.photo.SavedFilterState;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Framework-only instrumentation: actual EGL/GLES/JNI, not mocked Android. */
public final class ProofInstrumentation extends Instrumentation {
    private int passed;
    private final JSONArray observations = new JSONArray();
    private File output;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            output = new File(getTargetContext().getFilesDir(), "proof-results");
            if (!output.isDirectory() && !output.mkdirs()) throw new IllegalStateException("Cannot create output directory");
            permissions(); nativeKernel(); renders(); lifecycle(); surfaces();
            JSONObject report = new JSONObject().put("passed", passed).put("runtime_api", android.os.Build.VERSION.SDK_INT)
                .put("native_calls", NativeEnhancement.completedCalls()).put("observations", observations);
            try (FileOutputStream stream = new FileOutputStream(new File(output, "results.json"))) { stream.write(report.toString(2).getBytes("UTF-8")); }
            result.putInt("passed", passed); result.putString("stream", report.toString(2)); finish(-1, result);
        } catch (Throwable error) {
            result.putInt("passed_before_failure", passed); result.putString("stream", android.util.Log.getStackTraceString(error)); finish(0, result);
        }
    }
    private void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label); passed++;
    }
    private void permissions() throws Exception {
        PackageInfo info = getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(), PackageManager.GET_PERMISSIONS);
        String[] permissions = info.requestedPermissions;
        check(permissions == null || permissions.length == 0, "Host merged manifest has zero permissions");
        observations.put("Fresh offline host contains no permissions or account configuration");
    }
    private interface Action { void run(); }
    private void rejects(Action action, String label) {
        try { action.run(); throw new AssertionError(label); } catch (IllegalArgumentException expected) { passed++; }
    }
    private void rejectsUnsupported(Action action, String label) {
        try {action.run();throw new AssertionError(label);}catch(UnsupportedOperationException expected){passed++;}
    }
    private void nativeKernel() throws Exception {
        ByteBuffer hsv = ByteBuffer.allocateDirect(16*16*4);
        for(int i=0;i<16*16;i++) { hsv.put((byte)10).put((byte)150).put((byte)(i%256)).put((byte)255); }
        ByteBuffer out = ByteBuffer.allocateDirect(NativeEnhancement.OUTPUT_BYTES);
        ByteBuffer work = ByteBuffer.allocateDirect(NativeEnhancement.WORK_BYTES);
        long before=NativeEnhancement.completedCalls(); NativeEnhancement.calcCDT(hsv,16,16,out,work);
        check(NativeEnhancement.completedCalls()==before+1,"Real JNI call completed");
        byte[] first=new byte[out.capacity()]; out.position(0); out.get(first);
        for(int i=0;i<16*256;i++) check((first[i*4+3]&255)==255,"CDT alpha byte");
        NativeEnhancement.calcCDT(hsv,16,16,out,work); byte[] second=new byte[out.capacity()];out.position(0);out.get(second);
        check(Arrays.equals(first,second),"CDT deterministic same input");
        ByteBuffer guarded=ByteBuffer.allocateDirect(NativeEnhancement.OUTPUT_BYTES+16);
        for(int i=0;i<guarded.capacity();i++) guarded.put(i,(byte)0x5a);
        guarded.position(8); guarded.limit(8+NativeEnhancement.OUTPUT_BYTES);
        NativeEnhancement.calcCDT(hsv,16,16,guarded.slice(),work);
        guarded.clear(); for(int i=0;i<8;i++) {check(guarded.get(i)==0x5a,"Native prefix guard");check(guarded.get(guarded.capacity()-1-i)==0x5a,"Native suffix guard");}
        rejects(()->NativeEnhancement.calcCDT(hsv,3,16,out,work),"Tiny native dimensions rejected");
        rejects(()->NativeEnhancement.calcCDT(hsv,-1,16,out,work),"Negative native dimensions rejected");
        rejects(()->NativeEnhancement.calcCDT(hsv,Integer.MAX_VALUE,Integer.MAX_VALUE,out,work),"Overflow dimensions rejected");
        rejects(()->NativeEnhancement.calcCDT(ByteBuffer.allocate(1024),16,16,out,work),"Heap input rejected");
        rejects(()->NativeEnhancement.calcCDT(hsv,16,16,ByteBuffer.allocateDirect(5),work),"Short output rejected");
        rejects(()->NativeEnhancement.calcCDT(hsv,16,16,out,ByteBuffer.allocateDirect(5)),"Short work rejected");
        rejects(()->NativeEnhancement.calcCDT(hsv,16,16,out.asReadOnlyBuffer(),work),"Read-only output rejected");
        rejects(()->NativeEnhancement.calcCDT(null,16,16,out,work),"Null input rejected");
        ByteBuffer unaligned=ByteBuffer.allocateDirect(NativeEnhancement.WORK_BYTES+1);unaligned.position(1);
        rejects(()->NativeEnhancement.calcCDT(hsv,16,16,out,unaligned.slice()),"Unaligned work rejected");
        observations.put("Native calcCDT: deterministic pixels, 16x256 output entries, capacity/alignment checks and untouched guard regions");
    }
    private Bitmap await(CompletableFuture<Bitmap> future) throws Exception { return future.get(45,TimeUnit.SECONDS); }
    private void closed(PhotoEngine engine) throws Exception { engine.closeAsync().get(30,TimeUnit.SECONDS); }
    private static int[] pixels(Bitmap b) { int[] p=new int[b.getWidth()*b.getHeight()]; b.getPixels(p,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());return p; }
    private JSONObject compare(Bitmap expected, Bitmap actual) throws Exception {
        check(actual.getWidth()==expected.getWidth() && actual.getHeight()==expected.getHeight(),"Output dimensions match");
        long sum=0;int max=0,changed=0;int[] a=pixels(expected),b=pixels(actual);
        for(int i=0;i<a.length;i++) { if(a[i]!=b[i]) changed++;for(int shift:new int[]{0,8,16}) {int d=Math.abs(((a[i]>>shift)&255)-((b[i]>>shift)&255));sum+=d;max=Math.max(max,d);} }
        return new JSONObject().put("mean_rgb_error",sum/(double)(a.length*3)).put("max_rgb_error",max).put("changed_pixels",changed);
    }
    private void save(Bitmap b,String name) throws Exception {
        File f=new File(output,name+".png");try(FileOutputStream s=new FileOutputStream(f)) {check(b.compress(Bitmap.CompressFormat.PNG,100,s),"PNG encoded "+name);}
        Bitmap reopened=BitmapFactory.decodeFile(f.getAbsolutePath());check(reopened!=null,"PNG reopened "+name);
        check(Arrays.equals(pixels(b),pixels(reopened)),"PNG pixels preserved "+name);reopened.recycle();
    }
    private void renders() throws Exception {
        Bitmap source=ProofActivity.fixture(128,96,false);int[] immutable=pixels(source);save(source,"source");
        PhotoEngine engine=new PhotoEngine(source);
        try {
            long before=NativeEnhancement.completedCalls();Bitmap original=await(engine.renderOriginal());save(original,"original");
            check(NativeEnhancement.completedCalls()>before,"GLES enhancement pass reached native calcCDT");
            JSONObject identity=compare(source,original);observations.put(new JSONObject().put("original",identity));
            check(identity.getDouble("mean_rgb_error")<=3,"Identity mean RGB tolerance <=3");
            check(identity.getInt("max_rgb_error")<=12,"Identity max RGB tolerance <=12");
            Bitmap baseline=await(engine.render(new SavedFilterState())); save(baseline,"baseline");
            String[] names={"enhance","exposure","contrast","saturation","curves","sharpen","radial-blur","linear-blur"};
            for(String name:names) {
                SavedFilterState state=new SavedFilterState();
                switch(name) {
                    case "enhance":state.enhanceValue=100;break;
                    case "exposure":state.exposureValue=50;break;
                    case "contrast":state.contrastValue=100;break;
                    case "saturation":state.saturationValue=-100;break;
                    case "curves":state.curvesToolValue.luminanceCurve.midtonesLevel=70;state.curvesToolValue.redCurve.shadowsLevel=40;break;
                    case "sharpen":state.sharpenValue=100;break;
                    default:state.blurType=name.equals("radial-blur")?1:2;state.blurExcludePoint=new PointF(.5f,.5f);state.blurExcludeSize=.12f;state.blurExcludeBlurSize=.12f;state.blurAngle=.6f;
                }
                Bitmap changed=await(engine.render(state));JSONObject metrics=compare(baseline,changed);observations.put(new JSONObject().put(name,metrics));
                check(metrics.getInt("changed_pixels")>source.getWidth()*source.getHeight()/20,name+" changes significant pixels");
                save(changed,name);changed.recycle();
            }
            Bitmap repeated=await(engine.renderOriginal());check(Arrays.equals(pixels(original),pixels(repeated)),"Repeated original export deterministic after all adjustments");
            check(Arrays.equals(immutable,pixels(source)),"Caller input pixels unchanged");repeated.recycle();baseline.recycle();original.recycle();
        } finally { closed(engine); }
        for(int rotation:new int[]{90,180,270}) {
            PhotoEngine rotated=new PhotoEngine(source,rotation,2048);
            try {Bitmap result=await(rotated.renderOriginal());Matrix matrix=new Matrix();matrix.postRotate(rotation);Bitmap expected=Bitmap.createBitmap(source,0,0,128,96,matrix,true);
                JSONObject metrics=compare(expected,result);observations.put(new JSONObject().put("rotation"+rotation,metrics));check(metrics.getDouble("mean_rgb_error")<=3,"Rotation orientation correct");save(result,"rotation-"+rotation);expected.recycle();result.recycle();}
            finally {closed(rotated);}
        }
        Bitmap alpha=ProofActivity.fixture(128,96,true);PhotoEngine translucent=new PhotoEngine(alpha);
        try {
            Bitmap result=await(translucent.renderOriginal());save(result,"alpha-original");
            int[] inputPixels=pixels(alpha), resultPixels=pixels(result);
            for(int i=0;i<inputPixels.length;i++)check(Color.alpha(resultPixels[i])==Color.alpha(inputPixels[i]),"Original path preserves alpha");
            result.recycle();SavedFilterState unsupported=new SavedFilterState();unsupported.softenSkinValue=100;
            rejectsUnsupported(()->translucent.render(unsupported),"Smoothing is explicitly unsupported");
            observations.put("Original path preserves alpha; optional smoothing is absent and nonzero input is explicitly rejected.");
        } finally{closed(translucent);alpha.recycle();}
        Bitmap large=ProofActivity.fixture(512,384,false);PhotoEngine scaled=new PhotoEngine(large,0,128);
        try{Bitmap result=await(scaled.renderOriginal());check(result.getWidth()==128&&result.getHeight()==96,"Injected maximum side follows upstream scaling");save(result,"scaled");result.recycle();}finally{closed(scaled);large.recycle();}
        source.recycle();
    }
    private void lifecycle() throws Exception {
        Bitmap source=ProofActivity.fixture(128,96,false);
        for(int iteration=0;iteration<3;iteration++){
            PhotoEngine engine=new PhotoEngine(source);Bitmap result=await(engine.renderOriginal());result.recycle();
            CompletableFuture<Bitmap> cancelled=engine.renderOriginal();cancelled.cancel(false);
            CompletableFuture<Bitmap> queued=engine.renderOriginal();closed(engine);
            check(cancelled.isDone(),"Cancelled request terminal");check(queued.isDone(),"Queued request terminal on close");
            if(!queued.isCancelled()&&!queued.isCompletedExceptionally())queued.get().recycle();
            check(engine.renderOriginal().isCompletedExceptionally(),"Post-close export rejected");
            closed(engine);
        }
        Thread.sleep(100);
        for(Thread thread:Thread.getAllStackTraces().keySet())check(!thread.isAlive()||!thread.getName().equals("PhotoFilterGLThread"),"No retained EGL thread");
        rejects(()->new PhotoEngine(Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888)),"Tiny image rejected before native pipeline");
        rejects(()->new PhotoEngine(source,45,2048),"Unsupported rotation rejected");
        rejects(()->new PhotoEngine(Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888)),"Degenerate tiny histogram image rejected");
        Bitmap minimum=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);minimum.eraseColor(0xff4488aa);PhotoEngine minimumEngine=new PhotoEngine(minimum);
        try {Bitmap result=await(minimumEngine.renderOriginal());JSONObject metrics=compare(minimum,result);check(metrics.getInt("max_rgb_error")<=3,"Minimum accepted image original color retained");result.recycle();}
        finally {closed(minimumEngine);minimum.recycle();}
        Bitmap tinySkin=ProofActivity.fixture(64,48,true);PhotoEngine small=new PhotoEngine(tinySkin);
        try {SavedFilterState smooth=new SavedFilterState();smooth.softenSkinValue=100;
            rejectsUnsupported(()->small.render(smooth),"Removed smoothing rejected even on small images");
            Bitmap original=await(small.renderOriginal());check(original.getWidth()==64,"Small image original rendering remains supported");original.recycle();
        } finally {closed(small);tinySkin.recycle();}
        source.recycle();
        observations.put("Repeated session creation/export/cancellation/close and rejected post-close calls completed without retained EGL threads");
    }
    private void surfaces() throws Exception {
        for(int attempt=0;attempt<2;attempt++) {
            android.content.Intent intent=new android.content.Intent(getTargetContext(),SurfaceProofActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            SurfaceProofActivity activity=(SurfaceProofActivity)startActivitySync(intent);
            check(activity.ready.await(45,TimeUnit.SECONDS),"Real window surface became ready");
            Bitmap result=activity.renderer.getTexture();check(result!=null,"Real window surface exported bitmap");
            JSONObject metrics=compare(activity.source,result);check(metrics.getDouble("mean_rgb_error")<=3,"Window export matches source orientation");result.recycle();
            runOnMainSync(activity::finish);
            check(activity.detached.await(30,TimeUnit.SECONDS),"TextureView destruction delivered");
            activity.renderer.join(30000);check(!activity.renderer.isAlive(),"Destroyed window surface released GL thread");
            activity.source.recycle();
        }
        observations.put("Two real TextureView create/render/export/destroy cycles passed");
    }

}
