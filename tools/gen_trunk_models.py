#!/usr/bin/env python3
"""The trunk cable: the same sixteen middles as the string, on a cable three times across.

    python3 tools/gen_trunk_models.py            # writes the assets and runs its own checks
    python3 tools/gen_trunk_models.py --java     # prints the tables DcCableBlock declares

Built on gen_cable_models' framework rather than beside it: one implementation of the sixteen middles, the
blockstate, the disjointness proof and the Java tables, so the two gauges cannot drift apart.  Only the
figures and the fittings live here, because they are genuinely different objects - a 240 mm2 trunk is not
plugged together with connectors, it is crimped, heat-shrunk, bolted and cleated.

RADIUS is the figure everything else follows from.  HUB_LO is set by the bend: a big cable's inner radius
has to stay at twice its own, or the inner core folds back on itself.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_cable_models as cable                                                # noqa: E402
from gen_cable_models import Barrel, Plug, Product, Run, Slab                    # noqa: E402
from modellib import (FITTING, HEX, SHRINK, SHRINK_LENGTH, SHRINK_RADIUS, arc)   # noqa: E402

NAME = 'dc_trunk_cable'

MATERIALS = {name: '#' + name for name in
             ('core', 'shrink', 'lug', 'cleat', 'gland', 'jbox', 'jbox_side', 'trench')}
TEXTURES = {
    'core': 'electricity:block/dc_trunk_core',
    'shrink': 'electricity:block/dc_trunk_shrink',
    'lug': 'electricity:block/dc_trunk_lug',
    'cleat': 'electricity:block/dc_cleat',
    'gland': 'electricity:block/dc_gland',
    'jbox': 'electricity:block/dc_jbox',
    'jbox_side': 'electricity:block/dc_jbox_side',
    'trench': 'electricity:block/dc_trench',
}

# ---------------------------------------------------------------- the pair, in sixteenths

# One 240 mm2 aluminium single-core a pole, 27 mm across, drawn at the same exaggeration a string core is.
# 1.40 rather than the 1.55 the diameter alone would give: at 1.55 a swept bend and a wall riser in the
# same block came within 2.91 px of each other and needed 3.10, and check_disjoint would not have it.
RADIUS = 1.40
OFFSET = 2.05
CORES = (8.0 - OFFSET, 8.0 + OFFSET)
CORE_Y = RADIUS

# Where an arm stops.  A bend sweeps the inner core on a radius of CORES[0] - HUB_LO, and XLPE will not go
# below about twice its own diameter, so this is as high as it can be.
HUB_LO = 2.8
HUB_HI = 16.0 - HUB_LO

BEND_STEPS = 8

# Two shrink joints 4.4 px across will not lie 4.1 px apart, and a real installer never joints both poles
# at the same point anyway.  So they sit one after the other down the middle.
# Two of them end to end fill the middle exactly: HUB_HI - HUB_LO is 10.4 px and a sleeve is 5.1.
JOINT_LO = HUB_LO + 0.1
JOINT_STAGGER = SHRINK_LENGTH + 0.1

# The lug: a barrel crimped on the cable, then a flat palm with a bolt through it.
LUG_START = 6.2
LUG_BARREL = 3.6
LUG_RADIUS = 1.55
LUG_PALM = 3.4
LUG_HALF = 1.40
LUG_THICK = 0.62

# The box a trunk is linked in, and the trench that has to hold it.
BOX_TOP = 6.0
BOX_LID = 0.34
GLAND_RADIUS = 1.95
GROUND = 16.0 - 2.0 - BOX_TOP
RIM = 1.0


# ---------------------------------------------------------------- the fittings

def joint_start(index):
    """Where a core's shrink joint begins, the second one clear of the first down the run."""
    return JOINT_LO + (JOINT_STAGGER if index else 0.0)


def shrink(centre, base, index):
    """The heat-shrink sleeve over a crimped joint, on its own axis above the cable's."""
    return Plug(centre, base, index, joint_start(index), profile=SHRINK, group='shrink',
                joint='joint_%d' % index, axis_y=SHRINK_RADIUS, pin=False,
                materials=('shrink', 'shrink'))


