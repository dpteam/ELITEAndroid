"""Bounded flight/control replay against original APK machine code on four ABIs.

No complete game, Android process or sensor is launched. The sole original
hardware boundary substituted here is platform_get_tilt, supplied test values.
"""
import argparse
import hashlib
import json
import random
import struct
import zipfile
from collections import Counter
from pathlib import Path

from replay_android_core import Replay
from test_mesh_replay import pose
from unicorn import UC_HOOK_CODE


class OriginalFlight:
    def __init__(self, apk):
        with zipfile.ZipFile(apk) as archive:
            self.lib = Replay(archive.read('lib/x86/libelite_native.so'))
        self.tilt = (0, 0)
        self.tilt_reads = 0
        def tilt(uc, address, size, user):
            x, y = self.lib.arguments(2)
            uc.mem_write(x, struct.pack('<i', self.tilt[0]))
            uc.mem_write(y, struct.pack('<i', self.tilt[1]))
            self.tilt_reads += 1
            self.lib.return_from_call(0)
        at = self.address('platform_get_tilt')
        self.lib.uc.hook_add(UC_HOOK_CODE, tilt, begin=at, end=at)

    def address(self, name): return self.lib.BASE + self.lib.symbols[name]['value']
    def write_int(self, name, value): self.lib.uc.mem_write(self.address(name), struct.pack('<i', value))
    def read_int(self, name): return struct.unpack('<i', self.lib.uc.mem_read(self.address(name), 4))[0]

    def controls(self, state, input, profile):
        for name, value in zip(('player_alpha', 'player_beta', 'player_delta', 'g_alpha', 'g_beta', 'g_delta'), state):
            self.write_int(name, value)
        keys = bytes((x & 255 for x in input[:5])) + bytes(8)
        self.lib.uc.mem_write(self.address('g_virtual_keys'), keys)
        self.tilt = tuple(input[5:7])
        stats = bytearray(32); stats[15] = input[7]
        self.lib.uc.mem_write(self.address('g_my_stats'), bytes(stats))
        self.write_int('g_hyperspace_counter', input[8])
        commander = bytearray(92); commander[14] = profile
        self.lib.uc.mem_write(self.address('g_commander'), bytes(commander))
        self.lib.invoke('update_motion')
        return tuple(self.read_int(n) for n in ('player_alpha', 'player_beta', 'player_delta', 'g_alpha', 'g_beta', 'g_delta'))

    def move(self, name, object, motion, index):
        for field, value in zip(('g_alpha', 'g_beta', 'g_delta', 'g_mcount', 'total_beta'), motion):
            self.write_int(field, value)
        self.lib.put(0, object)
        self.lib.invoke('mveit', self.lib.DATA, self.address(name), index)
        return self.lib.read(0, 72), self.read_int('total_beta')


