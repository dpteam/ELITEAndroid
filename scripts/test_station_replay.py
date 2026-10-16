"""Replay station math against original APK x86 instructions and four new ABIs.

Market generation is exhaustive over economy/random-byte domains. Original
buy/sell UI wrappers are run, but display/audio boundaries are intercepted.
No Android runtime, full station loop, jumps, persistence or UI is executed.
"""
import argparse
import hashlib
import json
import random as random_module
import struct
import zipfile
from collections import Counter
from pathlib import Path
from unicorn import UC_HOOK_CODE
from replay_android_core import Replay


class OriginalStation:
    # Ghidra displays these with image base 0x10000; ELF linked addresses below
    # subtract that analysis-only rebase (runtime test BASE is added later).
    BUY = 0x1cab2
    SELL = 0x1ca24

    def __init__(self, apk):
        with zipfile.ZipFile(apk) as archive:
            self.lib = Replay(archive.read('lib/x86/libelite_native.so'))
        # Private wrappers verified from show_market_prices callback table and
        # shipped x86 decompilation; addresses bound to this exact input digest.
        digest = hashlib.sha256(self.lib.data).hexdigest()
        if digest != '1bdbafd395a016035a60892dba359426987721fec02338c1cc2b603039784360':
            raise ValueError('Private callback addresses require the exact supplied x86 artifact')
        self.lib.symbols['replay_buy'] = {'value': self.BUY}
        self.lib.symbols['replay_sell'] = {'value': self.SELL}
        self.tokens = []
        self.boundaries = Counter()
        def boundary(uc, address, size, user):
            name = self.boundary_names[address]
            if name == 'text2buffer': self.tokens.append(self.lib.arguments(1)[0])
            if name == 'platform_screen_size':
                width, height = self.lib.arguments(2)
                if width: uc.mem_write(width, struct.pack('<i', 256))
                if height: uc.mem_write(height, struct.pack('<i', 192))
            self.boundaries[name] += 1
            self.lib.return_from_call(0)
        names = ('clear_row', 'set_text_x', 'show_stock', 'update_cash', 'play_sound', 'text2buffer', 'platform_screen_size')
        self.boundary_names = {self.address(n): n for n in names}
        for at in self.boundary_names:
            self.lib.uc.hook_add(UC_HOOK_CODE, boundary, begin=at, end=at)

    def address(self, name): return self.lib.BASE + self.lib.symbols[name]['value']
    def set(self, name, data): self.lib.uc.mem_write(self.address(name), bytes(data))
    def read(self, name, length): return bytes(self.lib.uc.mem_read(self.address(name), length))
    def market(self, economy, random):
        commander = bytearray(92); commander[40] = random
        self.set('g_commander', commander); self.set('cpl_info', bytes([economy]) + bytes(7))
        self.lib.invoke('calculate_market')
        return self.read('g_market_prices', 68), self.read('g_commander', 92)[67:84]
    def info(self, seed, x, y):
        commander = bytearray(92); commander[31:33] = bytes((x, y))
        self.set('g_commander', commander); self.set('hyp_coord', bytes((seed[3], seed[1])))
        self.set('hyp_seed', seed)
        self.lib.invoke('set_hyp_info')
        return self.read('hyp_info', 8)
    def name(self, seed):
        self.tokens = []
        self.lib.put(0, seed)
        self.lib.invoke('christen_planet', self.lib.DATA)
        # The callback boundary yields original name digram tokens. Decode only
        # the literal QQ16 table; the rest of the text engine is out of scope.
        pointer = struct.unpack('<I', self.read('QQ16', 4))[0]
        pairs = bytes(self.lib.uc.mem_read(pointer, 90))
        name = bytearray()
        for token in self.tokens:
            i = token & 127
            name.append(pairs[26 + i * 2])
            if pairs[27 + i * 2] != ord('?'): name.append(pairs[27 + i * 2])
        return bytes(name)
    def nearest(self, galaxy, x, y):
        commander = bytearray(92); commander[33:39] = galaxy
        self.set('g_commander', commander)
        self.set('g_last_found', struct.pack('<i', -1))
        self.lib.put(0, b'\xa5' * 6)
        self.lib.invoke('get_planet_at', x, y, self.lib.DATA)
        return struct.unpack('<i', self.read('g_last_found', 4))[0], self.lib.read(0, 6)
    def trade(self, commander, prices, item, buy):
        self.set('g_commander', commander); self.set('g_market_prices', prices)
        changed = self.lib.invoke('replay_buy' if buy else 'replay_sell', item)
        return changed != 0, self.read('g_commander', 92)


