#!/usr/bin/env python3
"""Reproducible source extraction, never an image-algorithm reimplementation."""
from pathlib import Path
import os
import re, textwrap, difflib, json, hashlib
R=Path(__file__).resolve().parents[1]
U=Path(os.environ.get('TELEGRAM_REFERENCE_DIR',str(R.parent/'oritwig-photo-private-reference/upstream')))
if not U.is_dir(): raise SystemExit('Set TELEGRAM_REFERENCE_DIR to the external exact files in provenance/upstream-files.json')
if U.resolve().is_relative_to(R.resolve()): raise SystemExit('Keep full excluded reference files outside the distributable tree')
(U.parent/'patches').mkdir(parents=True,exist_ok=True)
D=R/'engine/src/main/java/dev/oritwig/telegram/photo'
PREFIX='TMessagesProj/src/main/java/org/telegram/'
PKG='dev.oritwig.telegram.photo'

def upstream(path): return (U/path).read_text()
def block(s,needle):
 a=s.index(needle); b=s.index('{',a); depth=0; quote=None; esc=False; comment=None; i=b
 while i<len(s):
  c=s[i]; n=s[i:i+2]
  if comment=='line':
   if c=='\n': comment=None
  elif comment=='block':
   if n=='*/': comment=None; i+=1
  elif quote:
   if esc: esc=False
   elif c=='\\': esc=True
   elif c==quote: quote=None
  elif n=='//': comment='line'; i+=1
  elif n=='/*': comment='block'; i+=1
  elif c in '\"\'': quote=c
  elif c=='{': depth+=1
  elif c=='}':
   depth-=1
   if depth==0: return a,i+1
  i+=1
 raise ValueError(needle)
def remove(s,needle):
 a,b=block(s,needle); return s[:a]+s[b:]
def replace(s,needle,new):
 a,b=block(s,needle); return s[:a]+new+s[b:]
def imports(s):
 s=re.sub(r'import org\.telegram\.[^;]+;\n','',s)
 return re.sub(r'package org\.telegram\.[^;]+;',f'package {PKG};',s)
def write(name,s,original,reason):
 p=D/name; p.write_text(s)
 diff=''.join(difflib.unified_diff(original.splitlines(True),s.splitlines(True),fromfile='upstream/'+source_paths[name],tofile='engine/'+name))
 (U.parent/'patches'/f'{name}.patch').write_text(diff)
 records.append({'output':str(p.relative_to(R)),'upstream_path':source_paths[name],'reason':reason,'sha256':hashlib.sha256(s.encode()).hexdigest()})
source_paths = {
 'FilterShaders.java':PREFIX+'ui/Components/FilterShaders.java',
 'FilterGLThread.java':PREFIX+'ui/Components/FilterGLThread.java',
 'DispatchQueue.java':PREFIX+'messenger/DispatchQueue.java',
 'CurvesValue.java':PREFIX+'ui/Components/PhotoFilterView.java',
 'CurvesToolValue.java':PREFIX+'ui/Components/PhotoFilterView.java',
 'SavedFilterState.java':PREFIX+'messenger/MediaController.java'}
