"""Replay selected newly compiled Android ELF routines on all four ABIs in Unicorn.

No runtime linker, constructors, JNI, Android process, files, or syscalls run.
Unresolved imports are traps. This is function-level validation, not device testing.
Requires unicorn==2.1.4; ELF metadata is parsed locally with struct.
"""
import argparse
import hashlib
import json
import random
import struct
import zipfile
from collections import Counter
from pathlib import Path

from unicorn import Uc, UC_ARCH_X86, UC_ARCH_ARM, UC_ARCH_ARM64, UC_MODE_32, UC_MODE_64, UC_MODE_ARM, UC_HOOK_CODE
from unicorn.x86_const import UC_X86_REG_EAX, UC_X86_REG_ESP, UC_X86_REG_EIP, UC_X86_REG_RAX, UC_X86_REG_RSP, UC_X86_REG_RIP, UC_X86_REG_RDI, UC_X86_REG_RSI, UC_X86_REG_RDX, UC_X86_REG_RCX, UC_X86_REG_R8, UC_X86_REG_R9
from unicorn.arm_const import UC_ARM_REG_R0, UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_R3, UC_ARM_REG_LR, UC_ARM_REG_SP, UC_ARM_REG_PC
from unicorn.arm64_const import UC_ARM64_REG_X0, UC_ARM64_REG_X1, UC_ARM64_REG_X2, UC_ARM64_REG_X3, UC_ARM64_REG_X4, UC_ARM64_REG_X5, UC_ARM64_REG_X6, UC_ARM64_REG_X7, UC_ARM64_REG_LR, UC_ARM64_REG_SP, UC_ARM64_REG_PC
from unicorn.arm_const import UC_ARM_REG_C1_C0_2, UC_ARM_REG_FPEXC
from unicorn.arm64_const import UC_ARM64_REG_CPACR_EL1
from unicorn.arm64_const import UC_ARM64_REG_TPIDR_EL0
from unicorn.x86_const import UC_X86_REG_FS_BASE
from unicorn.x86_const import UC_X86_REG_GDTR, UC_X86_REG_GS
from unicorn.x86_const import UC_X86_REG_DS, UC_X86_REG_ES, UC_X86_REG_SS, UC_X86_REG_CS


