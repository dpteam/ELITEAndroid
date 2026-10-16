package elite.preview;

/** Snapshot API for the rebuilt flight test scene; each call copies state.
 * Version 1: 912-byte little-endian snapshot. target at 68, controls at 24,
 * synthetic reference stars at 140. Not a game-save format or durable ABI.
 */
final class NativeFlight {
    private NativeFlight() {}
    static native byte[] reset(int model, int profile);
    static native byte[] step(byte[] state, int[] input, int ticks);
}
