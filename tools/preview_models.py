#!/usr/bin/env python3
"""Draws a generated OBJ as an SVG, from the angles a player actually looks from.

    python3 tools/preview_models.py                     # every pv model, three views each
    python3 tools/preview_models.py pv_flat --view plan
    python3 tools/preview_models.py pv_tilt --only harness,harness_entry_south --no-cable

Writes into build/preview/, which any browser opens.

Why this exists
---------------
Every defect in the cabling was found by a player looking at it and only afterwards by a script
measuring it. The clearance checker proves nothing intersects and the model validator proves every
texture resolves, and neither of them can say whether a cable *reads* as plugged in - which is the
thing that was wrong four times running. So this draws the geometry, with a length of laid cable in
the block alongside at the figures gen_cable_models.py writes, and the question "do the two meet"
becomes one you can answer by looking.

What it is not: the game. There are no textures here, one flat colour per material, and a painter's
sort rather than a depth buffer - so it will not show a z-fight, which is what check_pv_clearance.py
is for. It shows shape, position and whether two parts line up.

One thing worth knowing about the output of gen_pv_models.py, which this had to be taught: the up
face's corner list is wound clockwise seen from above and carries an explicit vn of +y. The game is
fine with that because the OBJ render type does not cull, but deriving a normal from the winding here
lit every face upside down and culled the wrong half of them. So the stated normal is what is read.
"""

import math
import os
import sys

MODELS = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')
OUT = os.path.join('build', 'preview')

# One flat colour per material. Not the textures - the point is to read shape, and a cell pattern at
# this size is noise. The pair is red because the pair texture is mostly red conductor.
COLOURS = {
    'module': '#1b2246', 'module_back': '#3a3a3a', 'module_edge': '#9aa0a6',
    'frame': '#b9bfc4', 'steel': '#8d949a', 'steel_end': '#7e858b',
    'cabinet': '#a8aeb3', 'cabinet_top': '#c2c8cd', 'cabinet_door': '#9fa5aa',
    'combiner_door': '#9fa5aa', 'dc_section': '#8f9599', 'switch': '#c8b23a',
    'vent': '#6e7378', 'display': '#2b3f2b', 'instrument': '#d8dadc', 'dome': '#e6e9ec',
    'dc_cable': '#b02b2b', 'dc_jacket': '#1d1d1f',
    'laid_cable': '#c03535', 'ground': '#d8caa2',
}

# Where the light comes from, so the six faces of a box are told apart.
LIGHT = (-0.36, 0.88, 0.31)

# The views: enough to see a mounting from a player's eye, from a player's usual three-quarter angle,
# and from straight above where a layout is unambiguous.
VIEWS = {'iso': (42.0, 30.0), 'plan': (0.0, 89.0), 'low': (38.0, 10.0)}

# A laid run's own figure, from gen_cable_models.py: two pixels across, one tall.
HALF, THICK = 1.0 / 16.0, 1.0 / 16.0


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def read_obj(path):
    """Quads as (object, material, corners, normal), with the normal the file states."""
    verts, normals, quads = [], [], []
    obj = material = None
    for line in open(path):
        parts = line.split()
        if not parts:
            continue
        if parts[0] == 'v':
            verts.append(tuple(float(x) for x in parts[1:4]))
        elif parts[0] == 'vn':
            normals.append(tuple(float(x) for x in parts[1:4]))
        elif parts[0] == 'o':
            obj = parts[1]
        elif parts[0] == 'usemtl':
            material = parts[1]
        elif parts[0] == 'f':
            fields = [f.split('/') for f in parts[1:]]
            corners = [verts[int(f[0]) - 1] for f in fields]
            normal = normals[int(fields[0][2]) - 1] if len(fields[0]) > 2 and fields[0][2] else None
            quads.append((obj, material, corners, normal))
    return quads


