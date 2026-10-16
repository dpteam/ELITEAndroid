package elite.preview;

/** New 1184-byte integration snapshot, NOT an original game/save format. */
final class NativeSession {
    private NativeSession() {}
    static native boolean valid(byte[] state);
    static native byte[] reset(int targetModel);
    static native byte[] selectTarget(byte[] state, int index);
    static native byte[] selectReachable(byte[] state);
    static native byte[] jump(byte[] state);
    static native byte[] approach(byte[] state, int ticks);
    static native byte[] dock(byte[] state);
    static native byte[] transition(byte[] state, int action);
    static native byte[] trade(byte[] state, int item, boolean buy);
    static native byte[] step(byte[] state, int[] input, int ticks);
}
