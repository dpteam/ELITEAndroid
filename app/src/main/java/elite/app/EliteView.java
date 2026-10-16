package elite.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;
import javax.microedition.khronos.opengles.GL11Ext;

/* loaded from: classes.dex */
public class EliteView extends GLSurfaceView implements SensorEventListener, View.OnClickListener {
    private static final int ARROW_SIZE = 50;
    private static final int LONG = 2;
    private static final int NUMBER_OF_CHANNELS = 4;
    private static final int PLATFORM_KEY_B = 1;
    private static final int PLATFORM_KEY_DOWN = 7;
    private static final int PLATFORM_KEY_L = 9;
    private static final int PLATFORM_KEY_LEFT = 5;
    private static final int PLATFORM_KEY_R = 8;
    private static final int PLATFORM_KEY_RIGHT = 4;
    private static final int PLATFORM_KEY_SELECT = 2;
    private static final int PLATFORM_KEY_START = 3;
    private static final int PLATFORM_KEY_TOUCH = 12;
    private static final int PLATFORM_KEY_UP = 6;
    private static final int PLATFORM_KEY_X = 10;
    private static final int PLATFORM_KEY_Y = 11;
    private static final int RATE = 30;
    private static final float SOUND_EFFECT_VOLUME = 0.5f;
    private static final int SOUND_PRIORITY = 1;
    private static final int STREAM_MUSIC = 3;
    private static final int SWIPE_HORI = 3;
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_MIN_DISTANCE = 120;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;
    private static final int SWIPE_VERT = 1;
    private static final int TAP = 0;
    private static EliteView s_currentInstance;
    private Activity m_activity;
    AudioManager m_audioMan;
    Filter[] m_filters;
    GestureDetector m_gestureDetector;
    View.OnTouchListener m_gestureListener;
    public GLSurfaceViewRenderer m_glSurfaceViewRenderer;
    private TrackballHandler m_handler;
    float[] m_lastAccels;
    float[] m_lastMagFields;
    float m_lastPitch;
    float m_lastRoll;
    float m_lastYaw;
    MediaPlayer m_mediaPlayer;
    private float[] m_orientation;
    boolean m_portrait;
    private float[] m_remappedR;
    private SparseArray<Integer> m_resourceIdToSoundIdMap;
    private float[] m_rotationMatrix;
    private SoundPool m_soundPool;
    private float m_tiltCentreX;
    private float m_tiltCentreY;
    private float m_tiltCentreZ;
    Vibrator m_vibrator;
    private static final int PLATFORM_KEY_A = 0;
    private static final int[] KEY_CODES = {23, 4, PLATFORM_KEY_A, 82, 22, 21, 19, 20, PLATFORM_KEY_A, PLATFORM_KEY_A};

    /* JADX INFO: Access modifiers changed from: private */
    public static native void elite_gl_render();

    /* JADX INFO: Access modifiers changed from: private */
    public static native void elite_gl_resize(int i, int i2);

    /* JADX INFO: Access modifiers changed from: private */
    public static native void elite_gl_surface_create(int i, int i2, int i3, int i4, int i5);

    /* JADX INFO: Access modifiers changed from: private */
    public static native void elite_key_down(int i);

    /* JADX INFO: Access modifiers changed from: private */
    public static native void elite_key_up(int i);

    private static native void elite_pause_activity();

    private static native void elite_set_tilt(int i, int i2);

    /* JADX INFO: Access modifiers changed from: private */
    public static native void elite_special_input(int i, int i2, int i3);

    /* JADX INFO: Access modifiers changed from: private */
    public static native void elite_start();

    private static native void elite_touch_event(int i, int i2, boolean z);

    static {
        System.loadLibrary("elite_native");
    }

