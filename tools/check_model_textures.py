#!/usr/bin/env python3
"""Texture faults that are mechanical: flicker, hidden geometry, sub-sampling, and ovals.

    python3 tools/check_model_textures.py            # every model the mod authors
    python3 tools/check_model_textures.py pv_flat    # one

The oval check is the one that matters.  Every face is measured for how differently it samples u and v;
a texture with something round on it has to sample them the same, unless SQUASH (in gen_block_textures)
declares it drawn pre-squashed for a face of a known shape - in which case the face has to match that.
Which textures have something round on them is read out of the generator's own call graph.
"""

import ast
import collections
import functools
import math
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from gen_block_textures import SQUASH                                            # noqa: E402

MODELS = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')
TEXTURES = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'textures', 'block')

# Models the mod draws itself.
OURS = ('pv_flat', 'pv_tilt', 'pv_track', 'pv_dual', 'pv_inverter', 'pv_combiner', 'met_mast',
        'utility_pole', 'power_box', 'tx_machine', 'tx_substation',
        'lattice_suspension', 'lattice_tension', 'lattice_terminal', 'electric_cab')

# How much of a face has to overlap another coplanar face before it is worth reporting.
OVERLAP = 1e-4

# Pairs of parts the renderer never draws in the same frame, so sharing a plane costs nothing.
# PvArrayRenderer.drawn picks exactly one piece for each end of a fixed row - the entry where a laid run
# meets the middle of that edge, the socket where a row plugs into the corner, the plain lead otherwise -
# and they meet at the same joint, so of course they share its faces.
EXCLUSIVE = (('harness', 'harness_plug'),
             ('harness_input', 'harness_lead_north'),
             ('harness_input', 'harness_entry_north'),
             ('harness_lead_north', 'harness_entry_north'),
             ('harness_lead_south', 'harness_entry_south'))
# Below this fraction of a texture's own size
SUBSAMPLE = 0.98
# Textures a face is *meant* to take the middle out of.
FRAME_EXEMPT = {'pv_dome.png'}
# Pixels of texture per block of surface, under which a face is stretched enough to look soft.
# ten metres in this mod, so this is not a vanilla figure: 16 would be one texel per 60 centimetres.
DENSITY = 48.0
# How far u and v may disagree on one face before a circle on that texture reads as an oval.
# below what the eye picks up on a disc; a third is unmistakable.
ANISOTROPY = 1.12
# The narrow way across a face, under which no oval on it can be seen whatever the ratio: a drip edge a
MIN_FEATURE = 0.062
# Faces meant to sample one texture at two scales.
# nothing along its length, so stretching it along the run is exactly what it is drawn for.
STRETCH_EXEMPT = {'dc_core.png',
                  # a dome's side faces want the glass the circle is drawn on
                  # same reason it is in FRAME_EXEMPT: sampling it unevenly is the intent
                  'pv_dome.png'}


def png_size(path):
    with open(path, 'rb') as f:
        return struct.unpack('>II', f.read(24)[16:24])


def png_pixels(path):
    """Decoded RGBA rows, for the handful of textures worth looking inside."""
    data = open(path, 'rb').read()
    index, idat = 8, b''
    width = height = depth = colour = 0
    while index < len(data):
        length = struct.unpack('>I', data[index:index + 4])[0]
        kind = data[index + 4:index + 8]
        payload = data[index + 8:index + 8 + length]
        if kind == b'IHDR':
            width, height, depth, colour = struct.unpack('>IIBB', payload[:10])
        elif kind == b'IDAT':
            idat += payload
        index += 12 + length

    if depth != 8 or colour not in (2, 6):
        return None, 0, 0

    stride = width * (4 if colour == 6 else 3)
    raw = zlib.decompress(idat)
    rows = []
    previous = bytearray(stride)
    step = 4 if colour == 6 else 3
    at = 0
    for _ in range(height):
        filter_type = raw[at]
        line = bytearray(raw[at + 1:at + 1 + stride])
        at += 1 + stride
        if filter_type == 1:
            for i in range(step, stride):
                line[i] = (line[i] + line[i - step]) & 0xff
        elif filter_type == 2:
            for i in range(stride):
                line[i] = (line[i] + previous[i]) & 0xff
        elif filter_type == 3:
            for i in range(stride):
                left = line[i - step] if i >= step else 0
                line[i] = (line[i] + ((left + previous[i]) >> 1)) & 0xff
        elif filter_type == 4:
            for i in range(stride):
                left = line[i - step] if i >= step else 0
                up = previous[i]
                upleft = previous[i - step] if i >= step else 0
                guess = left + up - upleft
                best = min((abs(guess - left), left), (abs(guess - up), up), (abs(guess - upleft), upleft))[1]
                line[i] = (line[i] + best) & 0xff

        rows.append(bytes(line))
        previous = line

    return rows, width, step


