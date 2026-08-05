#!/usr/bin/env python3
"""Generates the string cable's geometry, its blockstate, and the tables its block declares.

    python3 tools/gen_cable_models.py            # writes the assets
    python3 tools/gen_cable_models.py --java     # and prints the tables DcCableBlock declares

Writes OBJ pieces into src/main/resources/assets/electricity/models/dc_string_cable/, one block model
per piece next to the vanilla ones, and the multipart blockstate.

Why this is OBJ geometry inside a vanilla block model
-----------------------------------------------------
A cable is a cylinder, and this mod's standard says a round body gets the turbine's own subdivisions.
The vanilla JSON format cannot express a cylinder at all - an element with a rotation is a *union*, so
three boxes at 22.5 degrees make a twelve-pointed star rather than a twelve-sided prism.  Two attempts
out of axis-aligned boxes proved it: a flat painted bar read as a ribbon, and a stack of three stepped
boxes read as a square duct with a highlight down it.

So the pieces are real swept tubes at thirty-two sides, loaded through **Forge's own OBJ block-model
loader**.  That is the part that makes it affordable: a model with ``"loader": "forge:obj"`` is baked
into the chunk mesh like any other block model, so two hundred cables cost what two hundred blocks
cost - not what two hundred block entities cost, which is what the machines' pipeline would have made
them.

A purpose-made piece for every case
-----------------------------------
Nothing here is one shape stretched to cover several jobs.  The middle of the block is chosen by the
*set* of sides that connect, all sixteen of them, and each one is drawn for what it actually is:

  * two opposite sides - the pair runs straight through, under a stainless cleat
  * two adjacent - the pair **turns on a radius**, swept round a quarter circle rather than broken at a
    corner, and the two arcs are *concentric*: the inside core takes the tighter line and the outside
    one the wider, exactly a metre apart the whole way round, which is what a cleated pair does
  * one side - the pair ends in two MC4 plugs, red collar on the positive pole
  * none - a length of pair lying where it was dropped, with its plugs on
  * three sides - a junction box with three walls glanded
  * four - the same box with the fourth wall glanded too

A third leg cannot be a bare crossing: two runs can pass each other and two can turn, but something has
to *join* a third, and what joins direct-current strings is a small IP68 box with glands in it.

Each ``when`` names all four side properties - ``none`` for the ones it has not got and ``side|up`` for
the ones it has - so the sixteen conditions partition the states and exactly one middle is ever drawn.

Why the pattern and not the side
--------------------------------
Because a pair has a handedness.  The arms are one model turned by quarters, so the core on the west of
a north arm is the core on the north of an east arm; joining the cores through every straight and every
bend identifies all four of them, so no assignment of two colours survives a corner.  The real cable
settles it: H1Z2Z2-K to EN 50618 is black - carbon-black loaded, because that is what survives
twenty-five years of ultraviolet - and a plant marks the poles at the connectors.  Both cores are
black; the collars on the plugs carry the polarity.

Nothing coincides, which is the other half of it
------------------------------------------------
The version this replaces had every joint made of two boxes meeting on a plane and *both* drew a face
there - two surfaces at the same depth, which is the flicker a player saw at every corner.  A swept tube
has no internal joint: a bend is one continuous tube per core, and a climb is one tube from the middle
of the block, round the elbow, and up the wall.  Where two pieces really do meet - an arm against the
middle - both stop on the same plane and both close their end, which is a disc inside the neighbouring
cable and cannot be seen.  Left open, it is a hole wherever the cover is not exact.

The trunk cable
---------------
Still the old painted bar, on purpose: a different product doing a different job - one armoured home run
from a combiner to the cabinet, rather than the hundreds of string pairs a plant is stitched together
with - and it is next in line rather than done here.
"""

import itertools
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from modellib import (FITTING, Mesh, arc, box, clad_box, cylinder, tube,        # noqa: E402
                      write_mtl)