records=[]
# GLSL and image operations remain upstream; unavailable feature inputs are removed.
p=PREFIX+'ui/Components/FilterShaders.java'; orig=s=upstream(p)
s=imports(s)
s=s.replace('private boolean isVideo;\n    private StoryEntry.HDRInfo hdrInfo;', 'private final boolean isVideo = false;\n    private final int maximumPhotoSize;')
s=s.replace('public FilterShaders(boolean video, StoryEntry.HDRInfo hdrInfo) {\n        isVideo = video;\n        this.hdrInfo = hdrInfo;', 'public FilterShaders(int maximumPhotoSize) {\n        this.maximumPhotoSize = maximumPhotoSize;')
s=remove(s,'    public void updateHDRInfo(')
a=s.index('        String hdrProcessor = "";'); b=s.index('        String extension',a)
s=s[:a]+'        // Explicit SDR-only extraction. No HDR metadata/resource dependency.\n        String hdrProcessor = "";\n        int hdrType = 0;\n'+s[b:]
s=s.replace('MediaController.SavedFilterState','SavedFilterState').replace('AndroidUtilities.getPhotoSize(true)','maximumPhotoSize')
s=s.replace('BuildVars.LOGS_ENABLED','EngineLog.ENABLED').replace('FileLog.e','EngineLog.e').replace('Utilities.calcCDT','NativeEnhancement.calcCDT')
s=s.replace('    private void loadTexture(Bitmap bitmap, int orientation, int w, int h) {', '    private void loadTexture(Bitmap bitmap, int orientation, int w, int h) {\n        Bitmap inputBitmap = bitmap;')
s=s.replace('            GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);', '            GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);\n            if (bitmap != inputBitmap) bitmap.recycle(); // Only the owned transformed temporary')
s=remove(s,'    public void onVideoFrameUpdate(')
# Conservative publication boundary: remove optional smoothing and its private tone mapper.
a=s.index('    private static final String YUCIHighPassSkinSmoothingMaskBoostFilterFragmentShaderCode')
b=s.index('    private static final String radialBlurFragmentShaderCode',a)
s=s[:a]+s[b:]
s=remove(s,'    private static class ToneCurve')
s=re.sub(r'    (?:public|private) static final String simpleTwoTextureVertex(?:Video)?ShaderCode =.*?;\n\n', '', s, flags=re.S)
s=remove(s,'    public boolean drawSkinSmoothPass(')
s=s.replace('        float getSoftenSkinValue();\n','')
s=re.sub(r'            @Override\s+public float getSoftenSkinValue\(\)\s*\{[^}]*\}\s*', '', s)
# Fields exclusively owned by the removed feature.
s=re.sub(r'^    private (?:boolean|int|float|ToneCurve|BlurProgram) (?:needUpdateSkinTexture|skinTextureCreated|skinBlurProgram|lastRadius|skinPassDrawn|toneCurve|greenAndBlue\w*|highPass\w*|composite\w*|boost\w*)[^;]*;\n','',s,flags=re.M)
a=s.index('        fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, stillImageHighPassFilterFragmentShaderCode);')
b=s.index('        toneCurve = new ToneCurve();',a)+len('        toneCurve = new ToneCurve();')
s=s[:a]+s[b:]
a,b=block(s,'    private boolean setupExternalShaders('); section=s[a:b]
c=section.index('\n        if (isVideo) {');d=section.rindex('\n        return true;')
section=section[:c]+section[d:];s=s[:a]+section+s[b:]
s=s.replace('isVideo && !skinPassDrawn','isVideo').replace('skinPassDrawn ? renderTexture[1] : bitmapTextre[0]','bitmapTextre[0]')
s=s.replace('        needUpdateSkinTexture = true;\n','').replace('import java.util.ArrayList;\n','')
write('FilterShaders.java',s,orig,'SDR-only constructor; injected max image side; logging/native/state relocation. Optional smoothing/private tone mapper and their shader/program/state path removed. Retained shader math is unchanged. Video branches are unreachable; no video input API.')
# DispatchQueue is mature local looper infrastructure, with bounded readiness waits.
p=PREFIX+'messenger/DispatchQueue.java'; orig=s=upstream(p); s=imports(s).replace('FileLog.e','EngineLog.e')
s=s.replace('syncLatch.await();','if (!syncLatch.await(20, java.util.concurrent.TimeUnit.SECONDS)) {\n                throw new IllegalStateException("GL dispatch queue did not initialize");\n            }')
# Do not swallow readiness exceptions then dereference an uninitialized handler.
s=s.replace('EngineLog.e(e, false);','throw new IllegalStateException("GL dispatch queue unavailable", e);')
write('DispatchQueue.java',s,orig,'Package/logging relocation plus bounded queue readiness; no messaging/accounts copied.')
# State data and exact curve generator, no protocol persistence.
p=PREFIX+'ui/Components/PhotoFilterView.java'; photo=upstream(p)
header=photo[:photo.index('package ')]
for cls in ['CurvesValue','CurvesToolValue']:
 a,b=block(photo,'    public static class '+cls)
 s=textwrap.dedent(photo[a:b]).replace('public static class '+cls,'public class '+cls,1)
 s=remove(s,'    public void serializeToStream('); s=remove(s,'    public void readParams(')
 if cls=='CurvesValue': s=s.replace('{','{\n    private final static int curveGranularity = 100;\n    private final static int curveDataStep = 2;',1)
 s=header+f'package {PKG};\n\nimport java.nio.ByteBuffer;\nimport java.nio.ByteOrder;\nimport java.util.ArrayList;\n\n'+s+'\n'
 write(cls+'.java',s,photo,'Exact nested curve class lifted from PhotoFilterView; only package/container and Telegram protocol serialization removed.')