def box(lo, hi, material, obj='extra'):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    faces = (
        ([(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)], (0, -1, 0)),
        ([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], (0, 1, 0)),
        ([(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], (0, 0, -1)),
        ([(x1, y0, z1), (x0, y0, z1), (x0, y1, z1), (x1, y1, z1)], (0, 0, 1)),
        ([(x0, y0, z1), (x0, y0, z0), (x0, y1, z0), (x0, y1, z1)], (-1, 0, 0)),
        ([(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)], (1, 0, 0)),
    )
    return [(obj, material, corners, normal) for corners, normal in faces]


def laid_run(offset):
    """A block of surface run in the block at ``offset``, running along z: hub plus both arms."""
    spans = (((-HALF, -HALF), (HALF, HALF)), ((-HALF, -0.5), (HALF, -HALF)), ((-HALF, HALF), (HALF, 0.5)))
    quads = []
    for (x0, z0), (x1, z1) in spans:
        quads += box((x0, 0.0, z0), (x1, THICK, z1), 'laid_cable', 'laid')

    return [(o, m, [(p[0] + offset[0], p[1] + offset[1], p[2] + offset[2]) for p in f], n)
            for o, m, f, n in quads]


def basis(yaw, pitch):
    """Camera direction and the two screen axes, so depth and position come out of one frame."""
    a, b = math.radians(yaw), math.radians(pitch)
    eye = (math.sin(a) * math.cos(b), math.sin(b), math.cos(a) * math.cos(b))
    right = (math.cos(a), 0.0, -math.sin(a))
    up = (eye[1] * right[2] - eye[2] * right[1],
          eye[2] * right[0] - eye[0] * right[2],
          eye[0] * right[1] - eye[1] * right[0])
    return eye, right, up


def shade(colour, normal):
    lit = 0.42 + 0.58 * max(0.0, dot(normal, LIGHT))
    channels = (int(colour[i:i + 2], 16) for i in (1, 3, 5))
    return '#%02x%02x%02x' % tuple(min(255, int(c * lit)) for c in channels)


def render(quads, yaw, pitch, path, size=1100.0):
    frame = basis(yaw, pitch)
    drawn = []
    for obj, material, corners, normal in quads:
        if normal is None or dot(normal, frame[0]) <= 0.0:
            continue

        points = [(dot(p, frame[1]), dot(p, frame[2])) for p in corners]
        near = sum(dot(p, frame[0]) for p in corners) / len(corners)
        # the ground is one quad as wide as the scene, so its centroid sorts in front of half of what
        # stands on it - it goes to the back by fiat instead
        drawn.append((-9.0 if obj == 'ground' else near, points, shade(COLOURS.get(material, '#888888'), normal)))

    drawn.sort(key=lambda item: item[0])
    xs = [p[0] for _, points, _ in drawn for p in points]
    ys = [p[1] for _, points, _ in drawn for p in points]
    scale = size / max(max(xs) - min(xs), max(ys) - min(ys))
    pad = 16.0

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write('<svg xmlns="http://www.w3.org/2000/svg" width="%.0f" height="%.0f">\n'
                % ((max(xs) - min(xs)) * scale + 2 * pad, (max(ys) - min(ys)) * scale + 2 * pad))
        f.write('<rect width="100%" height="100%" fill="#7d8b97"/>\n')
        for _, points, colour in drawn:
            path_data = ' '.join('%.2f,%.2f' % ((p[0] - min(xs)) * scale + pad, (max(ys) - p[1]) * scale + pad)
                                 for p in points)
            f.write('<polygon points="%s" fill="%s" stroke="%s" stroke-width="0.5"/>\n' % (path_data, colour, colour))
        f.write('</svg>\n')


def scene(name, only, cable):
    quads = read_obj(os.path.join(MODELS, name, name + '.obj'))
    if only:
        quads = [q for q in quads if q[0] in only]

    quads += box((-1.05, -0.02, -1.05), (1.05, 0.0, 1.05), 'ground', 'ground')
    if cable:
        # up to both ends of the row's axis, which is where an array takes cable
        quads += laid_run((0.0, 0.0, 1.0)) + laid_run((0.0, 0.0, -1.0))

    return quads


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    flags = [a for a in sys.argv[1:] if a.startswith('--')]
    views = [v.split('=')[1] for v in flags if v.startswith('--view=')] or list(VIEWS)
    only = set(sum([v.split('=')[1].split(',') for v in flags if v.startswith('--only=')], []))
    cable = '--no-cable' not in flags

    names = args or [d for d in sorted(os.listdir(MODELS)) if d.startswith('pv_')]
    for name in names:
        quads = scene(name, only, cable)
        for view in views:
            yaw, pitch = VIEWS[view]
            path = os.path.join(OUT, '%s_%s.svg' % (name, view))
            render(quads, yaw, pitch, path)
            print(path)


if __name__ == '__main__':
    main()