ASSETS = os.path.join('src', 'main', 'resources', 'assets', 'electricity')
BLOCKSTATES = os.path.join(ASSETS, 'blockstates')
BLOCK_MODELS = os.path.join(ASSETS, 'models', 'block')
ITEM_MODELS = os.path.join(ASSETS, 'models', 'item')
OBJ_DIR = os.path.join(ASSETS, 'models', 'dc_string_cable')

NAME = 'dc_string_cable'
SIDES = ('north', 'east', 'south', 'west')

# The materials, and the textures they resolve to.  A ``#name`` in the MTL is looked up in the block
# model's own textures map, the way a vanilla model declares one - so the paths live in one place.
MATERIALS = {name: '#' + name for name in
             ('core', 'cleat', 'plug_plus', 'plug_minus', 'jbox', 'jbox_side', 'trench')}
TEXTURES = {
    'core': 'electricity:block/dc_core',
    'cleat': 'electricity:block/dc_cleat',
    'plug_plus': 'electricity:block/dc_connector_plus',
    'plug_minus': 'electricity:block/dc_connector_minus',
    'jbox': 'electricity:block/dc_jbox',
    'jbox_side': 'electricity:block/dc_jbox_side',
    'trench': 'electricity:block/dc_trench',
}

# ---------------------------------------------------------------- the pair, in sixteenths
#
# Authored in sixteenths, like every other figure in this mod's models, and divided down on the way
# out.  The frame the division lands in is the block's *own*, corner at the origin, 0 to 1 on all three
# axes - which is not the frame the rest of this mod's OBJ files use and is not a choice either.
#
# The machines are drawn by the mod's own renderer, which places a model about the centre of the block,
# so those are authored -0.5 to 0.5.  These are drawn by Forge's block-model OBJ loader, and that one
# takes the coordinates a vanilla block model uses: ``automatic_culling`` decides a quad lies on a block
# face by testing its vertices against 0 and 1, and a blockstate's ``y`` is applied through
# ``Transformation.blockCenterToCorner``, which turns about (0.5, 0.5, 0.5).  Authored about the origin
# instead, every piece came out half a block to the north-west, and the rotated states were thrown to a
# different corner each - which is what a player saw as cable lying nowhere near its own outline.
#
# ``check_inside_block`` fails the build if a piece leaves the frame, because the fault is invisible in
# any previewer: it draws correctly and it is the *game* that puts it in the wrong place.

# A 6 mm2 H1Z2Z2-K is 6.9 mm across.  Thin: a core is 1.3 px, so the pair is a pair of cables rather
# than a pair of ducts - which is what the first two attempts at this looked like.
CORE_RADIUS = 0.65
CORE_OFFSET = 1.05
CORES = (8.0 - CORE_OFFSET, 8.0 + CORE_OFFSET)
# The cable rests on the ground, so its axis is its own radius above it.
CORE_Y = CORE_RADIUS

# Where an arm stops and the middle begins.  This one figure decides the bend: a quarter circle tangent
# to both legs has its tangent points at ``8 - CORE_OFFSET - radius``, so setting the hub edge here
# makes both arcs of a bend run *exactly* from one edge of the middle to the other with no straight
# section inside it, and makes the two radii come out concentric.  See ``piece_bend``.
HUB_LO = 5.4
HUB_HI = 16.0 - HUB_LO

# The MC4 plug, from the sheath outwards, as (length, radius).
#
# The real one is 18 mm on a 6.9 mm cable - 2.6 diameters - and two of those cannot sit at the pair's
# own spacing, which is why an installer's two plugs splay apart.  Drawn at 1.5 diameters instead: it
# still reads as a plug, it stays in proportion to the thin cable, and the two clear each other.
#
# Each segment names the band of ``dc_connector_*`` it takes, because that tile is a strip along the
# plug's own length: a nut cannot show the latch window and a barrel cannot show the knurl.
# The collar is longer than scale: a real plug's coloured seal is a couple of millimetres and at this
# size that is a quarter of a pixel, which is the polarity marking invisible.  It is what the fitting is
# *for* to a player, so it gets a ring they can see - and the barrel gives up the length.
PLUG = ((0.80, 0.80, 0.00, 0.12),      # the collar, which carries the polarity
        (1.05, 0.85, 0.12, 0.44),      # the knurled gland nut
        (2.25, 0.95, 0.44, 0.90),      # the barrel, with the latch window
        (0.70, 0.62, 0.90, 1.00))      # the nose
