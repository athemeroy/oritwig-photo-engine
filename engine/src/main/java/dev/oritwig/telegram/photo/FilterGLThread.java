package dev.oritwig.telegram.photo;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Looper;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

public class FilterGLThread extends DispatchQueue {

    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private static final int EGL_OPENGL_ES2_BIT = 4;
    private SurfaceTexture surfaceTexture;
    private EGL10 egl10;
    private EGLDisplay eglDisplay;
    private EGLContext eglContext;
    private EGLSurface eglSurface;
    private volatile boolean initied;
    private volatile boolean closed;
    private volatile Throwable failure;

    private volatile int surfaceWidth;
    private volatile int surfaceHeight;

    private Bitmap currentBitmap;
    private int orientation;

    private FilterShaders filterShaders;

    private int simpleShaderProgram;
    private int simplePositionHandle;
    private int simpleInputTexCoordHandle;
    private int simpleSourceImageHandle;

    private boolean blurred;
    private int renderBufferWidth;
    private int renderBufferHeight;

    private FloatBuffer textureBuffer;

    private boolean renderDataSet;

    private long lastRenderCallTime;





    public FilterGLThread(SurfaceTexture surface, Bitmap bitmap, int bitmapOrientation, boolean mirror, int maximumPhotoSize, boolean allowBitmapScaling, int w, int h) {
        super("PhotoFilterGLThread", false);
        surfaceTexture = surface;
        surfaceWidth = w;
        surfaceHeight = h;
        currentBitmap = bitmap;
        orientation = bitmapOrientation;
        filterShaders = new FilterShaders(maximumPhotoSize);
        filterShaders.setScaleBitmap(allowBitmapScaling);

        float[] textureCoordinates = {
                0.0f, 0.0f,
                1.0f, 0.0f,
                0.0f, 1.0f,
                1.0f, 1.0f,
        };
        if (mirror) {
            float temp = textureCoordinates[2];
            textureCoordinates[2] = textureCoordinates[0];
            textureCoordinates[0] = temp;

            temp = textureCoordinates[6];
            textureCoordinates[6] = textureCoordinates[4];
            textureCoordinates[4] = temp;
        }

        ByteBuffer bb = ByteBuffer.allocateDirect(textureCoordinates.length * 4);
        bb.order(ByteOrder.nativeOrder());
        textureBuffer = bb.asFloatBuffer();
        textureBuffer.put(textureCoordinates);
        textureBuffer.position(0);

        start();
    }





    public void setFilterGLThreadDelegate(FilterShaders.FilterShadersDelegate filterShadersDelegate) {
        postRunnable(() -> filterShaders.setDelegate(filterShadersDelegate));
    }



