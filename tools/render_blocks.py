#!/usr/bin/env python3
"""Draws the models with their textures on, into build/render/, the way the game would.

    python3 tools/render_blocks.py                  # every scene
    python3 tools/render_blocks.py cable_run        # one
    python3 tools/render_blocks.py --list

The checkers measure geometry against tables and textures against models.  This is for the faults that
are only visible: something in the wrong place, the wrong size, or an oval where a circle was drawn.

Two conventions it has to match or it lies.  A machine's v is flipped (the mod's loader does
1 - texCoord.y) and a cable block model's is not.  A cable block model has shade_quads off, so no
directional shading is applied to it.  SCENES is a layout per fault found this way.
"""

import json
import math
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from texlib import Canvas                                                       # noqa: E402

ASSETS = os.path.join('src', 'main', 'resources', 'assets', 'electricity')
MODELS = os.path.join(ASSETS, 'models')
TEXTURES = os.path.join(ASSETS, 'textures')
OUT = os.path.join('build', 'render')

# The one light direction the whole mod is drawn from
LIGHT = (-0.46, 0.78, 0.42)
# How dark a face pointing away from the light goes.
# north/south, 0.6 east/west, 0.5 down; this is smooth rather than per-face, and lands in that range.
AMBIENT = 0.46


# ---------------------------------------------------------------- reading a PNG