PLUG_START = 8.4
# A plug is fatter than the cable it is moulded onto, so a plug resting on the ground holds its own axis
# higher than the cable's - and the cable rises into it over the last two pixels, which is what a stiff
# 6 mm2 core lying on the ground actually does.  Coaxial at the cable's own height instead, the barrel's
# underside was a third of a pixel below the ground.
PLUG_Y = max(radius for _, radius, _, _ in PLUG)
PLUG_RISE = 2.4

# How many segments a quarter turn is swept in.  Eight is smooth at the tighter of the two radii and
# costs eight rings of thirty-two.
BEND_STEPS = 8
# The elbow where a run turns up a wall, and how far the riser stands off it.
CLIMB_RADIUS = 2.4
WALL_STANDOFF = CORE_RADIUS + 0.5

# Height of the ground surface inside a buried block, and the rim that holds the block boundary shut.
GROUND = 14.0
RIM = 1.0


def out(value):
    """A figure in sixteenths, as the OBJ's own units."""
    return value / 16.0


def at(x, y, z):
    """A point in sixteenths, in the block's own frame: 0 to 1 on every axis, corner at the origin."""
    return (out(x), out(y), out(z))


# ---------------------------------------------------------------- the parts
#
# Every part is drawn *and* measured: ``boxes`` is what the block's outline claims, so the geometry and
# the collision tables cannot drift apart.

class Run:
    """A length of cable swept along a path given in sixteenths.

    Both ends are closed, always.  A cap at a joint is a disc inside the neighbouring cable and costs
    thirty quads; an *uncapped* end is a hole the moment the neighbour does not cover it exactly, and
    open ends at the joints were what made a laid run read as hollow rather than solid.  Cheaper to close
    every one than to reason about which ones are covered.
    """

    def __init__(self, path, radius=CORE_RADIUS, material='core'):
        self.path = _dedupe([tuple(float(c) for c in p) for p in path])
        self.radius, self.material = radius, material

    def draw(self, mesh):
        faces = mesh.faces('cable', self.material)
        # The texture wraps exactly once round the tube and exactly once along it.  Not a choice: a
        # block model's texture is a sprite in the block atlas, so a uv past one samples the sprite next
        # door rather than repeating - which is what put white bands across every cable.  Nothing on the
        # core's tile has a shape along its length, so stretching it there costs nothing.
        tube(mesh, faces, [at(*p) for p in self.path], out(self.radius), sides=FITTING,
             uv_scale=1.0, uv_along=1.0, caps=faces)

    def length(self):
        return sum(math.dist(self.path[i], self.path[i + 1]) for i in range(len(self.path) - 1))

    def boxes(self):
        """One box a segment, so a curve's outline follows the curve instead of boxing the whole arc.

        The padding is *perpendicular* to each segment and not around it: a tube running along one axis
        ends flat where its path ends, so padding that axis too would claim half a diameter of air past
        the end - which is what made an arm read as standing inside the junction box it feeds.  A segment
        that runs diagonally, which is every chord of an arc, is padded on all three, since its cap is
        not square to any of them.
        """
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
        return Run([_spin(p, quarter) for p in self.path], self.radius, self.material)


