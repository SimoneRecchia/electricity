#!/usr/bin/env python3
"""The three lattice transmission towers: suspension, tension, terminal.

    python3 tools/gen_tower_models.py

Donaumast arrangement - two crossarm levels, three phases a side, earth wire at the peak.  The duty is
what makes them different objects: a suspension tower hangs its chains vertically, a tension tower takes
the difference between two pulls on horizontal chains and is braced for it, a terminal tower takes the
whole pull on one side and is stayed back against it.

PHASES is in wire-index order and cannot be reordered without moving every wire in every world.
"""

import functools
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from modellib import (FITTING, Mesh, bolt, box, coarse, cylinder, emit, strut,   # noqa: E402
                      tube)

OUT = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')

MATERIALS = {
    'steel': 'tower_steel.png',
    'plate': 'tower_plate.png',
    'porcelain': 'porcelain_brown.png',
    'concrete': 'pole_concrete.png',
    # bare stranded aluminium, the same tile the ground-laid conductors use
    'conductor': 'line_alu.png',
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
    # stopping short of the upper crossarm: a frame at exactly UPPER_ARM puts its own members on the same
    # plane as the arm's, which is two surfaces at one depth and flickers
    top = UPPER_ARM - 0.16
    levels += [BODY_TOP + (top - BODY_TOP) * i / 4.0 for i in range(1, 5)]
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


def arm_half_depth(x, reach, depth):
    """How far apart the arm's two main members are at this distance out along it."""
    span = max(reach - WAIST_HALF, 1.0e-6)
    t = min(1.0, max(0.0, (abs(x) - WAIST_HALF) / span))
    return depth * (1.0 - 0.65 * t)


def hanger(mesh, x, y, reach, depth):
    """The cross member and gusset plate one phase hangs from.

    Only the arm tips used to get a plate, so the four inner phases hung off nothing: their top disc
    floated between the arm's two main members, which at that station are 0.3 apart with air on the
    centreline. A real arm carries a transverse member at every hanging point.
    """
    half_z = arm_half_depth(x, reach, depth)
    strut(mesh, mesh.faces('arm', 'steel'), (x, y, -half_z), (x, y, half_z), BRACE * 0.40,
          BRACE * 0.40, uv_scale=0.3)
    box(mesh, mesh.faces('arm', 'plate'), (x - 0.075, y - 0.030, -0.075),
        (x + 0.075, y + 0.010, 0.075), uv_scale=0.4)


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


def insulator_string(mesh, index, x, y, axis='y', sign=-1.0):
    """A cap-and-pin insulator string: eighteen glass discs on a steel link, and its conductor clamp.

    ``axis`` is which way the string lies. A suspension tower's hangs, because the only thing it carries
    is the weight of the span: 'y'. A tension tower's lies **along the line**, which is z here, because
    the string is what takes the pull - drawn radially along the arm instead, it ran back through the
    arm's own lattice. ``sign`` is which way from the hanger.
    """
    porcelain = mesh.faces('insulator_%d' % index, 'porcelain')
    steel = mesh.faces('string_%d' % index, 'steel')

    length = DISCS * DISC_PITCH
    for disc in range(DISCS):
        along = (disc + 0.5) * DISC_PITCH * sign
        centre = (x, y + along, 0.0) if axis == 'y' else (x, y, along)

        # one disc: a shallow cap over a pin
        cylinder(mesh, porcelain, centre, axis, DISC_RADIUS, DISC_PITCH * 0.34, sides=FITTING,
                 uv_scale=1.0, taper=0.62)
        cylinder(mesh, steel, centre, axis, DISC_RADIUS * 0.30, DISC_PITCH * 0.5, sides=FITTING,
                 uv_scale=0.4)

    # The clamp the conductor is held in, on the string's far end, in a group of its own: it is where the
    # span actually lands, and LatticeTowerBlockEntity anchors a wire there rather than halfway up the
    # discs.
    far_along = (length + 0.05) * sign
    far = (x, y + far_along, 0.0) if axis == 'y' else (x, y, far_along)
    box(mesh, mesh.faces('clamp_%d' % index, 'steel'),
        (far[0] - 0.055, far[1] - 0.035, far[2] - 0.035),
        (far[0] + 0.055, far[1] + 0.035, far[2] + 0.035), uv_scale=0.2)
    return far


def jumper(mesh, index, x, y):
    """The loop of conductor between a tension tower's two dead-ends on one phase.

    The line stops at each string's clamp, so the phase is carried across the tower by a jumper hanging
    below the arm. Without it the two halves of a circuit are two spans that never meet.
    """
    length = DISCS * DISC_PITCH + 0.05
    drop = 0.30
    faces = mesh.faces('jumper_%d' % index, 'conductor')
    points = []
    steps = 10
    for step in range(steps + 1):
        t = -1.0 + 2.0 * step / steps
        points.append((x, y - drop * (1.0 - t * t), length * t))
    tube(mesh, faces, points, 0.030, sides=FITTING, uv_scale=1.0, uv_along=1.0, caps=faces)


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

    for index, (x, y) in enumerate(PHASES):
        reach, depth = (LOWER_REACH, 0.42) if y == LOWER_ARM else (UPPER_REACH, 0.34)
        hanger(mesh, x, y, reach, depth)
        if duty == 'suspension':
            insulator_string(mesh, index + 1, x, y, axis='y', sign=-1.0)
        elif duty == 'tension':
            # a dead-end each way and the jumper between them, which is what a section tower is
            for sign in (-1.0, 1.0):
                insulator_string(mesh, index + 1, x, y, axis='z', sign=sign)
            jumper(mesh, index + 1, x, y)
        else:
            # the whole pull comes from -z, which is the side the back-stay does not anchor
            insulator_string(mesh, index + 1, x, y, axis='z', sign=-1.0)

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


# ---------------------------------------------------------------- collision
#
# Authored rather than cut from the model, and this is the one place in the mod where that is right.
# check_hitboxes cuts a shape from every group a machine draws; on a lattice the diagonals sweep almost
# every cell of the bounding volume, which came to 308 cells a tower - a tower you cannot walk into and
# three hundred shell blocks a placement.  What a player should collide with is the structure: the four
# legs, the two crossarms, the peak, the footings.  Between the braces is air, which is what a lattice is.
COLLISION_LEG = 0.16          # how wide a leg collides, wider than the angle so a player cannot slip past
COLLISION_ARM = 0.20          # the crossarm's thickness, top and bottom of its members


def collision_boxes(duty):
    """Every box the tower collides as, in model space and in blocks.

    One box a leg a *block level* rather than a box a panel: a leg batters gently enough that a level's
    worth of it is one box, and per panel it straddled cells and came to a hundred and ten of them.
    """
    boxes = []
    top = PEAK - 1.6
    for sx in (-1, 1):
        for sz in (-1, 1):
            for level in range(int(math.ceil(top))):
                y0, y1 = float(level), min(top, level + 1.0)
                if y1 - y0 < 0.05:
                    continue

                half = leg_half((y0 + y1) * 0.5)
                boxes.append(((sx * half - COLLISION_LEG, y0, sz * half - COLLISION_LEG),
                              (sx * half + COLLISION_LEG, y1, sz * half + COLLISION_LEG)))
            boxes.append(((sx * BASE_HALF - 0.19, 0.0, sz * BASE_HALF - 0.19),
                          (sx * BASE_HALF + 0.19, 0.10, sz * BASE_HALF + 0.19)))

    for y, reach, depth in ((LOWER_ARM, LOWER_REACH, 0.42), (UPPER_ARM, UPPER_REACH, 0.34)):
        boxes.append(((-reach - 0.08, y - COLLISION_ARM * 0.5, -depth),
                      (reach + 0.08, y + COLLISION_ARM * 0.5, depth)))

    # The peak: a box a leg a level, the same as the body. Filling the square between the four legs
    # instead made a column 1.56 blocks across - nine cells a level, a solid block on the centreline, and
    # a player standing on the crossarm walking into air. Near the point the four boxes overlap and
    # coarse() merges them back into one, which is right: there the pyramid really is solid.
    for level in range(int(math.floor(top)), int(math.ceil(PEAK))):
        y0, y1 = max(top, float(level)), min(PEAK, level + 1.0)
        if y1 - y0 < 0.05:
            continue

        half = WAIST_HALF * (1.0 - ((y0 + y1) * 0.5 - top) / (PEAK - top))
        for sx in (-1, 1):
            for sz in (-1, 1):
                boxes.append(((sx * half - COLLISION_LEG, y0, sz * half - COLLISION_LEG),
                              (sx * half + COLLISION_LEG, y1, sz * half + COLLISION_LEG)))

    # No back-stay for the terminal tower. Two sloped runs across seven levels claim sixty-six cells on
    # their own, and what they would buy is collision with a guy that a player walks past rather than
    # into. The anchor pads at their feet are there, because those are on the ground.
    if duty == 'terminal':
        for sx in (-1, 1):
            boxes.append(((sx * BASE_HALF * 1.25 - 0.16, 0.0, BASE_HALF * 1.55 - 0.16),
                          (sx * BASE_HALF * 1.25 + 0.16, 0.10, BASE_HALF * 1.55 + 0.16)))

    boxes += string_boxes(duty)

    return [(tuple(min(a[i], b[i]) for i in range(3)), tuple(max(a[i], b[i]) for i in range(3)))
            for a, b in boxes]


def string_boxes(duty):
    """A box round each phase's insulator string, cut to it.

    A fitting is what a player aims at - a span is hung on one by pointing at it - so every insulator in
    this mod carries collision of its own. The jumper does not: a conductor is a wire, and no wire in this
    mod collides with anything.
    """
    length = DISCS * DISC_PITCH + 0.09
    half = 0.06
    boxes = []
    for x, y in PHASES:
        if duty == 'suspension':
            boxes.append(((x - half, y - length, -half), (x + half, y + 0.01, half)))
        elif duty == 'tension':
            boxes.append(((x - half, y - half, -length), (x + half, y + 0.01, length)))
        else:
            boxes.append(((x - half, y - half, -length), (x + half, y + 0.01, half)))

    return boxes


def collision_cells(duty):
    """The boxes clipped into block cells, keyed by cell, in the sixteenths Block.box wants."""
    cells = {}
    for lo, hi in collision_boxes(duty):
        for cx in range(int(math.floor(lo[0] + 0.5)), int(math.ceil(hi[0] + 0.5))):
            for cy in range(int(math.floor(lo[1])), int(math.ceil(hi[1]))):
                for cz in range(int(math.floor(lo[2] + 0.5)), int(math.ceil(hi[2] + 0.5))):
                    low = (max(lo[0], cx - 0.5), max(lo[1], cy), max(lo[2], cz - 0.5))
                    high = (min(hi[0], cx + 0.5), min(hi[1], cy + 1.0), min(hi[2], cz + 0.5))
                    if any(high[i] - low[i] < 0.03 for i in range(3)):
                        continue

                    box_px = (round((low[0] - cx + 0.5) * 16.0, 2), round((low[1] - cy) * 16.0, 2),
                              round((low[2] - cz + 0.5) * 16.0, 2),
                              round((high[0] - cx + 0.5) * 16.0, 2), round((high[1] - cy) * 16.0, 2),
                              round((high[2] - cz + 0.5) * 16.0, 2))
                    cells.setdefault((cx, cy, cz), set()).add(box_px)

    # coarse() per cell: a lattice cell held one box per member, and a tower a player climbs does not need
    # its collision carved per angle iron - modellib.COLLISION_SLACK decides how coarse, for the whole mod.
    out = {}
    for cell, boxes in sorted(cells.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
        pairs = coarse([((b[0], b[1], b[2]), (b[3], b[4], b[5])) for b in boxes])
        out[cell] = sorted(tuple(round(v, 2) for v in lo + hi) for lo, hi in pairs)
    return out


def tables():
    """The shared body and the three deltas, as the declarations LatticeTowerBlock holds.

    The three duties are one structure with three heads: two thirds of their cells are identical, and
    declaring them three times was two hundred lines of Java saying the same thing.  A delta cell *replaces*
    the body's rather than adding to it - coarse() may have merged a duty's own strings into the structure's
    boxes into fewer, bigger ones, so the duty's cell is already the whole of that cell.
    """
    per_duty = {duty: collision_cells(duty) for _, duty in MODELS}
    body = {cell: boxes for cell, boxes in per_duty['suspension'].items()
            if all(other.get(cell) == boxes for other in per_duty.values())}

    out = [('BODY_CELLS', body, None)]
    for _, duty in MODELS:
        out.append(('%s_CELLS' % duty.upper(),
                    {c: b for c, b in per_duty[duty].items() if body.get(c) != b}, 'BODY_CELLS'))
    return out


def declaration(name, cells, over):
    """One table as the line LatticeTowerBlock declares it on."""
    rows = []
    for (x, y, z), boxes in cells.items():
        drawn = ['Block.box(%s)' % ', '.join('%.2f' % v for v in box) for box in boxes]
        shape = drawn[0] if len(drawn) == 1 else 'Shapes.or(%s)' % (',\n\t\t\t\t\t'.join(drawn))
        rows.append('\t\t\tnew Cell(%d, %d, %d, %s)' % (x, y, z, shape))

    body = ',\n'.join(rows)
    if over is None:
        return '\tprivate static final List<Cell> %s = List.of(\n%s);' % (name, body)

    return '\tprivate static final List<Cell> %s = over(%s, List.of(\n%s));' % (name, over, body)


def check_java():
    """Proves LatticeTowerBlock still declares the cells this authors, body and all three deltas.

    Same reason gen_cable_models checks its own: check_hitboxes cannot compare these, because they are
    authored rather than cut from the model, so nothing else would notice them going stale.
    """
    path = os.path.join('src', 'main', 'java', 'com', 'dooji', 'electricity', 'block',
                        'LatticeTowerBlock.java')
    source = ' '.join(open(path).read().split())
    return ['%s is not what LatticeTowerBlock declares' % name
            for name, cells, over in tables() if ' '.join(declaration(name, cells, over).split()) not in source]


MODELS = [
    ('lattice_suspension', 'suspension'),
    ('lattice_tension', 'tension'),
    ('lattice_terminal', 'terminal'),
]

def main():
    duties = dict(MODELS)
    builders = [(name, functools.partial(tower, duty)) for name, duty in MODELS]
    for name, _ in emit(OUT, builders, MATERIALS, 'gen_tower_models.py'):
        cells = collision_cells(duties[name])
        print('    %3d collision cells, %3d boxes' % (len(cells), sum(len(b) for b in cells.values())))

    print('%d cells are the same in all three duties, and are declared once' % len(tables()[0][1]))

    problems = check_java()
    for line in problems:
        print('    TABLE %s' % line)
    if not problems:
        print('LatticeTowerBlock declares the cells this authors, for all three duties')

    if '--java' in sys.argv:
        for name, cells, over in tables():
            print('\n\t/** ---- %d cell(s), printed by tools/gen_tower_models.py --java ---- */'
                  % len(cells))
            print(declaration(name, cells, over))


if __name__ == '__main__':
    main()