    class GLSurfaceViewRenderer implements GLSurfaceView.Renderer {
        int m_arrowTexture;
        int m_dockedTexture;
        int m_fireTexture;
        GL10 m_gl;
        int m_inflightTexture;
        int m_resetTexture;
        Paint m_textPaint;
        int m_textTexture;
        public int m_width = 256;
        public int m_height = 192;
        public long m_lastFrame = 0;
        Bitmap m_bitmap = null;
        Canvas m_canvas = null;
        int[] m_crop = new int[4];

        GLSurfaceViewRenderer() {
        }

        public void drawText(String text, int x, int y, int r, int g, int b) {
            if (this.m_bitmap == null) {
                clearText();
            }
            this.m_textPaint.setARGB(255, r, g, b);
            try {
                float ts = this.m_textPaint.getTextSize();
                if (text.endsWith("\f")) {
                    text = text.substring(EliteView.PLATFORM_KEY_A, text.length() - 1);
                }
                this.m_canvas.drawText(text, x, ((y * ts) / 8.0f) + ts, this.m_textPaint);
            } catch (Exception e) {
                Log.e("Elite", "Exception in drawText:" + e.getMessage());
            }
        }

        int nextPow2(int x) {
            int x2 = x - 1;
            int x3 = x2 | (x2 >> 1);
            int x4 = x3 | (x3 >> 2);
            int x5 = x4 | (x4 >> 4);
            int x6 = x5 | (x5 >> EliteView.PLATFORM_KEY_R);
            return (x6 | (x6 >> 16)) + 1;
        }

        public void clearText() {
            if (this.m_bitmap == null) {
                int w = nextPow2(this.m_width);
                int h = nextPow2(this.m_height);
                this.m_bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_4444);
                this.m_canvas = new Canvas(this.m_bitmap);
                this.m_bitmap.eraseColor(EliteView.PLATFORM_KEY_A);
            }
            this.m_canvas.drawColor(EliteView.PLATFORM_KEY_A, PorterDuff.Mode.CLEAR);
        }

        public void renderText(boolean redraw, int offset) {
            if (this.m_bitmap != null) {
                this.m_gl.glEnable(3553);
                this.m_gl.glEnable(3042);
                this.m_gl.glBlendFunc(770, 771);
                this.m_gl.glBindTexture(3553, this.m_textTexture);
                if (redraw) {
                    this.m_gl.glTexParameterf(3553, 10241, 9729.0f);
                    this.m_gl.glTexParameterf(3553, 10240, 9729.0f);
                    GLUtils.texImage2D(3553, EliteView.PLATFORM_KEY_A, this.m_bitmap, EliteView.PLATFORM_KEY_A);
                }
                ((GL11) this.m_gl).glTexParameteriv(3553, 35741, this.m_crop, EliteView.PLATFORM_KEY_A);
                ((GL11Ext) this.m_gl).glDrawTexiOES(offset, EliteView.PLATFORM_KEY_A, EliteView.PLATFORM_KEY_A, this.m_width, this.m_height);
            }
        }

        private void checkGLError(GL10 gl) {
            int error = gl.glGetError();
            if (error != 0) {
                Log.e("Elite", "GLError 0x" + Integer.toHexString(error));
            }
        }

        private int createTexture(GL10 gl) {
            int[] ids = new int[1];
            gl.glGenTextures(1, ids, EliteView.PLATFORM_KEY_A);
            gl.glBindTexture(3553, ids[EliteView.PLATFORM_KEY_A]);
            gl.glTexParameterf(3553, 10241, 9729.0f);
            gl.glTexParameterf(3553, 10240, 9729.0f);
            return ids[EliteView.PLATFORM_KEY_A];
        }

        private int generateTexture(GL10 gl, int drawable) {
            checkGLError(gl);
            int arrowTextureId = createTexture(gl);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inScaled = false;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Resources res = EliteView.this.getResources();
            Bitmap bmp = BitmapFactory.decodeResource(res, drawable, opts);
            GLUtils.texImage2D(3553, EliteView.PLATFORM_KEY_A, bmp, EliteView.PLATFORM_KEY_A);
            checkGLError(gl);
            bmp.recycle();
            return arrowTextureId;
        }

