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

import objlib                                                                    # noqa: E402
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


def read_obj(path, resolve, flip_v=False, shade=True, cull=True, forge_groups=True):
    """Triangles as (group, Texture, three (position, uv) pairs, normal, shade, cull).

    ``flip_v: false``, so v zero is the top row of the PNG; the mod's own renderer does 1 - v.
    ``cull`` because the two pipelines differ: a block model is baked into the chunk with RenderType.solid,
    which culls by winding, while ObjRendererBase draws a machine with entityCutoutNoCull, which does not.
    ``forge_groups`` is the other difference and objlib.forge_only explains it.  A face with no stated normal
    gets one from its winding, which is what the game does and what only a *renderer* may do.
    """
    faces = objlib.read(path)
    if forge_groups:
        faces = objlib.forge_only(faces)

    materials = read_mtl(os.path.join(os.path.dirname(path), objlib.material_library(path)), resolve)
    out = []
    for face in faces:
        corners = [(point, _uv(uv, flip_v)) for point, uv in zip(face.points, face.uvs)]
        normal = face.normal or _face_normal(face.points)
        for index in range(1, len(corners) - 1):
            out.append((face.group, materials.get(face.material),
                        (corners[0], corners[index], corners[index + 1]), normal, shade, cull))

    return out


def _uv(uv, flip_v):
    if uv is None:
        return (0.0, 0.0)

    return (uv[0], 1.0 - uv[1] if flip_v else uv[1])


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
    triangles = read_obj(os.path.join(MODELS, name, (obj or name) + '.obj'), {}, flip_v=True,
                         cull=False, forge_groups=False)
    return [(g, t, tuple(((p[0] + 0.5, p[1], p[2] + 0.5), uv) for p, uv in c), n, s, k)
            for g, t, c, n, s, k in triangles]


# ---------------------------------------------------------------- placing and turning

def placed(triangles, offset=(0.0, 0.0, 0.0), yaw=0, drop=()):
    """A model's triangles moved into the world, turned the way a blockstate's ``y`` turns them."""
    quarters = (yaw // 90) % 4
    out = []
    for group, texture, corners, normal, shade, cull in triangles:
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
        out.append((group, texture, tuple(moved), (nx, normal[1], nz), shade, cull))
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
                            (0.0, 1.0, 0.0), True, False))
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

    for _, texture, corners, normal, shade, cull in triangles:
        # Culled by *winding*, the way a GPU does it, not by the stated normal: a quad wound the wrong way
        # is a hole in the world and was a solid face in here.
        if cull and _dot(_face_normal([point for point, _ in corners]), _sub(corners[0][0], eye)) > 0.0:
            continue

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


