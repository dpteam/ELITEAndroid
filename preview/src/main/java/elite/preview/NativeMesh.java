package elite.preview;

/** Contract for the rebuilt mesh subset only; this does not start the game. */
final class NativeMesh {
    private NativeMesh() {}
    static native String[] modelNames();
    static native int recommendedDistance(int model);
    static native int[] buildFrame(int model, int[] pose, int flags);
}