class Barrel:
    """A round fitting: a plug's gland nut or barrel, or a gland through a junction box's wall."""

    def __init__(self, group, material, centre, axis, radius, low, high, cap=True, sleeve=False,
                 band=None):
        self.group, self.material, self.centre, self.axis = group, material, centre, axis
        self.radius, self.low, self.high, self.cap = radius, low, high, cap
        # Which window of its texture this segment takes, for a fitting whose tile is a strip along its
        # own length rather than something that tiles.
        self.band = band
        # A sleeve is a fitting a cable runs *through* - a gland in a junction box's wall - so it is the
        # one thing in this model allowed to contain cable.  The check below proves it contains it rather
        # than clipping it.
        self.sleeve = sleeve

    def draw(self, mesh):
        faces = mesh.faces(self.group, self.material)
        base = list(self.centre)
        base['xyz'.index(self.axis)] = self.low
        cylinder(mesh, faces, at(*base), self.axis, out(self.radius), out(self.high - self.low),
                 sides=FITTING, uv_scale=1.0, uv=self.band,
                 caps=faces if self.cap else None, cap_ends=(1,) if self.cap else ())

    def boxes(self):
        index = 'xyz'.index(self.axis)
        lo, hi = [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]
        for i in range(3):
            if i == index:
                lo[i], hi[i] = self.low, self.high
            else:
                lo[i], hi[i] = self.centre[i] - self.radius, self.centre[i] + self.radius
        return [(tuple(lo), tuple(hi))]

    def turned(self, quarter):
        return _Boxes([_spin_box(b, quarter) for b in self.boxes()], sleeve=self.sleeve)


class Slab:
    """A box of something that is not cable: a cleat's strap and feet, a junction box's shell.

    ``material`` may be a mapping from face name to material, with '*' for the rest - which is how the
    junction box gets its lid's four screws on the lid and not on its four walls as well.
    """

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
    """A point turned about the block's centre, the way a blockstate's y turns it."""
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

def plug(centre, base, positive):
    """An MC4 plug on the end of a core: the gland nut, the barrel, the nose.

    Two pieces that latch, in glass-filled polyamide, sealed onto the sheath by a knurled nut.  The
    collar says which pole it is - red for positive, black for negative - because the cable itself is
    black for its whole length and this is where a plant marks it.
    """
    material = 'plug_plus' if positive else 'plug_minus'
    parts, cursor = [], PLUG_START
    for index, (length, radius, v0, v1) in enumerate(PLUG):
        parts.append(Barrel('plug', material, (centre, base + PLUG_Y, 0.0), 'z', radius,
                            cursor, cursor + length, cap=index == len(PLUG) - 1,
                            band=(0.0, v0, 1.0, v1)))
        cursor += length
    return parts


def tail(centre, base, start):
    """The length of core between a piece's own boundary and a plug, rising into it.

    Three points rather than two: the run keeps the pair's own height until it is clear of the boundary,
    then lifts to the plug's axis.  Which has to happen inside the middle and not at its edge, because
    the edge is where the arm from the next block along meets it and that one is at the pair's height.
    """
    y = base + CORE_Y
    return Run([(centre, y, start), (centre, y, PLUG_START - PLUG_RISE),
                (centre, base + PLUG_Y, PLUG_START - PLUG_RISE + 0.9),
                (centre, base + PLUG_Y, PLUG_START)])


def cleat(base, low, high):
    """A stainless clip: a strap over the pair into a foot each side, one screw through it.

    A run is cleated about once a metre, and the clip is the only thing on a hundred metres of pair that
    is not pair - so it is what gives a straight run a rhythm.  The feet fill the gap between the pair
    and the edge of the middle exactly: any wider and they stand inside the next arm along, which is a
    fault ``check_disjoint`` catches.
    """
    inner = CORE_OFFSET + CORE_RADIUS
    top = base + CORE_Y + CORE_RADIUS
    return [
        # the two feet, filling the gap between the pair and the edge of the middle exactly: any wider
        # and they stand inside the next arm along, any narrower and they cut into the cable
        Slab('cleat', 'cleat', (HUB_LO, base, low), (8.0 - inner, top, high), uv_scale=0.4),
        Slab('cleat', 'cleat', (8.0 + inner, base, low), (HUB_HI, top, high), uv_scale=0.4),
        # the strap, resting *on* the crown rather than sunk into it - it used to start a third of a
        # pixel lower, which is a band cutting through the cable it is meant to hold
        Slab('cleat', 'cleat', (HUB_LO, top, low), (HUB_HI, top + 0.42, high), uv_scale=0.6),
        # and the screw through it, which is what a cleat is closed with
        Barrel('cleat', 'cleat', (8.0, 0.0, (low + high) / 2.0), 'y', 0.34, top + 0.42, top + 0.72),
    ]


