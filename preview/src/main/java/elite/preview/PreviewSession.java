package elite.preview;

import java.util.Arrays;

/** Single immutable-snapshot owner shared by UI and GL threads. No globals.
 * Synchronized JNI calls publish a new complete snapshot, never half a tick. */
final class PreviewSession {
    private byte[] state;
    PreviewSession(byte[] saved, int model) {
        if (saved != null && !NativeSession.valid(saved))
            throw new IllegalStateException("Saved integration snapshot is invalid");
        state = saved == null ? NativeSession.reset(model) : saved.clone();
    }
    synchronized byte[] snapshot() { return state.clone(); }
    synchronized byte[] station() { return Arrays.copyOfRange(state, 8, 212); }
    synchronized byte[] scene() { return Arrays.copyOfRange(state, 212, 1124); }
    synchronized boolean docked() { return state[204] == 1; }
    synchronized int model() { return readInt(state, 220); }
    synchronized void launch() { state = NativeSession.transition(state, 0); }
    synchronized void returnTest() { state = NativeSession.transition(state, 1); }
    synchronized void resetFlight() { state = NativeSession.transition(state, 2); }
    synchronized void reset() { state = NativeSession.reset(model()); }
    synchronized void trade(int item, boolean buy) { state = NativeSession.trade(state, item, buy); }
    synchronized byte[] stepScene(int[] input, int ticks) {
        state = NativeSession.step(state, input, ticks);
        return scene();
    }
    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 255) | ((data[offset + 1] & 255) << 8)
            | ((data[offset + 2] & 255) << 16) | (data[offset + 3] << 24);
    }
}
