"""Bounded offline replay of selected original APK x86 routines vs the rebuilt core.

Requires unicorn==2.1.4. No APK/JNI/process startup and no syscalls are allowed.
Original instructions run only in Unicorn mapped memory; the rebuilt host DLL
is loaded via ctypes. Missing imports trap rather than silently return a value.
"""
import argparse
import ctypes as C
import hashlib
import json
import math
import random
import struct
import zipfile
from collections import Counter
from pathlib import Path

from inspect_native import Elf32
from unicorn import Uc, UC_ARCH_X86, UC_MODE_32, UC_HOOK_CODE
from unicorn.x86_const import UC_X86_REG_ESP, UC_X86_REG_EAX, UC_X86_REG_EFLAGS


class Original:
    BASE = 0x100000
    DATA = 0x800000
    STOP = 0x900000
    STACK = 0xb00000

    def __init__(self, data):
        self.elf = Elf32(data)
        self.uc = Uc(UC_ARCH_X86, UC_MODE_32)
        self.uc.mem_map(self.BASE, 0x400000)
        self.uc.mem_map(self.DATA, 0x10000)
        self.uc.mem_map(self.STOP, 0x10000)
        self.uc.mem_map(0xa00000, 0x10000)
        self.uc.mem_map(self.STACK, 0x10000)
        self.names = {s['name']: s for s in self.elf.symbols if s['name'] and s['section_index']}
        phoff = struct.unpack_from('<I', data, 28)[0]
        phsize, phnum = struct.unpack_from('<HH', data, 42)
        for i in range(phnum):
            kind, off, va, _, size, _, _, _ = struct.unpack_from('<8I', data, phoff + i * phsize)
            if kind == 1:
                self.uc.mem_write(self.BASE + va, data[off:off + size])
        imports = {}
        for sec in self.elf.sections:
            if sec['type'] != 9:
                continue
            symtable = self.elf.sections[sec['link']]
            strings = self.elf.section_bytes(self.elf.sections[symtable['link']])
            for off in range(sec['offset'], sec['offset'] + sec['size'], 8):
                at, info = struct.unpack_from('<II', data, off)
                typ, si = info & 255, info >> 8
                addend = struct.unpack('<I', self.uc.mem_read(self.BASE + at, 4))[0]
                no, value, _, _, _, index = struct.unpack_from('<IIIBBH', data, symtable['offset'] + si * 16)
                name = Elf32.cstring(strings, no)
                if index:
                    address = value + (0 if index == 0xfff1 else self.BASE)
                else:
                    if name not in imports:
                        imports[name] = 0xa00000 + len(imports) * 16
                    address = imports[name]
                if typ == 8:
                    result = self.BASE + addend
                elif typ in (6, 7):
                    result = address
                elif typ == 1:
                    result = address + addend
                elif typ == 2:
                    result = address + addend - (self.BASE + at)
                else:
                    raise ValueError(f'Unsupported relocation {typ}')
                self.uc.mem_write(self.BASE + at, struct.pack('<I', result & 0xffffffff))
        self.import_names = {v: k for k, v in imports.items()}
        def trap(uc, address, size, user):
            raise RuntimeError(f'Unexpected external import: {self.import_names.get(address, hex(address))}')
        self.uc.hook_add(UC_HOOK_CODE, trap, begin=0xa00000, end=0xa0ffff)

    def invoke(self, name, *args):
        sp = self.STACK + 0x8000
        self.uc.mem_write(sp, struct.pack('<' + 'I' * (len(args) + 1), self.STOP,
            *(a & 0xffffffff for a in args)))
        self.uc.reg_write(UC_X86_REG_ESP, sp)
        self.uc.reg_write(UC_X86_REG_EFLAGS, 2)
        self.uc.emu_start(self.BASE + self.names[name]['address'], self.STOP,
                          timeout=1000000, count=100000)
        # A timeout/instruction cap must never count as a successful return.
        from unicorn.x86_const import UC_X86_REG_EIP
        if self.uc.reg_read(UC_X86_REG_EIP) != self.STOP:
            raise RuntimeError(f'{name} did not return within replay budget')
        return C.c_int32(self.uc.reg_read(UC_X86_REG_EAX)).value

    def write(self, offset, data):
        self.uc.mem_write(self.DATA + offset, bytes(data))
        return self.DATA + offset

    def read(self, offset, size):
        return bytes(self.uc.mem_read(self.DATA + offset, size))


