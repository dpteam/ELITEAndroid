"""Check the preview APK's actual packaged ABIs and 16KiB ELF/ZIP alignment.

Does not verify signatures or installation/runtime behavior. Use apksigner and
zipalign alongside it. Rejects an incomplete or wrongly labelled ABI archive.
"""
import argparse
import hashlib
import json
import struct
import zipfile
from pathlib import Path


ABIS = {'armeabi-v7a': (1, 40), 'arm64-v8a': (2, 183), 'x86': (1, 3), 'x86_64': (2, 62)}


def verify(path, output):
    raw = path.read_bytes()
    entries = {}
    with zipfile.ZipFile(path) as archive:
        expected = {f'lib/{abi}/lib{name}.so' for abi in ABIS for name in ('elite_core', 'elite_preview')}
        actual = {x.filename for x in archive.infolist() if x.filename.startswith('lib/') and x.filename.endswith('.so')}
        if actual != expected:
            raise ValueError(f'Wrong packaged libraries: missing={expected - actual}, extra={actual - expected}')
        for name in sorted(actual):
            abi = name.split('/')[1]
            entry = archive.getinfo(name)
            data = archive.read(name)
            if data[:4] != b'\x7fELF' or data[5] != 1:
                raise ValueError(f'Invalid little-endian ELF: {name}')
            elfclass, machine = data[4], struct.unpack_from('<H', data, 18)[0]
            if (elfclass, machine) != ABIS[abi]: raise ValueError(f'ABI mismatch: {name}')
            if elfclass == 1:
                phoff = struct.unpack_from('<I', data, 28)[0]
                phsize, phnum = struct.unpack_from('<HH', data, 42)
            else:
                phoff = struct.unpack_from('<Q', data, 32)[0]
                phsize, phnum = struct.unpack_from('<HH', data, 54)
            segments, tags = [], []
            for i in range(phnum):
                at = phoff + i * phsize
                if elfclass == 1:
                    kind, offset, va, _, size, _, flags, alignment = struct.unpack_from('<8I', data, at)
                else:
                    kind, flags, offset, va, _, size, _, alignment = struct.unpack_from('<II6Q', data, at)
                if kind == 1:
                    if alignment < 16384 or (va - offset) % 16384:
                        raise ValueError(f'Insufficient ELF segment alignment: {name}')
                    if flags & 3 == 3: raise ValueError(f'Writable-executable segment: {name}')
                    segments.append({'flags': flags, 'alignment': alignment})
                if kind == 2:
                    for j in range(offset, offset + size, 8 if elfclass == 1 else 16):
                        tags.append(struct.unpack_from('<II' if elfclass == 1 else '<QQ', data, j))
            if any(t == 22 or (t == 30 and v & 4) for t, v in tags):
                raise ValueError(f'Text relocations present: {name}')
            off = entry.header_offset
            filename_size, extra_size = struct.unpack_from('<HH', raw, off + 26)
            start = off + 30 + filename_size + extra_size
            if entry.compress_type != zipfile.ZIP_STORED or start % 16384:
                raise ValueError(f'Native library is not uncompressed / 16KiB ZIP-aligned: {name}')
            entries[name] = {'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data),
                'elf_class': elfclass * 32, 'machine': machine, 'textrel': False,
                'zip_data_offset': start, 'segments': segments}
    report = {'status': 'passed', 'apk_sha256': hashlib.sha256(raw).hexdigest(), 'apk_bytes': len(raw),
        'application': 'elite.preview — separate mesh test, not the game', 'libraries': entries,
        'limitations': ['Signature verification belongs to apksigner; this script checks archive/ELF layout only.',
            'This static check does not observe installation, JNI execution, Android lifecycle or GPU rendering; see the separate device report.']}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + '\n')
    print(f'PASS: {len(entries)} correctly labelled, uncompressed, 16KiB-aligned native libraries')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, default=Path('preview/build/outputs/apk/debug/preview-debug.apk'))
    parser.add_argument('--output', type=Path, default=Path('analysis/validation/preview-apk.json'))
    args = parser.parse_args()
    verify(args.apk, args.output)