p=PREFIX+'messenger/MediaController.java'; orig=upstream(p)
a,b=block(orig,'    public static class SavedFilterState')
s=textwrap.dedent(orig[a:b]).replace('public static class','public class',1)
s=remove(s,'    public void serializeToStream('); s=remove(s,'    public void readParams(')
s=s.replace('PhotoFilterView.CurvesToolValue','CurvesToolValue')
s=s.replace('    public float softenSkinValue;', '    /** Unsupported compatibility field; nonzero is rejected by PhotoEngine. */\n    @Deprecated public float softenSkinValue;')
s=orig[:orig.index('package ')]+f'package {PKG};\n\nimport android.graphics.PointF;\n\n'+s+'\n'
write('SavedFilterState.java',s,orig,'Exact SavedFilterState data/isEmpty extracted; protocol serialization excluded. No MediaController shell or stub.')
# Retain original EGL setup, pass chain, and bitmap readback. Exclude all optional video/UI blur entry points.
p=PREFIX+'ui/Components/FilterGLThread.java'; orig=s=upstream(p); s=imports(s)
s=s.replace('    private boolean initied;','    private volatile boolean initied;\n    private volatile boolean closed;\n    private volatile Throwable failure;')
s=s.replace('    private boolean isVideo;\n','')
a=s.index('    private SurfaceTexture videoSurfaceTexture;'); b=s.index('    private FilterShaders filterShaders;',a); s=s[:a]+s[b:]
a=s.index('    private int simpleOESShaderProgram;'); b=s.index('    private boolean blurred;',a); s=s[:a]+s[b:]
s=s.replace('    private int videoWidth;\n    private int videoHeight;\n','')
s=remove(s,'    public interface FilterGLThreadVideoDelegate')
s=s.replace('    private FilterGLThreadVideoDelegate videoDelegate;','')
s=s.replace('StoryEntry.HDRInfo hdrInfo, boolean allowBitmapScaling, BlurringShader.BlurManager blurManager,','int maximumPhotoSize, boolean allowBitmapScaling,')
a=s.index('        this.blurManager = blurManager;'); b=s.index('        filterShaders.setScaleBitmap',a)
s=s[:a]+'        filterShaders = new FilterShaders(maximumPhotoSize);\n'+s[b:]
for needle in ['    public FilterGLThread(SurfaceTexture surface, FilterGLThreadVideoDelegate', '    public void updateHDRInfo(', '    private boolean setupVideoShader(', '    public void setVideoSize(', '    private void setRenderData(', '    public Bitmap getUiBlurBitmap(', '    public void updateUiBlurTransform(', '    public void updateUiBlurGradient(', '    public void updateUiBlurManager(']:
 s=remove(s,needle)
