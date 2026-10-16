"""Check the NEW session composition on exact four-ABI APK libraries.

This is a new harness state machine, not an original Elite oracle. Existing
station/scene functions are composition references; traces are cross-ABI.
No Android/JNI/GPU or runtime linker runs. Uses bounded memory/TLS shims.
"""
import argparse
import hashlib
import json
import struct
import zipfile
from collections import Counter
from pathlib import Path

from replay_android_core import Replay


def run(apk, cycles, output):
    abis = ('armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64')
    report = {'scope': __doc__, 'packaged_apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
              'cycles': cycles, 'ticks_per_cycle': 60, 'snapshot_bytes': 1124, 'abis': {}}
    reference = None
    with zipfile.ZipFile(apk) as archive:
        for abi in abis:
            lib = Replay(archive.read(f'lib/{abi}/libelite_core.so'), memory_imports=True)
            counts, trace = Counter(), []

            def check(name, actual, expected):
                if actual != expected:
                    raise AssertionError(f'{abi}: {name} #{counts[name]}: {actual!r} != {expected!r}')
                counts[name] += 1

            def call(name, *args):
                return lib.invoke('elite_session_' + name, lib.DATA, *args)

            def saved(): return lib.read(0, 1124)
            def record(): trace.append(hashlib.sha256(saved()).hexdigest())

            models = lib.invoke('elite_model_count')
            model = next(i for i in range(models) if bytes(lib.uc.mem_read(
                lib.invoke('elite_model_name', i), 80)).split(b'\0')[0] == b'ship0')
            check('size', lib.invoke('elite_session_size'), 1124)
            check('reset', call('reset', model), 0)
            initial = saved()
            check('default_cash', struct.unpack_from('<I', initial, 24)[0], 5000)
            check('docked_step_reject', call('step', lib.DATA + 4096, 1), 2)
            check('docked_return_reject', call('return_test'), 2)
            check('docked_reset_scene_reject', call('reset_flight'), 2)
            check('atomic_wrong_phase', saved(), initial)
            for cycle in range(cycles):
                check('buy', call('trade', 0, 1), 0)
                station = saved()[8:212]
                check('launch', call('launch'), 0)
                launched = saved()
                check('launch_again_reject', call('launch'), 2)
                check('atomic_launch_reject', saved(), launched)
                check('flight_trade_reject', call('trade', 0, 1), 2)
                expected = bytearray(launched); struct.pack_into('<I', expected, 208, 2)
                check('only_trade_result_changed', saved(), bytes(expected))
                # Step a standalone scene with the same input, independently of
                # the session state. Reused math has its own original-code tests.
                lib.put(8192, saved()[212:])
                for tick in range(60):
                    inputs = (0, 0, 0, 0, 1 if tick < 30 else -1,
                              20 if tick < 30 else -20, 5 if tick < 30 else -5, 0, 0)
                    lib.put(4096, struct.pack('<9i', *inputs))
                    check('scene_reference_step', lib.invoke('elite_scene_step', lib.DATA + 8192, lib.DATA + 4096, 1), 0)
                    check('step', call('step', lib.DATA + 4096, 1), 0)
                    check('scene_composition', saved()[212:], lib.read(8192, 912))
                    check('commander_unchanged', saved()[16:108], station[8:100])
                    check('market_unchanged', saved()[124:192], station[116:184])
                    record()
                before = saved()
                check('too_many_ticks', call('step', lib.DATA + 4096, 5), 1)
                lib.put(4096, struct.pack('<9i', 0, 0, 0, 0, 0, -2147483648, 0, 0, 0))
                check('invalid_controls', call('step', lib.DATA + 4096, 1), 1)
                check('atomic_bad_step', saved(), before)
                check('reset_flight', call('reset_flight'), 0)
                check('reset_preserves_station', saved()[8:212], before[8:212])
                check('return_test', call('return_test'), 0)
                expected_station = bytearray(station); struct.pack_into('<I', expected_station, 200, 2)
                check('return_preserves_station', saved()[8:212], bytes(expected_station))
                check('sell', call('trade', 0, 0), 0)
                check('full_roundtrip', saved(), initial)
                record()
            for offset, value in ((0, 0), (4, 2), (204, 2), (224, 1),
                                  (352, 2147483648), (124, 3)):
                bad = bytearray(initial); struct.pack_into('<I', bad, offset, value); lib.put(0, bytes(bad))
                check('corrupt_state', call('valid'), 0)
                check('corrupt_launch_reject', call('launch'), 1)
                check('corrupt_trade_reject', call('trade', 0, 1), 1)
                check('atomic_corrupt', saved(), bytes(bad))
            if reference is None: reference = trace
            else: check('cross_abi_trace', trace, reference)
            report['abis'][abi] = {'status': 'passed', 'checks': dict(counts),
                                  'sha256': hashlib.sha256(lib.data).hexdigest(),
                                  'trace_sha256': hashlib.sha256(''.join(trace).encode()).hexdigest(),
                                  'memory_shim_calls': dict(lib.memory_shim_calls)}
            print(abi + ': ' + str(dict(counts)))
    report['status'] = 'passed'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--packaged-apk', type=Path, default=Path('preview/build/outputs/apk/debug/preview-debug.apk'))
    parser.add_argument('--cycles', type=int, default=20)
    parser.add_argument('--output', type=Path, default=Path('analysis/validation/preview-packaged-session.json'))
    args = parser.parse_args()
    if not 1 <= args.cycles <= 1000: parser.error('cycles must be 1..1000')
    run(args.packaged_apk, args.cycles, args.output)