        @Override // android.opengl.GLSurfaceView.Renderer
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            if (this.m_bitmap != null) {
                this.m_bitmap.recycle();
                this.m_bitmap = null;
            }
            this.m_textPaint = new Paint();
            this.m_textPaint.setTextSize(8.0f);
            this.m_textPaint.setAntiAlias(true);
            this.m_textPaint.setFakeBoldText(true);
            this.m_textPaint.setARGB(255, EliteView.PLATFORM_KEY_A, EliteView.PLATFORM_KEY_A, EliteView.PLATFORM_KEY_A);
            this.m_textPaint.setTextSize(16.0f);
            gl.glEnable(3553);
            this.m_arrowTexture = generateTexture(gl, R.drawable.arrow);
            this.m_resetTexture = generateTexture(gl, R.drawable.reset);
            this.m_fireTexture = generateTexture(gl, R.drawable.fire);
            this.m_dockedTexture = generateTexture(gl, R.drawable.docked);
            this.m_inflightTexture = generateTexture(gl, R.drawable.inflight);
            this.m_textTexture = createTexture(gl);
            gl.glDisable(3553);
            EliteView.elite_gl_surface_create(this.m_arrowTexture, this.m_resetTexture, this.m_fireTexture, this.m_dockedTexture, this.m_inflightTexture);
        }

        @Override // android.opengl.GLSurfaceView.Renderer
        public void onSurfaceChanged(GL10 gl, int w, int h) {
            EliteView.elite_gl_resize(w, h);
            this.m_crop[EliteView.PLATFORM_KEY_A] = EliteView.PLATFORM_KEY_A;
            this.m_crop[1] = h;
            this.m_crop[2] = w;
            this.m_crop[3] = -h;
            this.m_width = w;
            this.m_height = h;
            this.m_textPaint.setTextSize((8.0f * this.m_height) / 192.0f);
        }

        @Override // android.opengl.GLSurfaceView.Renderer
        public void onDrawFrame(GL10 gl) {
            this.m_gl = gl;
            long currentFrame = SystemClock.uptimeMillis();
            long diff = currentFrame - this.m_lastFrame;
            this.m_lastFrame = currentFrame;
            EliteView.elite_gl_render();
            long sleepfor = 33 - diff;
            if (sleepfor > 0) {
                try {
                    Thread.sleep(sleepfor);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public EliteView(Context context, AttributeSet attrs) throws NumberFormatException {
        super(context, attrs);
        this.m_soundPool = new SoundPool(4, 3, PLATFORM_KEY_A);
        this.m_resourceIdToSoundIdMap = new SparseArray<>();
        this.m_mediaPlayer = null;
        this.m_portrait = false;
        this.m_glSurfaceViewRenderer = null;
        this.m_rotationMatrix = new float[PLATFORM_KEY_L];
        this.m_remappedR = new float[PLATFORM_KEY_L];
        this.m_orientation = new float[3];
        this.m_lastPitch = 0.0f;
        this.m_lastYaw = 0.0f;
        this.m_lastRoll = 0.0f;
        this.m_filters = new Filter[]{new Filter(), new Filter(), new Filter()};
        this.m_tiltCentreX = 0.0f;
        this.m_tiltCentreY = 0.0f;
        this.m_tiltCentreZ = 0.0f;
        s_currentInstance = this;
        startNativeLoop();
        this.m_handler = new TrackballHandler();
        this.m_glSurfaceViewRenderer = new GLSurfaceViewRenderer();
        setRenderer(this.m_glSurfaceViewRenderer);
        requestFocus();
        setFocusableInTouchMode(true);
        for (int i = R.raw.cancel; i <= R.raw.warning; i++) {
            Integer soundId = Integer.valueOf(this.m_soundPool.load(context, i, 1));
            this.m_resourceIdToSoundIdMap.put(i, soundId);
        }
        this.m_gestureDetector = new GestureDetector(new MyGestureDetector());
        this.m_gestureListener = new View.OnTouchListener() { // from class: elite.app.EliteView.1
            @Override // android.view.View.OnTouchListener
            public boolean onTouch(View v, MotionEvent event) {
                EliteView.this.doTouchEvent(event);
                return EliteView.this.m_gestureDetector.onTouchEvent(event);
            }
        };
        setOnClickListener(this);
        setOnTouchListener(this.m_gestureListener);
        this.m_vibrator = (Vibrator) context.getSystemService("vibrator");
        this.m_audioMan = (AudioManager) context.getSystemService("audio");
        WindowManager wm = (WindowManager) context.getSystemService("window");
        VersionedRotationDetector detector = VersionedRotationDetector.newInstance(wm.getDefaultDisplay());
        int rot = detector.getRotation();
        this.m_portrait = rot == 1;
    }

    public final void setActivity(Activity activity) {
        this.m_activity = activity;
    }

    /* JADX WARN: Type inference failed for: r0v0, types: [elite.app.EliteView$2] */
    private void startNativeLoop() {
        new Thread() { // from class: elite.app.EliteView.2
            @Override // java.lang.Thread, java.lang.Runnable
            public void run() {
                EliteView.elite_start();
            }
        }.start();
    }

    class MyGestureDetector extends GestureDetector.SimpleOnGestureListener {
        MyGestureDetector() {
        }

        private void toast(String s) {
            Toast.makeText(EliteView.this.getContext(), s, EliteView.PLATFORM_KEY_A).show();
        }

        @Override // android.view.GestureDetector.SimpleOnGestureListener, android.view.GestureDetector.OnDoubleTapListener
        public boolean onDoubleTap(MotionEvent e) {
            return false;
        }

        @Override // android.view.GestureDetector.SimpleOnGestureListener, android.view.GestureDetector.OnGestureListener
        public void onLongPress(MotionEvent event) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            EliteView.elite_special_input(2, x, y);
            if (EliteView.this.m_vibrator != null) {
                EliteView.this.m_vibrator.vibrate(50L);
            }
        }

        @Override // android.view.GestureDetector.SimpleOnGestureListener, android.view.GestureDetector.OnDoubleTapListener
        public boolean onSingleTapConfirmed(MotionEvent event) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            EliteView.elite_special_input(EliteView.PLATFORM_KEY_A, x, y);
            return true;
        }

        @Override // android.view.GestureDetector.SimpleOnGestureListener, android.view.GestureDetector.OnGestureListener
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            try {
                float dy = e1.getY() - e2.getY();
                float dx = e1.getX() - e2.getX();
                float sx = Math.abs(velocityX);
                float sy = Math.abs(velocityY);
                if (Math.abs(dy) <= 250.0f || Math.abs(dx) <= 250.0f) {
                    if (dy > 120.0f && sy > 200.0f) {
                        EliteView.elite_special_input(1, (int) e1.getX(), (int) sy);
                    } else if ((-dy) > 120.0f && sy > 200.0f) {
                        EliteView.elite_special_input(1, (int) e1.getX(), (int) (-sy));
                    } else if (dx > 120.0f && sx > 200.0f) {
                        EliteView.elite_special_input(3, (int) sx, (int) e1.getY());
                    } else if ((-dx) > 120.0f && sx > 200.0f) {
                        EliteView.elite_special_input(3, (int) (-sx), (int) e1.getY());
                    }
                }
            } catch (Exception e) {
            }
            return false;
        }
    }

    @Override // android.opengl.GLSurfaceView
    public void onPause() throws IllegalStateException {
        super.onPause();
        pauseMediaPlayer();
        elite_pause_activity();
    }

    @Override // android.opengl.GLSurfaceView
    public void onResume() throws IllegalStateException {
        super.onResume();
        resumeMediaPlayer();
    }

    private boolean isSoundOn() {
        if (this.m_audioMan == null || !this.m_portrait) {
            return true;
        }
        switch (this.m_audioMan.getRingerMode()) {
            case PLATFORM_KEY_A /* 0 */:
            case 1:
                return false;
            case 2:
            default:
                return true;
        }
    }

    private void playSong(int rid) throws IllegalStateException {
        if (isSoundOn()) {
            releaseMediaPlayer();
            this.m_mediaPlayer = MediaPlayer.create(getContext(), rid);
            this.m_mediaPlayer.setLooping(true);
            this.m_mediaPlayer.start();
        }
    }

    public void releaseMediaPlayer() {
        if (this.m_mediaPlayer != null) {
            this.m_mediaPlayer.release();
            this.m_mediaPlayer = null;
        }
    }

    private void pauseMediaPlayer() throws IllegalStateException {
        if (this.m_mediaPlayer != null) {
            try {
                this.m_mediaPlayer.pause();
            } catch (IllegalStateException e) {
                Log.e("Elite", "IllegalStateException pausing media player.");
            }
        }
    }

    private void resumeMediaPlayer() throws IllegalStateException {
        if (this.m_mediaPlayer != null) {
            try {
                this.m_mediaPlayer.start();
            } catch (IllegalStateException e) {
                Log.e("Elite", "IllegalStateException starting media player.");
            }
        }
    }

    private void playSoundImpl(int sound) throws IllegalStateException {
        playSoundImpl(sound, -1.0f);
    }

    private void playSoundImpl(int sound, float rate) throws IllegalStateException {
        Integer soundId;
        int resourceId = R.raw.cancel + sound;
        if (resourceId >= R.raw.zz_danube) {
            playSong(resourceId);
            return;
        }
        if (isSoundOn() && (soundId = this.m_resourceIdToSoundIdMap.get(resourceId)) != null) {
            int stream = this.m_soundPool.play(soundId.intValue(), SOUND_EFFECT_VOLUME, SOUND_EFFECT_VOLUME, PLATFORM_KEY_A, PLATFORM_KEY_A, 1.0f);
            if (stream != 0 && rate > 0.0f) {
                this.m_soundPool.setRate(stream, rate);
            }
            if (stream == 0) {
                Log.e("Elite", "Error playing sound: " + Integer.toHexString(sound));
            }
        }
    }

    public static void playSound(int sound) throws IllegalStateException {
        s_currentInstance.playSoundImpl(sound);
    }

    public static void playSound(int sound, int rate) throws IllegalStateException {
        float frate = rate / 10.0f;
        if (frate > 2.0f) {
            Log.e("Elite", "Error in rate " + rate + ", setting to 2.0f");
            frate = 2.0f;
        } else if (frate < SOUND_EFFECT_VOLUME) {
            Log.e("Elite", "Error in rate " + rate + ", setting to 0.5f");
            frate = SOUND_EFFECT_VOLUME;
        }
        s_currentInstance.playSoundImpl(sound, frate);
    }

    public static void exit() {
        s_currentInstance.m_activity.finish();
        Process.killProcess(Process.myPid());
    }

    public static void stopSong() {
        s_currentInstance.releaseMediaPlayer();
    }

    public static void resetTiltControl() {
        s_currentInstance.resetTiltControlImpl();
    }

    public static void drawText(String text, int x, int y, int r, int g, int b) {
        s_currentInstance.m_glSurfaceViewRenderer.drawText(text, x, y, r, g, b);
    }

    public static void clearText() {
        s_currentInstance.m_glSurfaceViewRenderer.clearText();
    }

    public static void renderText(int need_redraw, int scroll) {
        s_currentInstance.m_glSurfaceViewRenderer.renderText(need_redraw != 0, scroll);
    }

    public static void saveGame(byte[] data) {
        s_currentInstance.saveGameImpl(data);
    }

    public static void loadGame(byte[] data) throws Throwable {
        s_currentInstance.loadGameImpl(data);
    }

    private void closeSilently(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Throwable th) {
            }
        }
    }

    private void saveGameImpl(byte[] data) {
        FileOutputStream tempStream = null;
        try {
            tempStream = getContext().openFileOutput("elite.sav", PLATFORM_KEY_A);
            tempStream.write(data);
            tempStream.close();
        } catch (IOException e) {
        } finally {
            closeSilently(tempStream);
        }
    }

    private void loadGameImpl(byte[] data) throws Throwable {
        Closeable in = null;
        try {
            DataInputStream in2 = new DataInputStream(getContext().openFileInput("elite.sav"));
            int read = PLATFORM_KEY_A;
            try {
                int len = data.length;
                while (read < len) {
                    read += in2.read(data, read, len - read);
                }
                in2.close();
                closeSilently(in2);
            } catch (FileNotFoundException e) {
                in = in2;
                closeSilently(in);
            } catch (IOException e2) {
                in = in2;
                closeSilently(in);
            } catch (IndexOutOfBoundsException e3) {
                in = in2;
                closeSilently(in);
            } catch (Throwable th) {
                th = th;
                in = in2;
                closeSilently(in);
                throw th;
            }
        } catch (FileNotFoundException e4) {
        } catch (IOException e5) {
        } catch (IndexOutOfBoundsException e6) {
        } catch (Throwable ignored) {
            // A missing or incompatible save is equivalent to a new game.
        }
    }

    public static class TrackballHandler extends Handler {

        /* renamed from: dx */
        public float f0dx = 0.0f;

        /* renamed from: dy */
        public float f1dy = 0.0f;

        public void doMove() {
            if (this.f0dx >= 0.15f) {
                EliteView.elite_key_down(4);
            }
            if (this.f0dx <= -0.15f) {
                EliteView.elite_key_down(EliteView.PLATFORM_KEY_LEFT);
            }
            if (this.f1dy >= 0.15f) {
                EliteView.elite_key_down(EliteView.PLATFORM_KEY_DOWN);
            }
            if (this.f1dy <= -0.15f) {
                EliteView.elite_key_down(EliteView.PLATFORM_KEY_UP);
            }
            keysUp(100L);
            this.f0dx = 0.0f;
            this.f1dy = 0.0f;
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            if (msg.what == 0) {
                doMove();
                return;
            }
            for (int i = EliteView.PLATFORM_KEY_A; i < EliteView.KEY_CODES.length; i++) {
                EliteView.elite_key_up(i);
            }
        }

        public void schedule(long delayMillis) {
            removeMessages(EliteView.PLATFORM_KEY_A);
            removeMessages(1);
            sendMessageDelayed(obtainMessage(EliteView.PLATFORM_KEY_A), delayMillis);
        }

        public void keysUp(long delayMillis) {
            removeMessages(1);
            sendMessageDelayed(obtainMessage(1), delayMillis);
        }
    }

    @Override // android.view.View
    public boolean onTrackballEvent(MotionEvent event) {
        if (event.getAction() == 2) {
            float x = event.getX();
            float y = event.getY();
            this.m_handler.f0dx += x;
            this.m_handler.f1dy += y;
            this.m_handler.doMove();
        }
        if (event.getAction() == 0) {
            elite_key_down(PLATFORM_KEY_A);
        }
        if (event.getAction() == 1) {
            elite_key_up(PLATFORM_KEY_A);
        }
        return true;
    }

    @Override // android.view.View, android.view.KeyEvent.Callback
    public boolean onKeyDown(int keyCode, KeyEvent msg) {
        for (int i = PLATFORM_KEY_A; i < KEY_CODES.length; i++) {
            if (KEY_CODES[i] != 0 && KEY_CODES[i] == keyCode) {
                elite_key_down(i);
                return true;
            }
        }
        return super.onKeyDown(keyCode, msg);
    }

    @Override // android.view.View, android.view.KeyEvent.Callback
    public boolean onKeyUp(int keyCode, KeyEvent msg) {
        for (int i = PLATFORM_KEY_A; i < KEY_CODES.length; i++) {
            if (KEY_CODES[i] == keyCode) {
                elite_key_up(i);
                return true;
            }
        }
        return super.onKeyUp(keyCode, msg);
    }

    @Override // android.view.View.OnClickListener
    public void onClick(View v) {
    }

    public boolean doTouchEvent(MotionEvent event) {
        if (event.getAction() == 2) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();
        if (event.getAction() == 0) {
            elite_touch_event((int) x, (int) y, true);
        } else if (event.getAction() == 1) {
            elite_touch_event((int) x, (int) y, false);
        }
        return true;
    }

    @Override // android.hardware.SensorEventListener
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override // android.hardware.SensorEventListener
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == 1) {
            accel(event);
        }
        if (event.sensor.getType() == 2) {
            mag(event);
        }
    }

    private static class Filter {
        static final int AVERAGE_BUFFER = 20;
        float[] m_arr;
        int m_idx;

        private Filter() {
            this.m_arr = new float[AVERAGE_BUFFER];
            this.m_idx = EliteView.PLATFORM_KEY_A;
        }

        public float append(float val) {
            this.m_arr[this.m_idx] = val;
            this.m_idx++;
            if (this.m_idx == AVERAGE_BUFFER) {
                this.m_idx = EliteView.PLATFORM_KEY_A;
            }
            return avg();
        }

        public float avg() {
            float sum = 0.0f;
            float[] arr$ = this.m_arr;
            int len$ = arr$.length;
            for (int i$ = EliteView.PLATFORM_KEY_A; i$ < len$; i$++) {
                float x = arr$[i$];
                sum += x;
            }
            return sum / 20.0f;
        }
    }

    private void resetTiltControlImpl() {
        this.m_tiltCentreX = this.m_lastRoll;
        this.m_tiltCentreY = this.m_lastPitch;
        this.m_tiltCentreZ = this.m_lastYaw;
    }

    private void computeOrientation() {
        if (SensorManager.getRotationMatrix(this.m_rotationMatrix, null, this.m_lastAccels, this.m_lastMagFields)) {
            SensorManager.getOrientation(this.m_rotationMatrix, this.m_orientation);
            float yaw = (float) Math.toDegrees(this.m_orientation[PLATFORM_KEY_A]);
            float pitch = (float) Math.toDegrees(this.m_orientation[1]);
            float roll = (float) Math.toDegrees(this.m_orientation[2]);
            this.m_lastYaw = this.m_filters[PLATFORM_KEY_A].append(yaw);
            this.m_lastPitch = this.m_filters[1].append(pitch);
            this.m_lastRoll = this.m_filters[2].append(roll);
            float dx = this.m_tiltCentreX - this.m_lastRoll;
            float dy = this.m_tiltCentreY - this.m_lastPitch;
            if (this.m_portrait) {
                elite_set_tilt((int) (-dy), (int) dx);
                return;
            } else {
                elite_set_tilt((int) dx, (int) dy);
                return;
            }
        }
        elite_set_tilt(PLATFORM_KEY_A, PLATFORM_KEY_A);
    }

    private void accel(SensorEvent event) {
        if (this.m_lastAccels == null) {
            this.m_lastAccels = new float[3];
        }
        System.arraycopy(event.values, PLATFORM_KEY_A, this.m_lastAccels, PLATFORM_KEY_A, 3);
        if (this.m_lastMagFields != null) {
            computeOrientation();
        }
    }

    private void mag(SensorEvent event) {
        if (this.m_lastMagFields == null) {
            this.m_lastMagFields = new float[3];
        }
        System.arraycopy(event.values, PLATFORM_KEY_A, this.m_lastMagFields, PLATFORM_KEY_A, 3);
        if (this.m_lastAccels != null) {
            computeOrientation();
        }
    }
}