def junction_box(base, glanded):
    """A small IP68 polycarbonate box, glanded on the walls that have a cable in them.

    A purpose-made piece per pattern rather than one box with four glands and some of them unused: a
    wall with no cable behind it has no gland in the real thing either.
    """
    top = base + CORE_Y + CORE_RADIUS + 1.6
    parts = [Slab('jbox', {'up': 'jbox', '*': 'jbox_side'},
                  (HUB_LO, base, HUB_LO), (HUB_HI, top, HUB_HI))]
    for side in glanded:
        axis = 'z' if side in ('north', 'south') else 'x'
        near = side in ('north', 'west')
        low, high = (HUB_LO - 0.8, HUB_LO) if near else (HUB_HI, HUB_HI + 0.8)
        for centre in CORES:
            middle = [8.0, base + CORE_Y, 8.0]
            middle[0 if axis == 'z' else 2] = centre
            parts.append(Barrel('gland', 'plug_minus', tuple(middle), axis, 0.90, low, high,
                                cap=False, sleeve=True))
    return parts


# ---------------------------------------------------------------- the sixteen middles

def piece_line(base):
    """Two opposite sides: the pair runs straight through, held down by a cleat."""
    y = base + CORE_Y
    return [Run([(c, y, HUB_LO), (c, y, HUB_HI)]) for c in CORES] + cleat(base, 7.1, 8.9)


def piece_bend(base):
    """Two adjacent sides: the pair turns on a radius, swept rather than broken at a corner.

    The turn is north to east.  A quarter circle tangent to both legs of one core has its centre one
    radius outside each of them, so the inside core's arc has radius ``8 + CORE_OFFSET - HUB_LO`` and
    the outside core's ``8 - CORE_OFFSET - ... ``- and those two work out to the **same centre**, which
    means the pair goes round the bend concentric, a fixed distance apart the whole way, exactly as a
    cleated pair does.  Both arcs also begin and end precisely on the middle's own boundary, so there is
    no straight section inside it and nothing to line up by hand.

    The radius is symbolic and says so: a real cable bends no tighter than four diameters, which at a
    block to ten metres is four hundredths of a pixel and invisible.  What matters is that it reads as
    bent and not as broken.

    No cleat: both axes of the middle are full of turning cable and there is nowhere for a foot to
    stand - which is true of the real thing too, where a bend is supported before it and after it.
    """
    y = base + CORE_Y
    # A core comes in on one lane and leaves on the other, and its arc is tangent to both - so its
    # radius is set by the lane it *leaves* on: tangent to z = other means the centre sits one radius
    # north of it, and the inbound tangent point lands on the middle's edge when radius = other - HUB_LO.
    # Which puts both centres at the same place, (HUB_HI, HUB_LO), so the pair is concentric.
    centre = (HUB_HI, y, HUB_LO)
    parts = []
    for lane, other in ((CORES[1], CORES[0]), (CORES[0], CORES[1])):
        radius = other - HUB_LO
        turn = arc(centre, radius, (0, 2), 180.0, 90.0, BEND_STEPS)
        parts.append(Run([(lane, y, HUB_LO)] + [(p[0], y, p[2]) for p in turn]
                         + [(HUB_HI, y, other)]))
    return parts


def piece_end(base):
    """One side connected: the pair comes in and ends in two MC4 plugs."""
    parts = []
    for index, c in enumerate(CORES):
        parts.append(tail(c, base, HUB_LO))
        parts += plug(c, base, index == 0)
    return parts


def piece_loose(base):
    """Nothing connected: a length of pair lying where it was dropped, with its plugs on."""
    parts = []
    for index, c in enumerate(CORES):
        parts.append(tail(c, base, 3.2))
        parts += plug(c, base, index == 0)
    return parts


def piece_tee(base):
    """Three sides: a junction box, glanded north, east and south."""
    return junction_box(base, ('north', 'east', 'south'))