def run(apk, build, samples, ticks, output, packaged_apk=None):
    original = OriginalFlight(apk)
    abis = ('armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64')
    if packaged_apk:
        with zipfile.ZipFile(packaged_apk) as archive:
            sources = {abi: archive.read(f'lib/{abi}/libelite_core.so') for abi in abis}
    else: sources = {abi: build / abi / 'libelite_core.so' for abi in abis}
    report = {'original_sha256': hashlib.sha256(original.lib.data).hexdigest(),
        'packaged_apk_sha256': hashlib.sha256(packaged_apk.read_bytes()).hexdigest() if packaged_apk else None,
        'random_seed': '0xe117f', 'samples': samples, 'control_ticks_per_profile': ticks,
        'abis': {}, 'limitations': ['Object-level movement and control math only, not update_universe, collisions, AI, rendering or a complete flight game.',
            'Original platform_get_tilt is explicitly replaced with supplied integer test input; no sensor/JNI/Android runtime is executed.',
            'Only bounded valid poses and game-like speed/control inputs; invalid-input safety is covered separately by C tests.',
            'Fire-laser callbacks and button/menu polling are excluded; all unhandled external calls trap.',
            'New test-scene traces are compared between rebuilt ABIs, not to an original game scene: scheduling and synthetic reference stars are new code.',
            'Bounded memory-copy/clearing shims and test TLS are supplied for the compiled code; no game/control math is shimmed.']}
    for abi in abis:
        lib = Replay(sources[abi], memory_imports=True)
        rng = random.Random(0xe117f)
        counts = Counter()
        def check(name, actual, expected):
            if actual != expected:
                raise AssertionError(f'{abi} {name} case {counts[name]}: actual={actual!r}, expected={expected!r}')
            counts[name] += 1
        for profile in range(15):
            ptr = lib.invoke('elite_profile_at', profile)
            expected = bytes(original.lib.uc.mem_read(original.address('player_ship_data') + profile * 18, 18))
            check('profile_bytes', bytes(lib.uc.mem_read(ptr, 18)), expected)
            state = (0, 0, 0, 0, 0, 10)
            for tick in range(ticks):
                # Sustained input, release damping, reversals, queued acceleration,
                # tilt clamping, suppression during hyperspace/autopilot, AP sentinel.
                right, left = (1, 0) if tick % 80 < 30 else (0, 1) if tick % 80 < 60 else (0, 0)
                up, down = (1, 0) if tick % 100 < 40 else (0, 1) if tick % 100 < 80 else (0, 0)
                if tick % 53 == 0: right, left = 127, 0
                if tick % 47 == 0: left = 3
                tilt = [rng.randrange(-100, 101), rng.randrange(-100, 101)] if tick % 3 == 0 else [0, 0]
                inputs = (right, left, up, down, 1 if tick % 60 < 30 else -1,
                          *tilt, int(tick % 7 == 0), int(tick % 11 == 0))
                expected = original.controls(state, inputs, profile)
                lib.put(0, struct.pack('<6i', *state)); lib.put(128, struct.pack('<9i', *inputs))
                status = lib.invoke('elite_controls_step', lib.DATA, lib.DATA + 128, profile)
                result = struct.unpack('<6i', lib.read(0, 24))
                check('control_tick', (status, result), (0, expected))
                state = result
        model_count = lib.invoke('elite_model_count')
        for case in range(samples):
            model = rng.randrange(model_count)
            ptr = lib.invoke('elite_model_name', model)
            name = bytes(lib.uc.mem_read(ptr, 80)).split(b'\0')[0].decode()
            object = pose(rng, rng.randrange(1000, 12001))
            struct.pack_into('<hh', object, 52, rng.randrange(0, 61), rng.randrange(-3, 4))
            object[56] = rng.choice((0, 1, 7, 127, 128, 129, 135, 255))
            object[57] = rng.choice((0, 1, 7, 127, 128, 129, 135, 255))
            object[62] = rng.choice((1, 0, 255, 254))
            index = rng.randrange(12)
            counter = index if case % 3 == 0 else rng.randrange(256)
            motion = (rng.randrange(-32, 33), rng.randrange(-8, 9), rng.randrange(-60, 61),
                      counter, rng.randrange(-100000, 100001))
            expected, total_beta = original.move(name, object, motion, index)
            lib.put(0, object); lib.put(128, struct.pack('<5i', *motion))
            status = lib.invoke('elite_object_step', model, lib.DATA, lib.DATA + 128, index)
            check('mveit_tick', (status, lib.read(0, 72), struct.unpack('<5i', lib.read(128, 20))),
                  (0, expected, (*motion[:4], total_beta)))
        # Continuous state drift / periodic tidy(), not only reset-per-case calls.
        name = 'ship36'
        model = 0
        for i in range(model_count):
            ptr = lib.invoke('elite_model_name', i)
            if bytes(lib.uc.mem_read(ptr, 80)).split(b'\0')[0].decode() == name: model = i
        object = pose(rng, 5000)
        struct.pack_into('<3i', object, 36, 0, 0, 5000)
        motion = (1, 1, 8, 255, 0)
        for tick in range(1200):
            alpha = 2 if tick % 120 < 60 else -2
            beta = 1 if tick % 160 < 80 else -1
            motion = (alpha, beta, 8, (255 - tick) & 255, motion[4])
            expected, total_beta = original.move(name, object, motion, 2)
            lib.put(0, object); lib.put(128, struct.pack('<5i', *motion))
            status = lib.invoke('elite_object_step', model, lib.DATA, lib.DATA + 128, 2)
            check('mveit_continuous_tick', (status, lib.read(0, 72)), (0, expected))
            object = expected
            motion = (*motion[:4], total_beta)

        # New harness state across architectures. This is not an original game
        # scene equivalence claim: star generation/scheduling are design choices.
        lib.put(4096, bytes(912))
        check('scene_reset', lib.invoke('elite_scene_reset', lib.DATA + 4096, model, 0), 0)
        states = []
        for tick in range(600):
            inputs = (0, 0, 0, 0, 1 if tick % 60 < 30 else -1,
                      20 if tick % 120 < 60 else -20, 5 if tick % 80 < 40 else -5, 0, 0)
            lib.put(128, struct.pack('<9i', *inputs))
            check('scene_tick', lib.invoke('elite_scene_step', lib.DATA + 4096, lib.DATA + 128, 1), 0)
            states.append(hashlib.sha256(lib.read(4096, 912)).hexdigest())
        if abi == abis[0]: scene_reference = states
        else: check('cross_abi_scene_trace', states, scene_reference)
        report['abis'][abi] = {'status': 'passed', 'checks': dict(counts),
                              'sha256': hashlib.sha256(lib.data).hexdigest()}
        print(abi + ': ' + str(dict(counts)))
    report['original_tilt_boundary_calls'] = original.tilt_reads
    report['status'] = 'passed'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, default=Path('Elite.apk'))
    parser.add_argument('--build-root', type=Path, default=Path('build/native'))
    parser.add_argument('--packaged-apk', type=Path)
    parser.add_argument('--samples', type=int, default=300)
    parser.add_argument('--ticks', type=int, default=200)
    parser.add_argument('--output', type=Path, default=Path('analysis/validation/flight.json'))
    args = parser.parse_args()
    if not 1 <= args.samples <= 10000 or not 1 <= args.ticks <= 10000: parser.error('samples/ticks must be 1..10000')
    run(args.apk, args.build_root, args.samples, args.ticks, args.output, args.packaged_apk)