s=s.replace('BuildVars.LOGS_ENABLED','EngineLog.ENABLED').replace('FileLog.e','EngineLog.e')
s=s.replace('EGLContext parentContext = blurManager != null ? blurManager.getParentContext() : EGL10.EGL_NO_CONTEXT;','EGLContext parentContext = EGL10.EGL_NO_CONTEXT;')
s=remove(s,'        if (blurManager != null)')
s=s.replace('                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,','                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,\n                EGL10.EGL_SURFACE_TYPE, surfaceTexture == null ? EGL10.EGL_PBUFFER_BIT : EGL10.EGL_WINDOW_BIT,')
s=s.replace('''        } else {
            finish();
            return false;
        }

        if (eglSurface == null''','''        } else {
            // Platform adapter: caller may request a headless offline export session.
            eglSurface = egl10.eglCreatePbufferSurface(eglDisplay, eglConfig,
                new int[] { EGL10.EGL_WIDTH, surfaceWidth, EGL10.EGL_HEIGHT, surfaceHeight, EGL10.EGL_NONE });
        }

        if (eglSurface == null''',1)
s=remove(s,'        if (!setupVideoShader(null))')
a=s.index('        int w;'); b=s.index('        if (!filterShaders.create())',a)
s=s[:a]+'        int w = currentBitmap.getWidth();\n        int h = currentBitmap.getHeight();\n\n'+s[b:]
s=s.replace('filterShaders.setRenderData(currentBitmap, orientation, videoTexture[0], w, h);','filterShaders.setRenderData(currentBitmap, orientation, 0, w, h);')
s=remove(s,'            if (blurManager != null)')
s=s.replace('    public void finish() {\n','    public void finish() {\n        initied = false;\n')
s=s.replace('            surfaceTexture.release();','            surfaceTexture.release();\n            surfaceTexture = null;')
s=remove(s,'        if (updateSurface)')
s=remove(s,'        if (isVideo && filterShaders.drawOriginal())')
s=s.replace('        if (videoDelegate == null || videoFrameAvailable) {','        {')
s=s.replace('''            if (videoDelegate == null) {
                filterShaders.drawSharpenPass();
            }''','''            filterShaders.drawSharpenPass();''')
s=remove(s,'            if (uiBlur != null)')
s=s.replace('    public Bitmap getTexture() {\n','    public Bitmap getTexture() {\n        if (Thread.currentThread() == this) throw new IllegalStateException("Readback must run off the GL thread");\n        if (closed) throw new IllegalStateException("Engine is closed");\n')
s=s.replace('''            if (postRunnable(() -> {
                GLES20.glBindFramebuffer''','''            if (postRunnable(() -> {
                try {
                if (!initied) throw new IllegalStateException("EGL is unavailable", failure);
                makeCurrentContext();
                GLES20.glBindFramebuffer''',1)
s=s.replace('''                countDownLatch.countDown();
                GLES20.glBindFramebuffer''','''                GLES20.glBindFramebuffer''',1)
s=s.replace('''                GLES20.glClear(0);
            })) {
                countDownLatch.await();''','''                GLES20.glClear(0);
                } catch (Throwable error) { failure = error; }
                finally { countDownLatch.countDown(); }
            })) {
                if (!countDownLatch.await(20, java.util.concurrent.TimeUnit.SECONDS))
                    throw new IllegalStateException("GL readback timed out");''',1)
s=s.replace('''        } catch (Exception e) {
            EngineLog.e(e);
        }
        return object[0];''','''        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GL readback interrupted", e);
        }
        if (object[0] == null) throw new IllegalStateException("GL readback failed", failure);
        return object[0];''')
s=s.replace('    public void shutdown() {\n','    public void shutdown() {\n        if (closed) return;\n        closed = true;\n')
s=s.replace('''        initied = initGL();
        super.run();''','''        try { initied = initGL(); }
        catch (Throwable error) { failure = error; finish(); }
        super.run();''')
s=s.replace('''            if (surface) {
                updateSurface = true;
            }
''','')
s=s.replace('''                drawRunnable.run();''','''                try { drawRunnable.run(); }
                catch (Throwable error) { failure = error; initied = false; }''')
