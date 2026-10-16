"""Import a bounded set of actual Elite AGB routines, never modifying refs/.

Generated vendor files remain local and ignored; refs/ is required to import.
Original authorship and licensing notes are retained alongside the files.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path


def function(source, name):
    # Mask comments/strings without changing offsets before balancing braces.
    masked = re.sub(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'',
                    lambda m: ' ' * len(m[0]), source, flags=re.S)
    match = re.search(r'^\s*(?:void|int)\s+' + re.escape(name) + r'\s*\([^;{}]*\)\s*\{', masked, re.M)
    if match is None:
        raise ValueError(f'Missing function definition: {name}')
    start = match.start()
    depth = 0
    for i in range(masked.index('{', start), len(masked)):
        depth += (masked[i] == '{') - (masked[i] == '}')
        if depth == 0:
            return source[start:i + 1].strip() + '\n'
    raise ValueError(f'Unterminated function: {name}')


def prepare(ref, dest):
    dest.mkdir(parents=True, exist_ok=True)
    records = {}
    def read(name):
        data = (ref / name).read_bytes()
        records[name] = hashlib.sha256(data).hexdigest()
        return data.decode('utf-8')
    def write(name, text):
        (dest / name).write_text(text, encoding='utf-8', newline='\n')

    banner = '/* Imported from Elite AGB 0.5.2; see native/ORIGIN.md and upstream-readme.txt. */\n'
    for name in ('shipdata.h', 'motion.h', 'tactics.h', 'encyclo.h', 'colours.h', 'killvalues.h', 'c64_killvalues.h'):
        text = read('includes/' + name)
        if name == 'motion.h':
            text = '#include <stdint.h>\n' + text
            text = text.replace('signed long int', 'int32_t').replace('signed long', 'int32_t')
        if name == 'shipdata.h':
            text = text.replace('const unsigned char ship_flags[32][2];', 'extern const unsigned char ship_flags[32][2];')
            text = text.replace('const unsigned char * ship_flags[23];', 'extern const unsigned char * ship_flags[23];')
        write(name, banner + text)
    shipdata = read('shipdata.c')
    write('shipdata.c', banner + shipdata)
    visible_shipdata = re.sub(r'/\*.*?\*/|//[^\n]*', lambda m: ' ' * len(m[0]), shipdata, flags=re.S)
    arrays = re.findall(r'const\s+(vertex_t|line_t|face_t)\s+(\w+)\[(\d+)\]\s*=', visible_shipdata)
    geometry = '#include "elite_core.h"\n'
    for kind, name, count in arrays:
        geometry += f'extern const {kind} {name}[{count}];\n'
    geometry += '''
static uint32_t hash_u32(uint32_t hash, uint32_t value) {
    for (unsigned i = 0; i < 4; ++i) { hash = (hash ^ (value & 255u)) * 16777619u; value >>= 8; }
    return hash;
}
static uint32_t hash_byte(uint32_t hash, uint8_t value) { return (hash ^ value) * 16777619u; }
uint32_t elite_geometry_checksum(void) {
    uint32_t hash = 2166136261u;
'''
    for kind, name, count in arrays:
        geometry += f'    for (unsigned i = 0; i < {count}; ++i) {{\n'
        for field in ('x', 'y', 'z', 'vanishpoint') if kind != 'line_t' else ('vanishpoint',):
            geometry += f'        hash = hash_u32(hash, (uint32_t){name}[i].{field});\n'
        for field, length in (('faces', 4),) if kind == 'vertex_t' else ((('faces', 2), ('vertices', 2)) if kind == 'line_t' else ()):
            geometry += f'        for (unsigned j = 0; j < {length}; ++j) hash = hash_byte(hash, {name}[i].{field}[j]);\n'
        geometry += '    }\n'
    geometry += '    return hash;\n}\n'
    write('geometry_checksum.c', banner + geometry)
    write('upstream-readme.txt', read('readme.txt'))

    motion = read('motion.c')
    math = '#include <stdlib.h>\n#include "elite_core.h"\n#include "motion.h"\n#define X_ROW 0\n#define Z_ROW 2\n'
    for name in ('norm', 'dot_product', 'tidy', 'fixed_rotate'):
        text = function(motion, name)
        text = text.replace('signed long', 'int32_t').replace('bios_sqrt(RQ)', 'elite_sqrt_u32(RQ == 0 ? 1u : (uint32_t)RQ)')
        math += '\n' + text
    write('motion_math.c', banner + math)

    autopilot = read('autopilot.c')
    text = '#include <stdlib.h>\n#include "motion.h"\n#define ROTATE_AMOUNT 3\n#define X_ROW 0\n#define Y_ROW 1\n#define Z_ROW 2\n'
    for name in ('norm_big', 'fly_to_vector_final', 'fly_to_vector', 'flip_vect'):
        piece = function(autopilot, name)
        if name == 'norm_big':
            piece = piece.replace('int total = abs(x)|abs(y)|abs(z);',
                'int total = abs(x)|abs(y)|abs(z);\n  /* Avoid the legacy infinite loop for the zero vector. */\n  if (total == 0) { v->x = v->y = v->z = 0; return; }')
        text += '\n' + piece
    write('autopilot_math.c', banner + text)
    write('import-manifest.json', json.dumps({'reference': 'Elite AGB 0.5.2', 'inputs_sha256': records,
        'changes': ['motion vector and fixed_rotate long fields/pointers -> int32_t',
            'ship_flags declaration -> extern for modern -fno-common toolchains',
            'norm: APK-compatible zero-length guard and portable sqrt',
            'norm_big: explicit zero-vector guard (intentional safety fix)',
            'Only math/autopilot functions listed in scripts/prepare_native.py are imported; no hardware stubs.']}, indent=2) + '\n')
    print(f'Imported gameplay data and math into {dest}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference', type=Path, default=Path('refs/eliteagb-0.5.2'))
    parser.add_argument('--output', type=Path, default=Path('native/vendor/eliteagb'))
    args = parser.parse_args()
    prepare(args.reference, args.output)