    private boolean initGL() {
        egl10 = (EGL10) EGLContext.getEGL();

        eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
            if (EngineLog.ENABLED) {
                EngineLog.e("eglGetDisplay failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
            }
            finish();
            return false;
        }

        int[] version = new int[2];
        if (!egl10.eglInitialize(eglDisplay, version)) {
            if (EngineLog.ENABLED) {
                EngineLog.e("eglInitialize failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
            }
            finish();
            return false;
        }

        int[] configsCount = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        int[] configSpec = new int[] {
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL10.EGL_SURFACE_TYPE, surfaceTexture == null ? EGL10.EGL_PBUFFER_BIT : EGL10.EGL_WINDOW_BIT,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 0,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_NONE
        };
        EGLConfig eglConfig;
        if (!egl10.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
            if (EngineLog.ENABLED) {
                EngineLog.e("eglChooseConfig failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
            }
            finish();
            return false;
        } else if (configsCount[0] > 0) {
            eglConfig = configs[0];
        } else {
            if (EngineLog.ENABLED) {
                EngineLog.e("eglConfig not initialized");
            }
            finish();
            return false;
        }

        int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
        EGLContext parentContext = EGL10.EGL_NO_CONTEXT;
        eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, parentContext, attrib_list);
        if (eglContext == null) {
            if (EngineLog.ENABLED) {
                EngineLog.e("eglCreateContext failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
            }
            finish();
            return false;
        }


        if (surfaceTexture instanceof SurfaceTexture) {
            eglSurface = egl10.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, null);
        } else {
            // Platform adapter: caller may request a headless offline export session.
            eglSurface = egl10.eglCreatePbufferSurface(eglDisplay, eglConfig,
                new int[] { EGL10.EGL_WIDTH, surfaceWidth, EGL10.EGL_HEIGHT, surfaceHeight, EGL10.EGL_NONE });
        }

        if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
            if (EngineLog.ENABLED) {
                EngineLog.e("createWindowSurface failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
            }
            finish();
            return false;
        }
        if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            if (EngineLog.ENABLED) {
                EngineLog.e("eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
            }
            finish();
            return false;
        }

        int vertexShader = FilterShaders.loadShader(GLES20.GL_VERTEX_SHADER, FilterShaders.simpleVertexShaderCode);
        int fragmentShader = FilterShaders.loadShader(GLES20.GL_FRAGMENT_SHADER, FilterShaders.simpleFragmentShaderCode);
        if (vertexShader != 0 && fragmentShader != 0) {
            simpleShaderProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(simpleShaderProgram, vertexShader);
            GLES20.glAttachShader(simpleShaderProgram, fragmentShader);
            GLES20.glBindAttribLocation(simpleShaderProgram, 0, "position");
            GLES20.glBindAttribLocation(simpleShaderProgram, 1, "inputTexCoord");

            GLES20.glLinkProgram(simpleShaderProgram);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(simpleShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
                GLES20.glDeleteProgram(simpleShaderProgram);
                simpleShaderProgram = 0;
            } else {
                simplePositionHandle = GLES20.glGetAttribLocation(simpleShaderProgram, "position");
                simpleInputTexCoordHandle = GLES20.glGetAttribLocation(simpleShaderProgram, "inputTexCoord");
                simpleSourceImageHandle = GLES20.glGetUniformLocation(simpleShaderProgram, "sourceImage");
            }
        } else {
            return false;
        }



        int w = currentBitmap.getWidth();
        int h = currentBitmap.getHeight();

        if (!filterShaders.create()) {
            finish();
            return false;
        }

        if (w != 0 && h != 0) {
            filterShaders.setRenderData(currentBitmap, orientation, 0, w, h);
            renderDataSet = true;
            renderBufferWidth = filterShaders.getRenderBufferWidth();
            renderBufferHeight = filterShaders.getRenderBufferHeight();
        }

        return true;
    }



    public void finish() {
        initied = false;
        currentBitmap = null;
        if (eglSurface != null) {
            egl10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            egl10.eglDestroySurface(eglDisplay, eglSurface);
            eglSurface = null;
        }
        if (eglContext != null) {

            egl10.eglDestroyContext(eglDisplay, eglContext);
            eglContext = null;
        }
        if (eglDisplay != null) {
            egl10.eglTerminate(eglDisplay);
            eglDisplay = null;
        }
        if (surfaceTexture != null) {
            surfaceTexture.release();
            surfaceTexture = null;
        }
    }



    private void makeCurrentContext() {
        if (!eglContext.equals(egl10.eglGetCurrentContext()) || !eglSurface.equals(egl10.eglGetCurrentSurface(EGL10.EGL_DRAW))) {
            if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                if (EngineLog.ENABLED) {
                    EngineLog.e("eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                return;
            }
        }
    }

    private boolean filterTextureAvailable;

    private final Runnable drawRunnable = () -> {
        if (!initied) {
            return;
        }

        makeCurrentContext();



        if (!renderDataSet) {
            return;
        }



        {
            GLES20.glViewport(0, 0, renderBufferWidth, renderBufferHeight);
            filterShaders.drawEnhancePass();
            filterShaders.drawSharpenPass();
            filterShaders.drawCustomParamsPass();
            blurred = filterShaders.drawBlurPass();
            filterTextureAvailable = true;
        }

        if (filterTextureAvailable) {
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

            int tex = filterShaders.getRenderTexture(blurred ? 0 : 1);

            GLES20.glUseProgram(simpleShaderProgram);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);

            GLES20.glUniform1i(simpleSourceImageHandle, 0);
            GLES20.glEnableVertexAttribArray(simpleInputTexCoordHandle);
            GLES20.glVertexAttribPointer(simpleInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer != null ? textureBuffer : filterShaders.getTextureBuffer());
            GLES20.glEnableVertexAttribArray(simplePositionHandle);
            GLES20.glVertexAttribPointer(simplePositionHandle, 2, GLES20.GL_FLOAT, false, 8, filterShaders.getVertexBuffer());
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            egl10.eglSwapBuffers(eglDisplay, eglSurface);


        }
    };

    private Bitmap getRenderBufferBitmap() {
        if (renderBufferWidth == 0 || renderBufferHeight == 0) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(renderBufferWidth * renderBufferHeight * 4);
        GLES20.glReadPixels(0, 0, renderBufferWidth, renderBufferHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
        Bitmap bitmap = Bitmap.createBitmap(renderBufferWidth, renderBufferHeight, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }









    public Bitmap getTexture() {
        if (Thread.currentThread() == this) throw new IllegalStateException("Readback must run off the GL thread");
        if (closed) throw new IllegalStateException("Engine is closed");
        if (!initied || !isAlive()) {
            return null;
        }
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final Bitmap[] object = new Bitmap[1];
        try {
            if (postRunnable(() -> {
                try {
                if (!initied) throw new IllegalStateException("EGL is unavailable", failure);
                makeCurrentContext();
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, filterShaders.getRenderFrameBuffer());
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, filterShaders.getRenderTexture(blurred ? 0 : 1), 0);
                GLES20.glClear(0);
                object[0] = getRenderBufferBitmap();
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                GLES20.glClear(0);
                } catch (Throwable error) { failure = error; }
                finally { countDownLatch.countDown(); }
            })) {
                if (!countDownLatch.await(20, java.util.concurrent.TimeUnit.SECONDS))
                    throw new IllegalStateException("GL readback timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GL readback interrupted", e);
        }
        if (object[0] == null) throw new IllegalStateException("GL readback failed", failure);
        return object[0];
    }

    public void shutdown() {
        if (closed) return;
        closed = true;
        postRunnable(() -> {
            finish();
            Looper looper = Looper.myLooper();
            if (looper != null) {
                looper.quit();
            }
        });
    }

    public void setSurfaceTextureSize(int width, int height) {
        postRunnable(() -> {
            surfaceWidth = width;
            surfaceHeight = height;
        });
    }

    @Override
    public void run() {
        try { initied = initGL(); }
        catch (Throwable error) { failure = error; finish(); }
        super.run();
    }

    public void requestRender(final boolean updateBlur) {
        requestRender(updateBlur, false, false);
    }

    public void requestRender(final boolean updateBlur, final boolean force, boolean surface) {
        if (surface) throw new IllegalArgumentException("Video input is not part of this extraction");
        if (closed) throw new IllegalStateException("Engine is closed");
        postRunnable(() -> {
            if (updateBlur) {
                filterShaders.requestUpdateBlurTexture();
            }
            long newTime = System.currentTimeMillis();
            if (force || Math.abs(lastRenderCallTime - newTime) > 30) {
                lastRenderCallTime = newTime;
                try { drawRunnable.run(); }
                catch (Throwable error) { failure = error; initied = false; }
            }
        });
    }
}
