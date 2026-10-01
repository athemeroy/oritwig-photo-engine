package dev.oritwig.aarproof;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.Bundle;
import dev.oritwig.telegram.photo.PhotoEngine;
import dev.oritwig.telegram.photo.SavedFilterState;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;
public final class ConsumerProof extends Instrumentation {
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle output=new Bundle();try{
  Bitmap input=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);input.eraseColor(0xff445566);
  PhotoEngine engine=new PhotoEngine(input);
  try{
   Bitmap original=engine.renderOriginal().get(45,TimeUnit.SECONDS);SavedFilterState state=new SavedFilterState();state.exposureValue=75;
   Bitmap adjusted=engine.render(state).get(45,TimeUnit.SECONDS);
   if(original.getWidth()!=64||adjusted.getHeight()!=64||original.getPixel(32,32)==adjusted.getPixel(32,32))throw new AssertionError("AAR did not render changed pixels");
   try(FileOutputStream file=new FileOutputStream(new File(getTargetContext().getFilesDir(),"independent-export.png"))){if(!adjusted.compress(Bitmap.CompressFormat.PNG,100,file))throw new AssertionError("Export failed");}
   if(input.getPixel(32,32)!=0xff445566)throw new AssertionError("Source was modified");
   original.recycle();adjusted.recycle();
  }finally{engine.closeAsync().get(30,TimeUnit.SECONDS);input.recycle();}
  output.putInt("passed",4);output.putString("stream","Independent copied AAR rendered original and changed pixels, encoded PNG, preserved input and closed successfully");finish(-1,output);
 }catch(Throwable error){output.putString("stream",android.util.Log.getStackTraceString(error));finish(0,output);}}
}
