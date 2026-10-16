package elite.preview;

/** Pure-Java frame validation, also exercised on the host JVM. */
final class MeshPacket {
    private MeshPacket() {}
    static int submittedVertices(int[] frame) {
        if (frame == null || frame.length < 4 || frame[0] != 1)
            throw new IllegalArgumentException("Invalid mesh packet header");
        int vertices = frame[2], commands = frame[3];
        if (vertices < 0 || vertices > 64 || commands < 0 || commands > 64)
            throw new IllegalArgumentException("Mesh packet exceeds bounds");
        int cursor = 4 + vertices * 3, submitted = 0;
        if (cursor > frame.length) throw new IllegalArgumentException("Truncated mesh positions");
        for (int i = 0; i < commands; ++i) {
            if (cursor + 2 > frame.length) throw new IllegalArgumentException("Truncated draw command");
            int count = frame[cursor++];
            int colour = frame[cursor++];
            // The native command stores an unsigned byte. Shaded commands are
            // intensities, not necessarily indices in the 16-colour palette.
            if (count < 1 || count > 64 || colour < 0 || colour > 255 || cursor + count > frame.length)
                throw new IllegalArgumentException("Invalid draw command");
            for (int j = 0; j < count; ++j) {
                int index = frame[cursor++];
                if (index < 0 || index >= vertices) throw new IllegalArgumentException("Invalid vertex index");
            }
            submitted += count;
        }
        if (cursor != frame.length) throw new IllegalArgumentException("Trailing packet data");
        return submitted;
    }
}