def lug(centre, base, index):
    """A tinned copper compression lug: the crimped barrel, the palm, and the bolt through it.

    What a trunk terminates in.  A palm is flat, so it is not a profile - it is a plate on the end of a
    barrel, which is the whole difference between a bolted termination and a plug-in one.
    """
    axis = base + LUG_RADIUS
    palm = LUG_START + LUG_BARREL
    name = 'lead_%d' % index
    # A group per part - CLAUDE.md section 4: one lug per pole, so one bounding box per pole.
    group = 'lug_%s' % ('plus' if index == 0 else 'minus')
    return [
        # Both ends closed: the cable enters through a crimped rim and the far end runs into the palm,
        # which is a fifth of the barrel's diameter and covers nothing.
        Barrel(group, 'lug', (centre, axis, 0.0), 'z', LUG_RADIUS, LUG_START, palm, cap=True,
               band=(0.0, 0.0, 1.0, 0.72), outward=-1, cap_ends=(-1, 1), joint=name),
        Slab(group, 'lug', (centre - LUG_HALF, axis - LUG_THICK / 2.0, palm),
             (centre + LUG_HALF, axis + LUG_THICK / 2.0, palm + LUG_PALM), uv=(0.0, 0.74, 1.0, 1.0),
             joint=name),
        # the bolt hole, as the bolt that goes through it: a hole is not drawable and a bolt is the point.
        # For a y axis it is x and z that place it, so the z here is the palm's own middle.
        Barrel(group, 'lug', (centre, axis, palm + LUG_PALM * 0.62), 'y', 0.55,
               axis - LUG_THICK / 2.0 - 0.30, axis + LUG_THICK / 2.0 + 0.30, cap=True, sides=HEX,
               band=(0.0, 0.0, 1.0, 0.30), cap_ends=(-1, 1), joint=name),
    ]


def cleat(base, low, high):
    """A two-bolt aluminium cleat, which is what holds a 240 mm2 single-core down.

    Non-magnetic on purpose: a steel cleat round one pole of a direct-current pair heats in the field it
    sits in.  A base under the pair, a strap over it, and a bolt each side of both cores.
    """
    outer = OFFSET + RADIUS + 0.45
    crown = base + CORE_Y + RADIUS
    # No base plate: the cable rests on the ground, so there is nothing to put under it.  A strap over the
    # crown into a foot each side and one between the poles, which is what a two-bolt cleat is.
    # dc_cleat's top half is a face looking up and its bottom half the same metal at Minecraft's own
    # factor for a vertical one: the strap over the crown takes the first, the three feet the second.
    return [
        Slab('cleat', 'cleat', (8.0 - outer, crown, low), (8.0 + outer, crown + 0.40, high),
             uv=cable.CLEAT_TOP),
        Slab('cleat', 'cleat', (8.0 - outer, base, low), (8.0 - outer + 0.40, crown, high),
             uv=cable.CLEAT_SIDE),
        Slab('cleat', 'cleat', (8.0 + outer - 0.40, base, low), (8.0 + outer, crown, high),
             uv=cable.CLEAT_SIDE),
        Slab('cleat', 'cleat', (8.0 - 0.40, base, low), (8.0 + 0.40, crown, high),
             uv=cable.CLEAT_SIDE),
    ] + [Barrel('cleat', 'cleat', (x, 0.0, (low + high) / 2.0), 'y', 0.42, crown + 0.40, crown + 0.78,
                sides=HEX, band=cable.CLEAT_TOP)
         for x in (8.0 - outer + 0.20, 8.0 + outer - 0.20)]


def gland(base, side, centre, index):
    """A gland through the link box's wall, sized to the trunk rather than to a string core."""
    axis = 'z' if side in ('north', 'south') else 'x'
    near = side in ('north', 'west')
    step = -1.0 if near else 1.0
    middle = [8.0, base + CORE_Y, 8.0]
    middle[0 if axis == 'z' else 2] = centre
    group = 'gland_%s_%s' % (side, 'plus' if index == 0 else 'minus')

    parts, cursor = [], HUB_LO if near else HUB_HI
    # 1.50 px of protrusion in all: any more and the gland reaches the arm's own cleat, which has to sit
    # hard against the block edge because a bend on this gauge sweeps almost to HUB_LO.
    for length, radius, taper, sides, band in ((0.45, GLAND_RADIUS, 1.0, HEX, (0.0, 0.0, 1.0, 0.25)),
                                              (1.05, GLAND_RADIUS - 0.10, 0.80, FITTING,
                                               (0.0, 0.25, 1.0, 1.0))):
        low, high = sorted((cursor, cursor + step * length))
        parts.append(Barrel(group, 'gland', tuple(middle), axis, radius, low, high, cap=True,
                            sleeve=True, band=band, sides=sides, taper=taper, outward=int(step)))
        cursor += step * length
    return parts


