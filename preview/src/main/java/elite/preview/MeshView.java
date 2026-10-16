package elite.preview;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.view.MotionEvent;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

final class MeshView extends GLSurfaceView implements GLSurfaceView.Renderer {
    volatile int model;
    volatile int flags = 1;
    volatile boolean rotating = true;
    volatile boolean flightMode;
    volatile int throttleTarget = 8;
    interface StatusListener { void changed(String status); }
    volatile StatusListener statusListener;
    private String lastStatus = "";
    private byte[] flightState;
    private int flightModel = -1;
    private volatile boolean resetRequested;
    private volatile float flightRoll, flightPitch;
    private boolean previousFlight;
    private float tickAccumulator;
    private String flightFailure;
    private volatile float dragYaw, dragPitch;
    private float lastX, lastY;
    private float yaw = 0.5f;
    private long lastTime;
    private int program, position, colour, projection;
    private final float[] matrix = new float[16];
    private final FloatBuffer buffer = ByteBuffer.allocateDirect(64 * 64 * 7 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static final int[] PALETTE = {
        0x000000, 0x42ff5a, 0xffff00, 0xffffff, 0x00ffff, 0xd6d6d6,
        0x5a5aff, 0xcece42, 0xff5252, 0xff00ff, 0xce4242, 0x42ce42,
        0x4242ce, 0xce00ce, 0x00cece, 0xadadad
    };

    MeshView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setRenderer(this);
    }

    void resetFlight() {
        // Called by UI thread. Native snapshot ownership stays on GL thread.
        resetRequested = true;
    }

