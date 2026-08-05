#!/usr/bin/env python3
"""The three lattice transmission towers: suspension, tension, terminal.

    python3 tools/gen_tower_models.py

Donaumast arrangement - two crossarm levels, three phases a side, earth wire at the peak.  The duty is
what makes them different objects: a suspension tower hangs its chains vertically, a tension tower takes
the difference between two pulls on horizontal chains and is braced for it, a terminal tower takes the
whole pull on one side and is stayed back against it.

PHASES is in wire-index order and cannot be reordered without moving every wire in every world.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from modellib import (FITTING, Mesh, angle, bolt, box, cylinder, lathe, strut,   # noqa: E402
                      write_mtl)

OUT = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')

MATERIALS = {
    'steel': 'tower_steel.png',
    'plate': 'tower_plate.png',
    'porcelain': 'porcelain_brown.png',
    'concrete': 'pole_concrete.png',
    'conductor': 'tower_conductor.png',
    'sign': 'pole_plate.png',
}

# ---------------------------------------------------------------- the tower's own figures

HEIGHT = 11.5                 # to the earth peak
BODY_TOP = 7.60               # where the legs stop battering and the head begins
BASE_HALF = 1.90              # half the footprint at ground level
WAIST_HALF = 0.62             # half the body at the top of the batter
LEG = 0.085                   # the leg angle's leg length
BRACE = 0.052                 # a diagonal's
PANELS = 7                    # horizontal frames up the battered body

# The two crossarms of a Donaumast: the lower one carries two phases a side
LOWER_ARM = 8.30
UPPER_ARM = 9.90
LOWER_REACH = 2.55
UPPER_REACH = 1.55
# and the earth wire's peak above them
PEAK = HEIGHT

# Where the six phases hang.
PHASES = (
    (-LOWER_REACH, LOWER_ARM), (-LOWER_REACH * 0.55, LOWER_ARM),
    (LOWER_REACH * 0.55, LOWER_ARM), (LOWER_REACH, LOWER_ARM),
    (-UPPER_REACH, UPPER_ARM), (UPPER_REACH, UPPER_ARM),
)

# An insulator string: how many discs and how big.
# eighteen, which is what reads as a string rather than as a rod at the distance a tower is seen from.
DISCS = 18
DISC_RADIUS = 0.052
DISC_PITCH = 0.038


def leg_half(height):
    """Half the tower's width at a height: battered below the waist"""
    if height >= BODY_TOP:
        return WAIST_HALF

    t = height / BODY_TOP
    return BASE_HALF + (WAIST_HALF - BASE_HALF) * t


def legs(mesh, steel):
    """The four legs, battered inwards, as angle sections turned to face the corner they stand on."""
    for sx in (-1, 1):
        for sz in (-1, 1):
            for panel in range(PANELS + 1):
                y0 = BODY_TOP * panel / (PANELS + 1)
                y1 = BODY_TOP * (panel + 1) / (PANELS + 1)
                h0, h1 = leg_half(y0), leg_half(y1)
                strut(mesh, steel, (sx * h0, y0, sz * h0), (sx * h1, y1, sz * h1), LEG * 0.5,
                      LEG * 0.5, uv_scale=0.5)

            # and on up the parallel body to the head
            strut(mesh, steel, (sx * WAIST_HALF, BODY_TOP, sz * WAIST_HALF),
                  (sx * WAIST_HALF, PEAK - 1.6, sz * WAIST_HALF), LEG * 0.45, LEG * 0.45,
                  uv_scale=0.5)


def frames(mesh, steel):
    """A horizontal frame at every panel joint, which is what stops the legs folding inwards."""
    levels = [BODY_TOP * i / (PANELS + 1) for i in range(1, PANELS + 2)]
    levels += [BODY_TOP + (PEAK - 1.6 - BODY_TOP) * i / 4.0 for i in range(1, 5)]
    for y in levels:
        h = leg_half(y)
        for sx, sz, tx, tz in ((-1, -1, 1, -1), (1, -1, 1, 1), (1, 1, -1, 1), (-1, 1, -1, -1)):
            strut(mesh, steel, (sx * h, y, sz * h), (tx * h, y, tz * h), BRACE * 0.5, BRACE * 0.5,
                  uv_scale=0.3)

    return levels


def diagonals(mesh, steel, levels):
    """A diagonal in every panel of every face: what makes it a lattice rather than a mechanism."""
    stack = [0.0] + levels
    for panel in range(len(stack) - 1):
        y0, y1 = stack[panel], stack[panel + 1]
        h0, h1 = leg_half(y0), leg_half(y1)
        for face in range(4):
            # the four faces, each between two adjacent legs
            corners = ((-1, -1), (1, -1), (1, 1), (-1, 1))
            ax, az = corners[face]
            bx, bz = corners[(face + 1) % 4]
            if panel % 2:
                start = (ax * h0, y0, az * h0)
                end = (bx * h1, y1, bz * h1)
            else:
                start = (bx * h0, y0, bz * h0)
                end = (ax * h1, y1, az * h1)
            strut(mesh, steel, start, end, BRACE * 0.42, BRACE * 0.42, uv_scale=0.3)


