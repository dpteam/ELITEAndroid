package elite.preview;

/** No Android dependency: regression for the on-device Bad position 3/0 crash. */
public final class MeshPacketTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static void rejects(int[] frame) {
        try { MeshPacket.submittedVertices(frame); throw new AssertionError("Accepted invalid packet"); }
        catch (IllegalArgumentException expected) {}
    }
    public static void main(String[] args) {
        int[] emptyModel = new int[4 + 16 * 3];
        emptyModel[0] = 1; emptyModel[2] = 16;
        check(MeshPacket.submittedVertices(emptyModel) == 0);
        check(MeshPacket.submittedVertices(new int[]{1, 0, 0, 0}) == 0);
        check(MeshPacket.submittedVertices(new int[]{1, 1, 0, 0}) == 0);
        int[] line = {1, 0, 2, 1, 0, 0, -100, 1, 0, -100, 2, 4, 0, 1};
        check(MeshPacket.submittedVertices(line) == 2);
        int[] shade = line.clone(); shade[11] = 255;
        check(MeshPacket.submittedVertices(shade) == 2);
        shade[11] = 256; rejects(shade);
        rejects(null); rejects(new int[]{1,0,65,0}); rejects(new int[]{1,0,1,1});
        int[] badIndex = line.clone(); badIndex[13] = 2; rejects(badIndex);
        int[] point = {1, 0, 1, 1, 0, 0, -100, 1, 4, 0};
        check(MeshPacket.submittedVertices(point) == 1);
        rejects(new int[]{1,0,0,0,0});
        System.out.println("PASS: empty/culled frames, valid line, malformed bounds/index checks");
    }
}