    void clearFlightInput() {
        flightRoll = flightPitch = 0;
        queueEvent(() -> { lastTime = 0; tickAccumulator = 0; });
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            lastX = event.getX(); lastY = event.getY(); return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (flightMode) {
                float radius = Math.max(48, Math.min(getWidth(), getHeight()) * .2f);
                flightRoll = Math.max(-1, Math.min(1, (event.getX() - lastX) / radius));
                flightPitch = Math.max(-1, Math.min(1, (event.getY() - lastY) / radius));
            } else {
                dragYaw += (event.getX() - lastX) * 0.01f;
                dragPitch += (event.getY() - lastY) * 0.01f;
                lastX = event.getX(); lastY = event.getY();
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            flightRoll = flightPitch = 0;
            if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }

    private int shader(int kind, String source) {
        int shader = GLES20.glCreateShader(kind);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) throw new IllegalStateException(GLES20.glGetShaderInfoLog(shader));
        return shader;
    }
    @Override public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        int vertex = shader(GLES20.GL_VERTEX_SHADER,
            "attribute vec3 aPosition; attribute vec4 aColour; uniform mat4 uProjection;"
            + "varying vec4 vColour; void main(){gl_Position=uProjection*vec4(aPosition,1.0);"
            + "vColour=aColour;gl_PointSize=2.0;}");
        int fragment = shader(GLES20.GL_FRAGMENT_SHADER,
            "precision mediump float; varying vec4 vColour; void main(){gl_FragColor=vColour;}");
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertex); GLES20.glAttachShader(program, fragment);
        GLES20.glLinkProgram(program);
        int[] status = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment);
        if (status[0] == 0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
        position = GLES20.glGetAttribLocation(program, "aPosition");
        colour = GLES20.glGetAttribLocation(program, "aColour");
        projection = GLES20.glGetUniformLocation(program, "uProjection");
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glClearColor(0.015f, 0.02f, 0.04f, 1f);
        lastTime = 0;
    }
    @Override public void onSurfaceChanged(GL10 unused, int width, int height) {
        // Match the original 256x192 logical viewport, rather than stretching it.
        int w = Math.min(width, height * 4 / 3);
        int h = w * 3 / 4;
        GLES20.glViewport((width - w) / 2, (height - h) / 2, w, h);
        Matrix.frustumM(matrix, 0, -8, 8, -6, 6, 16, 65536);
    }
    @Override public void onDrawFrame(GL10 unused) {
        long now = SystemClock.uptimeMillis();
        long elapsed = lastTime == 0 ? 0 : Math.min(now - lastTime, 100);
        boolean flight = flightMode;
        if (rotating && !flight) yaw += elapsed * 0.00035f;
        lastTime = now;
        double a = yaw + dragYaw, b = 0.3 + dragPitch;
        double cy = Math.cos(a), sy = Math.sin(a), cp = Math.cos(b), sp = Math.sin(b);
        int selectedModel = model;
        if (flight && (flightModel != selectedModel || resetRequested || !previousFlight)) {
            flightState = NativeFlight.reset(selectedModel, 0);
            flightModel = selectedModel;
            resetRequested = false;
            flightFailure = null;
            tickAccumulator = 0;
            elapsed = 0;
        }
        previousFlight = flight;
        int[] pose = {
            (int)(cy*24576), 0, (int)(-sy*24576),
            (int)(sy*sp*24576), (int)(cp*24576), (int)(cy*sp*24576),
            (int)(sy*cp*24576), (int)(-sp*24576), (int)(cy*cp*24576),
            0, 0, NativeMesh.recommendedDistance(selectedModel)
        };
        if (flight) {
            int[] input = new int[9];
            input[5] = clamp(Math.round(flightRoll * 100f), -100, 100);
            input[6] = clamp(Math.round(flightPitch * 100f), -100, 100);
            // New test-scene scheduler: fixed 30Hz simulation, independent of
            // display refresh. Pause freezes state; no background catch-up.
            if (rotating) tickAccumulator += elapsed;
            else tickAccumulator = 0;
            int ticks = Math.min(4, (int)(tickAccumulator / (1000f / 30f)));
            for (int i = 0; i < ticks && flightFailure == null; ++i) {
                int nextSpeed = clamp(readInt(flightState, 44) + readInt(flightState, 32), 1, 36);
                input[4] = Integer.compare(throttleTarget, nextSpeed);
                try {
                    flightState = NativeFlight.step(flightState, input, 1);
                } catch (IllegalStateException failure) {
                    flightFailure = "Flight stopped: " + failure.getMessage() + " · use Reset";
                    tickAccumulator = 0;
                    break;
                }
                tickAccumulator -= 1000f / 30f;
            }
            if (flightFailure != null) tickAccumulator = 0;
            for (int i = 0; i < 12; ++i) pose[i] = readInt(flightState, 68 + i * 4);
        }
        int frameFlags = flags;
        int[] frame = NativeMesh.buildFrame(selectedModel, pose, frameFlags);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (flight) drawReferenceStars();
        if (frame.length < 4 || frame[0] != 1) throw new IllegalStateException("Invalid native frame format");
        if (frame[1] != 0) {
            report((flight ? flightStatus() + " · " : "")
                + "Mesh status " + frame[1] + " (culled/distant/unsupported/invalid)");
            return;
        }
        int vertices = frame[2], commands = frame[3], cursor = 4 + vertices * 3;
        buffer.clear();
        int[] counts = new int[commands];
        for (int i = 0; i < commands; ++i) {
            int count = frame[cursor++], c = frame[cursor++];
            counts[i] = count;
            float r, g, bcol;
            if ((frameFlags & 2) != 0 && frameFlags != 0) {
                r = g = bcol = c == 0 ? 0 : (32f - c) / 31f;
            } else {
                int rgb = PALETTE[c & 15];
                r = ((rgb >> 16) & 255) / 255f;
                g = ((rgb >> 8) & 255) / 255f;
                bcol = (rgb & 255) / 255f;
            }
            for (int j = 0; j < count; ++j) {
                int index = frame[cursor++] * 3 + 4;
                buffer.put(frame[index]).put(frame[index + 1]).put(frame[index + 2]);
                buffer.put(r).put(g).put(bcol).put(1f);
            }
        }
        buffer.flip();
        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(projection, 1, false, matrix, 0);
        buffer.position(0);
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 28, buffer);
        buffer.position(3);
        GLES20.glVertexAttribPointer(colour, 4, GLES20.GL_FLOAT, false, 28, buffer);
        GLES20.glEnableVertexAttribArray(position); GLES20.glEnableVertexAttribArray(colour);
        int first = 0;
        for (int count : counts) {
            int primitive = count == 2 ? GLES20.GL_LINES : GLES20.GL_TRIANGLE_FAN;
            GLES20.glDrawArrays(primitive, first, count);
            first += count;
        }
        int error = GLES20.glGetError();
        report(error == GLES20.GL_NO_ERROR ? (flight ? flightStatus() : "GLES2")
            + " · " + commands + " draw commands · " + vertices + " model vertices"
            : "OpenGL error: 0x" + Integer.toHexString(error));
    }

    private void drawReferenceStars() {
        buffer.clear();
        int count = 0;
        for (int i = 0; i < 64; ++i) {
            int offset = 140 + i * 12;
            int x = readInt(flightState, offset), y = readInt(flightState, offset + 4), z = readInt(flightState, offset + 8);
            if (z <= 16) continue;
            buffer.put(x).put(y).put(-z).put(.6f).put(.7f).put(.85f).put(1);
            ++count;
        }
        buffer.flip();
        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(projection, 1, false, matrix, 0);
        buffer.position(0);
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 28, buffer);
        buffer.position(3);
        GLES20.glVertexAttribPointer(colour, 4, GLES20.GL_FLOAT, false, 28, buffer);
        GLES20.glEnableVertexAttribArray(position); GLES20.glEnableVertexAttribArray(colour);
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count);
    }

    private String flightStatus() {
        if (flightFailure != null) return flightFailure;
        int x = readInt(flightState, 104), y = readInt(flightState, 108), z = readInt(flightState, 112);
        int distance = (int)Math.sqrt((double)x*x + (double)y*y + (double)z*z);
        return "FLIGHT TEST · speed " + readInt(flightState, 44) + "/36"
            + " roll " + readInt(flightState, 36) + " pitch " + readInt(flightState, 40)
            + " range " + distance + " · tick " + readInt(flightState, 16);
    }

    private void report(String message) {
        if (message.equals(lastStatus)) return;
        lastStatus = message;
        post(() -> { StatusListener listener = statusListener; if (listener != null) listener.changed(message); });
    }

    private static int clamp(int value, int low, int high) {
        return value < low ? low : value > high ? high : value;
    }
    private static int readInt(byte[] data, int offset) {
        if (data == null || data.length != 912 || offset < 0 || offset + 4 > data.length)
            throw new IllegalStateException("Invalid flight snapshot layout");
        return (data[offset] & 255) | ((data[offset + 1] & 255) << 8)
            | ((data[offset + 2] & 255) << 16) | (data[offset + 3] << 24);
    }
}