def piece_cross(base):
    """Four sides: the same box with the fourth wall glanded too."""
    return junction_box(base, SIDES)


def piece_arm(base, near):
    """The pair from a block edge in to the middle piece.

    It stops exactly on the boundary rather than overrunning it: two tubes that overlap would put two
    cylinder surfaces at the same depth, and two that stop on the same plane with no caps carry the run
    across the seam with nothing drawn there at all.
    """
    y = base + CORE_Y
    return [Run([(c, y, near), (c, y, HUB_LO)]) for c in CORES]


def piece_climb(base):
    """The pair going up the wall alongside, to reach a run on top of it.

    One swept tube a core, from the middle of the block, round the elbow, and up the wall - the elbow
    included in the same sweep, which is what removes the last place two pieces of this model used to
    meet on a plane.  It replaces the arm rather than adding to it, so nothing is drawn twice.
    """
    y = base + CORE_Y
    turn = arc((0.0, y + CLIMB_RADIUS, WALL_STANDOFF + CLIMB_RADIUS), CLIMB_RADIUS, (1, 2),
               180.0, 270.0, BEND_STEPS)
    parts = []
    for c in CORES:
        path = ([(c, y, HUB_LO)] + [(c, p[1], p[2]) for p in turn] + [(c, 16.0, WALL_STANDOFF)])
        parts.append(Run(path))
    return parts


def bedding():
    """The sand a buried cable is laid in, and the rim that keeps the block boundary solid.

    A buried run has taken a block of ground out of the world, so the model has to be a *complete*
    solid: the game culls the soil's faces around it, and any part of the boundary this did not cover
    would be a hole to see through the world with.  Four rim boxes rather than a ring, because two boxes
    overlapping at a corner would put two faces on one plane.
    """
    parts = [Slab('bedding', 'trench', (0.0, 0.0, 0.0), (16.0, GROUND, 16.0))]
    for x0, z0, x1, z1 in ((0.0, 0.0, 16.0, RIM), (0.0, 16.0 - RIM, 16.0, 16.0),
                           (0.0, RIM, RIM, 16.0 - RIM), (16.0 - RIM, RIM, 16.0, 16.0 - RIM)):
        parts.append(Slab('bedding', 'trench', (x0, GROUND, z0), (x1, 16.0, z1), uv_scale=0.5))
    return parts


# Which middle a set of connected sides gets, and how far round it is turned.  Authored with the one
# side at north, the pair running north and south, the bend from north to east, and the tee glanded
# north, east and south.
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
    """One piece as an OBJ, its material library, and the block model that loads it."""
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
        # Not flipped, and the reason is exact rather than a preference.  Forge's loader computes the
        # sprite coordinate as ``getV((flipV ? 1 - v : v) * 16)``, and a sprite's v runs from its *top*,
        # which is also where row zero of a PNG is and where texlib's Canvas puts y = 0.  So v = 0 has to
        # mean the top of the drawing, which is flipV off.  With it on, every band of the plug's strip
        # landed one segment out: the red collar came out on the nose and the knurl on the barrel.
        'flip_v': False,
        # Culling off: the loader would drop the quads that lie on a block boundary, and on a cable
        # those are the ones that carry a run across a seam.  A few extra quads on a piece this small
        # costs nothing.
        'automatic_culling': False,
        # Shading off, and this is what makes a cable read as one solid object.  With it on, Minecraft
        # multiplies each quad by the face direction its normal is nearest to - 1.0 up, 0.8 north and
        # south, 0.6 east and west - and a tube's normals sweep all of them, so a bend came out in bands
        # of three brightnesses across the curve and the unlit half of a black cable went to nothing.
        # dc_core already carries a lit cylinder's own gradient, drawn from this mod's one light
        # direction, so the shading is in the texture where it belongs.
        'shade_quads': False,
        'textures': dict(TEXTURES, particle=TEXTURES['core']),
    })
    return mesh.stats()[1]


