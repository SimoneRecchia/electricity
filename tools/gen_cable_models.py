#!/usr/bin/env python3
"""The string cable: 16 OBJ pieces, their block models, the multipart blockstate, the Java shapes.

    python3 tools/gen_cable_models.py            # writes the assets and runs its own checks
    python3 tools/gen_cable_models.py --java     # prints the tables DcCableBlock declares

Two traps live here.  Forge's block-model OBJ loader reads the *block's own frame* - corner at the
origin, 0..1 - not the centred frame the machines use; check_inside_block enforces it.  And a block
model's texture is a sprite in an atlas, so a uv past 1 samples the sprite next door; check_inside_sprite
enforces that.  Both faults are invisible outside the game.

The middle of a block is chosen by which sides connect, all sixteen of them, and each has its own piece.
HUB_LO is the figure everything else follows from - see piece_bend.
"""

import itertools
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from modellib import (FITTING, HEX, MC4, MC4_JOINT, MC4_JOINT_LENGTH,          # noqa: E402
                      MC4_LENGTH, MC4_PIN, MC4_RADIUS, MC4_SPREAD, MC4_STAGGER, Mesh, arc, box,
                      clad_box, cylinder, mc4, tube, write_mtl)

ASSETS = os.path.join('src', 'main', 'resources', 'assets', 'electricity')
BLOCKSTATES = os.path.join(ASSETS, 'blockstates')
BLOCK_MODELS = os.path.join(ASSETS, 'models', 'block')
ITEM_MODELS = os.path.join(ASSETS, 'models', 'item')
OBJ_DIR = os.path.join(ASSETS, 'models', 'dc_string_cable')

NAME = 'dc_string_cable'
SIDES = ('north', 'east', 'south', 'west')

# The materials, and the textures they resolve to.
# model's own textures map, the way a vanilla model declares one - so the paths live in one place.
MATERIALS = {name: '#' + name for name in
             ('core', 'cleat', 'tie', 'plug_plus', 'plug_minus', 'joint_plus', 'joint_minus', 'gland',
              'jbox', 'jbox_side', 'trench')}
TEXTURES = {
    'core': 'electricity:block/dc_core',
    'cleat': 'electricity:block/dc_cleat',
    'tie': 'electricity:block/dc_tie',
    'plug_plus': 'electricity:block/dc_connector_plus',
    'plug_minus': 'electricity:block/dc_connector_minus',
    'joint_plus': 'electricity:block/dc_joint_plus',
    'joint_minus': 'electricity:block/dc_joint_minus',
    'gland': 'electricity:block/dc_gland',
    'jbox': 'electricity:block/dc_jbox',
    'jbox_side': 'electricity:block/dc_jbox_side',
    'trench': 'electricity:block/dc_trench',
}

# ---------------------------------------------------------------- the pair, in sixteenths
# axes - which is not the frame the rest of this mod's OBJ files use and is not a choice either.

# A 6 mm2 H1Z2Z2-K is 6.9 mm across.
CORE_RADIUS = 0.65
CORE_OFFSET = 1.05
CORES = (8.0 - CORE_OFFSET, 8.0 + CORE_OFFSET)
# The cable rests on the ground
CORE_Y = CORE_RADIUS

# Where an arm stops and the middle begins.
HUB_LO = 5.4
HUB_HI = 16.0 - HUB_LO

# Where the plug's own axis starts, for the longer of the pair.  MC4 and its figures are in modellib,
# shared with gen_pv_models.  The tip has to stay a pixel inside the block - check_inside_block.
PLUG_START = 15.0 - MC4_LENGTH - MC4_PIN
# A plug is fatter than the cable it is moulded onto, so a plug resting on the ground holds its own axis
PLUG_Y = MC4_RADIUS
PLUG_RISE = 2.2

# Where the mated joint on a straight run sits: centred in the middle, so each arm's cable has room to
# splay onto the joint's lane and climb onto its axis before it gets there.
JOINT_LO = 8.0 - MC4_JOINT_LENGTH / 2.0
JOINT_HI = 8.0 + MC4_JOINT_LENGTH / 2.0

# How many segments a quarter turn is swept in.
# costs eight rings of thirty-two.
BEND_STEPS = 8
# The elbow where a run turns up a wall
CLIMB_RADIUS = 2.4
WALL_STANDOFF = CORE_RADIUS + 0.5

# Height of the ground surface inside a buried block, and the rim that holds the block boundary shut.
# Deep enough for the fattest thing a run puts in it, which is the coupling ring on an MC4 plug: at 14
# the plug's crown left the block and check_inside_block failed the build.
GROUND = 16.0 - 2.0 - MC4_RADIUS * 2.0
RIM = 1.0


def out(value):
    """A figure in sixteenths, as the OBJ's own units."""
    return value / 16.0


def at(x, y, z):
    """A point in sixteenths, in the block's own frame: 0 to 1 on every axis, corner at the origin."""
    return (out(x), out(y), out(z))


# ---------------------------------------------------------------- the parts