# The texlib primitives that put something round on a tile.
# circle on it, so the face it lands on has to sample u and v at the same rate or the circle is an oval.
ROUND_PRIMITIVES = {'aa_disc', 'disc', 'dome', 'screw', 'hex_head', 'warning_triangle'}


@functools.lru_cache(maxsize=None)
def round_textures():
    """Which generated textures have something round on them, read out of the generator's own source."""
    source = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'gen_block_textures.py')
    tree = ast.parse(open(source).read())

    calls, builders = {}, {}
    for node in tree.body:
        if isinstance(node, ast.FunctionDef):
            calls[node.name] = {inner.func.id for inner in ast.walk(node)
                                if isinstance(inner, ast.Call) and isinstance(inner.func, ast.Name)} | \
                               {inner.func.attr for inner in ast.walk(node)
                                if isinstance(inner, ast.Call) and isinstance(inner.func, ast.Attribute)}

    for node in ast.walk(tree):
        if not isinstance(node, ast.Assign) or not isinstance(node.value, ast.Dict):
            continue
        for key, value in zip(node.value.keys, node.value.values):
            if not isinstance(key, ast.Constant) or not isinstance(key.value, str):
                continue
            named = value.body.func if isinstance(value, ast.Lambda) and isinstance(value.body, ast.Call) \
                else value
            if isinstance(named, ast.Name):
                builders[key.value + '.png'] = named.id

    def reaches(name, seen=None):
        seen = seen or set()
        if name in seen or name not in calls:
            return False
        seen.add(name)
        if calls[name] & ROUND_PRIMITIVES:
            return True
        return any(reaches(inner, seen) for inner in calls[name])

    return {texture for texture, builder in builders.items() if reaches(builder)}