def crossarm(mesh, steel, y, reach, depth):
    """One crossarm: a triangulated cantilever each side, which is how a real one carries its load."""
    for side in (-1, 1):
        tip = (side * reach, y, 0.0)
        root_out = (side * WAIST_HALF, y, 0.0)
        # the two main members, one either side of the tower's own depth
        for sz in (-1, 1):
            strut(mesh, steel, (side * WAIST_HALF, y, sz * depth), (side * reach, y, sz * depth * 0.35),
                  BRACE * 0.5, BRACE * 0.5, uv_scale=0.3)
        # the tie up to the body and the strut down from it
        strut(mesh, steel, tip, (side * WAIST_HALF, y + reach * 0.42, 0.0), BRACE * 0.45,
              BRACE * 0.45, uv_scale=0.3)
        strut(mesh, steel, tip, (side * WAIST_HALF, y - reach * 0.30, 0.0), BRACE * 0.45,
              BRACE * 0.45, uv_scale=0.3)
        # and the lattice between the two main members
        for i in range(5):
            t0, t1 = i / 5.0, (i + 1) / 5.0
            for sz, other in ((-1, 1), (1, -1)):
                if i % 2 != (0 if sz < 0 else 1):
                    continue
                a = (side * (WAIST_HALF + (reach - WAIST_HALF) * t0), y,
                     sz * depth * (1.0 - 0.65 * t0))
                b = (side * (WAIST_HALF + (reach - WAIST_HALF) * t1), y,
                     other * depth * (1.0 - 0.65 * t1))
                strut(mesh, steel, a, b, BRACE * 0.34, BRACE * 0.34, uv_scale=0.3)
        # the gusset plate the insulator hangs from
        box(mesh, mesh.faces('arm', 'plate'), (side * reach - 0.075, y - 0.030, -0.075),
            (side * reach + 0.075, y + 0.010, 0.075), uv_scale=0.4)


def peak(mesh, steel):
    """The earth-wire peak: a short pyramid above the phases"""
    base = PEAK - 1.6
    for sx in (-1, 1):
        for sz in (-1, 1):
            strut(mesh, steel, (sx * WAIST_HALF, base, sz * WAIST_HALF), (0.0, PEAK, 0.0),
                  BRACE * 0.45, BRACE * 0.45, uv_scale=0.3)
    for i in range(1, 3):
        y = base + (PEAK - base) * i / 3.0
        h = WAIST_HALF * (1.0 - i / 3.0)
        for sx, sz, tx, tz in ((-1, -1, 1, -1), (1, -1, 1, 1), (1, 1, -1, 1), (-1, 1, -1, -1)):
            strut(mesh, steel, (sx * h, y, sz * h), (tx * h, y, tz * h), BRACE * 0.34,
                  BRACE * 0.34, uv_scale=0.3)
    # the earth wire's own fitting on the point
    cylinder(mesh, mesh.faces('peak', 'plate'), (0.0, PEAK, 0.0), 'y', 0.045, 0.030,
             sides=FITTING, uv_scale=0.2, caps=mesh.faces('peak', 'plate'))


def insulator_string(mesh, index, x, y, horizontal=False):
    """A cap-and-pin insulator string: eighteen glass discs on a steel link"""
    porcelain = mesh.faces('insulator_%d' % index, 'porcelain')
    steel = mesh.faces('string_%d' % index, 'steel')

    length = DISCS * DISC_PITCH
    for disc in range(DISCS):
        along = (disc + 0.5) * DISC_PITCH
        if horizontal:
            centre = (x + math.copysign(along, x), y, 0.0)
            axis = 'x'
        else:
            centre = (x, y - along, 0.0)
            axis = 'y'

        # one disc: a shallow cap over a pin
        cylinder(mesh, porcelain, centre, axis, DISC_RADIUS, DISC_PITCH * 0.34, sides=FITTING,
                 uv_scale=1.0, taper=0.62)
        cylinder(mesh, steel, centre, axis, DISC_RADIUS * 0.30, DISC_PITCH * 0.5, sides=FITTING,
                 uv_scale=0.4)

    # the clamp the conductor is held in, at the far end of the string
    if horizontal:
        far = (x + math.copysign(length + 0.05, x), y, 0.0)
    else:
        far = (x, y - length - 0.05, 0.0)
    box(mesh, steel, (far[0] - 0.055, far[1] - 0.035, far[2] - 0.035),
        (far[0] + 0.055, far[1] + 0.035, far[2] + 0.035), uv_scale=0.2)