class Run:
    """A length of cable swept along a path given in sixteenths."""

    def __init__(self, path, radius=CORE_RADIUS, material='core', joint=None, ends=(True, True)):
        self.path = _dedupe([tuple(float(c) for c in p) for p in path])
        self.radius, self.material = radius, material
        # Parts that name the same joint are one fitting, and check_disjoint lets them touch: a tail and
        # the plug moulded onto it are not two objects.
        self.joint = joint
        # Which ends are capped, and only a *free* end is.  A cap where another tube carries on is a disc
        # sitting in the same plane as that tube's own end, and the disc wins: it takes its colour from the
        # middle of dc_core, so every joint in a run came out as a pale ring across the cable.
        self.ends = ends

    def draw(self, mesh):
        faces = mesh.faces('cable', self.material)
        cap_ends = tuple(end for end, wanted in ((-1, self.ends[0]), (1, self.ends[1])) if wanted)
        # The texture wraps exactly once round the tube and exactly once along it.
        tube(mesh, faces, [at(*p) for p in self.path], out(self.radius), sides=FITTING,
             uv_scale=1.0, uv_along=1.0, caps=faces if cap_ends else None, cap_ends=cap_ends)

    def length(self):
        return sum(math.dist(self.path[i], self.path[i + 1]) for i in range(len(self.path) - 1))

    def boxes(self):
        """One box a segment, so a curve's outline follows the curve instead of boxing the whole arc."""
        result = []
        for i in range(len(self.path) - 1):
            a, b = self.path[i], self.path[i + 1]
            moving = [j for j in range(3) if abs(b[j] - a[j]) > 1e-6]
            lo, hi = [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]
            for j in range(3):
                pad = 0.0 if moving == [j] else self.radius
                lo[j] = min(a[j], b[j]) - pad
                hi[j] = max(a[j], b[j]) + pad
            result.append((tuple(lo), tuple(hi)))
        return result

    def turned(self, quarter):
        return Run([_spin(p, quarter) for p in self.path], self.radius, self.material, self.joint,
                   self.ends)


class Barrel:
    """A round fitting: a cleat's screw, or a gland through a junction box's wall."""

    def __init__(self, group, material, centre, axis, radius, low, high, cap=True, sleeve=False,
                 band=None, sides=FITTING, taper=1.0, outward=1):
        self.group, self.material, self.centre, self.axis = group, material, centre, axis
        self.radius, self.low, self.high, self.cap = radius, low, high, cap
        # Which window of its texture this segment takes
        # own length rather than something that tiles.
        self.band = band
        # A sleeve is a fitting a cable runs *through* - a gland in a junction box's wall
        self.sleeve = sleeve
        # ``outward`` is which end is the far one: cylinder() tapers its +axis end, so a fitting facing
        self.sides, self.taper, self.outward = sides, taper, outward

    def draw(self, mesh):
        faces = mesh.faces(self.group, self.material)
        base = list(self.centre)
        base['xyz'.index(self.axis)] = self.low
        radius, taper = ((self.radius, self.taper) if self.outward > 0
                         else (self.radius * self.taper, 1.0 / self.taper))
        # A hexagon wants a flat on top, which is a vertex at sixty degrees.
        phase = math.pi / 3 if self.sides == HEX else (math.pi / 2 if self.axis == 'z' else 0.0)
        cylinder(mesh, faces, at(*base), self.axis, out(radius), out(self.high - self.low),
                 sides=self.sides, uv_scale=1.0, uv=self.band, taper=taper, phase=phase,
                 caps=faces if self.cap else None, cap_ends=(self.outward,) if self.cap else ())

    def boxes(self):
        index = 'xyz'.index(self.axis)
        span = self.radius * max(1.0, self.taper)
        lo, hi = [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]
        for i in range(3):
            if i == index:
                lo[i], hi[i] = self.low, self.high
            else:
                lo[i], hi[i] = self.centre[i] - span, self.centre[i] + span
        return [(tuple(lo), tuple(hi))]

    def turned(self, quarter):
        return _Boxes([_spin_box(b, quarter) for b in self.boxes()], sleeve=self.sleeve)


class Plug:
    """An MC4 moulding on a core: a plug at a free end, or a mated joint mid-run.

    modellib.mc4 draws it; its boxes are the profile's own steps.
    """

    def __init__(self, centre, base, index, start, profile=MC4, group='plug', joint=None):
        self.centre, self.base, self.index, self.start = centre, base, index, start
        self.profile, self.group = profile, group
        self.positive = index == 0
        # A joint is one plug pushed into another, so it has no free pin to show.
        self.pin = self.positive and profile is MC4
        self.material = '%s_%s' % (group, 'plus' if self.positive else 'minus')
        self.joint = joint

    def draw(self, mesh):
        mc4(mesh, mesh.faces(self.group, self.material),
            at(self.centre, self.base + PLUG_Y, self.start), 'z', unit=1.0 / 16.0,
            profile=self.profile, pin=self.pin)

    def boxes(self):
        y, result, cursor = self.base + PLUG_Y, [], self.start
        steps = list(self.profile) + ([(MC4_PIN, 0.22, 1.0, 0.0, 0.0)] if self.pin else [])
        for length, radius, taper, _, _ in steps:
            span = radius * max(1.0, taper)
            result.append(((self.centre - span, y - span, cursor),
                           (self.centre + span, y + span, cursor + length)))
            cursor += length
        return result

    def turned(self, quarter):
        return _Boxes([_spin_box(b, quarter) for b in self.boxes()])


class Slab:
    """A box of something that is not cable: a cleat's strap and feet, a junction box's shell."""

    def __init__(self, group, material, lo, hi, uv_scale=1.0):
        self.group, self.material = group, material
        self.lo, self.hi, self.uv_scale = tuple(lo), tuple(hi), uv_scale

    def draw(self, mesh):
        if isinstance(self.material, dict):
            clad_box(mesh, self.group, at(*self.lo), at(*self.hi), self.material,
                     uv_scale=self.uv_scale)
            return

        box(mesh, mesh.faces(self.group, self.material), at(*self.lo), at(*self.hi),
            uv_scale=self.uv_scale)

    def boxes(self):
        return [(self.lo, self.hi)]

    def turned(self, quarter):
        return _Boxes([_spin_box(b, quarter) for b in self.boxes()])


