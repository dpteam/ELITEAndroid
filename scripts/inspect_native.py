"""Offline ELF32/APK analysis with no third-party dependencies or target execution.

Extracts exact JNI registration records, symbol metadata and read-only data;
compares numeric ship geometry in the supplied Elite AGB reference with APK bytes.
The output does NOT claim that matched names prove matching function behavior.
"""
import argparse
import hashlib
import json
import re
import struct
import zipfile
from pathlib import Path


class Elf32:
    def __init__(self, data):
        self.data = data
        if data[:6] != b"\x7fELF\x01\x01":
            raise ValueError("Only little-endian ELF32 is supported")
        self.machine = struct.unpack_from("<H", data, 18)[0]
        shoff = struct.unpack_from("<I", data, 32)[0]
        shentsize, shnum, shstrndx = struct.unpack_from("<HHH", data, 46)
        if shentsize != 40:
            raise ValueError("Unexpected ELF32 section header size")
        self.sections = []
        for i in range(shnum):
            fields = struct.unpack_from("<10I", data, shoff + i * shentsize)
            self.sections.append(dict(zip(
                ("name_offset", "type", "flags", "address", "offset", "size",
                 "link", "info", "alignment", "entry_size"), fields)))
        names = self.section_bytes(self.sections[shstrndx])
        for section in self.sections:
            section["name"] = self.cstring(names, section["name_offset"])
        self.symbols = []
        for section in self.sections:
            if section["type"] not in (2, 11):
                continue
            strings = self.section_bytes(self.sections[section["link"]])
            for off in range(section["offset"], section["offset"] + section["size"], 16):
                name, value, size, info, other, index = struct.unpack_from("<IIIBBH", data, off)
                self.symbols.append({"name": self.cstring(strings, name),
                    "address": value, "size": size, "type": info & 15,
                    "binding": info >> 4, "visibility": other & 3, "section_index": index})

    @staticmethod
    def cstring(data, off):
        return data[off:data.index(b"\0", off)].decode("utf-8", errors="replace")

    def section_bytes(self, section):
        return self.data[section["offset"]:section["offset"] + section["size"]]

    def file_offset(self, address, size=1):
        for section in self.sections:
            if section["type"] != 8 and section["address"] <= address and (
                    address + size <= section["address"] + section["size"]):
                return section["offset"] + address - section["address"]
        raise ValueError(f"Address 0x{address:x} is not file-backed")

    def read(self, address, size):
        off = self.file_offset(address, size)
        return self.data[off:off + size]

    def string(self, address):
        off = self.file_offset(address)
        return self.cstring(self.data, off)

    def jni_records(self):
        records = []
        # JNINativeMethod: name pointer, descriptor pointer, function pointer.
        # These are linked addresses/addends, NOT observed runtime pointers.
        for section in self.sections:
            if section["type"] != 1 or not (section["flags"] & 2):
                continue
            content = self.section_bytes(section)
            for off in range(0, len(content) - 11, 4):
                name, descriptor, function = struct.unpack_from("<III", content, off)
                try:
                    method = self.string(name)
                    if not re.fullmatch(r"elite_[a-z_]+", method):
                        continue
                    signature = self.string(descriptor)
                    if not signature.startswith("("):
                        continue
                    self.file_offset(function & ~1)
                except (ValueError, IndexError):
                    continue
                records.append({"method": method, "descriptor": signature,
                    "linked_function_address": f"0x{function:x}",
                    "record_file_offset": section["offset"] + off,
                    "record_linked_address": f"0x{section['address'] + off:x}"})
        return records