def read_png(path):
    """A PNG as (width, height, rows of RGBA bytes).  Enough of the format for this mod's own files."""
    data = open(path, 'rb').read()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise SystemExit('%s is not a PNG' % path)

    pos, idat, palette, alpha, header = 8, bytearray(), None, None, None
    while pos < len(data):
        length, kind = struct.unpack('>I', data[pos:pos + 4])[0], data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + length]
        if kind == b'IHDR':
            header = struct.unpack('>IIBBBBB', payload)
        elif kind == b'IDAT':
            idat += payload
        elif kind == b'PLTE':
            palette = payload
        elif kind == b'tRNS':
            alpha = payload
        elif kind == b'IEND':
            break
        pos += 12 + length

    width, height, depth, colour, _, _, interlace = header
    if depth != 8 or interlace:
        raise SystemExit('%s: %d-bit%s is not read here' % (path, depth, ', interlaced' if interlace else ''))

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
    raw = zlib.decompress(bytes(idat))
    stride = width * channels
    rows, previous = [], bytearray(stride)
    for y in range(height):
        start = y * (stride + 1)
        filter_type = raw[start]
        line = bytearray(raw[start + 1:start + 1 + stride])
        for i in range(stride):
            left = line[i - channels] if i >= channels else 0
            up = previous[i]
            corner = previous[i - channels] if i >= channels else 0
            if filter_type == 1:
                line[i] = (line[i] + left) & 0xff
            elif filter_type == 2:
                line[i] = (line[i] + up) & 0xff
            elif filter_type == 3:
                line[i] = (line[i] + (left + up) // 2) & 0xff
            elif filter_type == 4:
                p = left + up - corner
                candidates = ((abs(p - left), left), (abs(p - up), up), (abs(p - corner), corner))
                line[i] = (line[i] + min(candidates)[1]) & 0xff
        previous = line
        rows.append(_to_rgba(line, width, channels, palette, alpha))
    return width, height, rows


def _to_rgba(line, width, channels, palette, alpha):
    out = bytearray(width * 4)
    for x in range(width):
        source = line[x * channels:(x + 1) * channels]
        if channels == 4:
            out[x * 4:x * 4 + 4] = source
        elif channels == 3:
            out[x * 4:x * 4 + 3], out[x * 4 + 3] = source, 255
        elif channels == 2:
            out[x * 4:x * 4 + 3] = bytes(source[:1]) * 3
            out[x * 4 + 3] = source[1]
        elif palette is not None:
            index = source[0]
            out[x * 4:x * 4 + 3] = palette[index * 3:index * 3 + 3]
            out[x * 4 + 3] = alpha[index] if alpha is not None and index < len(alpha) else 255
        else:
            out[x * 4:x * 4 + 3] = bytes(source) * 3
            out[x * 4 + 3] = 255
    return out


class Texture:
    """A loaded PNG, sampled by uv with v measured from the top"""

    _cache = {}

    def __init__(self, path):
        self.w, self.h, self.rows = read_png(path)

    @classmethod
    def get(cls, path):
        if path not in cls._cache:
            cls._cache[path] = cls(path)
        return cls._cache[path]

    def sample(self, u, v):
        x = min(self.w - 1, max(0, int(u * self.w)))
        y = min(self.h - 1, max(0, int(v * self.h)))
        row = self.rows[y]
        return row[x * 4], row[x * 4 + 1], row[x * 4 + 2], row[x * 4 + 3]


# ---------------------------------------------------------------- reading a model

def texture_path(reference):
    """``electricity:block/dc_core`` to the file it is."""
    namespace, _, path = reference.partition(':')
    if namespace != 'electricity':
        raise SystemExit('%s is not this mod\'s texture' % reference)
    return os.path.join(TEXTURES, *path.split('/')) + '.png'


def read_mtl(path, resolve):
    """material name to Texture."""
    out, current = {}, None
    for line in open(path):
        parts = line.split()
        if not parts:
            continue
        if parts[0] == 'newmtl':
            current = parts[1]
        elif parts[0] == 'map_Kd' and current:
            name = parts[1]
            if name.startswith('#'):
                file_path = texture_path(resolve[name[1:]])
            else:
                file_path = os.path.join(TEXTURES, 'block', name)
            out[current] = Texture.get(file_path) if os.path.exists(file_path) else None
    return out


def read_obj(path, resolve, flip_v=False, shade=True):
    """Triangles as (group, Texture, three (position, uv) pairs, normal, shade).

    ``flip_v: false``, so v zero is the top row of the PNG; the mod's own renderer does
    """
    directory = os.path.dirname(path)
    verts, uvs, normals, materials = [], [], [], {}
    group, material, out = 'root', None, []
    for line in open(path):
        parts = line.split()
        if not parts:
            continue
        head = parts[0]
        if head == 'mtllib':
            materials = read_mtl(os.path.join(directory, parts[1]), resolve)
        elif head == 'v':
            verts.append(tuple(float(v) for v in parts[1:4]))
        elif head == 'vt':
            v = float(parts[2])
            uvs.append((float(parts[1]), 1.0 - v if flip_v else v))
        elif head == 'vn':
            normals.append(tuple(float(v) for v in parts[1:4]))
        elif head == 'o':
            group = parts[1]
        elif head == 'usemtl':
            material = parts[1]
        elif head == 'f':
            fields = [f.split('/') for f in parts[1:]]
            corners = [(verts[int(f[0]) - 1],
                        uvs[int(f[1]) - 1] if len(f) > 1 and f[1] else (0.0, 0.0)) for f in fields]
            first = fields[0]
            normal = normals[int(first[2]) - 1] if len(first) > 2 and first[2] else None
            if normal is None:
                normal = _face_normal([c[0] for c in corners])
            for i in range(1, len(corners) - 1):
                out.append((group, materials.get(material), (corners[0], corners[i], corners[i + 1]),
                            normal, shade))
    return out


def _face_normal(points):
    a, b, c = points[0], points[1], points[2]
    u = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
    v = (c[0] - a[0], c[1] - a[1], c[2] - a[2])
    n = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
    length = math.sqrt(sum(k * k for k in n)) or 1.0
    return tuple(k / length for k in n)


def block_model(name):
    """A ``forge:obj`` block model's triangles, with its own textures map resolving the MTL's ``#``."""
    model = json.load(open(os.path.join(ASSETS, 'models', 'block', name + '.json')))
    obj = os.path.join(ASSETS, *model['model'].split(':')[1].split('/'))
    return read_obj(obj, dict(model['textures']), shade=model.get('shade_quads', True))


def machine_model(name, obj=None):
    """One of the machines, drawn by the mod's own renderer, so authored about the block's centre.

    ``obj`` where the file is not named after its directory, which electric_cab/cab.obj is.
    """
    triangles = read_obj(os.path.join(MODELS, name, (obj or name) + '.obj'), {}, flip_v=True)
    return [(g, t, tuple(((p[0] + 0.5, p[1], p[2] + 0.5), uv) for p, uv in c), n, s)
            for g, t, c, n, s in triangles]


# ---------------------------------------------------------------- placing and turning

def placed(triangles, offset=(0.0, 0.0, 0.0), yaw=0, drop=()):
    """A model's triangles moved into the world, turned the way a blockstate's ``y`` turns them."""
    quarters = (yaw // 90) % 4
    out = []
    for group, texture, corners, normal, shade in triangles:
        if any(group.startswith(prefix) for prefix in drop):
            continue

        moved = []
        for point, uv in corners:
            x, z = point[0] - 0.5, point[2] - 0.5
            for _ in range(quarters):
                x, z = -z, x
            moved.append(((x + 0.5 + offset[0], point[1] + offset[1], z + 0.5 + offset[2]), uv))
        nx, nz = normal[0], normal[2]
        for _ in range(quarters):
            nx, nz = -nz, nx
        out.append((group, texture, tuple(moved), (nx, normal[1], nz), shade))
    return out


def ground(x0, z0, x1, z1, texture, y=0.0):
    """A floor to see things stand on, tiled a block to a tile."""
    tile = Texture.get(texture_path(texture))
    out = []
    for x in range(int(x0), int(x1)):
        for z in range(int(z0), int(z1)):
            corners = (((x, y, z + 1), (0.0, 1.0)), ((x + 1, y, z + 1), (1.0, 1.0)),
                       ((x + 1, y, z), (1.0, 0.0)), ((x, y, z), (0.0, 0.0)))
            for i in (1, 2):
                out.append(('ground', tile, (corners[0], corners[i], corners[i + 1]),
                            (0.0, 1.0, 0.0), True))
    return out


# ---------------------------------------------------------------- drawing

def outline(boxes, offset=(0.0, 0.0, 0.0), yaw=0):
    """The twelve edges of each collision box, in world space: what F3+B draws in the game.

    A hitbox is the only thing a player sees that no render showed, and a box round nothing looks exactly
    like a box round something.  Shapes come from the generator's own tables, so this is the game's own
    outline rather than a drawing of it.
    """
    quarters = (yaw // 90) % 4
    segments = []
    for lo, hi in boxes:
        corners = []
        for x in (lo[0], hi[0]):
            for y in (lo[1], hi[1]):
                for z in (lo[2], hi[2]):
                    a, b = x - 0.5, z - 0.5
                    for _ in range(quarters):
                        a, b = -b, a
                    corners.append((a + 0.5 + offset[0], y + offset[1], b + 0.5 + offset[2]))
        # the pairs that differ in exactly one axis of the three-bit corner index
        for i in range(8):
            for bit in (1, 2, 4):
                if i & bit:
                    continue
                segments.append((corners[i], corners[i | bit]))
    return segments


def render(triangles, eye, target, path, size=1400, fov=42.0, up=(0.0, 1.0, 0.0), edges=()):
    """A z-buffered pass with perspective-correct texture sampling."""
    forward = _unit(_sub(target, eye))
    right = _unit(_cross(forward, up))
    above = _cross(right, forward)
    focal = 0.5 * size / math.tan(math.radians(fov) * 0.5)

    depth = [1e30] * (size * size)
    canvas = Canvas(size, size, (96, 108, 122, 255))

    def project(point):
        offset = _sub(point, eye)
        z = _dot(offset, forward)
        if z <= 0.05:
            return None
        inverse = focal / z
        return (size * 0.5 + _dot(offset, right) * inverse,
                size * 0.5 - _dot(offset, above) * inverse, z)

    for _, texture, corners, normal, shade in triangles:
        lit = 1.0 if not shade else \
            AMBIENT + (1.0 - AMBIENT) * max(0.0, sum(normal[i] * LIGHT[i] for i in range(3)))
        screen = []
        for point, uv in corners:
            offset = _sub(point, eye)
            z = _dot(offset, forward)
            if z <= 0.05:
                screen = None
                break
            inverse = focal / z
            screen.append((size * 0.5 + _dot(offset, right) * inverse,
                           size * 0.5 - _dot(offset, above) * inverse, 1.0 / z, uv))
        if screen is None:
            continue

        _triangle(canvas, depth, size, screen, texture, lit)

    for a, b in edges:
        first, second = project(a), project(b)
        if first and second:
            _line(canvas, depth, size, first, second)

    canvas.write(path)
    return path


def _line(canvas, depth, size, first, second, colour=(24, 24, 28)):
    """A depth-tested line, biased towards the eye so an edge on a face is not swallowed by it."""
    steps = max(2, int(max(abs(second[0] - first[0]), abs(second[1] - first[1]))) + 1)
    for k in range(steps + 1):
        t = k / steps
        x = first[0] + (second[0] - first[0]) * t
        y = first[1] + (second[1] - first[1]) * t
        z = first[2] + (second[2] - first[2]) * t
        px, py = int(x), int(y)
        if not (0 <= px < size and 0 <= py < size):
            continue

        index = py * size + px
        if z - 0.004 >= depth[index]:
            continue

        depth[index] = z - 0.004
        canvas.px[index] = list(colour) + [255]


def _triangle(canvas, depth, size, screen, texture, lit):
    (x0, y0, w0, t0), (x1, y1, w1, t1), (x2, y2, w2, t2) = screen
    area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
    if abs(area) < 1e-9:
        return

    lo_x = max(0, int(min(x0, x1, x2)))
    hi_x = min(size - 1, int(max(x0, x1, x2)) + 1)
    lo_y = max(0, int(min(y0, y1, y2)))
    hi_y = min(size - 1, int(max(y0, y1, y2)) + 1)
    for py in range(lo_y, hi_y + 1):
        sy = py + 0.5
        for px in range(lo_x, hi_x + 1):
            sx = px + 0.5
            a = ((x1 - sx) * (y2 - sy) - (x2 - sx) * (y1 - sy)) / area
            b = ((x2 - sx) * (y0 - sy) - (x0 - sx) * (y2 - sy)) / area
            c = 1.0 - a - b
            if a < 0.0 or b < 0.0 or c < 0.0:
                continue

            w = a * w0 + b * w1 + c * w2
            index = py * size + px
            if w <= 0.0 or 1.0 / w >= depth[index]:
                continue

            u = (a * t0[0] * w0 + b * t1[0] * w1 + c * t2[0] * w2) / w
            v = (a * t0[1] * w0 + b * t1[1] * w1 + c * t2[1] * w2) / w
            if texture is None:
                colour = (150, 150, 155, 255)
            else:
                colour = texture.sample(u % 1.0 if u != 1.0 else 1.0 - 1e-9,
                                        v % 1.0 if v != 1.0 else 1.0 - 1e-9)
            if colour[3] < 24:
                continue

            depth[index] = 1.0 / w
            canvas.px[index] = [min(255, int(colour[i] * lit)) for i in range(3)] + [255]


def _sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def _dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _unit(v):
    length = math.sqrt(_dot(v, v)) or 1.0
    return (v[0] / length, v[1] / length, v[2] / length)


# ---------------------------------------------------------------- the scenes

SAND = 'electricity:block/dc_trench'
CABLE = 'dc_string_cable_'


def cable_piece(kind, offset, yaw=0):
    return placed(block_model(CABLE + kind), offset, yaw)


def scene_cable_run():
    """A run that goes straight, turns, tees and ends, so every join is in one picture."""
    triangles = ground(-1, -1, 8, 8, SAND)
    # a straight run west to east along z = 1, turning north at x = 4
    for x in range(0, 4):
        triangles += cable_piece('line', (x, 0, 1), yaw=90)
        for side in (90, 270):
            triangles += cable_piece('arm', (x, 0, 1), yaw=side)
    triangles += cable_piece('bend', (4, 0, 1), yaw=180)          # in from the west, out to the north
    triangles += cable_piece('arm', (4, 0, 1), yaw=270)
    triangles += cable_piece('arm', (4, 0, 1), yaw=0)
    for z in (0,):
        triangles += cable_piece('line', (4, 0, z), yaw=0)
        for side in (0, 180):
            triangles += cable_piece('arm', (4, 0, z), yaw=side)

    # a tee at x = 2 on the same run, with a spur going south, and the spur ending in plugs
    triangles += cable_piece('tee', (2, 0, 1), yaw=90)
    triangles += cable_piece('arm', (2, 0, 1), yaw=180)
    triangles += cable_piece('end', (2, 0, 2), yaw=0)
    triangles += cable_piece('arm', (2, 0, 2), yaw=0)
    # and an offcut lying on its own two blocks away
    triangles += cable_piece('loose', (1, 0, 4))
    triangles += cable_piece('cross', (6, 0, 4))
    for side in (0, 90, 180, 270):
        triangles += cable_piece('arm', (6, 0, 4), yaw=side)
        triangles += cable_piece('end', (6 + (side == 90) - (side == 270), 0,
                                        4 + (side == 180) - (side == 0)), yaw=(side + 180) % 360)
        triangles += cable_piece('arm', (6 + (side == 90) - (side == 270), 0,
                                        4 + (side == 180) - (side == 0)), yaw=(side + 180) % 360)
    return triangles, (2.6, 3.4, 8.2), (3.2, 0.1, 2.0)


def scene_cable_corner():
    """One bend, close enough to see whether it is a radius and whether the two cores stay parallel."""
    triangles = ground(-1, -1, 3, 3, SAND)
    triangles += cable_piece('bend', (1, 0, 1), yaw=0)
    triangles += cable_piece('arm', (1, 0, 1), yaw=0)
    triangles += cable_piece('arm', (1, 0, 1), yaw=90)
    triangles += cable_piece('line', (1, 0, 0), yaw=0)
    triangles += cable_piece('arm', (1, 0, 0), yaw=0)
    triangles += cable_piece('arm', (1, 0, 0), yaw=180)
    triangles += cable_piece('line', (2, 0, 1), yaw=90)
    triangles += cable_piece('arm', (2, 0, 1), yaw=90)
    triangles += cable_piece('arm', (2, 0, 1), yaw=270)
    return triangles, (0.4, 1.15, 2.3), (1.7, 0.06, 1.2)


def scene_cable_junction():
    """The tee's own fittings up close: the box, and the four cable glands through its walls."""
    triangles = ground(-1, -1, 4, 4, SAND)
    triangles += cable_piece('tee', (1, 0, 1), yaw=0)
    for side in (0, 90, 270):
        triangles += cable_piece('arm', (1, 0, 1), yaw=side)
    triangles += cable_piece('line', (1, 0, 0), yaw=0)
    triangles += cable_piece('arm', (1, 0, 0), yaw=0)
    triangles += cable_piece('arm', (1, 0, 0), yaw=180)
    return triangles, (0.55, 0.62, 2.35), (1.5, 0.06, 1.45)


def scene_cable_hitbox():
    """A dead end with its collision drawn, which is the view every cable fault has been reported from.

    The boxes come from gen_cable_models' own tables, so this is what F3+B shows in the game.
    """
    import gen_cable_models as cable

    triangles = ground(-1, -1, 4, 4, SAND)
    triangles += cable_piece('end', (1, 0, 1), yaw=0)
    triangles += cable_piece('arm', (1, 0, 1), yaw=0)
    triangles += cable_piece('line', (1, 0, 0), yaw=0)
    triangles += cable_piece('arm', (1, 0, 0), yaw=0)
    triangles += cable_piece('arm', (1, 0, 0), yaw=180)

    boxes = [(tuple(v / 16.0 for v in lo), tuple(v / 16.0 for v in hi))
             for part in cable.piece_end(0.0) for lo, hi in part.boxes()]
    boxes += [(tuple(v / 16.0 for v in lo), tuple(v / 16.0 for v in hi))
              for part in cable.piece_arm(0.0, 0.0) for lo, hi in part.boxes()]
    edges = outline(cable.merged(boxes), (1, 0, 1))
    return triangles, (0.45, 0.80, 2.65), (1.55, 0.08, 1.45), edges


def scene_cable_plug():
    """The plugs from three quarters above, which is where a player standing over one looks from.

    Down the axis they read as one dark disc whatever the profile does: the steps only show off-axis.
    """
    triangles = ground(-1, -1, 3, 3, SAND)
    triangles += cable_piece('end', (1, 0, 1), yaw=0)
    triangles += cable_piece('arm', (1, 0, 1), yaw=0)
    triangles += cable_piece('loose', (1, 0, 2))
    return triangles, (0.35, 0.95, 2.85), (1.5, 0.05, 1.55)


# Every machine is authored facing north, which is -z, so a scene that wants to see the face a player
# reads has to stand on that side of it.  The first pass of these looked at four backs.
def scene_inverter_front():
    """The door, from where a player reads it: the plate, the lights, the screen, the handle."""
    triangles = ground(-2, -2, 4, 4, SAND)
    triangles += placed(machine_model('pv_inverter'), (1, 0, 1))
    return triangles, (1.05, 0.78, -0.55), (1.4, 0.58, 0.8)


def scene_inverter_roof():
    """The roof, from above and to one side: the fans, the lifting eyes, the insulator."""
    triangles = ground(-2, -2, 4, 4, SAND)
    triangles += placed(machine_model('pv_inverter'), (1, 0, 1))
    return triangles, (2.55, 2.15, -0.75), (1.45, 0.98, 1.05)


def scene_combiner_front():
    """The combiner's door and its switch"""
    triangles = ground(-2, -2, 4, 4, SAND)
    triangles += placed(machine_model('pv_combiner'), (1, 0, 1))
    return triangles, (1.15, 0.66, -0.5), (1.45, 0.52, 0.85)


def scene_power_box():
    triangles = ground(-2, -2, 4, 4, SAND)
    triangles += placed(machine_model('power_box'), (1, 0, 1))
    return triangles, (1.1, 0.82, -0.55), (1.45, 0.62, 0.85)


def scene_cab():
    triangles = ground(-3, -3, 6, 6, SAND)
    triangles += placed(machine_model('electric_cab', 'cab'), (1, 0, 1))
    return triangles, (-1.9, 2.6, -3.4), (1.45, 1.0, 1.1)


def scene_pole():
    triangles = ground(-4, -4, 8, 8, SAND)
    triangles += placed(machine_model('utility_pole'), (2, 0, 2))
    return triangles, (-1.4, 3.0, 6.4), (2.5, 2.6, 2.5)


def scene_tower():
    triangles = ground(-6, -6, 12, 12, SAND)
    triangles += placed(machine_model('lattice_suspension'), (3, 0, 3))  # noqa: E501
    return triangles, (-4.0, 6.0, 12.0), (3.5, 5.0, 3.5)


def scene_machine_cable():
    """A laid run arriving at a machine, which is the join that has to be one product."""
    triangles = ground(-2, -2, 6, 6, SAND)
    triangles += placed(machine_model('pv_inverter'), (2, 0, 2), drop=('entry_east', 'entry_west',
                                                                      'entry_south', 'blank'))
    for z in (0, 1):
        triangles += cable_piece('line', (2, 0, z), yaw=0)
        for side in (0, 180):
            triangles += cable_piece('arm', (2, 0, z), yaw=side)
    return triangles, (0.55, 0.85, -1.0), (2.2, 0.25, 1.4)


def scene_array_harness():
    """Where a row's own harness ends: the plugs the row behind it mates with, on the ground at the edge.

    Two rows a block apart, so the socket on one and the lead of the other are in the same picture - which
    is what has to line up for a string to read as one product.
    """
    triangles = ground(-2, -2, 6, 6, SAND)
    for z in (1, 2):
        triangles += placed(machine_model('pv_tilt'), (2, 0, z))
    triangles += placed(machine_model('pv_track'), (4, 0, 1))
    return triangles, (0.9, 0.75, 0.2), (2.6, 0.16, 1.35)


def scene_tx_machine():
    triangles = ground(-3, -3, 6, 6, SAND)
    triangles += placed(machine_model('tx_machine'), (2, 0, 2))
    return triangles, (0.85, 0.95, -0.2), (2.4, 0.55, 1.75)


def scene_tx_substation():
    triangles = ground(-4, -4, 8, 8, SAND)
    triangles += placed(machine_model('tx_substation'), (3, 0, 3))
    return triangles, (0.6, 2.1, -1.1), (3.4, 1.1, 2.7)


def scene_ground_conductor():
    """The three conductors laid on the ground: a run along x, a bend, a dead end and an offcut of each.

    A run along x is the `line` piece turned a quarter with arms east and west - the same thing the
    blockstate applies, which is what makes this a picture of the game rather than of the OBJ files.
    """
    triangles = ground(-1, -1, 10, 8, SAND)
    for row, name in enumerate(('abc_conductor_run', 'mv_conductor_run', 'hv_conductor_run')):
        z = 1 + row * 2
        for x in range(0, 4):
            triangles += placed(block_model(name + '_line'), (x, 0, z), yaw=90)
            for side in (90, 270):
                triangles += placed(block_model(name + '_arm'), (x, 0, z), yaw=side)
        # the bend: in from the west, out to the north
        triangles += placed(block_model(name + '_bend'), (4, 0, z), yaw=180)
        triangles += placed(block_model(name + '_arm'), (4, 0, z), yaw=270)
        triangles += placed(block_model(name + '_arm'), (4, 0, z), yaw=0)
        # the dead end, one block on from the bend
        triangles += placed(block_model(name + '_end'), (4, 0, z - 1), yaw=180)
        triangles += placed(block_model(name + '_arm'), (4, 0, z - 1), yaw=180)
        triangles += placed(block_model(name + '_loose'), (7, 0, z))
    return triangles, (1.2, 2.4, -2.2), (4.0, 0.1, 3.0)


SCENES = {
    'ground_conductor': scene_ground_conductor,
    'tx_machine': scene_tx_machine,
    'tx_substation': scene_tx_substation,
    'machine_cable': scene_machine_cable,
    'array_harness': scene_array_harness,
    'cable_run': scene_cable_run,
    'cable_corner': scene_cable_corner,
    'cable_junction': scene_cable_junction,
    'cable_hitbox': scene_cable_hitbox,
    'cable_plug': scene_cable_plug,
    'inverter_front': scene_inverter_front,
    'inverter_roof': scene_inverter_roof,
    'combiner_front': scene_combiner_front,
    'power_box': scene_power_box,
    'cab': scene_cab,
    'pole': scene_pole,
    'tower': scene_tower,
}


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    if '--list' in sys.argv:
        print('\n'.join(sorted(SCENES)))
        return

    size = 1400
    for flag in sys.argv[1:]:
        if flag.startswith('--size='):
            size = int(flag.split('=')[1])

    for name in args or sorted(SCENES):
        scene = SCENES[name]()
        triangles, eye, target = scene[:3]
        edges = scene[3] if len(scene) > 3 else ()
        path = render(triangles, eye, target, os.path.join(OUT, name + '.png'), size=size,
                      edges=edges)
        print('%-16s %6d triangles  %s' % (name, len(triangles), path))


if __name__ == '__main__':
    main()