class _Boxes:
    """A part reduced to its boxes, once it has been turned: only the checks ever see one."""

    def __init__(self, boxes, sleeve=False):
        self._boxes = boxes
        self.sleeve = sleeve

    def boxes(self):
        return self._boxes


def _spin(point, quarter):
    """A point turned about the block's centre"""
    a, b = point[0] - 8.0, point[2] - 8.0
    for _ in range(quarter // 90):
        a, b = -b, a
    return (a + 8.0, point[1], b + 8.0)


def _spin_box(bounds, quarter):
    lo, hi = bounds
    corners = [_spin((x, lo[1], z), quarter) for x in (lo[0], hi[0]) for z in (lo[2], hi[2])]
    xs = [c[0] for c in corners]
    zs = [c[2] for c in corners]
    return ((min(xs), lo[1], min(zs)), (max(xs), hi[1], max(zs)))


def _dedupe(path):
    kept = [path[0]]
    for p in path[1:]:
        if math.dist(p, kept[-1]) > 1e-6:
            kept.append(p)
    return kept


# ---------------------------------------------------------------- the fittings

def plug_lane(centre):
    """Which lane a core's plug sits on: the pair splays from CORE_OFFSET out to MC4_SPREAD, because two
    coupling rings 2.2 px across will not quite lie 2.1 px apart."""
    return 8.0 + (centre - 8.0) / CORE_OFFSET * MC4_SPREAD


def plug_start(index):
    """Where a core's plug begins.  The second is MC4_STAGGER short of the first, which is how a string's
    two leads are cut and what makes the pair read as two connectors rather than one wide lump."""
    return PLUG_START - (MC4_STAGGER if index else 0.0)


def tail(centre, base, start, index, joint=None, free=False):
    """The core between a piece's own boundary and its plug: straight, then out and up onto the lane.

    ``free`` when the far end of the run is nothing - an offcut lying on the ground - so it takes a cap.
    """
    y, lane, into = base + CORE_Y, plug_lane(centre), plug_start(index)
    # It runs 0.30 px *into* the plug so its own end cap is buried: level with the plug's first face the
    # two discs are coplanar and flicker against each other.
    return Run([(centre, y, start), (centre, y, into - PLUG_RISE),
                (lane, base + PLUG_Y, into - 0.45), (lane, base + PLUG_Y, into + 0.30)],
               joint=joint, ends=(free, True))


def gland(base, side, centre):
    """An M16 cable gland through a box wall: the hex body against it, the compression nut in front.

    Its band is dc_gland's own strip, wall to tip.  ``sleeve`` is how check_disjoint knows the cable is
    meant to run through it rather than into it - see _sleeved.
    """
    axis = 'z' if side in ('north', 'south') else 'x'
    near = side in ('north', 'west')
    step = -1.0 if near else 1.0
    middle = [8.0, base + CORE_Y, 8.0]
    middle[0 if axis == 'z' else 2] = centre

    parts, cursor = [], HUB_LO if near else HUB_HI
    # (length, radius, taper, sides, band): a hex body and a nut tapering the way a compression nut does
    for length, radius, taper, sides, band in ((0.34, 0.92, 1.0, HEX, (0.0, 0.0, 1.0, 0.25)),
                                              (1.02, 0.86, 0.80, FITTING, (0.0, 0.25, 1.0, 1.0))):
        low, high = sorted((cursor, cursor + step * length))
        # Each step is closed at its outer end.  Open, the game culls the far inside wall too and a gland
        # is a hole you see the ground through - the cable running out through the disc hides its middle.
        parts.append(Barrel('gland', 'gland', tuple(middle), axis, radius, low, high, cap=True,
                            sleeve=True, band=band, sides=sides, taper=taper, outward=int(step)))
        cursor += step * length
    return parts


def tie(base, low, high):
    """A UV-black nylon cable tie round the pair, which is what actually holds a string down.

    A stainless cleat belongs where the cable is on a tray or up a wall, and that is where piece_climb
    keeps one.  On open ground a strap 5.2 px wide in bright steel was the loudest thing in the plant -
    from ten metres the run read as a double pipe cinched every third of a block.
    """
    crown = base + CORE_Y + CORE_RADIUS
    # clear of the cable, not into it: the strap passes outside the pair
    outer = CORE_OFFSET + CORE_RADIUS + 0.34
    return [
        # over the crown and down both flanks: the strap of a tie is one band, not a plate on feet
        Slab('tie', 'tie', (8.0 - outer, crown, low), (8.0 + outer, crown + 0.28, high), uv_scale=0.5),
        Slab('tie', 'tie', (8.0 - outer, base, low), (8.0 - outer + 0.28, crown, high), uv_scale=0.3),
        Slab('tie', 'tie', (8.0 + outer - 0.28, base, low), (8.0 + outer, crown, high), uv_scale=0.3),
        # the ratchet head, which is the one lump a tie has and the only way to tell which it is
        Slab('tie', 'tie', (8.0 + outer, base + 0.2, low - 0.10),
             (8.0 + outer + 0.42, base + 0.95, high + 0.10), uv_scale=0.4),
    ]


def cleat(base, low, high):
    """A stainless clip: a strap over the pair into a foot each side"""
    inner = CORE_OFFSET + CORE_RADIUS
    top = base + CORE_Y + CORE_RADIUS
    return [
        # the two feet, filling the gap between the pair and the edge of the middle exactly: any wider
        # and they stand inside the next arm along, any narrower and they cut into the cable
        Slab('cleat', 'cleat', (HUB_LO, base, low), (8.0 - inner, top, high), uv_scale=0.4),
        Slab('cleat', 'cleat', (8.0 + inner, base, low), (HUB_HI, top, high), uv_scale=0.4),
        # the strap, resting *on* the crown rather than sunk into it
        # pixel lower, which is a band cutting through the cable it is meant to hold
        Slab('cleat', 'cleat', (HUB_LO, top, low), (HUB_HI, top + 0.42, high), uv_scale=0.6),
        # and the screw through it
        Barrel('cleat', 'cleat', (8.0, 0.0, (low + high) / 2.0), 'y', 0.34, top + 0.42, top + 0.72),
    ]


# How tall the junction box stands.  At 2.9 px on a 5.2 px footprint it read as a plate lying on the sand
# rather than as an enclosure - the walls were there, there was just not enough of them to see.  The trench
# is the ceiling: GROUND plus this has to stay inside the block.
JBOX_TOP = 4.1
JBOX_LID = 0.30


def junction_box(base, glanded):
    """A small IP68 polycarbonate box, glanded on the walls that have a cable in them."""
    top = base + JBOX_TOP
    parts = [
        Slab('jbox', {'*': 'jbox_side'}, (HUB_LO, base, HUB_LO), (HUB_HI, top - JBOX_LID, HUB_HI)),
        # the lid, overhanging the body: the lip is what tells a player there is a lid at all
        Slab('jbox', {'up': 'jbox', '*': 'jbox_side'},
             (HUB_LO - 0.30, top - JBOX_LID, HUB_LO - 0.30), (HUB_HI + 0.30, top, HUB_HI + 0.30)),
    ]
    for side in glanded:
        for centre in CORES:
            parts += gland(base, side, centre)
    return parts


# ---------------------------------------------------------------- the sixteen middles

def piece_line(base):
    """Two opposite sides: the pair runs through, and it has a mated pair of MC4s in it.

    A string is a chain of finite lengths plugged together - module lead into module lead - so a metre of
    one has a joint in it.  With plugs only on the dead end and the offcut, a player who lays cable from a
    row to an inverter never saw a connector at all.  The cleat that used to hold this middle down is on
    the arms now, either side of the joint, which is where a cleat goes.
    """
    y, parts = base + CORE_Y, []
    for index, c in enumerate(CORES):
        lane, name = plug_lane(c), 'joint_%d' % index
        parts.append(Plug(lane, base, index, JOINT_LO, profile=MC4_JOINT, group='joint', joint=name))
        # in from each arm, splaying onto the joint's lane and climbing onto its axis as it comes.  0.30 px
        # inside the joint, so the tube's own cap is buried rather than coplanar with the joint's face.
        parts.append(Run([(c, y, HUB_LO), (lane, base + PLUG_Y, JOINT_LO + 0.30)], joint=name,
                         ends=(False, True)))
        parts.append(Run([(lane, base + PLUG_Y, JOINT_HI - 0.30), (c, y, HUB_HI)], joint=name,
                         ends=(True, False)))
    return parts


def piece_bend(base):
    """Two adjacent sides: the pair turns on a radius, swept rather than broken at a corner."""
    y = base + CORE_Y
    # A core comes in on one lane and leaves on the other
    # Which puts both centres at the same place, (HUB_HI, HUB_LO), so the pair is concentric.
    centre = (HUB_HI, y, HUB_LO)
    parts = []
    for lane, other in ((CORES[1], CORES[0]), (CORES[0], CORES[1])):
        radius = other - HUB_LO
        turn = arc(centre, radius, (0, 2), 180.0, 90.0, BEND_STEPS)
        # both ends meet an arm, by definition of a bend
        parts.append(Run([(lane, y, HUB_LO)] + [(p[0], y, p[2]) for p in turn]
                         + [(HUB_HI, y, other)], ends=(False, False)))
    return parts


def piece_end(base):
    """One side connected: the pair comes in and ends in two MC4 plugs."""
    parts = []
    for index, c in enumerate(CORES):
        parts.append(tail(c, base, HUB_LO, index, joint='lead_%d' % index))
        parts.append(Plug(plug_lane(c), base, index, plug_start(index),
                          joint='lead_%d' % index))
    return parts


def piece_loose(base):
    """Nothing connected: a length of pair lying where it was dropped"""
    parts = []
    for index, c in enumerate(CORES):
        parts.append(tail(c, base, 3.2, index, joint='lead_%d' % index, free=True))
        parts.append(Plug(plug_lane(c), base, index, plug_start(index),
                          joint='lead_%d' % index))
    return parts


def piece_tee(base):
    """Three sides: a junction box, glanded north, east and south."""
    return junction_box(base, ('north', 'east', 'south'))


def piece_cross(base):
    """Four sides: the same box with the fourth wall glanded too."""
    return junction_box(base, SIDES)


def piece_arm(base, near):
    """The pair from a block edge in to the middle piece, cleated where it crosses open ground."""
    y = base + CORE_Y
    # An arm is drawn only for a side that connects, so neither of its ends is ever free.
    return [Run([(c, y, near), (c, y, HUB_LO)], ends=(False, False)) for c in CORES] \
        + tie(base, 2.5, 3.5)


def piece_climb(base):
    """The pair going up the wall alongside"""
    y = base + CORE_Y
    turn = arc((0.0, y + CLIMB_RADIUS, WALL_STANDOFF + CLIMB_RADIUS), CLIMB_RADIUS, (1, 2),
               180.0, 270.0, BEND_STEPS)
    parts = []
    for c in CORES:
        path = ([(c, y, HUB_LO)] + [(c, p[1], p[2]) for p in turn] + [(c, 16.0, WALL_STANDOFF)])
        # down into the middle, up into the block above: a climb has no free end either
        parts.append(Run(path, ends=(False, False)))
    # Cable on a wall is cleated in steel, not tied: this is the one place a cleat belongs, and the strap
    # sits above the elbow where the run has gone vertical.
    return parts + wall_cleat(base)


def wall_cleat(base):
    """The stainless cleat that holds a climb to the wall it runs up."""
    inner = CORE_OFFSET + CORE_RADIUS
    face = WALL_STANDOFF + CORE_RADIUS
    return [
        Slab('cleat', 'cleat', (8.0 - inner, 9.0, 0.0), (8.0 + inner, 10.4, face - 0.02),
             uv_scale=0.4),
        Slab('cleat', 'cleat', (HUB_LO, 9.0, face - 0.02), (HUB_HI, 10.4, face + 0.40), uv_scale=0.6),
        Barrel('cleat', 'cleat', (8.0, 9.7, 0.0), 'z', 0.34, face + 0.40, face + 0.70),
    ]


def bedding():
    """The sand a buried cable is laid in, and the rim that keeps the block boundary solid."""
    parts = [Slab('bedding', 'trench', (0.0, 0.0, 0.0), (16.0, GROUND, 16.0))]
    for x0, z0, x1, z1 in ((0.0, 0.0, 16.0, RIM), (0.0, 16.0 - RIM, 16.0, 16.0),
                           (0.0, RIM, RIM, 16.0 - RIM), (16.0 - RIM, RIM, 16.0, 16.0 - RIM)):
        parts.append(Slab('bedding', 'trench', (x0, GROUND, z0), (x1, 16.0, z1), uv_scale=0.5))
    return parts


# Which middle a set of connected sides gets, and how far round it is turned.
HUBS = {
    (): ('loose', 0),
    ('north',): ('end', 0), ('east',): ('end', 90),
    ('south',): ('end', 180), ('west',): ('end', 270),
    ('north', 'south'): ('line', 0), ('east', 'west'): ('line', 90),
    ('north', 'east'): ('bend', 0), ('east', 'south'): ('bend', 90),
    ('south', 'west'): ('bend', 180), ('north', 'west'): ('bend', 270),
    ('north', 'east', 'south'): ('tee', 0), ('east', 'south', 'west'): ('tee', 90),
    ('north', 'south', 'west'): ('tee', 180), ('north', 'east', 'west'): ('tee', 270),
    ('north', 'east', 'south', 'west'): ('cross', 0),
}

MIDDLES = {'loose': piece_loose, 'end': piece_end, 'line': piece_line, 'bend': piece_bend,
           'tee': piece_tee, 'cross': piece_cross}
QUARTERS = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


# ---------------------------------------------------------------- writing

def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def write_piece(name, parts):
    """One piece as an OBJ, its material library"""
    mesh = Mesh()
    for part in parts:
        part.draw(mesh)

    seen = []
    for _, material, faces in mesh.objects:
        if faces and material not in seen:
            seen.append(material)

    check_inside_sprite(name, mesh)
    check_inside_block(name, mesh)
    mesh.write(os.path.join(OBJ_DIR, name + '.obj'), name + '.mtl', 'gen_cable_models.py')
    write_mtl(os.path.join(OBJ_DIR, name + '.mtl'), seen, MATERIALS, 'gen_cable_models.py')

    write(os.path.join(BLOCK_MODELS, name + '.json'), {
        'loader': 'forge:obj',
        'model': 'electricity:models/%s/%s.obj' % (NAME, name),
        # Not flipped, and the reason is exact rather than a preference.
        'flip_v': False,
        # Culling off: the loader would drop the quads that lie on a block boundary, and on a cable
        'automatic_culling': False,
        # Shading off, and this is what makes a cable read as one solid object.
        'shade_quads': False,
        'textures': dict(TEXTURES, particle=TEXTURES['core']),
    })
    return mesh.stats()[1]


def check_inside_sprite(name, mesh):
    """Fails if any uv leaves the tile, which in a block model means leaving the *sprite*."""
    for u, v in mesh.vt:
        if not (-1e-6 <= u <= 1.0 + 1e-6 and -1e-6 <= v <= 1.0 + 1e-6):
            raise SystemExit('%s: uv (%.3f, %.3f) is outside its sprite, so it would sample the '
                             'texture next to it in the atlas' % (name, u, v))


def check_inside_block(name, mesh):
    """Fails if a piece leaves its block"""
    margin = 1.0 / 16.0
    for index, (x, y, z) in enumerate(mesh.v):
        if not all(-margin <= v <= 1.0 + margin for v in (x, y, z)):
            raise SystemExit('%s: vertex %d at (%.3f, %.3f, %.3f) is outside the block, so Forge\'s '
                             'loader would draw it in the wrong place and cull the wrong faces'
                             % (name, index + 1, x, y, z))


def blockstate():
    """Multipart, and the sixteen middles are an exact partition of the states."""
    parts = []
    for buried, prefix in ((False, ''), (True, 'trench_')):
        if buried:
            parts.append({'when': {'buried': 'true'},
                          'apply': {'model': 'electricity:block/%s_trench_bed' % NAME}})

        for connected, (kind, turn) in sorted(HUBS.items()):
            when = {'buried': 'true' if buried else 'false'}
            for side in SIDES:
                when[side] = 'side|up' if side in connected else 'none'
            apply = {'model': 'electricity:block/%s_%s%s' % (NAME, prefix, kind)}
            if turn:
                apply['y'] = turn
            parts.append({'when': when, 'apply': apply})

        # A trench has nothing to climb: the pair is already at the surface
        # the next block along without going anywhere.
        wanted = (('arm', 'side|up'),) if buried else (('arm', 'side'), ('climb', 'up'))
        for side, turn in QUARTERS.items():
            for suffix, values in wanted:
                apply = {'model': 'electricity:block/%s_%s%s' % (NAME, prefix, suffix)}
                if turn:
                    apply['y'] = turn
                parts.append({'when': {'buried': 'true' if buried else 'false', side: values},
                              'apply': apply})

    return {'multipart': parts}


# ---------------------------------------------------------------- checks

def parts_of(state):
    """Every part the given connection state draws, turned into the block's own frame and labelled."""
    connected = tuple(s for s in SIDES if state[s] != 'none')
    kind, turn = HUBS[connected]
    labelled = [(p.turned(turn), '%s[%s]' % (kind, getattr(p, 'joint', None) or i))
                for i, p in enumerate(MIDDLES[kind](0.0))]
    for side in SIDES:
        if state[side] == 'none':
            continue

        pieces = piece_climb(0.0) if state[side] == 'up' else piece_arm(0.0, 0.0)
        labelled += [(p.turned(QUARTERS[side]), '%s:%s[%d]' % (side, state[side], i))
                     for i, p in enumerate(pieces)]
    return labelled


def check_disjoint():
    """Proves nothing a run draws touches anything else it draws, in any of the 162 states."""
    problems = []
    for connected in HUBS:
        for values in itertools.product(*[('side', 'up') if s in connected else ('none',)
                                          for s in SIDES]):
            state = dict(zip(SIDES, values))
            labelled = parts_of(state)
            for (a, la), (b, lb) in itertools.combinations(labelled, 2):
                if la == lb:
                    continue

                if isinstance(a, Run) and isinstance(b, Run):
                    # Two cables that share an end are a *joint* - an arm meeting the middle it feeds -
                    # and they are meant to touch there.  Everything else has to clear.
                    if _jointed(a.path, b.path):
                        continue

                    gap = _path_gap(a.path, b.path)
                    if gap < a.radius + b.radius - 1e-6:
                        problems.append('%s and %s come within %.2f px, and they are %.2f wide'
                                        % (la, lb, gap, a.radius + b.radius))
                    continue

                sleeve, through = _sleeved(a, b)
                for one, other in itertools.product(a.boxes(), b.boxes()):
                    share = [min(one[1][i], other[1][i]) - max(one[0][i], other[0][i])
                             for i in range(3)]
                    if not all(v > 1e-6 for v in share):
                        continue

                    if sleeve is not None:
                        bore = sleeve.boxes()[0]
                        # every axis but the one it overlaps along has to be inside the bore
                        # what 'the cable goes through the gland' means as an inequality
                        axial = max(range(3), key=lambda i: share[i])
                        if all(bore[0][i] - 1e-6 <= other[0][i] and other[1][i] <= bore[1][i] + 1e-6
                               for i in range(3) if i != axial) or \
                           all(bore[0][i] - 1e-6 <= one[0][i] and one[1][i] <= bore[1][i] + 1e-6
                               for i in range(3) if i != axial):
                            continue

                        problems.append('%s clips the gland %s rather than passing through it'
                                        % (lb if through is b else la, la if through is b else lb))
                        continue

                    problems.append('%s and %s share %.2f x %.2f x %.2f px'
                                    % (la, lb, share[0], share[1], share[2]))
    return sorted(set(problems))


def clearances():
    """How much room the two cores of a bend and of a climb keep"""
    out = {}
    for name, builder in (('bend', piece_bend), ('climb', piece_climb)):
        runs = [p for p in builder(0.0) if isinstance(p, Run)]
        out[name] = min(_path_gap(a.path, b.path) for a, b in itertools.combinations(runs, 2))
    return out


def _sleeved(a, b):
    """Whether one of a pair is a gland and the other the cable running through it."""
    for one, other in ((a, b), (b, a)):
        if getattr(one, 'sleeve', False) and isinstance(other, Run):
            return one, other
    return None, None


def _jointed(first, second):
    return any(math.dist(a, b) < 1e-6 for a in (first[0], first[-1]) for b in (second[0], second[-1]))


def _path_gap(first, second):
    """Closest approach between two polylines, sampled finely enough for a 1 px cable."""
    def samples(path):
        for i in range(len(path) - 1):
            a, b = path[i], path[i + 1]
            steps = max(2, int(math.dist(a, b) * 6))
            for k in range(steps + 1):
                t = k / steps
                yield tuple(a[j] + (b[j] - a[j]) * t for j in range(3))

    return min(math.dist(p, q) for p in samples(first) for q in samples(second))


# ---------------------------------------------------------------- the Java tables

def merged(boxes):
    """Boxes cut to the block and merged where one contains another, so a curve is not fifty boxes."""
    inside = []
    for lo, hi in boxes:
        lo = tuple(min(max(v, 0.0), 16.0) for v in lo)
        hi = tuple(min(max(v, 0.0), 16.0) for v in hi)
        if all(hi[i] - lo[i] > 1e-6 for i in range(3)):
            inside.append((lo, hi))

    kept = []
    for candidate in sorted(inside, key=lambda b: -sum(b[1][i] - b[0][i] for i in range(3))):
        contained = any(all(k[0][i] <= candidate[0][i] + 1e-6 and k[1][i] >= candidate[1][i] - 1e-6
                            for i in range(3)) for k in kept)
        if not contained:
            kept.append(candidate)
    return kept


def shape(boxes):
    lines = ['Block.box(%s)' % ', '.join('%.2f' % v for v in (lo[0], lo[1], lo[2],
                                                             hi[0], hi[1], hi[2]))
             for lo, hi in merged(boxes)]
    if len(lines) == 1:
        return lines[0]
    return 'Shapes.or(%s)' % (',\n\t\t\t\t\t'.join(lines))


def tables():
    """Every shape the block declares, as text"""
    hubs = {}
    for connected, (kind, turn) in sorted(HUBS.items(), key=lambda kv: mask(kv[0])):
        boxes = [b for p in MIDDLES[kind](0.0) for b in p.turned(turn).boxes()]
        hubs['0b%s' % format(mask(connected), '04b')] = shape(boxes)

    out = {'STRING_HUBS': hubs}
    for label, builder in (('STRING_ARMS', lambda: piece_arm(0.0, 0.0)),
                           ('STRING_CLIMBS', lambda: piece_climb(0.0))):
        rows = {}
        for side, turn in (('NORTH', 0), ('EAST', 90), ('SOUTH', 180), ('WEST', 270)):
            rows['Direction.%s' % side] = shape([b for p in builder() for b in p.turned(turn).boxes()])
        out[label] = rows
    return out


def java():
    print('\n\t// ---- printed by tools/gen_cable_models.py --java ----\n')
    built = tables()
    print('\tprivate static final Map<Integer, VoxelShape> STRING_HUBS = Map.ofEntries(')
    print(',\n'.join('\t\t\tMap.entry(%s, %s)' % item for item in built['STRING_HUBS'].items()) + ');')
    for label in ('STRING_ARMS', 'STRING_CLIMBS'):
        print('\n\tprivate static final Map<Direction, VoxelShape> %s = Map.of(' % label)
        print(',\n'.join('\t\t\t%s, %s' % item for item in built[label].items()) + ');')


def check_java():
    """Proves DcCableBlock still declares the shape this draws, in all twenty-four states."""
    path = os.path.join('src', 'main', 'java', 'com', 'dooji', 'electricity', 'block',
                        'DcCableBlock.java')
    source = ' '.join(open(path).read().split())
    problems = []
    for label, rows in tables().items():
        for key, text in rows.items():
            wanted = ' '.join(('%s, %s' % (key, text)).split())
            if wanted not in source:
                problems.append('%s[%s] is not what DcCableBlock declares' % (label, key))
    return problems


def mask(connected):
    return sum(1 << SIDES.index(s) for s in connected)


# ---------------------------------------------------------------- the trunk, unchanged for now

TRUNK = 'dc_trunk_cable'
TRUNK_CABLE = '#cable'
TRUNK_TRENCH = '#trench'
TRUNK_GROUND = 15.0


def trunk_face(texture, uv, cull=None):
    entry = {'uv': [round(v, 2) for v in uv], 'texture': texture}
    if cull is not None:
        entry['cullface'] = cull
    return entry


def trunk_faces(lo, hi, cull=None):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    faces = {'up': trunk_face(TRUNK_CABLE, (x0, z0, x1, z1))}
    for name, uv in (('north', (x0, 16 - y1, x1, 16 - y0)), ('south', (x0, 16 - y1, x1, 16 - y0)),
                     ('west', (z0, 16 - y1, z1, 16 - y0)), ('east', (z0, 16 - y1, z1, 16 - y0))):
        faces[name] = trunk_face(TRUNK_CABLE, uv, cull=name if name == cull else None)
    return faces


def trunk_element(lo, hi, faces):
    return {'from': list(lo), 'to': list(hi), 'faces': faces}


def trunk_models():
    half = thick = 1.0
    path = 'electricity:block/dc_trunk_line'
    flat = {'parent': 'block/block', 'ambientocclusion': False,
            'textures': {'particle': TRUNK_CABLE, 'cable': path}}
    trench = {'parent': 'block/block',
              'textures': {'particle': TRUNK_TRENCH, 'cable': path,
                           'trench': 'electricity:block/dc_trench'}}

    def dot(y0, y1):
        lo, hi = (8 - half, y0, 8 - half), (8 + half, y1, 8 + half)
        return trunk_element(lo, hi, trunk_faces(lo, hi))

    def bar(y0, y1, near):
        lo, hi = (8 - half, y0, near), (8 + half, y1, 8 - half)
        return trunk_element(lo, hi, trunk_faces(lo, hi, cull='north' if near == 0.0 else None))

    def wall():
        lo, hi = (8 - half, 0.0, 0.0), (8 + half, 16.0, thick)
        return trunk_element(lo, hi, {
            'north': trunk_face(TRUNK_CABLE, (8 - half, 0, 8 + half, 16), cull='north'),
            'south': trunk_face(TRUNK_CABLE, (8 - half, 0, 8 + half, 16)),
            'west': trunk_face(TRUNK_CABLE, (0, 0, thick, 16)),
            'east': trunk_face(TRUNK_CABLE, (0, 0, thick, 16)),
        })

    bed = [trunk_element((0.0, 0.0, 0.0), (16.0, TRUNK_GROUND, 16.0), {
        'down': trunk_face(TRUNK_TRENCH, (0, 0, 16, 16), cull='down'),
        'up': trunk_face(TRUNK_TRENCH, (0, 0, 16, 16)),
        'north': trunk_face(TRUNK_TRENCH, (0, 16 - TRUNK_GROUND, 16, 16), cull='north'),
        'south': trunk_face(TRUNK_TRENCH, (0, 16 - TRUNK_GROUND, 16, 16), cull='south'),
        'west': trunk_face(TRUNK_TRENCH, (0, 16 - TRUNK_GROUND, 16, 16), cull='west'),
        'east': trunk_face(TRUNK_TRENCH, (0, 16 - TRUNK_GROUND, 16, 16), cull='east'),
    })]
    for x0, z0, x1, z1, outward in ((0.0, 0.0, 16.0, RIM, 'north'),
                                    (0.0, 16.0 - RIM, 16.0, 16.0, 'south'),
                                    (0.0, RIM, RIM, 16.0 - RIM, 'west'),
                                    (16.0 - RIM, RIM, 16.0, 16.0 - RIM, 'east')):
        bed.append(trunk_element((x0, TRUNK_GROUND, z0), (x1, 16.0, z1), {
            'up': trunk_face(TRUNK_TRENCH, (x0, z0, x1, z1)),
            'down': trunk_face(TRUNK_TRENCH, (x0, z0, x1, z1)),
            'north': trunk_face(TRUNK_TRENCH, (x0, 0, x1, RIM),
                                cull='north' if outward == 'north' else None),
            'south': trunk_face(TRUNK_TRENCH, (x0, 0, x1, RIM),
                                cull='south' if outward == 'south' else None),
            'west': trunk_face(TRUNK_TRENCH, (z0, 0, z1, RIM),
                               cull='west' if outward == 'west' else None),
            'east': trunk_face(TRUNK_TRENCH, (z0, 0, z1, RIM),
                               cull='east' if outward == 'east' else None),
        }))

    return {
        TRUNK: dict(flat, elements=[dot(0.0, thick)]),
        TRUNK + '_arm': dict(flat, elements=[bar(0.0, thick, 0.0)]),
        TRUNK + '_climb': dict(flat, elements=[wall()]),
        TRUNK + '_trench': dict(trench, elements=bed + [dot(TRUNK_GROUND, 16.0)]),
        TRUNK + '_trench_arm': dict(trench, elements=[bar(TRUNK_GROUND, 16.0, RIM)]),
    }


def trunk_blockstate():
    model = 'electricity:block/' + TRUNK
    parts = [{'when': {'buried': 'false'}, 'apply': {'model': model}}]
    rotations = (('north', 0), ('east', 90), ('south', 180), ('west', 270))
    for side, turn in rotations:
        for suffix, values in (('_arm', 'side|up'), ('_climb', 'up')):
            apply = {'model': model + suffix}
            if turn:
                apply['y'] = turn
            parts.append({'when': {'buried': 'false', side: values}, 'apply': apply})

    parts.append({'when': {'buried': 'true'}, 'apply': {'model': model + '_trench'}})
    for side, turn in rotations:
        apply = {'model': model + '_trench_arm'}
        if turn:
            apply['y'] = turn
        parts.append({'when': {'buried': 'true', side: 'side|up'}, 'apply': apply})

    return {'multipart': parts}


def main():
    if os.path.isdir(OBJ_DIR):
        for stale in os.listdir(OBJ_DIR):
            os.remove(os.path.join(OBJ_DIR, stale))

    faces, pieces = 0, 0
    for buried, prefix, base in ((False, '', 0.0), (True, 'trench_', GROUND)):
        for kind, builder in MIDDLES.items():
            parts = list(builder(base)) + (list(bedding()) if buried else [])
            faces += write_piece('%s_%s%s' % (NAME, prefix, kind), parts)
            pieces += 1

        parts = piece_arm(base, RIM if buried else 0.0) + (list(bedding()) if buried else [])
        faces += write_piece('%s_%sarm' % (NAME, prefix), parts)
        pieces += 1
        if not buried:
            faces += write_piece('%s_climb' % NAME, piece_climb(base))
            pieces += 1

    faces += write_piece('%s_trench_bed' % NAME, bedding())
    pieces += 1
    print('%s: %d OBJ pieces, %d faces in all' % (NAME, pieces, faces))

    state = blockstate()
    write(os.path.join(BLOCKSTATES, NAME + '.json'), state)
    print('%s: %d blockstate parts' % (NAME, len(state['multipart'])))

    problems = check_disjoint()
    for line in problems:
        print('    OVERLAP %s' % line)
    if problems:
        print('%d clash(es)' % len(problems))
    else:
        print('nothing a run draws touches anything else it draws, in any of the 162 states')
    for name, gap in sorted(clearances().items()):
        print("    the two cores of a %-5s stay %.2f px apart, on a %.2f px cable"
              % (name, gap, CORE_RADIUS * 2.0))

    for file_name, model in trunk_models().items():
        write(os.path.join(BLOCK_MODELS, file_name + '.json'), model)
    write(os.path.join(BLOCKSTATES, TRUNK + '.json'), trunk_blockstate())
    print('%s: unchanged, 5 models' % TRUNK)

    for cable in (NAME, TRUNK):
        write(os.path.join(ITEM_MODELS, cable + '.json'),
              {'parent': 'minecraft:item/generated',
               'textures': {'layer0': 'electricity:item/' + cable}})

    for line in check_java():
        print('    TABLE %s' % line)
        problems.append(line)
    if not problems:
        print('DcCableBlock declares the same shape this draws, in all twenty-four states')

    if '--java' in sys.argv:
        java()


if __name__ == '__main__':
    main()
