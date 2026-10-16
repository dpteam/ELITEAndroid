// Export all native functions without executing the target.
// Usage: -postScript ExportNative.java <absolute output directory>
// @category Elite
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionIterator;
import ghidra.program.model.symbol.Symbol;
import ghidra.program.model.symbol.SymbolIterator;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExportNative extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length != 1) throw new IllegalArgumentException("Expected output directory");
        Path out = Path.of(args[0]);
        Files.createDirectories(out.resolve("functions"));
        DecompInterface decompiler = new DecompInterface();
        try {
            if (!decompiler.openProgram(currentProgram)) {
                throw new IllegalStateException(decompiler.getLastMessage());
            }
            int ok = 0, failed = 0, external = 0;
            try (BufferedWriter code = Files.newBufferedWriter(out.resolve("decompiled.c"), StandardCharsets.UTF_8);
                 BufferedWriter index = Files.newBufferedWriter(out.resolve("functions.tsv"), StandardCharsets.UTF_8);
                 BufferedWriter symbols = Files.newBufferedWriter(out.resolve("symbols.tsv"), StandardCharsets.UTF_8)) {
                code.write("/* Ghidra pseudocode: NOT compilable recovered source.\n");
                code.write(" * Input: " + currentProgram.getExecutablePath() + "\n");
                code.write(" * SHA256: " + currentProgram.getExecutableSHA256() + "\n");
                code.write(" * Language: " + currentProgram.getLanguageID() + " */\n\n");
                index.write("address\tname\tbytes\tstatus\tfile\n");
                symbols.write("address\tname\ttype\tsource\n");
                SymbolIterator syms = currentProgram.getSymbolTable().getAllSymbols(true);
                while (syms.hasNext()) {
                    Symbol s = syms.next();
                    symbols.write(s.getAddress() + "\t" + s.getName() + "\t" + s.getSymbolType() + "\t" + s.getSource() + "\n");
                }
                FunctionIterator funcs = currentProgram.getFunctionManager().getFunctions(true);
                while (funcs.hasNext() && !monitor.isCancelled()) {
                    Function f = funcs.next();
                    if (f.isExternal() || f.isThunk()) { external++; continue; }
                    String name = f.getName();
                    String filename = f.getEntryPoint() + "_" + name.replaceAll("[^A-Za-z0-9_.-]", "_") + ".c";
                    DecompileResults r = decompiler.decompileFunction(f, 60, monitor);
                    String text;
                    String status;
                    if (r.decompileCompleted() && r.getDecompiledFunction() != null) {
                        text = r.getDecompiledFunction().getC();
                        status = "decompiled";
                        ok++;
                    } else {
                        text = "/* DECOMPILATION FAILED: " + r.getErrorMessage().replace("*/", "* /") + " */\n";
                        status = "failed";
                        failed++;
                    }
                    String header = "/* " + f.getEntryPoint() + " " + name + " (" + f.getBody().getNumAddresses() + " bytes) */\n";
                    Files.writeString(out.resolve("functions").resolve(filename), header + text, StandardCharsets.UTF_8);
                    code.write(header + text + "\n\n");
                    index.write(f.getEntryPoint() + "\t" + name + "\t" + f.getBody().getNumAddresses() + "\t" + status + "\tfunctions/" + filename + "\n");
                }
                Files.writeString(out.resolve("coverage.txt"), "sha256=" + currentProgram.getExecutableSHA256()
                    + "\nlanguage=" + currentProgram.getLanguageID() + "\ndecompiled=" + ok
                    + "\nfailed=" + failed + "\nexternal_or_thunk=" + external
                    + "\ncancelled=" + monitor.isCancelled() + "\n", StandardCharsets.UTF_8);
                println("EXPORT: decompiled=" + ok + " failed=" + failed + " skipped external/thunk=" + external);
            }
        } finally {
            decompiler.dispose();
        }
    }
}