s=s.replace('    public void requestRender(final boolean updateBlur, final boolean force, boolean surface) {','    public void requestRender(final boolean updateBlur, final boolean force, boolean surface) {\n        if (surface) throw new IllegalArgumentException("Video input is not part of this extraction");\n        if (closed) throw new IllegalStateException("Engine is closed");')
s=s.replace('            filterShaders.drawSkinSmoothPass();\n','')
write('FilterGLThread.java',s,orig,'Exclude video/HDR/UI blur; real still-image EGL/GLES/readback retained. Add EGL pbuffer platform integration and bounded error/lifecycle handling. SurfaceTexture ownership follows upstream.')
# Native function exact body except JNI symbol relocation plus input validation before it.
orig=upstream('TMessagesProj/jni/image.cpp'); a=orig.index('const uint32_t PGPhotoEnhanceHistogramBins'); _,b=block(orig,'JNIEXPORT void Java_org_telegram_messenger_Utilities_calcCDT')
s=orig[a:b]
s=s.replace('Java_org_telegram_messenger_Utilities_calcCDT','Java_dev_oritwig_telegram_photo_NativeEnhancement_calculate')
validation='''
    // Extraction safety boundary; the original kernel below remains unchanged.
    const jlong pixels = static_cast<jlong>(width) * height;
    if (!hsvBuffer || !buffer || !calcBuffer || width < 4 || height < 4 || width > 16384 || height > 16384 ||
        pixels > 64LL * 1024 * 1024 ||
        env->GetDirectBufferCapacity(hsvBuffer) < pixels * 4 ||
        env->GetDirectBufferCapacity(buffer) < 16 * 256 * 4 ||
        env->GetDirectBufferCapacity(calcBuffer) < 16 * 2 * 4 * 257 ||
        !env->GetDirectBufferAddress(hsvBuffer) || !env->GetDirectBufferAddress(buffer) ||
        !env->GetDirectBufferAddress(calcBuffer) ||
        (reinterpret_cast<uintptr_t>(env->GetDirectBufferAddress(calcBuffer)) % alignof(uint32_t)) != 0) {
        jclass error = env->FindClass("java/lang/IllegalArgumentException");
        env->ThrowNew(error, "calcCDT requires valid dimensions and sufficiently sized direct buffers");
        return;
    }
'''
s=s.replace('    float imageWidth = width;',validation+'    float imageWidth = width;',1)
macros=upstream('TMessagesProj/jni/c_utils.h'); a=macros.index('#ifndef MAX'); b=macros.index('\n\n#endif',a)
s='// Extracted from Telegram Android image.cpp at f2908b14133bbffbf7ab04f641ecb5bfaf533242.\n// GPL-2.0-or-later; see LICENSE and NOTICE.\n#include <jni.h>\n#include <stdint.h>\n#include <string.h>\n#include <math.h>\n'+macros[a:b]+'\n\nextern "C" {\n'+s+'\n}\n'
p=R/'engine/src/main/cpp/enhancement.cpp'; p.write_text(s)
(U.parent/'patches/enhancement.cpp.patch').write_text(''.join(difflib.unified_diff(orig.splitlines(True),s.splitlines(True),fromfile='upstream/image.cpp',tofile='engine/enhancement.cpp')))
records.append({'output':str(p.relative_to(R)),'upstream_path':'TMessagesProj/jni/image.cpp','additional_input':'TMessagesProj/jni/c_utils.h','reason':'Exact calcCDT and constants only; JNI symbol relocation and direct-buffer/dimension safety checks; MIN/MAX macros copied from c_utils.h.','sha256':hashlib.sha256(s.encode()).hexdigest()})
(R/'docs/extraction-manifest.json').write_text(json.dumps({'commit':'f2908b14133bbffbf7ab04f641ecb5bfaf533242','outputs':records},indent=2)+'\n')
patch_records=[{'patch_name':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),
 'distribution':'Private full-context patch; omitted because deletion hunks contain excluded source',
 'adaptation_ledger':'docs/extraction-manifest.json'} for p in sorted((U.parent/'patches').glob('*.patch'))]
(R/'provenance/private-patch-hashes.json').write_text(json.dumps(patch_records,indent=2)+'\n')
print('Extracted',len(records),'files')