def run(apk, root, output, packaged_apk=None):
    original = OriginalStation(apk)
    rng = random_module.Random(0xe1180)
    expected_market = {(economy, random): original.market(economy, random)
                       for economy in range(8) for random in range(256)}
    galaxy = bytes((0x4a, 0x5a, 0x48, 0x02, 0x53, 0xb7))
    systems = []
    # Original churn instruction stream generates the oracle seeds.
    current = galaxy
    for index in range(256):
        info = original.info(current, 20, 173)
        name = original.name(current)
        systems.append((current, info, name))
        original.lib.put(0, current)
        for _ in range(4): original.lib.invoke('churn_seed', original.lib.DATA)
        current = original.lib.read(0, 6)
    nearest = [(x, y, original.nearest(galaxy, x, y))
               for x, y in [(20, 173), (0, 0), (255, 255)] + [(rng.randrange(256), rng.randrange(256)) for _ in range(50)]]
    # Valid bounded trading snapshots, including depleted stock, full holds,
    # no funds, reverse trades and kg/g cargo. Overflow fixes tested separately.
    trades = []
    for i in range(1000):
        economy, random = rng.randrange(8), rng.randrange(256)
        prices, stock = expected_market[(economy, random)]
        commander = bytearray(original.read('default_commander', 92))
        commander[67:84] = stock
        commander[50:67] = bytes(rng.randrange(4) for _ in range(17))
        struct.pack_into('<H', commander, 48, rng.randrange(5, 60))
        struct.pack_into('<I', commander, 8, rng.randrange(20000) if i % 5 else 0)
        item, buy = rng.randrange(17), i % 2
        trades.append((bytes(commander), prices, item, buy, original.trade(commander, prices, item, buy)))
    abis = ('armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64')
    if packaged_apk:
        with zipfile.ZipFile(packaged_apk) as archive:
            sources = {abi: archive.read(f'lib/{abi}/libelite_core.so') for abi in abis}
    else: sources = {abi: root / abi / 'libelite_core.so' for abi in abis}
    report = {'scope': 'Station math and transaction state only, not original UI/lifecycle/save/jump behavior.',
              'original_sha256': hashlib.sha256(original.lib.data).hexdigest(),
              'packaged_apk_sha256': hashlib.sha256(packaged_apk.read_bytes()).hexdigest() if packaged_apk else None,
              'abis': {}, 'limitations': ['Original trade wrappers are replayed with explicit UI/audio boundary interception.',
                'UI layout query platform_screen_size is supplied a fixed 256x192; no renderer is initialized.',
                'Name comparison captures original text digram tokens and decodes QQ16, not the full text renderer.',
                'Market values exhaustive for 8 economies and 256 random bytes; transactions are bounded sampled states.',
                'Overflow guards intentionally reject legacy uint8/credit wrapping and are not claimed equivalent.',
                'No Android runtime, persistence, trade UI, hyperspace transition, mission or legal-status effects observed.']}
    for abi in abis:
        lib = Replay(sources[abi], memory_imports=True)
        counts = Counter()
        def check(name, actual, expected):
            if actual != expected:
                raise AssertionError(f'{abi} {name} case {counts[name]}: {actual!r} != {expected!r}')
            counts[name] += 1
        check('state_size', lib.invoke('elite_station_size'), 204)
        # Verify commander and table data independently of gameplay code.
        for name, length in (('default_commander', 92), ('market_control', 68)):
            check(name + '_bytes', bytes(lib.uc.mem_read(lib.BASE + lib.symbols[name]['value'], length)), original.read(name, length))
        for (economy, random), expected in expected_market.items():
            status = lib.invoke('elite_market_generate', economy, random, lib.DATA, lib.DATA + 128)
            check('market', (status, lib.read(0, 68), lib.read(128, 17)), (0, *expected))
        for index, (seed, info, name) in enumerate(systems):
            lib.put(0, galaxy)
            check('seed_status', lib.invoke('elite_system_seed', lib.DATA, index, lib.DATA + 128), 0)
            check('system_seed', lib.read(128, 6), seed)
            lib.invoke('elite_system_info', lib.DATA + 128, 20, 173, lib.DATA + 256)
            check('system_info', lib.read(256, 8), info)
            lib.invoke('elite_system_name', lib.DATA + 128, lib.DATA + 256)
            check('system_name', lib.read(256, 12).split(b'\0')[0], name)
        for x, y, expected in nearest:
            lib.put(0, galaxy); lib.put(128, b'\xa5' * 6)
            idx = lib.invoke('elite_nearest_system', lib.DATA, x, y, lib.DATA + 128)
            idx = idx if idx < 0x80000000 else idx - 0x100000000
            check('nearest', (idx, lib.read(128, 6)), expected)
        for commander, prices, item, buy, (changed, expected) in trades:
            state = bytearray(204)
            struct.pack_into('<2I', state, 0, 0x4553544e, 1)
            state[8:100] = commander; state[116:184] = prices
            struct.pack_into('<I', state, 196, 1)
            lib.put(0, state)
            result = lib.invoke('elite_station_trade', lib.DATA, item, buy)
            check('trade', (result == 0, lib.read(8, 92)), (changed, expected))
        report['abis'][abi] = {'status': 'passed', 'checks': dict(counts), 'sha256': hashlib.sha256(lib.data).hexdigest()}
        print(abi + ': ' + str(dict(counts)))
    report['original_boundary_calls'] = dict(original.boundaries)
    report['status'] = 'passed'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, default=Path('Elite.apk'))
    parser.add_argument('--build-root', type=Path, default=Path('build/native'))
    parser.add_argument('--packaged-apk', type=Path)
    parser.add_argument('--output', type=Path, default=Path('analysis/validation/station.json'))
    args = parser.parse_args()
    run(args.apk, args.build_root, args.output, args.packaged_apk)