def see_through(triangles, eye, target, size=520, up=(0.0, 1.0, 0.0), inside=None):
    """Pixels where the nearest surface is back-facing: places a player sees the inside of the model.

    Rasterised twice - the solid shape as the chunk renderer draws it, and the same shape with culling off
    - so a pixel the second pass covers and the first does not has no front face on it at all, and what a
    player sees there is the inside.  That is "in molti punti ci sono zone trasparenti", measured.
    """
    forward = _unit(_sub(target, eye))
    right = _unit(_cross(forward, up))
    above = _cross(right, forward)
    focal = 0.5 * size / math.tan(math.radians(42.0) * 0.5)

    def pass_over(cull):
        depth = [1e30] * (size * size)
        seen = bytearray(size * size)
        # Whether the nearest surface at each pixel points at the ground.  The band between a tube's
        # silhouette and where it touches the sand is back-facing and has no front face over it, but the
        # ground is behind it and a player sees sand - so it is not a hole, and it is the whole of what a
        # cable lying on the floor contributes here.
        downward = bytearray(size * size)
        for _, _, corners, _, _, _ in triangles:
            points = [point for point, _ in corners]
            normal = _face_normal(points)
            if cull and _dot(normal, _sub(points[0], eye)) > 0.0:
                continue
            screen = []
            for point in points:
                offset = _sub(point, eye)
                z = _dot(offset, forward)
                if z <= 0.05:
                    screen = None
                    break
                inverse = focal / z
                screen.append((size * 0.5 + _dot(offset, right) * inverse,
                               size * 0.5 - _dot(offset, above) * inverse, 1.0 / z))
            if screen is None:
                continue
            _cover(seen, depth, size, screen, downward, normal[1] < -0.5)
        return seen, downward

    def to_screen(point):
        offset = _sub(point, eye)
        z = _dot(offset, forward)
        if z <= 0.05:
            return None
        inverse = focal / z
        return (size * 0.5 + _dot(offset, right) * inverse, size * 0.5 - _dot(offset, above) * inverse)

    # ``inside`` bounds the region that is being judged.  A composed run's outermost tube ends are open by
    # design - the block past them is where the run carries on - so counting them is counting the edge of
    # the picture, not a fault in the piece.
    window = None
    if inside:
        corners = [to_screen(p) for p in inside]
        corners = [c for c in corners if c]
        if corners:
            window = (min(c[0] for c in corners), min(c[1] for c in corners),
                      max(c[0] for c in corners), max(c[1] for c in corners))

    (solid, _), (both, underside) = pass_over(True), pass_over(False)
    holes = [i for i in range(size * size) if both[i] and not solid[i] and not underside[i]]
    if window:
        holes = [i for i in holes
                 if window[0] <= i % size <= window[2] and window[1] <= i // size <= window[3]]
    return holes, size


def _cover(seen, depth, size, screen, downward=None, faces_down=False):
    """Marks the pixels this triangle is nearest at, and whether that nearest face points at the ground."""
    (x0, y0, w0), (x1, y1, w1), (x2, y2, w2) = screen
    area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
    if abs(area) < 1e-9:
        return
    for py in range(max(0, int(min(y0, y1, y2))), min(size - 1, int(max(y0, y1, y2)) + 1) + 1):
        sy = py + 0.5
        for px in range(max(0, int(min(x0, x1, x2))), min(size - 1, int(max(x0, x1, x2)) + 1) + 1):
            sx = px + 0.5
            a = ((x1 - sx) * (y2 - sy) - (x2 - sx) * (y1 - sy)) / area
            b = ((x2 - sx) * (y0 - sy) - (x0 - sx) * (y2 - sy)) / area
            c = 1.0 - a - b
            if a < 0.0 or b < 0.0 or c < 0.0:
                continue
            w = a * w0 + b * w1 + c * w2
            index = py * size + px
            if w > 0.0 and 1.0 / w < depth[index]:
                depth[index] = 1.0 / w
                seen[index] = 1
                if downward is not None:
                    downward[index] = 1 if faces_down else 0


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
TRUNK = 'dc_trunk_cable_'


def cable_piece(kind, offset, yaw=0):
    return placed(block_model(CABLE + kind), offset, yaw)


def trunk_piece(kind, offset, yaw=0):
    return placed(block_model(TRUNK + kind), offset, yaw)


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


# An arm's outer end is at the block boundary and the neighbour's own arm covers it, so it carries no cap
# - which means a scene has to place that neighbour or the render shows an open tube the game never does.
NEIGHBOUR = {0: (0, -1), 90: (1, 0), 180: (0, 1), 270: (-1, 0)}


def joined(kind, at, sides, yaw=0, prefix=CABLE):
    """A piece, its arms, and a straight in every block those arms reach into."""
    def piece(name, offset, turn=0):
        return placed(block_model(prefix + name), offset, turn)

    triangles = piece(kind, at, yaw)
    for side in sides:
        triangles += piece('arm', at, side)
        dx, dz = NEIGHBOUR[side]
        beyond = (at[0] + dx, at[1], at[2] + dz)
        triangles += piece('line', beyond, 90 if dx else 0)
        triangles += piece('arm', beyond, side)
        triangles += piece('arm', beyond, (side + 180) % 360)
    return triangles


def scene_cable_junction():
    """The tee's own fittings up close: the box, and the four cable glands through its walls."""
    triangles = ground(-1, -1, 4, 4, SAND)
    # piece_tee glands north, east and south, and HUBS places it unturned for exactly that set
    triangles += joined('tee', (1, 0, 1), (0, 90, 180))
    # A standing player's eye, not a crouching one: from 1.7 blocks up the box's walls are two pixels of
    # a five-pixel square and it reads as a plate, which is the fault a close-up cannot show.
    return triangles, (3.1, 1.70, 3.9), (1.5, 0.06, 1.45)


def composed(connected, at=(1, 0, 1), prefix=CABLE):
    """One connection pattern the way the blockstate draws it, neighbours included.

    The middle its HUBS table names, an arm on each connected side, and the neighbour's own pieces - which
    is what closes the arm's open end, so a run is only whole when its neighbours are in the picture.
    """
    import gen_cable_models as cable

    kind, turn = cable.HUBS[connected]
    triangles = placed(block_model(prefix + kind), at, turn)
    for side in connected:
        yaw = cable.QUARTERS[side]
        triangles += placed(block_model(prefix + 'arm'), at, yaw)
        dx, dz = NEIGHBOUR[yaw]
        beyond = (at[0] + dx, at[1], at[2] + dz)
        triangles += placed(block_model(prefix + 'line'), beyond, 90 if dx else 0)
        triangles += placed(block_model(prefix + 'arm'), beyond, yaw)
        triangles += placed(block_model(prefix + 'arm'), beyond, (yaw + 180) % 360)
    return triangles


def scene_cable_states():
    """All sixteen states of a run at once, composed the way the blockstate composes them.

    Three scenes chosen by hand kept missing what the other thirteen do, and a fault that only shows on
    one connection pattern is invisible until every pattern is on the same sheet.
    """
    import gen_cable_models as cable

    triangles = ground(-2, -2, 15, 15, SAND)
    for index, connected in enumerate(sorted(cable.HUBS, key=len)):
        triangles += composed(connected, (1 + (index % 4) * 4, 0, 1 + (index // 4) * 4))
    return triangles, (7.5, 7.2, 20.0), (7.5, 0.1, 7.0)


def scene_cable_climb():
    """A run turning up a wall, and the piece nothing else in these scenes draws."""
    triangles = ground(-1, -1, 4, 4, SAND)
    triangles += cable_piece('line', (1, 0, 2), yaw=0)
    triangles += cable_piece('arm', (1, 0, 2), yaw=0)
    triangles += cable_piece('arm', (1, 0, 2), yaw=180)
    triangles += cable_piece('end', (1, 0, 1), yaw=0)
    triangles += cable_piece('arm', (1, 0, 1), yaw=180)
    triangles += cable_piece('climb', (1, 0, 1), yaw=0)
    triangles += cable_piece('line', (1, 1, 1), yaw=0)
    triangles += cable_piece('arm', (1, 1, 1), yaw=0)
    triangles += cable_piece('arm', (1, 1, 1), yaw=180)
    return triangles, (2.6, 1.9, 3.6), (1.5, 0.9, 1.4)


def scene_trunk():
    """The trunk beside the string, which is the only way to judge either of them.

    The gauges are three times apart in diameter and the fittings are different objects: heat-shrink joints
    staggered down the run, bolted lugs at a dead end, two-bolt cleats.
    """
    triangles = ground(-1, -1, 6, 6, SAND)
    for kind, at, sides in (('tee', (2, 0, 2), (0, 90, 180)),):
        triangles += joined(kind, at, sides, prefix=TRUNK)
    triangles += trunk_piece('end', (3, 0, 2), yaw=90)
    triangles += trunk_piece('arm', (3, 0, 2), yaw=270)
    # the string cable alongside, for scale
    triangles += cable_piece('line', (2, 0, 4), yaw=90)
    for side in (90, 270):
        triangles += cable_piece('arm', (2, 0, 4), yaw=side)
        triangles += cable_piece('line', (2 + (1 if side == 90 else -1), 0, 4), yaw=90)
        triangles += cable_piece('arm', (2 + (1 if side == 90 else -1), 0, 4), yaw=side)
        triangles += cable_piece('arm', (2 + (1 if side == 90 else -1), 0, 4), yaw=(side + 180) % 360)
    return triangles, (0.7, 2.4, 0.5), (2.4, 0.06, 2.6)


def scene_cable_over():
    """From over the run, close and steep, which is where a player stands when they look at what they laid.

    A shallow eye looks along a wall and sees it; from up here the junction box shows almost nothing but
    its lid, so anything that relies on a wall being visible has to be legible from this angle too.
    """
    triangles = ground(-1, -1, 5, 5, SAND)
    triangles += joined('tee', (2, 0, 2), (0, 90, 180))
    triangles += cable_piece('end', (3, 0, 2), yaw=90)
    triangles += cable_piece('arm', (3, 0, 2), yaw=270)
    return triangles, (0.9, 2.6, 0.4), (2.3, 0.06, 2.1)


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
    # coarse() as well as merged(), or this draws boxes the block does not declare - shape() does both.
    edges = outline(cable.coarse(cable.merged(boxes)), (1, 0, 1))
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


def scene_switches():
    """The pair, closed: a disconnector's blade lying between its posts and a breaker's flag showing red."""
    triangles = ground(-1, -1, 5, 5, SAND)
    triangles += placed(machine_model('mv_disconnector'), (1, 0, 2), drop=('flag_open',))
    triangles += placed(machine_model('mv_breaker'), (3, 0, 2), drop=('flag_open',))
    return triangles, (2.5, 1.55, -1.6), (2.5, 0.30, 2.5)


def scene_switches_open():
    """And open: the blade stood up on its hinge, the handle down, the flag showing green."""
    import gen_switch_models as switches

    triangles = ground(-1, -1, 5, 5, SAND)
    turned = []
    for group, texture, corners, normal, shade, cull in machine_model('mv_disconnector'):
        if group.startswith('pivot_') or group == 'flag_open':
            continue
        if group.startswith('rotate_'):
            corners = tuple((spun(point, switches.HINGE_Y, switches.REACH, 84.0), uv)
                            for point, uv in corners)
            normal = spun((normal[0], normal[1], normal[2]), 0.0, 0.0, 84.0)
        turned.append((group, texture, corners, normal, shade, cull))
    triangles += placed(turned, (1, 0, 2))
    triangles += placed(machine_model('mv_breaker'), (3, 0, 2), drop=('flag_shut',))
    return triangles, (2.5, 1.55, -1.6), (2.5, 0.30, 2.5)


def spun(point, hinge_y, hinge_z, degrees):
    """A point turned about the hinge axis, which runs along x - the same turn the renderer applies."""
    angle = math.radians(degrees)
    y, z = point[1] - hinge_y, point[2] - 0.5 - hinge_z
    return (point[0], hinge_y + y * math.cos(angle) - z * math.sin(angle),
            0.5 + hinge_z + y * math.sin(angle) + z * math.cos(angle))


def scene_tower():
    triangles = ground(-6, -6, 12, 12, SAND)
    triangles += placed(machine_model('lattice_suspension'), (3, 0, 3))  # noqa: E501
    return triangles, (-6.0, 9.0, 18.0), (3.5, 6.5, 3.5)


def scene_tower_head():
    """The head of each duty side by side: the arms, the hangers and the strings they carry."""
    triangles = ground(-8, -8, 28, 28, SAND)
    for index, duty in enumerate(('suspension', 'tension', 'terminal')):
        triangles += placed(machine_model('lattice_' + duty), (3 + index * 7, 0, 3))
    return triangles, (10.5, 12.0, 20.0), (10.5, 8.9, 3.0)


def scene_tower_arm():
    """One tension tower's lower arm from below: the jumper loops and the dead-ends either side."""
    triangles = ground(-8, -8, 24, 24, SAND)
    triangles += placed(machine_model('lattice_tension'), (3, 0, 3))
    return triangles, (3.5, 6.2, 9.5), (3.5, 8.4, 3.5)


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


def row(name, at, harnessed=True, fed_north=False, fed_south=False, mid_north=False, mid_south=False):
    """One array in one of the states PvArrayRenderer can draw it in.

    The state is what makes a row's cabling right or wrong, so a picture of a row has to be a picture of a
    state - drawn with every harness group at once it is five cables in the same place and proves nothing.
    """
    import check_row_cabling as cabling

    args = (harnessed, fed_north, fed_south, mid_north, mid_south)
    # kept by name rather than through placed()'s drop, which matches by prefix: "harness" is a prefix of
    # every other harness group, so dropping the spine that way dropped the whole row's cabling with it
    kept = [triangle for triangle in machine_model(name) if cabling.drawn(triangle[0], *args)]
    return placed(kept, at)


def scene_array_harness():
    """Where a row's own cabling ends: the plugs the row behind it mates with, on the lane at the edge.

    Two rows a block apart, so the socket on one and the lead of the other are in the same picture - which
    is what has to line up for a string to read as one product.
    """
    triangles = ground(-2, -2, 6, 6, SAND)
    triangles += row('pv_tilt', (2, 0, 1), fed_north=True)
    triangles += row('pv_tilt', (2, 0, 2), fed_north=True)
    triangles += row('pv_track', (4, 0, 1))
    return triangles, (0.9, 0.75, 0.2), (2.6, 0.16, 1.35)


def scene_row_entry():
    """A laid run arriving at the middle of a table's edge and leaving at the far one.

    The S-bend onto the lane, the moulded joint, the ribbon clipped up under the laminate, and the joint at
    the other end - which is the whole route, and the state a row in the middle of a plant is in.
    """
    triangles = ground(-1, -1, 5, 5, SAND)
    triangles += row('pv_flat', (2, 0, 2), mid_north=True, mid_south=True)
    for z, towards in ((1, 180), (3, 0)):
        triangles += cable_piece('line', (2, 0, z), yaw=0)
        triangles += cable_piece('arm', (2, 0, z), yaw=towards)
    return triangles, (0.10, 1.25, 0.05), (2.5, 0.16, 2.4)


def scene_row_under():
    """The same table from underneath its low side, which is the only way to see what is clipped to it."""
    triangles = ground(-1, -1, 5, 5, SAND)
    triangles += row('pv_flat', (2, 0, 2), mid_north=True, mid_south=True)
    return triangles, (1.35, 0.24, 0.45), (2.6, 0.17, 2.5)


def scene_row_track():
    """A tracked row's pull box: the pair down the middle, in one gland and out the other."""
    triangles = ground(-1, -1, 4, 4, SAND)
    triangles += row('pv_track', (1, 0, 1))
    triangles += row('pv_dual', (2, 0, 1))
    return triangles, (0.15, 0.75, -0.35), (2.0, 0.14, 1.35)


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
    'row_entry': scene_row_entry,
    'row_under': scene_row_under,
    'row_track': scene_row_track,
    'cable_run': scene_cable_run,
    'cable_corner': scene_cable_corner,
    'cable_junction': scene_cable_junction,
    'cable_hitbox': scene_cable_hitbox,
    'cable_states': scene_cable_states,
    'cable_over': scene_cable_over,
    'trunk': scene_trunk,
    'cable_climb': scene_cable_climb,
    'cable_plug': scene_cable_plug,
    'inverter_front': scene_inverter_front,
    'inverter_roof': scene_inverter_roof,
    'combiner_front': scene_combiner_front,
    'power_box': scene_power_box,
    'cab': scene_cab,
    'pole': scene_pole,
    'tower': scene_tower,
    'switches': scene_switches,
    'switches_open': scene_switches_open,
    'tower_head': scene_tower_head,
    'tower_arm': scene_tower_arm,
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
