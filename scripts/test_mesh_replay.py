"""Compare ported ship mesh commands with APK entry()/polygon3d in Unicorn.

Only polygon-command submission and the colour-setting boundary are intercepted;
entry's original matrix, culling, visibility and coordinate code really runs.
Does not start Android, invoke GLES, or test lasers/explosions/distant effects.
"""
import argparse
import hashlib
import json
import math
import random
import struct
import zipfile
from collections import Counter
from pathlib import Path

from replay_android_core import Replay
from unicorn import UC_HOOK_CODE
from unicorn.x86_const import UC_X86_REG_EIP, UC_X86_REG_ESP


def pose(rng, distance):
    yaw, pitch, roll = [rng.uniform(-math.pi, math.pi) for _ in range(3)]
    cy, sy, cp, sp, cr, sr = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch), math.cos(roll), math.sin(roll)
    rows = ((cy*cr + sy*sp*sr, cp*sr, -sy*cr + cy*sp*sr),
            (-cy*sr + sy*sp*cr, cp*cr, sy*sr + cy*sp*cr),
            (sy*cp, -sp, cy*cp))
    data = bytearray(72)
    struct.pack_into('<9i', data, 0, *(round(x * 0x6000) for row in rows for x in row))
    struct.pack_into('<3i', data, 36, rng.randrange(-distance//4, distance//4 + 1),
                     rng.randrange(-distance//4, distance//4 + 1), distance)
    data[62] = 1
    return data


class OriginalMesh:
    def __init__(self, apk):
        with zipfile.ZipFile(apk) as package:
            self.lib = Replay(package.read('lib/x86/libelite_native.so'))
        self.commands = []
        self.polygons = self.address('polygon3d')
        self.colour = self.address('platform_set_line_color')
        def boundary(uc, address, size, user):
            stack = uc.reg_read(UC_X86_REG_ESP)
            ret = struct.unpack('<I', uc.mem_read(stack, 4))[0]
            if address == self.polygons:
                n, indices, vertices, coords, colour = struct.unpack('<5I', uc.mem_read(stack + 4, 20))
                if n > 64 or vertices > 64:
                    raise ValueError('Original polygon exceeds frame budget')
                if n:
                    index = bytes(uc.mem_read(indices, n))
                    xyz = bytes(uc.mem_read(coords, vertices * 6))
                    self.commands.append((colour, list(index), [struct.unpack_from('<3h', xyz, i * 6) for i in index]))
            uc.reg_write(UC_X86_REG_ESP, stack + 4)
            uc.reg_write(UC_X86_REG_EIP, ret)
        self.lib.uc.hook_add(UC_HOOK_CODE, boundary, begin=self.polygons, end=self.polygons)
        self.lib.uc.hook_add(UC_HOOK_CODE, boundary, begin=self.colour, end=self.colour)

    def address(self, name):
        return self.lib.BASE + self.lib.symbols[name]['value']

    def frame(self, name, data, flags):
        self.commands = []
        self.lib.uc.mem_write(self.address('g_crds'), bytes(64 * 6))
        self.lib.uc.mem_write(self.address('g_scrncrd'), bytes(64 * 8))
        self.lib.uc.mem_write(self.address('g_face_vis'), bytes(16))
        options = bytearray(7)
        options[1] = 1 if flags & 1 else 0
        options[2] = 2 if flags & 2 else 0
        self.lib.uc.mem_write(self.address('g_game_options'), bytes(options))
        self.lib.put(0, data)
        self.lib.invoke('entry', self.lib.DATA, self.address(name))
        return list(self.commands)


def unpack_frame(raw):
    status, vertices, commands = struct.unpack_from('<3I', raw)
    if status != 0:
        return status, []
    result = []
    for i in range(commands):
        count, colour = struct.unpack_from('<2B', raw, 412 + i * 66)
        index = raw[414 + i * 66:414 + i * 66 + count]
        result.append((colour, list(index), [struct.unpack_from('<3h', raw, 12 + j * 6) for j in index]))
    return status, result


def run(apk, build, samples, output, packaged_apk=None):
    original = OriginalMesh(apk)
    abis = ('armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64')
    if packaged_apk:
        with zipfile.ZipFile(packaged_apk) as archive:
            sources = {abi: archive.read(f'lib/{abi}/libelite_core.so') for abi in abis}
    else:
        sources = {abi: build / abi / 'libelite_core.so' for abi in abis}
    reference = Replay(sources['x86'])
    count = reference.invoke('elite_model_count')
    cases, missing = [], []
    rng = random.Random(0xE117E)
    for model in range(count):
        ptr = reference.invoke('elite_model_name', model)
        name = bytes(reference.uc.mem_read(ptr, 80)).split(b'\0')[0].decode()
        if name not in original.lib.symbols:
            missing.append(name)
            continue
        # Bound to the original non-distant branch, with z > near-clip issues.
        size = original.lib.uc.mem_read(original.address(name) + 29, 1)[0]
        maxdistance = min(3072, size * 256)
        for _ in range(samples):
            data = pose(rng, rng.randrange(128, max(129, maxdistance + 1)))
            for flags in (0, 1, 3):
                commands = original.frame(name, data, flags)
                cases.append((model, name, bytes(data), flags, commands))
    report = {'scope': 'Non-effect ship meshes, non-distant distances, initially cleared visibility buffers.',
        'original_sha256': hashlib.sha256(original.lib.data).hexdigest(),
        'packaged_apk_sha256': hashlib.sha256(packaged_apk.read_bytes()).hexdigest() if packaged_apk else None,
        'unicorn_version': __import__('unicorn').__version__, 'random_seed': '0xe117e',
        'models': count, 'missing_original_models': missing, 'poses_per_model': samples,
        'cases_per_abi': len(cases), 'abis': {},
        'limitations': ['polygon3d and platform_set_line_color are intercepted at the rendering boundary; GLES is not invoked.',
            'No lasers, explosions, distant-object flicker, sprites, HUD or gameplay-state changes are tested.',
            'The port explicitly rejects unsupported effects and degenerate matrices.',
            'Actual Android GPU rendering and lifecycle still need device/emulator testing.']}
    for abi in abis:
        lib = Replay(sources[abi], memory_imports=True)
        checks = Counter()
        if lib.invoke('elite_mesh_frame_size') != 4636:
            raise AssertionError(f'{abi}: ABI frame-size drift')
        for model, name, data, flags, expected in cases:
            lib.put(0, data)
            lib.put(4096, bytes(4636))
            status = lib.invoke('elite_mesh_build', model, lib.DATA, flags, lib.DATA + 4096)
            frame_status, result = unpack_frame(lib.read(4096, 4636))
            if status or frame_status or result != expected:
                raise AssertionError(f'{abi} {name} flags={flags} case={sum(checks.values())}: '
                                     f'status={status} actual={result!r} expected={expected!r}')
            checks[{0: 'wireframe', 1: 'solid', 3: 'shaded'}[flags]] += 1
        report['abis'][abi] = {'status': 'passed', 'sha256': hashlib.sha256(lib.data).hexdigest(),
                              'checks': dict(checks), 'memory_shim_calls': dict(lib.memory_shim_calls)}
        print(abi + ': ' + str(dict(checks)))
    report['status'] = 'passed'
    report['limitations'].append('Explicit bounded memset/memclr/memcpy shims replace Bionic memory routines; fortified bounds are checked; no game/graphics math is shimmed.')
    report['limitations'].append('Test TLS pages provide stack-canary slots; runtime linker/constructor/real Bionic TLS behavior is not tested.')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, default=Path('Elite.apk'))
    parser.add_argument('--build-root', type=Path, default=Path('build/native'))
    parser.add_argument('--packaged-apk', type=Path, help='Replay the actual packaged core bytes instead of standalone builds')
    parser.add_argument('--samples', type=int, default=5)
    parser.add_argument('--output', type=Path, default=Path('analysis/validation/mesh.json'))
    args = parser.parse_args()
    if not 1 <= args.samples <= 1000: parser.error('samples must be 1..1000')
    run(args.apk, args.build_root, args.samples, args.output, args.packaged_apk)