def link_box(base, glanded):
    """The box a trunk is linked in: bigger than a string's, because the cable in it is."""
    top = base + BOX_TOP
    parts = [
        Slab('jbox', {'*': 'jbox_side'}, (HUB_LO, base, HUB_LO), (HUB_HI, top - BOX_LID, HUB_HI)),
        # the rim takes dc_jbox_side's lit top band, the same as the string's - see junction_box.
        Slab('jbox', {'up': 'jbox', '*': 'jbox_side'},
             (HUB_LO - 0.34, top - BOX_LID, HUB_LO - 0.34), (HUB_HI + 0.34, top, HUB_HI + 0.34),
             uv={'up': None, '*': (0.0, 0.0, 1.0, 0.055)}),
    ]
    for side in glanded:
        for index, centre in enumerate(CORES):
            parts += gland(base, side, centre, index)
    return parts


# ---------------------------------------------------------------- the sixteen middles

def piece_line(base):
    """Two opposite sides: the pair runs through, with a shrink joint in each core, staggered."""
    y, parts = base + CORE_Y, []
    for index, c in enumerate(CORES):
        name, into = 'joint_%d' % index, joint_start(index)
        parts.append(shrink(c, base, index))
        # in from each arm, rising onto the sleeve's axis; 0.35 px inside it so the cap is buried
        parts.append(Run([(c, y, HUB_LO), (c, base + SHRINK_RADIUS, into + 0.35)], radius=RADIUS,
                         joint=name, ends=(False, True)))
        parts.append(Run([(c, base + SHRINK_RADIUS, into + SHRINK_LENGTH - 0.35), (c, y, HUB_HI)],
                         radius=RADIUS, joint=name, ends=(True, False)))
    return parts


def piece_bend(base):
    """Two adjacent sides, swept on a radius a 240 mm2 cable will actually take."""
    y = base + CORE_Y
    centre = (HUB_HI, y, HUB_LO)
    parts = []
    for lane, other in ((CORES[1], CORES[0]), (CORES[0], CORES[1])):
        turn = arc(centre, other - HUB_LO, (0, 2), 180.0, 90.0, BEND_STEPS)
        parts.append(Run([(lane, y, HUB_LO)] + [(p[0], y, p[2]) for p in turn] + [(HUB_HI, y, other)],
                         radius=RADIUS, ends=(False, False)))
    return parts


def piece_end(base):
    """One side connected: the pair comes in and ends in two bolted lugs."""
    parts = []
    for index, c in enumerate(CORES):
        parts.append(tail(c, base, HUB_LO, 'lead_%d' % index))
        parts += lug(c, base, index)
    return parts


def piece_loose(base):
    """Nothing connected: a length of trunk lying where it was cut, lugs already crimped on."""
    parts = []
    for index, c in enumerate(CORES):
        parts.append(tail(c, base, 2.0, 'lead_%d' % index, free=True))
        parts += lug(c, base, index)
    return parts


def tail(centre, base, start, joint, free=False):
    """The core between a piece's boundary and its lug, rising onto the lug's own axis."""
    return Run([(centre, base + CORE_Y, start), (centre, base + LUG_RADIUS, LUG_START + 0.35)],
               radius=RADIUS, joint=joint, ends=(free, True))


def piece_tee(base):
    return link_box(base, ('north', 'east', 'south'))


def piece_cross(base):
    return link_box(base, cable.SIDES)


def piece_arm(base, near):
    """The pair from a block edge in to the middle, cleated where it crosses open ground.

    Hard against the block edge, because a bend on this gauge sweeps almost to HUB_LO and its outline pads
    by the cable's own radius.  A buried run gets none: it is bedded in sand, not cleated to anything.
    """
    y = base + CORE_Y
    runs = [Run([(c, y, near), (c, y, HUB_LO)], radius=RADIUS, ends=(False, False)) for c in CORES]
    return runs + (cleat(base, 0.05, 1.15) if near == 0.0 else [])


MIDDLES = {'loose': piece_loose, 'end': piece_end, 'line': piece_line, 'bend': piece_bend,
           'tee': piece_tee, 'cross': piece_cross}

TRUNK = Product(name=NAME, source='gen_trunk_models.py', materials=MATERIALS, textures=TEXTURES,
                middles=MIDDLES, arm=piece_arm, climb=None, ground=GROUND, rim=RIM,
                java_class='DcCableBlock', java_prefix='TRUNK')


def main():
    return cable.emit(TRUNK, RADIUS * 2.0)


if __name__ == '__main__':
    main()