def check_inside_sprite(name, mesh):
    """Fails if any uv leaves the tile, which in a block model means leaving the *sprite*.

    Worth failing loudly over, because of how it looks: a block model's texture lives in the block
    atlas, so a uv past one does not tile - it samples whatever sprite the atlas happened to put next
    door.  The first version of this cable ran its v to 1.9 along every tube and came out with white
    bands across the cable and a white collar on the plug, and none of it showed in a previewer, which
    binds one texture at a time and wraps.
    """
    for u, v in mesh.vt:
        if not (-1e-6 <= u <= 1.0 + 1e-6 and -1e-6 <= v <= 1.0 + 1e-6):
            raise SystemExit('%s: uv (%.3f, %.3f) is outside its sprite, so it would sample the '
                             'texture next to it in the atlas' % (name, u, v))


def check_inside_block(name, mesh):
    """Fails if a piece leaves its block, which is the frame Forge's loader reads these in.

    The reason it is worth a check of its own rather than a careful read of ``at``: this fault does not
    show anywhere except in the game.  The OBJ is self-consistent, a previewer draws it correctly, the
    hitbox tables printed from the same figures are correct - and the block appears with its cable half a
    block away from its own outline, because the loader's frame has the block's corner at the origin and
    not its centre.  A whole cable set was drawn twice before that was found by looking rather than by
    measuring.

    A pixel of slack, and it is spent on one thing: a fitting wider than the cable it is on - a plug's
    barrel, a gland's nut - beds a fraction of a pixel into the ground, the way the real one sits in the
    dirt.  The fault this is looking for is eight pixels, so a pixel of tolerance does not hide it.
    """
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

        # A trench has nothing to climb: the pair is already at the surface, so it meets a run on top of
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
    labelled = [(p.turned(turn), '%s[%d]' % (kind, i))
                for i, p in enumerate(MIDDLES[kind](0.0))]
    for side in SIDES:
        if state[side] == 'none':
            continue

        pieces = piece_climb(0.0) if state[side] == 'up' else piece_arm(0.0, 0.0)
        labelled += [(p.turned(QUARTERS[side]), '%s:%s[%d]' % (side, state[side], i))
                     for i, p in enumerate(pieces)]
    return labelled


def check_disjoint():
    """Proves nothing a run draws touches anything else it draws, in any of the 162 states.

    Two cables are compared **along their paths** rather than by their bounding boxes, and that is not a
    convenience: the two cores of a bend are concentric arcs, so their boxes overlap almost completely
    while the tubes stay a fixed distance apart the whole way round.  Boxing them reported nineteen
    clashes that do not exist.  Everything that is not a cable is a box and is compared as one.
    """
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
                        # every axis but the one it overlaps along has to be inside the bore, which is
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
    """How much room the two cores of a bend and of a climb keep, for the log to say so."""
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


def java():
    print('\n\t// ---- printed by tools/gen_cable_models.py --java ----\n')
    print('\tprivate static final Map<Integer, VoxelShape> STRING_HUBS = Map.ofEntries(')
    rows = []
    for connected, (kind, turn) in sorted(HUBS.items(), key=lambda kv: mask(kv[0])):
        boxes = [b for p in MIDDLES[kind](0.0) for b in p.turned(turn).boxes()]
        rows.append('\t\t\tMap.entry(0b%s, %s)' % (format(mask(connected), '04b'), shape(boxes)))
    print(',\n'.join(rows) + ');')

    for label, builder in (('STRING_ARMS', lambda: piece_arm(0.0, 0.0)),
                           ('STRING_CLIMBS', lambda: piece_climb(0.0))):
        print('\n\tprivate static final Map<Direction, VoxelShape> %s = Map.of(' % label)
        rows = []
        for side, turn in (('NORTH', 0), ('EAST', 90), ('SOUTH', 180), ('WEST', 270)):
            boxes = [b for p in builder() for b in p.turned(turn).boxes()]
            rows.append('\t\t\tDirection.%s, %s' % (side, shape(boxes)))
        print(',\n'.join(rows) + ');')


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

    if '--java' in sys.argv:
        java()


if __name__ == '__main__':
    main()