def bordered(path):
    """Whether a texture is a picture in a frame rather than a pattern."""
    rows, width, step = png_pixels(path)
    if rows is None or width < 4:
        return False

    def pixel(x, y):
        return rows[y][x * step:x * step + 3]

    height = len(rows)
    ring = [pixel(x, 0) for x in range(width)] + [pixel(x, height - 1) for x in range(width)] \
        + [pixel(0, y) for y in range(height)] + [pixel(width - 1, y) for y in range(height)]
    middle = [pixel(x, y) for y in range(height // 4, 3 * height // 4) for x in range(width // 4, 3 * width // 4)]
    if not middle:
        return False

    def mean(values):
        return [sum(v[i] for v in values) / len(values) for i in range(3)]

    ring_mean, middle_mean = mean(ring), mean(middle)
    spread = sum(max(abs(v[i] - ring_mean[i]) for v in ring) for i in range(3)) / 3.0
    difference = sum(abs(ring_mean[i] - middle_mean[i]) for i in range(3)) / 3.0
    return spread < 26.0 and difference > 10.0


def faces(path):
    """Every face of an OBJ as (object, material, corners, uvs, normal)."""
    verts, uvs, normals, out = [], [], [], []
    obj = material = None
    for line in open(path):
        parts = line.split()
        if not parts:
            continue
        if parts[0] == 'v':
            verts.append(tuple(float(v) for v in parts[1:4]))
        elif parts[0] == 'vt':
            uvs.append(tuple(float(v) for v in parts[1:3]))
        elif parts[0] == 'vn':
            normals.append(tuple(float(v) for v in parts[1:4]))
        elif parts[0] == 'o':
            obj = parts[1]
        elif parts[0] == 'usemtl':
            material = parts[1]
        elif parts[0] == 'f':
            fields = [f.split('/') for f in parts[1:]]
            out.append((obj, material,
                        [verts[int(f[0]) - 1] for f in fields],
                        [uvs[int(f[1]) - 1] for f in fields if len(f) > 1 and f[1]],
                        normals[int(fields[0][2]) - 1] if len(fields[0]) > 2 and fields[0][2] else None))

    return out


def materials(path):
    """Material name to texture file, read out of the MTL beside the model."""
    found = {}
    name = None
    for line in open(path):
        parts = line.split()
        if not parts:
            continue
        if parts[0] == 'newmtl':
            name = parts[1]
        elif parts[0] == 'map_Kd' and name:
            found[name] = parts[1]

    return found


def plane_of(corners, normal):
    """Which plane a face lies in, as (axis, offset), or None if it is not axis aligned."""
    if normal is None:
        return None
    for axis in range(3):
        if abs(abs(normal[axis]) - 1.0) < 1e-6:
            offsets = {round(c[axis], 6) for c in corners}
            if len(offsets) == 1:
                return axis, offsets.pop(), 1 if normal[axis] > 0 else -1

    return None


def area_overlap(a, b, axes):
    """Overlapping area of two faces in the two axes that are not their normal."""
    total = 1.0
    for axis in axes:
        lo = max(min(c[axis] for c in a), min(c[axis] for c in b))
        hi = min(max(c[axis] for c in a), max(c[axis] for c in b))
        if hi - lo <= 0:
            return 0.0
        total *= hi - lo

    return total


def exclusive(a, b):
    for first, second in EXCLUSIVE:
        if (a.startswith(first) and b.startswith(second)) or (a.startswith(second) and b.startswith(first)):
            return True

    return False


def coplanar_pairs(model_faces):
    """Pairs of faces on the same plane, pointing the same way, that overlap in area."""
    by_plane = collections.defaultdict(list)
    for face in model_faces:
        plane = plane_of(face[2], face[4])
        if plane is not None:
            by_plane[plane].append(face)

    problems = []
    for (axis, offset, sign), group in by_plane.items():
        # the block's own floor
        if axis == 1 and abs(offset) < 1e-6 and sign < 0:
            continue

        others = [i for i in range(3) if i != axis]
        for i in range(len(group)):
            for j in range(i + 1, len(group)):
                if group[i][0] == group[j][0] and group[i][1] == group[j][1]:
                    # the same part's own faces, e.g.
                    continue
                if exclusive(group[i][0], group[j][0]):
                    continue
                overlap = area_overlap(group[i][2], group[j][2], others)
                if overlap > OVERLAP:
                    problems.append((group[i][0] + ':' + group[i][1], group[j][0] + ':' + group[j][1],
                                     'xyz'[axis], offset, overlap))

    return problems


def boxes(model_faces):
    """One bounding box per object, for the enclosure test."""
    found = collections.OrderedDict()
    for obj, _, corners, _, _ in model_faces:
        box = found.setdefault(obj, [[9.0] * 3, [-9.0] * 3])
        for corner in corners:
            for axis in range(3):
                box[0][axis] = min(box[0][axis], corner[axis])
                box[1][axis] = max(box[1][axis], corner[axis])

    return found


def single_boxes(model_faces):
    """The objects that are one box: six faces, one to each side of their own bounding box."""
    by_object = collections.defaultdict(list)
    for obj, _, corners, _, normal in model_faces:
        by_object[obj].append((corners, normal))

    found = set()
    for obj, group in by_object.items():
        if len(group) != 6:
            continue
        planes = {plane_of(corners, normal) for corners, normal in group}
        if None not in planes and len(planes) == 6:
            found.add(obj)

    return found


# One model whose file is not named after its directory, which is inherited and stays that way: a rename
# would move the asset every existing world's cabins are loaded from.
OBJ_NAME = {'electric_cab': 'cab'}


def report(name):
    stem = OBJ_NAME.get(name, name)
    path = os.path.join(MODELS, name, stem + '.obj')
    model_faces = faces(path)
    texture_of = materials(os.path.join(MODELS, name, stem + '.mtl'))
    print('=== %s: %d faces, %d objects' % (name, len(model_faces), len(boxes(model_faces))))
    problems = 0

    for a, b, axis, offset, overlap in coplanar_pairs(model_faces):
        problems += 1
        print('    FLICKER  %s and %s share the plane %s=%.4f over %.4f of area' % (a, b, axis, offset, overlap))

    inside = boxes(model_faces)
    solid = single_boxes(model_faces)
    for name_a, (lo_a, hi_a) in inside.items():
        for name_b, (lo_b, hi_b) in inside.items():
            if name_a == name_b or name_a.startswith('pivot') or name_b.startswith('pivot'):
                continue
            # only a part that really is one box encloses anything: a door with a handle on it has a
            # bounding box far larger than the plate
            if name_b not in solid:
                continue
            if all(lo_b[i] <= lo_a[i] and hi_a[i] <= hi_b[i] for i in range(3)) and \
                    any(lo_b[i] < lo_a[i] or hi_a[i] < hi_b[i] for i in range(3)):
                problems += 1
                print('    HIDDEN   %s is wholly inside %s and can never be seen' % (name_a, name_b))

    stretched = []
    sliced = set()
    skew = []
    for obj, material, corners, uvs, normal in model_faces:
        texture = texture_of.get(material)
        if texture is None or not uvs:
            continue
        file_path = os.path.join(TEXTURES, texture)
        if not os.path.exists(file_path):
            problems += 1
            print('    MISSING  %s wants %s, which is not there' % (material, texture))
            continue

        width, height = png_size(file_path)
        span_u = max(u for u, _ in uvs) - min(u for u, _ in uvs)
        span_v = max(v for _, v in uvs) - min(v for _, v in uvs)
        # a cylinder's end cap maps the whole texture across a circle and each of its quads takes a
        rectangular = len({round(u, 5) for u, _ in uvs}) <= 2 and len({round(v, 5) for _, v in uvs}) <= 2
        if rectangular and texture not in FRAME_EXEMPT and max(span_u, span_v) < SUBSAMPLE \
                and bordered(file_path):
            slice_key = (obj, texture, round(max(span_u, span_v), 2))
            if slice_key not in sliced:
                sliced.add(slice_key)
                problems += 1
                print('    SLICED   %s uses %.2f of %s, which is a bordered picture - the frame is cut off'
                      % (obj, max(span_u, span_v), texture))

        # Texels per block, measured along the face's own u and v rather than along the world axes.
        wedge = False
        if len(uvs) >= 4:
            wedge = (abs(uvs[1][1] - uvs[0][1]) > abs(uvs[1][0] - uvs[0][0])
                     or abs(uvs[-1][0] - uvs[0][0]) > abs(uvs[-1][1] - uvs[0][1]))
        if len(corners) >= 4 and len(uvs) >= 4 and not wedge:
            along_u = math.dist(corners[0], corners[1])
            along_v = math.dist(corners[0], corners[-1])
            step_u = abs(uvs[1][0] - uvs[0][0])
            step_v = abs(uvs[-1][1] - uvs[0][1])
            densities = []
            if along_u > 1e-6 and step_u > 1e-9:
                densities.append(step_u * width / along_u)
            if along_v > 1e-6 and step_v > 1e-9:
                densities.append(step_v * height / along_v)
            if densities and min(densities) < DENSITY:
                stretched.append((min(densities), obj, material, texture, max(along_u, along_v)))

            # Anisotropy: how differently this face samples u and v
            # A texture in SQUASH is drawn pre-squashed for a face that is *meant* to be anisotropic
            if len(densities) == 2 and min(densities) > 1e-9 and texture in round_textures() \
                    and texture not in STRETCH_EXEMPT and min(along_u, along_v) > MIN_FEATURE:
                want = densities[1] / densities[0]
                expected = SQUASH.get(texture[:-4], 1.0)
                ratio = max(want / expected, expected / want)
                if ratio > ANISOTROPY:
                    skew.append((ratio, obj, material, texture, want, expected))

    by_material = {}
    for density, obj, material, texture, size in stretched:
        if material not in by_material or density < by_material[material][0]:
            by_material[material] = (density, obj, texture, size)
    for material, (density, obj, texture, size) in sorted(by_material.items(), key=lambda item: item[1][0]):
        print('    stretched %-14s %-22s %5.0f texels per block over %.2f blocks (%s)'
              % (material, obj, density, size, texture))

    worst = {}
    for ratio, obj, material, texture, want, expected in skew:
        if (obj, texture) not in worst or ratio > worst[(obj, texture)][0]:
            worst[(obj, texture)] = (ratio, want, expected)
    for (obj, texture), (ratio, want, expected) in sorted(worst.items(), key=lambda item: -item[1][0]):
        problems += 1
        print('    OVAL     %s samples %s at %.2f across to 1 along, wants %.2f - a circle on it comes '
              'out %.2f to 1' % (obj, texture, want, expected, ratio))

    sides = collections.defaultdict(set)
    for obj, material, corners, _, normal in model_faces:
        plane = plane_of(corners, normal)
        if plane is not None:
            sides[(obj, material)].add((plane[0], plane[2]))
    for (obj, material), used in sorted(sides.items()):
        texture = texture_of.get(material)
        # a texture in FRAME_EXEMPT is one whose middle is meant to be sampled, which is the same reason
        # it is allowed on every side: a dome's side wants the glass the circle is drawn on
        if texture in FRAME_EXEMPT:
            continue
        if texture and len(used) >= 5 and bordered(os.path.join(TEXTURES, texture)):
            problems += 1
            print('    ALLSIDES %s wears %s on %d faces, and it is a picture rather than a pattern'
                  % (obj, texture, len(used)))

    return problems


def main():
    wanted = sys.argv[1:] or OURS
    total = 0
    for name in wanted:
        total += report(name)

    print('\n%s' % ('nothing mechanical left to find' if total == 0 else '%d problem(s)' % total))


if __name__ == '__main__':
    main()