def geometry_comparison(elf, reference):
    """Parse numeric-only C initializer arrays; never evaluate C or Python input."""
    source = re.sub(r"/\*.*?\*/|//[^\n]*", "", reference.read_text(errors="replace"), flags=re.S)
    symbols = {s["name"]: s for s in elf.symbols if s["type"] == 1 and s["size"]}
    results = []
    for kind, name, count, initializer in re.findall(
            r"const\s+(vertex_t|line_t|face_t)\s+(\w+)\[(\d+)\]\s*=\s*\{(.*?)\};", source, re.S):
        symbol = symbols.get(name)
        if symbol is None:
            continue
        # Numeric tokens and punctuation only. Skip macros instead of guessing.
        remainder = re.sub(r"-?(?:0[xX][0-9a-fA-F]+|\d+)|[\s{},]", "", initializer)
        if remainder:
            continue
        numbers = [int(n, 16 if "x" in n.lower() else 10) for n in re.findall(
            r"-?(?:0[xX][0-9a-fA-F]+|\d+)", initializer)]
        fmt, stride = {"vertex_t": ("<iiii4B", 8), "line_t": ("<i4B", 5),
                       "face_t": ("<iiii", 4)}[kind]
        if len(numbers) != int(count) * stride:
            continue
        expected = b"".join(struct.pack(fmt, *numbers[i:i + stride]) for i in range(0, len(numbers), stride))
        actual = elf.read(symbol["address"], symbol["size"])
        results.append({"name": name, "type": kind, "count": int(count),
            "reference_bytes": len(expected), "apk_bytes": len(actual), "exact_match": actual == expected,
            "reference_sha256": hashlib.sha256(expected).hexdigest(),
            "apk_sha256": hashlib.sha256(actual).hexdigest()})
    return results


def inspect(apk, ref, out):
    out.mkdir(parents=True, exist_ok=True)
    reference_names = set()
    if ref.is_dir():
        for source in sorted(ref.glob("*.c")):
            # Candidate definitions only; this is name-level evidence, not AST equivalence.
            reference_names.update(re.findall(r"^\s*(?:static\s+)?(?:void|int|unsigned\s+int|const\s+\w+\s*\*|\w+_t)\s+\*?\s*(\w+)\s*\([^;{}]*\)\s*\{",
                source.read_text(errors="replace"), re.M))
    report = {"apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "reference": str(ref), "static_only": True, "architectures": {}}
    with zipfile.ZipFile(apk) as package:
        for member in sorted(package.namelist()):
            if not member.startswith("lib/") or not member.endswith(".so"):
                continue
            abi = member.split("/")[1]
            elf = Elf32(package.read(member))
            folder = out / abi
            folder.mkdir(exist_ok=True)
            dynamics = []
            for section in elf.sections:
                if section["type"] == 6:
                    content = elf.section_bytes(section)
                    dynamics.extend(struct.unpack_from("<II", content, i) for i in range(0, len(content), 8))
                if section["name"] == ".rodata":
                    (folder / "rodata.bin").write_bytes(elf.section_bytes(section))
            (folder / "symbols.json").write_text(json.dumps(elf.symbols, indent=2), encoding="utf-8")
            exported = sorted(s["name"] for s in elf.symbols if s["type"] == 2 and s["binding"] == 1 and s["section_index"])
            matches = geometry_comparison(elf, ref / "shipdata.c") if (ref / "shipdata.c").is_file() else []
            info = {"sha256": hashlib.sha256(elf.data).hexdigest(), "bytes": len(elf.data),
                "elf_machine": elf.machine, "text_relocations_declared": any(t == 22 for t, v in dynamics),
                "jni": elf.jni_records(), "exported_function_count": len(exported),
                "reference_function_names": sorted(set(exported) & reference_names),
                "geometry_compared": len(matches), "geometry_exact_matches": sum(x["exact_match"] for x in matches),
                "geometry": matches,
                "limitations": ["Static linked addresses, not runtime addresses.",
                    "Function name matches do not establish behavior equivalence.",
                    "Geometry coverage is limited to numeric-only vertex_t, line_t and face_t arrays with matching symbol names.",
                    "No gameplay, JNI safety or Android runtime compatibility has been tested."]}
            report["architectures"][abi] = info
    (out / "inventory.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    for abi, data in report["architectures"].items():
        print(f"{abi}: JNI={len(data['jni'])}, exported functions={data['exported_function_count']}, "
              f"reference names={len(data['reference_function_names'])}, "
              f"geometry exact={data['geometry_exact_matches']}/{data['geometry_compared']}, "
              f"TEXTREL={data['text_relocations_declared']}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=Path("Elite.apk"))
    parser.add_argument("--reference", type=Path, default=Path("refs/eliteagb-0.5.2"))
    parser.add_argument("--output", type=Path, default=Path("analysis/artifact"))
    args = parser.parse_args()
    inspect(args.apk, args.reference, args.output)