class Vec(C.Structure):
    _fields_ = [('x', C.c_int32), ('y', C.c_int32), ('z', C.c_int32)]


def run(apk, library, samples, output):
    data = zipfile.ZipFile(apk).read('lib/x86/libelite_native.so')
    original = Original(data)
    rebuilt = C.CDLL(str(library.resolve()))
    rebuilt.elite_geometry_checksum.restype = C.c_uint32
    rebuilt.elite_churn_seed.argtypes = [C.POINTER(C.c_uint8)]
    rebuilt.elite_churn_seed.restype = C.c_uint32
    rebuilt.elite_sqrt_u32.argtypes = [C.c_uint32]
    rebuilt.elite_sqrt_u32.restype = C.c_uint32
    rebuilt.norm.argtypes = [C.POINTER(Vec)]
    rebuilt.norm.restype = C.c_int
    rebuilt.norm_big.argtypes = [C.POINTER(Vec), C.c_int, C.c_int, C.c_int]
    rebuilt.fixed_rotate.argtypes = [C.POINTER(C.c_int32), C.POINTER(C.c_int32), C.c_int]
    rebuilt.flip_vect.argtypes = [C.POINTER(Vec)]
    rebuilt.fly_to_vector_final.argtypes = [C.c_void_p, C.POINTER(Vec), C.c_int, C.c_int]
    rebuilt.fly_to_vector.argtypes = [C.c_void_p, C.POINTER(Vec)]
    rebuilt.tidy.argtypes = [C.c_void_p]
    rng = random.Random(0xE117E)
    counts = Counter()
    def check(condition, routine):
        if not condition:
            raise AssertionError(f'Differential mismatch in {routine}, case {counts[routine]}')
        counts[routine] += 1

    for _ in range(samples):
        seed = bytes(rng.randrange(256) for i in range(6))
        p = original.write(0, seed)
        original.invoke('churn_seed', p)
        carry = struct.unpack('<I', original.uc.mem_read(original.BASE + original.names['g_carry']['address'], 4))[0]
        newseed = (C.c_uint8 * 6).from_buffer_copy(seed)
        newcarry = rebuilt.elite_churn_seed(newseed)
        check(bytes(newseed) == original.read(0, 6) and carry == newcarry, 'churn_seed')

        values = [rng.randrange(-30000, 30001) for i in range(3)]
        p = original.write(0, struct.pack('<3i', *values))
        v = Vec(*values)
        ret = original.invoke('norm', p)
        newret = rebuilt.norm(C.byref(v))
        check(ret == newret and bytes(v) == original.read(0, 12), 'norm')
        original.write(0, struct.pack('<3i', *values))
        v = Vec(*values)
        original.invoke('flip_vect', p)
        rebuilt.flip_vect(C.byref(v))
        check(bytes(v) == original.read(0, 12), 'flip_vect')

        # norm_big intentionally fixes zero-vector hanging; omit it from original replay.
        if any(values):
            original.write(0, b'\0' * 12)
            v = Vec()
            original.invoke('norm_big', p, *values)
            rebuilt.norm_big(C.byref(v), *values)
            check(bytes(v) == original.read(0, 12), 'norm_big')

        x, y = values[:2]
        direction = rng.randrange(256)
        original.write(0, struct.pack('<2i', x, y))
        newx, newy = C.c_int32(x), C.c_int32(y)
        original.invoke('fixed_rotate', original.DATA, original.DATA + 4, direction)
        rebuilt.fixed_rotate(C.byref(newx), C.byref(newy), direction)
        check(struct.pack('<2i', newx.value, newy.value) == original.read(0, 8), 'fixed_rotate')

        ship = bytearray(rng.randbytes(72))
        for j in range(9):
            struct.pack_into('<i', ship, j * 4, rng.randrange(-24576, 24577))
        target = Vec(*(rng.randrange(-96, 97) for i in range(3)))
        for name in ('fly_to_vector_final', 'fly_to_vector'):
            original.write(0, ship)
            original.write(128, bytes(target))
            newship = (C.c_uint8 * 72).from_buffer_copy(ship)
            angle, limit = rng.randrange(-12000, 12001), rng.choice((0, 2, 6, 10))
            if name == 'fly_to_vector_final':
                original.invoke(name, original.DATA, original.DATA + 128, angle, limit)
                rebuilt.fly_to_vector_final(newship, C.byref(target), angle, limit)
            else:
                original.invoke(name, original.DATA, original.DATA + 128)
                rebuilt.fly_to_vector(newship, C.byref(target))
            check(bytes(newship) == original.read(0, 72), name)

    # Compare all compiled numeric geometry by the exact ELF symbol bytes.
    inventory = json.loads(Path('analysis/artifact/inventory.json').read_text())['architectures']['x86']['geometry']
    hash_value = 2166136261
    for item in inventory:
        sym = original.names.get(item['name'])
        if not sym:
            # names includes objects and functions, but not undefined entries
            raise ValueError(item['name'])
        actual = original.elf.read(sym['address'], sym['size'])
        compiled = bytes((C.c_uint8 * len(actual)).in_dll(rebuilt, item['name']))
        check(compiled == actual, 'geometry_array')
        for b in actual:
            hash_value = ((hash_value ^ b) * 16777619) & 0xffffffff
    check(hash_value == rebuilt.elite_geometry_checksum(), 'geometry_checksum')

    # Degenerate/reliability fixes are not equated to original behavior.
    zeros = Vec()
    check(rebuilt.norm(C.byref(zeros)) == 1, 'zero_norm_guard')
    rebuilt.norm_big(C.byref(zeros), 0, 0, 0)
    check(bytes(zeros) == bytes(12), 'zero_norm_big_guard')
    for value in (0, 1, 2, 65535, 65536, 16777215, 16777216, 0xfffffffe, 0xffffffff):
        check(rebuilt.elite_sqrt_u32(value) == math.isqrt(value), 'integer_sqrt')
    result = {'status': 'passed', 'random_seed': '0xe117e', 'samples': samples,
        'original_sha256': hashlib.sha256(data).hexdigest(),
        'rebuilt_host_sha256': hashlib.sha256(library.read_bytes()).hexdigest(),
        'checks': dict(counts), 'geometry_checksum': f'{hash_value:08x}',
        'limitations': ['Original x86 instructions replayed offline in Unicorn; no Android runtime or GLES/JNI startup.',
            'Rebuilt routines executed as a Windows x64 DLL; Android binaries are cross-compiled separately.',
            'Bounded input domains: small fixed-point vectors and matrix entries, not arbitrary int32 overflow inputs.',
            'norm_big zero-vector handling is an intentional safety fix, not an original behavior match.',
            'Integer square root is exact; legacy float32 rounding at large values is not reproduced.']}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, default=Path('Elite.apk'))
    parser.add_argument('--library', type=Path, default=Path('build/native-host/libelite_core.dll'))
    parser.add_argument('--samples', type=int, default=1000)
    parser.add_argument('--output', type=Path, default=Path('analysis/validation/native-core.json'))
    args = parser.parse_args()
    if not 1 <= args.samples <= 100000:
        parser.error('--samples must be between 1 and 100000')
    run(args.apk, args.library, args.samples, args.output)
