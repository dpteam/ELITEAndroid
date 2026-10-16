package elite.preview;

/** Station math snapshots, NOT the original save format or full game state.
 * Version 1: 204 bytes, little-endian. Commander at 8, price array at 116,
 * ASCII system name at 184, docked flag at 196 and transaction result at 200.
 */
final class NativeStation {
    private NativeStation() {}
    static native byte[] reset();
    static native byte[] trade(byte[] state, int item, boolean buy);
}
