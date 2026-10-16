"""Render native mesh packets to a PNG using a small offline software rasterizer.

This is NOT an Android/GPU screenshot. It is a reproducible visual check of
the rebuilt core's geometry and command stream. No third-party Python package.
"""
import argparse
import ctypes
import json
import math
import struct
import zlib
from pathlib import Path


PALETTE = (0x000000, 0x42ff5a, 0xffff00, 0xffffff, 0x00ffff, 0xd6d6d6,
           0x5a5aff, 0xcece42, 0xff5252, 0xff00ff, 0xce4242, 0x42ce42,
           0x4242ce, 0xce00ce, 0x00cece, 0xadadad)


def render(library, model_name, output, width, height, yaw, pitch, flags, distance):
    lib = ctypes.CDLL(str(library.resolve()))
    lib.elite_model_count.restype = ctypes.c_uint32
    lib.elite_model_name.argtypes = [ctypes.c_uint32]
    lib.elite_model_name.restype = ctypes.c_char_p
    lib.elite_mesh_build.argtypes = [ctypes.c_uint32, ctypes.c_void_p, ctypes.c_uint32, ctypes.c_void_p]
    models = [lib.elite_model_name(i).decode() for i in range(lib.elite_model_count())]
    index = models.index(model_name)
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    pose = bytearray(72)
    rows = (cy, 0, -sy, sy*sp, cp, cy*sp, sy*cp, -sp, cy*cp)
    struct.pack_into('<9i', pose, 0, *(int(x * 24576) for x in rows))
    struct.pack_into('<3i', pose, 36, 0, 0, distance)
    pose[62] = 1
    obj = (ctypes.c_ubyte * 72).from_buffer_copy(pose)
    buf = (ctypes.c_ubyte * 4636)()
    status = lib.elite_mesh_build(index, obj, flags, buf)
    if status: raise ValueError(f'Native frame status {status}')
    raw = bytes(buf)
    _, vertices, commands = struct.unpack_from('<3I', raw)
    xyz = [struct.unpack_from('<3h', raw, 12 + i * 6) for i in range(vertices)]
    pixels = bytearray((4, 5, 10)) * (width * height)
    zbuffer = [0.0] * (width * height)
    scale = min(width / 256, height / 192)

    def project(point):
        x, y, z = point
        if z >= -16: return None
        return (width/2 + x * 256 / -z * scale,
                height/2 - y * 256 / -z * scale, 1 / -z)

    def put(x, y, inverse_z, colour):
        if not 0 <= x < width or not 0 <= y < height: return
        at = y * width + x
        if inverse_z < zbuffer[at]: return
        zbuffer[at] = inverse_z
        pixels[at*3:at*3+3] = colour

    def edge(a, b, x, y):
        return (x - a[0]) * (b[1] - a[1]) - (y - a[1]) * (b[0] - a[0])

    def triangle(a, b, c, colour):
        area = edge(a, b, c[0], c[1])
        if abs(area) < 1e-8: return
        left, right = max(0, int(min(a[0], b[0], c[0]))), min(width - 1, math.ceil(max(a[0], b[0], c[0])))
        top, bottom = max(0, int(min(a[1], b[1], c[1]))), min(height - 1, math.ceil(max(a[1], b[1], c[1])))
        for y in range(top, bottom + 1):
            for x in range(left, right + 1):
                wa = edge(b, c, x + .5, y + .5) / area
                wb = edge(c, a, x + .5, y + .5) / area
                wc = 1 - wa - wb
                if min(wa, wb, wc) >= 0:
                    put(x, y, wa*a[2] + wb*b[2] + wc*c[2], colour)

    def line(a, b, colour):
        steps = max(1, int(max(abs(a[0] - b[0]), abs(a[1] - b[1]))))
        for i in range(steps + 1):
            t = i / steps
            put(round(a[0]*(1-t) + b[0]*t), round(a[1]*(1-t) + b[1]*t), a[2]*(1-t) + b[2]*t, colour)

    for i in range(commands):
        count, colour = struct.unpack_from('<2B', raw, 412 + i * 66)
        indices = raw[414 + i * 66:414 + i * 66 + count]
        points = [project(xyz[j]) for j in indices]
        if any(p is None for p in points): continue
        if flags & 2:
            grey = 0 if colour == 0 else round((32 - colour) * 255 / 31)
            rgb = bytes((grey, grey, grey))
        else:
            p = PALETTE[colour & 15]
            rgb = bytes((p >> 16, (p >> 8) & 255, p & 255))
        if count == 2: line(*points, rgb)
        else:
            for j in range(1, count - 1): triangle(points[0], points[j], points[j + 1], rgb)

    def chunk(name, data):
        return struct.pack('>I', len(data)) + name + data + struct.pack('>I', zlib.crc32(name + data) & 0xffffffff)
    rows = b''.join(b'\0' + pixels[y*width*3:(y+1)*width*3] for y in range(height))
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>2I5B', width, height, 8, 2, 0, 0, 0))
    png += chunk(b'tEXt', b'Description\0Offline native mesh rasterization; NOT an Android screenshot')
    png += chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b'')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(png)
    output.with_suffix('.json').write_text(json.dumps({'model': model_name, 'vertices': vertices,
        'commands': commands, 'flags': flags, 'yaw': yaw, 'pitch': pitch, 'distance': distance,
        'scope': 'Offline software visualization of rebuilt mesh packets, not Android or GPU execution.'}, indent=2) + '\n')
    print(f'{output}: {model_name}, {vertices} vertices, {commands} commands')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--library', type=Path, default=Path('build/native-host/libelite_core.dll'))
    parser.add_argument('--model', default='ship0')
    parser.add_argument('--output', type=Path, default=Path('build/mesh/ship0.png'))
    parser.add_argument('--width', type=int, default=768)
    parser.add_argument('--height', type=int, default=576)
    parser.add_argument('--yaw', type=float, default=.5)
    parser.add_argument('--pitch', type=float, default=.3)
    parser.add_argument('--flags', type=int, default=1, choices=(0, 1, 3))
    parser.add_argument('--distance', type=int, default=360)
    args = parser.parse_args()
    if not (16 <= args.width <= 4096 and 16 <= args.height <= 4096): parser.error('dimensions must be 16..4096')
    if not 1 <= args.distance <= 49152: parser.error('distance must be 1..49152')
    render(args.library, args.model, args.output, args.width, args.height, args.yaw, args.pitch, args.flags, args.distance)