def footings(mesh):
    """Four concrete pads, one under each leg: what a tower actually stands on."""
    concrete = mesh.faces('footing', 'concrete')
    for sx in (-1, 1):
        for sz in (-1, 1):
            box(mesh, concrete, (sx * BASE_HALF - 0.19, 0.0, sz * BASE_HALF - 0.19),
                (sx * BASE_HALF + 0.19, 0.10, sz * BASE_HALF + 0.19), uv_scale=0.5)
            for bx in (-0.10, 0.06):
                for bz in (-0.10, 0.06):
                    bolt(mesh, mesh.faces('footing', 'plate'),
                         (sx * BASE_HALF + bx + 0.02, 0.10, sz * BASE_HALF + bz + 0.02), 'y',
                         0.014, 0.022, uv_scale=0.1)


def tower(duty):
    """One tower, by duty: what changes between the three and what does not."""
    mesh = Mesh()
    steel = mesh.faces('body', 'steel')

    footings(mesh)
    legs(mesh, steel)
    levels = frames(mesh, steel)
    diagonals(mesh, steel, levels)
    if duty in ('tension', 'terminal'):
        # the second diagonal in every panel: a cross brace, which is what a tower under load carries
        stack = [0.0] + levels
        for panel in range(len(stack) - 1):
            y0, y1 = stack[panel], stack[panel + 1]
            h0, h1 = leg_half(y0), leg_half(y1)
            corners = ((-1, -1), (1, -1), (1, 1), (-1, 1))
            for face in range(4):
                ax, az = corners[face]
                bx, bz = corners[(face + 1) % 4]
                if panel % 2:
                    start, end = (bx * h0, y0, bz * h0), (ax * h1, y1, az * h1)
                else:
                    start, end = (ax * h0, y0, az * h0), (bx * h1, y1, bz * h1)
                strut(mesh, steel, start, end, BRACE * 0.34, BRACE * 0.34, uv_scale=0.3)

    crossarm(mesh, mesh.faces('arm', 'steel'), LOWER_ARM, LOWER_REACH, 0.42)
    crossarm(mesh, mesh.faces('arm', 'steel'), UPPER_ARM, UPPER_REACH, 0.34)
    peak(mesh, steel)

    horizontal = duty in ('tension', 'terminal')
    for index, (x, y) in enumerate(PHASES):
        insulator_string(mesh, index + 1, x, y, horizontal=horizontal)

    if duty == 'terminal':
        # the back-stay: the whole pull of the line is on one side, so it is anchored on the other
        for sx in (-1, 1):
            strut(mesh, mesh.faces('stay', 'steel'), (sx * WAIST_HALF, BODY_TOP, WAIST_HALF),
                  (sx * BASE_HALF * 1.25, 0.06, BASE_HALF * 1.55), BRACE * 0.6, BRACE * 0.6,
                  uv_scale=0.4)
            box(mesh, mesh.faces('stay', 'concrete'),
                (sx * BASE_HALF * 1.25 - 0.16, 0.0, BASE_HALF * 1.55 - 0.16),
                (sx * BASE_HALF * 1.25 + 0.16, 0.10, BASE_HALF * 1.55 + 0.16), uv_scale=0.5)

    # the number plate and the danger sign, low on one leg where every tower carries them
    box(mesh, mesh.faces('plate', 'sign'), (-0.14, 1.10, -leg_half(1.10) - 0.020),
        (0.14, 1.46, -leg_half(1.10) - 0.008), uv_scale=1.0)
    return mesh


MODELS = [
    ('lattice_suspension', lambda: tower('suspension')),
    ('lattice_tension', lambda: tower('tension')),
    ('lattice_terminal', lambda: tower('terminal')),
]

USED = ('steel', 'plate', 'porcelain', 'concrete', 'sign')


def main():
    for name, builder in MODELS:
        mesh = builder()
        directory = os.path.join(OUT, name)
        mesh.write(os.path.join(directory, name + '.obj'), name + '.mtl', 'gen_tower_models.py')
        write_mtl(os.path.join(directory, name + '.mtl'), USED, MATERIALS, 'gen_tower_models.py')
        vertices, faces = mesh.stats()
        groups = ['%s_%s' % (obj, material) for obj, material, face_list in mesh.objects if face_list]
        print('%-20s %5d vertices, %5d faces, %2d groups' % (name, vertices, faces, len(groups)))
    print('\nthe six insulator strings are named insulator_1 .. insulator_6, in the order')
    print('ObjDefinitions declares them: the lower arm outward-in, then the upper arm.')


if __name__ == '__main__':
    main()
