// Restore a known GCC PIC get_pc_thunk that starts with eight NOP bytes.
// These bytes are present in the supplied ELF; this only annotates the database.
// @category Elite
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.SourceType;

public class PrepareX86 extends GhidraScript {
    @Override
    public void run() throws Exception {
        if (!currentProgram.getLanguageID().toString().startsWith("x86:LE:32:")) {
            throw new IllegalStateException("Expected the x86 ELF32 analysis target");
        }
        Address at = toAddr(0x228a1L);
        byte[] expected = {(byte)0x90,(byte)0x90,(byte)0x90,(byte)0x90,
                           (byte)0x90,(byte)0x90,(byte)0x90,(byte)0x90,
                           (byte)0x8b,(byte)0x1c,(byte)0x24,(byte)0xc3};
        if (!java.util.Arrays.equals(getBytes(at, expected.length), expected)) {
            throw new IllegalStateException("PIC thunk bytes do not match the supplied x86 artifact");
        }
        Function thunk = getFunctionAt(at);
        if (thunk == null) throw new IllegalStateException("Missing PIC thunk function");
        thunk.setName("__x86.get_pc_thunk.bx", SourceType.USER_DEFINED);
        thunk.setCallFixup("get_pc_thunk_bx");
        println("Annotated GCC PIC EBX thunk at " + at + "; ELF bytes were not modified");
    }
}