class Replay:
    BASE = 0x100000
    DATA = 0x900000
    STOP = 0xa00000
    IMPORTS = 0xb00000
    STACK = 0xc00000

    def __init__(self, path, memory_imports=False):
        self.data = path if isinstance(path, bytes) else path.read_bytes()
        data = self.data
        if data[:4] != b'\x7fELF' or data[5] != 1:
            raise ValueError('Expected little-endian ELF')
        self.bits = {1: 32, 2: 64}[data[4]]
        self.machine = struct.unpack_from('<H', data, 18)[0]
        arch, mode = {3: (UC_ARCH_X86, UC_MODE_32), 62: (UC_ARCH_X86, UC_MODE_64),
                      40: (UC_ARCH_ARM, UC_MODE_ARM), 183: (UC_ARCH_ARM64, UC_MODE_ARM)}[self.machine]
        self.uc = Uc(arch, mode)
        # Android's ABI permits VFP/NEON. Enable it in the emulated CPU; otherwise
        # compiler-generated structure copies may trap despite valid ELF code.
        if self.machine == 40:
            self.uc.reg_write(UC_ARM_REG_C1_C0_2, 0xf << 20)
            self.uc.reg_write(UC_ARM_REG_FPEXC, 1 << 30)
        elif self.machine == 183:
            self.uc.reg_write(UC_ARM64_REG_CPACR_EL1, 3 << 20)
        for start, size in ((self.BASE, 0x800000), (self.DATA, 0x10000), (self.STOP, 0x10000),
                            (self.IMPORTS, 0x10000), (self.STACK, 0x10000), (0xd00000, 0x10000)):
            self.uc.mem_map(start, size)
        # A zeroed test TLS page supplies the stack-canary slot. No Bionic
        # initialization is run; __stack_chk_fail still traps if reached.
        if self.machine == 183:
            self.uc.reg_write(UC_ARM64_REG_TPIDR_EL0, 0xd00000)
        elif self.machine == 62:
            self.uc.reg_write(UC_X86_REG_FS_BASE, 0xd00000)
        elif self.machine == 3:
            tls = 0xd01000
            descriptor = struct.pack('<HHBBBB', 0xffff, tls & 0xffff,
                                     (tls >> 16) & 255, 0xf2, 0xcf, tls >> 24)
            self.uc.mem_write(0xd00000 + 8, struct.pack('<HHBBBB', 0xffff, 0, 0, 0x92, 0xcf, 0))
            self.uc.mem_write(0xd00000 + 16, struct.pack('<HHBBBB', 0xffff, 0, 0, 0x9a, 0xcf, 0))
            self.uc.mem_write(0xd00000 + 24, descriptor)
            self.uc.reg_write(UC_X86_REG_GDTR, (0, 0xd00000, 0x1000, 0))
            for register in (UC_X86_REG_DS, UC_X86_REG_ES, UC_X86_REG_SS):
                self.uc.reg_write(register, 8)
            self.uc.reg_write(UC_X86_REG_CS, 16)
            self.uc.reg_write(UC_X86_REG_GS, 27)
        if self.bits == 32:
            phoff, shoff = struct.unpack_from('<II', data, 28)
            phsize, phnum, shsize, shnum, _ = struct.unpack_from('<5H', data, 42)
        else:
            phoff, shoff = struct.unpack_from('<QQ', data, 32)
            phsize, phnum, shsize, shnum, _ = struct.unpack_from('<5H', data, 54)
        self.segments = []
        for i in range(phnum):
            if self.bits == 32:
                kind, off, va, _, size, memsize, flags, align = struct.unpack_from('<8I', data, phoff + i * phsize)
            else:
                kind, flags, off, va, _, size, memsize, align = struct.unpack_from('<II6Q', data, phoff + i * phsize)
            if kind == 1:
                if va + memsize > 0x800000:
                    raise ValueError('Image exceeds test memory budget')
                self.uc.mem_write(self.BASE + va, data[off:off + size])
                self.segments.append({'flags': flags, 'alignment': align, 'offset': off, 'address': va})
        sections = [struct.unpack_from('<10I' if self.bits == 32 else '<II4QII2Q', data,
                                      shoff + i * shsize) for i in range(shnum)]
        def cstring(offset):
            return data[offset:data.index(b'\0', offset)].decode(errors='replace')
        def symbols(index):
            s = sections[index]
            strings = sections[s[6]][4]
            result = []
            for i in range(s[5] // s[9]):
                at = s[4] + i * s[9]
                if self.bits == 32:
                    name, value, size, info, _, sec = struct.unpack_from('<IIIBBH', data, at)
                else:
                    name, info, _, sec, value, size = struct.unpack_from('<IBBHQQ', data, at)
                result.append({'name': cstring(strings + name), 'value': value, 'size': size, 'info': info, 'sec': sec})
            return result
        self.symbols = {}
        for index, s in enumerate(sections):
            if s[1] in (2, 11):
                self.symbols.update({x['name']: x for x in symbols(index) if x['sec'] and x['name']})
        self.import_names = {}
        imports = {}
        self.textrel = False
        ptrfmt = '<I' if self.bits == 32 else '<Q'
        mask = (1 << self.bits) - 1
        for section in sections:
            if section[1] == 6:
                for off in range(section[4], section[4] + section[5], 8 if self.bits == 32 else 16):
                    tag, _ = struct.unpack_from('<II' if self.bits == 32 else '<QQ', data, off)
                    self.textrel |= tag == 22
            if section[1] not in (4, 9):
                continue
            table = symbols(section[6])
            for at in range(section[4], section[4] + section[5], section[9]):
                offset, info = struct.unpack_from('<II' if self.bits == 32 else '<QQ', data, at)
                typ = info & (255 if self.bits == 32 else 0xffffffff)
                si = info >> (8 if self.bits == 32 else 32)
                addend = (struct.unpack_from('<i' if self.bits == 32 else '<q', data, at + (8 if self.bits == 32 else 16))[0]
                          if section[1] == 4 else struct.unpack(ptrfmt, self.uc.mem_read(self.BASE + offset, self.bits // 8))[0])
                sym = table[si]
                if sym['sec']:
                    address = sym['value'] + (0 if sym['sec'] == 0xfff1 else self.BASE)
                else:
                    if sym['name'] not in imports:
                        imports[sym['name']] = self.IMPORTS + len(imports) * 16
                    address = imports[sym['name']]
                relative, absolute, glob, jump = {3: (8, 1, 6, 7), 62: (8, 1, 6, 7),
                                                40: (23, 2, 21, 22), 183: (1027, 257, 1025, 1026)}[self.machine]
                if typ == 0:
                    continue
                if typ == relative:
                    result = self.BASE + addend
                elif typ == absolute:
                    result = address + addend
                elif typ in (glob, jump):
                    result = address + (addend if self.bits == 64 else 0)
                else:
                    raise ValueError(f'Unsupported relocation {typ} on machine {self.machine}')
                self.uc.mem_write(self.BASE + offset, struct.pack(ptrfmt, result & mask))
        self.import_names = {v: k for k, v in imports.items()}
        self.memory_shim_calls = Counter()
        def trap(uc, address, size, user):
            name = self.import_names.get(address)
            if memory_imports and name in ('memset', '__memset_chk', '__aeabi_memclr', '__aeabi_memclr4', '__aeabi_memclr8'):
                args = self.arguments(4 if name == '__memset_chk' else 3)
                dest, value, length = args[:3] if name in ('memset', '__memset_chk') else (args[0], 0, args[1])
                if name == '__memset_chk' and length > args[3]:
                    raise RuntimeError('Fortified memset bounds check failed')
                if length > 65536:
                    raise RuntimeError('Memory shim exceeds test budget')
                uc.mem_write(dest, bytes([value & 255]) * length)
                self.memory_shim_calls[name] += 1
                self.return_from_call(dest)
                return
            if memory_imports and name in ('memcpy', '__memcpy_chk', '__aeabi_memcpy', '__aeabi_memcpy4', '__aeabi_memcpy8'):
                args = self.arguments(4 if name == '__memcpy_chk' else 3)
                dest, source, length = args[:3]
                if length > 65536 or (name == '__memcpy_chk' and length > args[3]):
                    raise RuntimeError('Memory copy shim exceeds bounds/budget')
                uc.mem_write(dest, bytes(uc.mem_read(source, length)))
                self.memory_shim_calls[name] += 1
                self.return_from_call(dest)
                return
            raise RuntimeError(f'Unresolved import reached: {self.import_names.get(address, hex(address))}')
        self.uc.hook_add(UC_HOOK_CODE, trap, begin=self.IMPORTS, end=self.IMPORTS + 0xffff)

    def arguments(self, count):
        if self.machine == 3:
            return struct.unpack('<' + 'I' * count, self.uc.mem_read(self.uc.reg_read(UC_X86_REG_ESP) + 4, count * 4))
        regs = {62: (UC_X86_REG_RDI, UC_X86_REG_RSI, UC_X86_REG_RDX, UC_X86_REG_RCX),
                40: (UC_ARM_REG_R0, UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_R3),
                183: (UC_ARM64_REG_X0, UC_ARM64_REG_X1, UC_ARM64_REG_X2, UC_ARM64_REG_X3)}[self.machine]
        if count > len(regs): raise ValueError('Too many register arguments for helper')
        return tuple(self.uc.reg_read(r) for r in regs[:count])

    def return_from_call(self, value):
        if self.machine in (3, 62):
            ptr = 4 if self.machine == 3 else 8
            spreg = UC_X86_REG_ESP if ptr == 4 else UC_X86_REG_RSP
            sp = self.uc.reg_read(spreg)
            ret = int.from_bytes(self.uc.mem_read(sp, ptr), 'little')
            self.uc.reg_write(spreg, sp + ptr)
            self.uc.reg_write(UC_X86_REG_EAX if ptr == 4 else UC_X86_REG_RAX, value)
            self.uc.reg_write(UC_X86_REG_EIP if ptr == 4 else UC_X86_REG_RIP, ret)
        elif self.machine == 40:
            self.uc.reg_write(UC_ARM_REG_R0, value)
            self.uc.reg_write(UC_ARM_REG_PC, self.uc.reg_read(UC_ARM_REG_LR))
        else:
            self.uc.reg_write(UC_ARM64_REG_X0, value)
            self.uc.reg_write(UC_ARM64_REG_PC, self.uc.reg_read(UC_ARM64_REG_LR))

    def invoke(self, name, *args):
        if name not in self.symbols:
            raise ValueError(f'Missing symbol {name}')
        address = self.BASE + self.symbols[name]['value']
        sp = self.STACK + 0x8000
        if self.machine == 3:
            self.uc.mem_write(sp, struct.pack('<' + 'I' * (len(args) + 1), self.STOP, *(x & 0xffffffff for x in args)))
            self.uc.reg_write(UC_X86_REG_ESP, sp)
            pc, result = UC_X86_REG_EIP, UC_X86_REG_EAX
        elif self.machine == 62:
            self.uc.mem_write(sp - 8, struct.pack('<Q', self.STOP))
            self.uc.reg_write(UC_X86_REG_RSP, sp - 8)
            for reg, value in zip((UC_X86_REG_RDI, UC_X86_REG_RSI, UC_X86_REG_RDX, UC_X86_REG_RCX, UC_X86_REG_R8, UC_X86_REG_R9), args):
                self.uc.reg_write(reg, value & 0xffffffffffffffff)
            pc, result = UC_X86_REG_RIP, UC_X86_REG_RAX
        elif self.machine == 40:
            self.uc.reg_write(UC_ARM_REG_SP, sp)
            self.uc.reg_write(UC_ARM_REG_LR, self.STOP)
            for reg, value in zip((UC_ARM_REG_R0, UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_R3), args):
                self.uc.reg_write(reg, value & 0xffffffff)
            pc, result = UC_ARM_REG_PC, UC_ARM_REG_R0
        else:
            self.uc.reg_write(UC_ARM64_REG_SP, sp)
            self.uc.reg_write(UC_ARM64_REG_LR, self.STOP)
            for reg, value in zip((UC_ARM64_REG_X0, UC_ARM64_REG_X1, UC_ARM64_REG_X2, UC_ARM64_REG_X3,
                                   UC_ARM64_REG_X4, UC_ARM64_REG_X5, UC_ARM64_REG_X6, UC_ARM64_REG_X7), args):
                self.uc.reg_write(reg, value & 0xffffffffffffffff)
            pc, result = UC_ARM64_REG_PC, UC_ARM64_REG_X0
        self.uc.emu_start(address, self.STOP, timeout=1000000, count=200000)
        if self.uc.reg_read(pc) != self.STOP:
            raise RuntimeError('Routine did not return within replay budget')
        return self.uc.reg_read(result) & 0xffffffff

    def put(self, offset, data):
        self.uc.mem_write(self.DATA + offset, bytes(data))
        return self.DATA + offset

    def read(self, offset, size):
        return bytes(self.uc.mem_read(self.DATA + offset, size))


def run(root, samples, output):
    with zipfile.ZipFile('Elite.apk') as package:
        original = Replay(package.read('lib/x86/libelite_native.so'))
    report = {'scope': 'Function-level emulation only; no Android runtime, JNI, rendering, or device install.',
              'unicorn_version': __import__('unicorn').__version__, 'samples_per_routine': samples,
              'original_x86_sha256': hashlib.sha256(original.data).hexdigest(), 'abis': {}}
    for abi in ('armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'):
        path = root / abi / 'libelite_core.so'
        lib = Replay(path)
        rng = random.Random(0xE117E)
        counts = Counter()
        def check(name, a, b):
            if a != b:
                raise AssertionError(f'{abi}: {name} mismatch at {counts[name]}: {a!r} != {b!r}')
            counts[name] += 1
        check('geometry_checksum', lib.invoke('elite_geometry_checksum'), 0xac59f864)
        for _ in range(samples):
            seed = rng.randbytes(6)
            original.put(0, seed); lib.put(0, seed)
            original.invoke('churn_seed', original.DATA)
            newcarry = lib.invoke('elite_churn_seed', lib.DATA)
            carry = struct.unpack('<I', original.uc.mem_read(original.BASE + original.symbols['g_carry']['value'], 4))[0]
            check('churn_seed', (lib.read(0, 6), newcarry), (original.read(0, 6), carry))
            values = [rng.randrange(-30000, 30001) for i in range(3)]
            for name in ('norm', 'flip_vect'):
                data = struct.pack('<3i', *values)
                original.put(0, data); lib.put(0, data)
                oldret = original.invoke(name, original.DATA)
                newret = lib.invoke(name, lib.DATA)
                check(name, lib.read(0, 12), original.read(0, 12))
                if name == 'norm': check('norm_return', newret, oldret)
            if any(values):
                original.put(0, bytes(12)); lib.put(0, bytes(12))
                original.invoke('norm_big', original.DATA, *values)
                lib.invoke('norm_big', lib.DATA, *values)
                check('norm_big', lib.read(0, 12), original.read(0, 12))
            direction = rng.randrange(256)
            data = struct.pack('<2i', *values[:2])
            original.put(0, data); lib.put(0, data)
            original.invoke('fixed_rotate', original.DATA, original.DATA + 4, direction)
            lib.invoke('fixed_rotate', lib.DATA, lib.DATA + 4, direction)
            check('fixed_rotate', lib.read(0, 8), original.read(0, 8))
            ship = bytearray(rng.randbytes(72))
            for j in range(9): struct.pack_into('<i', ship, j * 4, rng.randrange(-24576, 24577))
            target = struct.pack('<3i', *(rng.randrange(-96, 97) for j in range(3)))
            for name in ('fly_to_vector_final', 'fly_to_vector'):
                original.put(0, ship); lib.put(0, ship)
                original.put(128, target); lib.put(128, target)
                extra = (rng.randrange(-12000, 12001), rng.choice((0, 2, 6, 10))) if name.endswith('_final') else ()
                original.invoke(name, original.DATA, original.DATA + 128, *extra)
                lib.invoke(name, lib.DATA, lib.DATA + 128, *extra)
                check(name, lib.read(0, 72), original.read(0, 72))
        if lib.textrel or any(s['alignment'] < 16384 for s in lib.segments):
            raise AssertionError(f'{abi}: TEXTREL or insufficient segment alignment')
        report['abis'][abi] = {'status': 'passed', 'sha256': hashlib.sha256(lib.data).hexdigest(),
            'bits': lib.bits, 'machine': lib.machine, 'checks': dict(counts),
            'textrel': lib.textrel, 'load_segments': lib.segments}
        print(abi + ': ' + str(dict(counts)))
    report['status'] = 'passed'
    report['limitations'] = ['ELF relocation and call helpers are test harnesses, not a production Android loader.',
        'Small bounded fixed-point inputs only; no claim of all-input equivalence.',
        'Replay does not test GLES, JNI, audio, saves, UI or Android lifecycle.',
        'APK still requires its original full libelite_native.so; elite_core is only the rebuilt subset.']
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-root', type=Path, default=Path('build/native'))
    parser.add_argument('--samples', type=int, default=100)
    parser.add_argument('--output', type=Path, default=Path('analysis/validation/android-core.json'))
    args = parser.parse_args()
    if not 1 <= args.samples <= 100000: parser.error('samples must be 1..100000')
    run(args.build_root, args.samples, args.output)
