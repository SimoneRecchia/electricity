#!/usr/bin/env python3
"""Generates the blockstates and block models for the direct-current cables.

    python3 tools/gen_cable_models.py            # writes the assets
    python3 tools/gen_cable_models.py --java     # and prints the tables DcCableBlock declares

Writes into src/main/resources/assets/electricity/{blockstates,models/block,models/item}/.

Vanilla JSON models rather than the OBJ pipeline the machines use, and deliberately: a cable is
laid by the hundred, it wants ambient occlusion and chunk batching, and its shape is decided by a
block state rather than by anything animated.  Which is also what redstone dust is, so this
generates the same kind of multipart definition dust has.

The string cable, and why it is built the way it is
---------------------------------------------------
A photovoltaic string is wired with two *single-core* cables, one per pole: H1Z2Z2-K to EN 50618, a
flexible tinned-copper conductor under two layers of cross-linked polyolefin, 1500 V d.c., 6.9 mm
across at 6 mm2.  They are clipped in pairs along the racking about once a metre, bent on a radius of
four diameters at worst, and terminated in MC4 connectors.  So that is what is drawn: two round cores
side by side, a stainless cleat where they are held, a bend that turns instead of crossing, and a
plug on any end that goes nowhere.

**A piece per connection pattern.**  The version this replaces was a hub plus an arm per side, and
the arms were one model turned by quarters - which is how dust is built and it is wrong for a pair.
Turn a north arm to make an east arm and the core that was on the west is now on the north, so at a
corner the two cores of the pair meet the *wrong* ones and the run visibly crosses itself.  Nor can
it be fixed by choosing the colours more carefully: joining the cores through every straight and
every bend identifies all four of them, so no two-colour assignment exists at all.  (Which is also
why both cores here are black, as the real cable is - see gen_block_textures.py.)

So the middle of the block is chosen by the *set* of sides that connect, all sixteen of them, and
each one is an exact ``when``: a side is either ``none`` or ``side|up``, so the sixteen conditions
partition the states and exactly one middle piece is ever drawn.  No two pieces of this model ever
share any volume, which is checked below rather than asserted.

And the sixteen are what the real thing would be, which is the part worth having:

  * two opposite sides - the pair runs through, with a cleat over it
  * two adjacent - the pair *turns*, the inside core staying inside, cleated on the way in
  * one side - the pair ends in two MC4 connectors, red collar for the positive pole
  * none - a length of pair lying there with its plugs on
  * three or four - a junction box, because a third leg cannot be a bare crossing: something has to
    join it, and what joins direct-current strings is a box with glands in it

**Roundness.**  This format has no rotation, so a cable is a stack of square-edged boxes, and the
game shades a face by which way it points - so every upward face is the same brightness and a stepped
box is flat from above however many steps it has.  The roundness is therefore in the texture and runs
*across* the cable, and every face's uv here maps its own across-the-cable extent onto the full width
of that tile: a face taking the crown gets the bright part, a flank gets the edge.  ``dc_core`` has
the gradient across it for the runs along z and ``dc_core_turn`` down it for the runs along x, since a
face's u is its own first axis and that axis changes with the run.

The trench
----------
A buried run replaces a block of ground, so it has to be a *complete* solid: the game culls the faces
of the soil around it, and any part of the block boundary the model does not cover would be a hole you
could see through the world with.  So the buried model is a bedding cube up to ``GROUND`` with a rim
above it round all four edges, which covers the boundary from nothing to sixteen with no two faces
ever landing on the same plane.

The trunk cable
---------------
Still the old painted bar, on purpose: it is a different product with a different job - one armoured
home run from a combiner to the cabinet rather than the hundreds of string pairs a plant is stitched
together with - and it is next in line rather than done here.
"""

import itertools
import json
import os
import sys

ASSETS = os.path.join('src', 'main', 'resources', 'assets', 'electricity')
BLOCKSTATES = os.path.join(ASSETS, 'blockstates')
BLOCK_MODELS = os.path.join(ASSETS, 'models', 'block')
ITEM_MODELS = os.path.join(ASSETS, 'models', 'item')

# Face textures are *names* looked up in the model's own textures map, not paths - a raw resource
# location in a face comes out as the missing-texture chequerboard, which is exactly what an early
# version of this did to every cable in the world.  So the paths are declared once at the top of each
# model under these names and the faces reference them.
CORE = '#core'                 # the gradient across the tile: cores running north and south
CORE_TURN = '#core_turn'       # the same gradient down the tile: cores running east and west
CLEAT = '#cleat'
PLUS = '#plus'
MINUS = '#minus'
BOX = '#box'
TRENCH = '#trench'

PATHS = {
    CORE: 'electricity:block/dc_core',
    CORE_TURN: 'electricity:block/dc_core_turn',
    CLEAT: 'electricity:block/dc_cleat',
    PLUS: 'electricity:block/dc_connector_plus',
    MINUS: 'electricity:block/dc_connector_minus',
    BOX: 'electricity:block/dc_jbox',
    TRENCH: 'electricity:block/dc_trench',
}

SIDES = ('north', 'east', 'south', 'west')
OPPOSITE = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}

# ---------------------------------------------------------------- the pair, in sixteenths

# One core's cross-section, from the ground up: (low, high, half-width).  Three steps rather than one
# box, because the shoulder either side of a narrower crown is what the eye reads as round even before
# the texture's gradient is on it.
PROFILE = ((0.00, 0.62, 0.62), (0.62, 1.44, 1.00), (1.44, 2.00, 0.62))
CORE_HALF = max(step[2] for step in PROFILE)
CORE_TOP = PROFILE[-1][1]
# Each core's centre, either side of the block's centreline: two cores 2 px across, a fifth of a pixel
# apart - a pair cleated together, which is how a string's two cables are run.
CORE_OFFSET = 1.1
CORES = (8.0 - CORE_OFFSET, 8.0 + CORE_OFFSET)
# Where the arms stop and the middle piece begins, and how far a climb stands off its wall.
HUB_LO = 8.0 - CORE_OFFSET - CORE_HALF - 0.4
HUB_HI = 16.0 - HUB_LO
WALL = 2.0

# The MC4 connector: a gland nut onto the sheath, a barrel, and a nose, as (length, half-width).
#
# A real plug is 18 mm across against the cable's 6.9, and two of them cannot sit at the pair's own
# spacing - which is why an installer's two plugs splay apart.  Here they keep the pair's spacing and
# are told from the cable by standing *taller* instead: half again the cable's height, with the knurl
# and the latch window on their sides.  Splaying them would want a jog piece per core and four more
# boxes on a piece that is only ever seen at the end of a run.
CONNECTOR = ((1.30, 1.05), (2.90, 1.05), (0.80, 0.80))
CONNECTOR_TOP = 3.10

# Height of the ground surface inside a buried block, and the rim that holds the boundary shut.  Low
# enough that the pair fits between it and the top of the block.
GROUND = 16.0 - CORE_TOP
RIM = 1.0


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


# ---------------------------------------------------------------- the parts of a run
#
# A part knows how to become both the boxes the model draws and the one box the block's outline claims,
# so the two cannot drift: DcCableBlock's tables are printed from this same list.

class Part:
    """One box of something that is not cable: a cleat's strap, a connector's barrel, a junction box."""

    def __init__(self, lo, hi, texture, faces=None, cull=None):
        self.lo, self.hi, self.texture = tuple(lo), tuple(hi), texture
        self.faces = faces          # which faces to draw, or None for all but the underside
        self.cull = cull

    def bounds(self):
        return self.lo, self.hi

    def elements(self):
        return [element(self.lo, self.hi, plain_faces(self.lo, self.hi, self.texture,
                                                     self.faces, self.cull))]


class Core:
    """A length of one core, running along an axis with its round profile standing on the ground.

    ``across`` is the horizontal axis the core is offset on - 'x' for a run north and south, 'z' for
    one east and west - and ``centre`` is where on that axis it sits.  ``cut`` names an end whose cap
    should be drawn, which is only ever an end that stops in the open air: the caps at a block boundary
    are always inside the neighbour's own cable and drawing them is a quad nobody can see.
    """

    def __init__(self, across, centre, low, high, base=0.0, cut=()):
        self.across, self.centre, self.low, self.high, self.base = across, centre, low, high, base
        self.cut = cut

    def bounds(self):
        lo = [0.0, self.base, 0.0]
        hi = [0.0, self.base + CORE_TOP, 0.0]
        a = 0 if self.across == 'x' else 2
        b = 2 - a
        lo[a], hi[a] = self.centre - CORE_HALF, self.centre + CORE_HALF
        lo[b], hi[b] = self.low, self.high
        return tuple(lo), tuple(hi)

    def elements(self):
        out = []
        a = 0 if self.across == 'x' else 2
        b = 2 - a
        for low, high, half in PROFILE:
            lo, hi = [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]
            lo[1], hi[1] = self.base + low, self.base + high
            lo[a], hi[a] = self.centre - half, self.centre + half
            lo[b], hi[b] = self.low, self.high
            top = high == PROFILE[-1][1]
            out.append(element(lo, hi, core_faces(tuple(lo), tuple(hi), self.across, self.centre,
                                                 top=top, cut=self.cut)))
        return out


class Riser:
    """A length of one core going up a wall, so its round profile stands out from the wall instead.

    The same three steps, measured away from the face it is clipped to rather than off the ground -
    which is what makes a climb read as a cable against a wall and not a strip painted on it.
    """

    def __init__(self, centre, low, high):
        self.centre, self.low, self.high = centre, low, high

    def bounds(self):
        return ((self.centre - CORE_HALF, self.low, 0.0),
                (self.centre + CORE_HALF, self.high, CORE_TOP))

    def elements(self):
        out = []
        for low, high, half in PROFILE:
            lo = (self.centre - half, self.low, low)
            hi = (self.centre + half, self.high, high)
            out.append(element(lo, hi, riser_faces(lo, hi, self.centre,
                                                  outward=high == PROFILE[-1][1])))
        return out


def element(lo, hi, faces):
    return {'from': [round(v, 3) for v in lo], 'to': [round(v, 3) for v in hi], 'faces': faces}


def face(texture, uv, cull=None):
    entry = {'uv': [round(v, 3) for v in uv], 'texture': texture}
    if cull is not None:
        entry['cullface'] = cull
    return entry


def gradient(value, centre):
    """Where a point across a core falls on its tile: the full width of the texture, edge to edge."""
    return (value - (centre - CORE_HALF)) / (2.0 * CORE_HALF) * 16.0


def core_faces(lo, hi, across, centre, top, cut):
    """The faces of one step of a core, each mapped so the round shading lands the right way round.

    Three cases, and which one a face is depends on the run's direction, because the format fixes
    which world axis a face's u is:

      * the crown - the upward face - wants the gradient across the cable, so it takes ``dc_core``
        when u is the across axis and ``dc_core_turn`` when v is
      * a flank wants one value, the edge of the gradient, so it pins that axis to a sliver
      * a cut end wants the whole gradient, which is a cable seen down its length
    """
    a, b = ('x', 'z') if across == 'x' else ('z', 'x')
    ai, bi = (0, 2) if across == 'x' else (2, 0)
    g0, g1 = gradient(lo[ai], centre), gradient(hi[ai], centre)
    faces = {}

    # the crown, and the shoulders either side of it: the same mapping, each taking the part of the
    # gradient its own position across the cable earns
    if across == 'x':
        faces['up'] = face(CORE, (g0, lo[2], g1, hi[2]))
    else:
        faces['up'] = face(CORE_TURN, (lo[0], g0, hi[0], g1))

    # the two flanks, pinned to the gradient's edges
    flanks = ('west', 'east') if across == 'x' else ('north', 'south')
    for side, value in zip(flanks, (g0, g1)):
        edge = min(max(value, 0.0), 15.2)
        faces[side] = face(CORE, (edge, lo[1], edge + 0.8, hi[1]))

    # and the ends, drawn only where the cable really stops
    ends = ('north', 'south') if across == 'x' else ('west', 'east')
    for side, value in zip(ends, (lo[bi], hi[bi])):
        if side not in cut:
            continue
        faces[side] = face(CORE, (g0, 16.0 - hi[1], g1, 16.0 - lo[1]))

    return faces


def riser_faces(lo, hi, centre, outward):
    """The faces of one step of a climb: the run is up, so the gradient goes across it in x."""
    g0, g1 = gradient(lo[0], centre), gradient(hi[0], centre)
    faces = {}
    if outward:
        faces['south'] = face(CORE, (g0, 16.0 - hi[1], g1, 16.0 - lo[1]))
    else:
        faces['south'] = face(CORE, (g0, 16.0 - hi[1], g1, 16.0 - lo[1]))
    for side, value in (('west', g0), ('east', g1)):
        edge = min(max(value, 0.0), 15.2)
        faces[side] = face(CORE, (edge, 16.0 - hi[1], edge + 0.8, 16.0 - lo[1]))
    return faces


def plain_faces(lo, hi, texture, wanted=None, cull=None):
    """A box mapped straight off its own coordinates, so nothing on it is stretched.

    Mapping by position rather than stretching the tile onto every face is what keeps a knurl the same
    pitch on a connector's barrel as on its nut, and what keeps the screw in a cleat round.
    """
    uv = {
        'down': (lo[0], 16.0 - hi[2], hi[0], 16.0 - lo[2]),
        'up': (lo[0], lo[2], hi[0], hi[2]),
        'north': (16.0 - hi[0], 16.0 - hi[1], 16.0 - lo[0], 16.0 - lo[1]),
        'south': (lo[0], 16.0 - hi[1], hi[0], 16.0 - lo[1]),
        'west': (lo[2], 16.0 - hi[1], hi[2], 16.0 - lo[1]),
        'east': (16.0 - hi[2], 16.0 - hi[1], 16.0 - lo[2], 16.0 - lo[1]),
    }
    sides = wanted if wanted is not None else [s for s in uv if s != 'down']
    return {s: face(texture, uv[s], cull if cull == s else None) for s in sides}


# ---------------------------------------------------------------- the middle, one per pattern

def hub_loose(base):
    """Nothing connected: a length of pair lying where it was dropped, with its plugs on."""
    parts = []
    for i, centre in enumerate(CORES):
        parts.append(Core('x', centre, 2.5, 8.0, base, cut=('north',)))
        parts += connector('x', centre, 8.0, +1, base, PLUS if i == 0 else MINUS)
    return parts


def hub_end(base):
    """One side connected: the pair comes in and ends in two MC4 connectors.

    Which is what the end of a string cable is - there is no bare end on a plant, and the red collar
    on one of the two is how a cable that is black for its whole length says which pole it is.
    """
    parts = []
    for i, centre in enumerate(CORES):
        parts.append(Core('x', centre, HUB_LO, 8.0, base))
        parts += connector('x', centre, 8.0, +1, base, PLUS if i == 0 else MINUS)
    return parts


def hub_line(base):
    """Two opposite sides: the pair runs through, held down by a cleat."""
    parts = [Core('x', centre, HUB_LO, HUB_HI, base) for centre in CORES]
    return parts + cleat(base, 7.2, 8.8)


def hub_bend(base):
    """Two adjacent sides: the pair turns, and the core on the inside of the turn stays inside.

    Which is the whole reason the middle of this block is chosen by pattern.  The inside core reaches
    the corner and leaves; the outside one runs past it and crosses behind - and the two nest without
    ever sharing a pixel, so a bend needs nothing hiding it.
    """
    inner, outer = CORES[1], CORES[0]        # the turn is north to east, so the inside is the east core
    parts = [
        # the inside of the turn: down the north leg as far as the corner, then out to the east on the
        # near line - the shorter way round, which is what being on the inside means
        Core('x', inner, HUB_LO, outer + CORE_HALF, base),
        Core('z', outer, inner + CORE_HALF, HUB_HI, base),
        # and the outside, which runs past the corner and turns behind it
        Core('x', outer, HUB_LO, inner + CORE_HALF, base),
        Core('z', inner, outer + CORE_HALF, HUB_HI, base),
    ]
    # And no cleat, which is not an omission.  Both axes of the middle are full of turning cable, so
    # there is nowhere for a foot to stand - the check below refused every position tried.  Which is
    # also true of the real thing: a bend is supported before it and after it, never at it, because
    # there is no room at it either.
    return parts


def hub_box(base):
    """Three sides or four: a junction box, because a third leg has to be joined to something.

    Two runs can pass each other and two can turn, but a third cannot be a bare crossing - and what
    joins direct-current strings is a small IP68 box with glands in it.  It fills the middle exactly,
    so the arms' cores run into its walls the way cables run into the real one.
    """
    return [Part((HUB_LO, base, HUB_LO), (HUB_HI, base + 3.6, HUB_HI), BOX,
                 faces=['up', 'north', 'south', 'east', 'west'])]


def connector(across, centre, start, direction, base, texture):
    """An MC4 plug on the end of a core: the gland nut, the barrel and the nose."""
    parts, at = [], start
    for length, half in CONNECTOR:
        a, b = at, at + length * direction
        lo = [0.0, base, 0.0]
        hi = [0.0, base + CONNECTOR_TOP, 0.0]
        ai, bi = (0, 2) if across == 'x' else (2, 0)
        lo[ai], hi[ai] = centre - half, centre + half
        lo[bi], hi[bi] = min(a, b), max(a, b)
        parts.append(Part(lo, hi, texture))
        at = b
    return parts


def cleat(base, low, high):
    """A stainless clip: a strap over the pair into a foot each side, with one screw through it.

    The feet fill the gap between the pair and the edge of the middle exactly.  Half a pixel wider and
    they stand inside the next arm along, which ``check_disjoint`` catches and which would have been two
    surfaces flickering at every corner of every run.
    """
    inner = CORES[1] + CORE_HALF
    return [
        Part((HUB_LO, base, low), (16.0 - inner, base + CORE_TOP, high), CLEAT),
        Part((inner, base, low), (HUB_HI, base + CORE_TOP, high), CLEAT),
        Part((HUB_LO, base + CORE_TOP, low), (HUB_HI, base + CORE_TOP + 0.7, high), CLEAT),
    ]


# Which middle piece a set of connected sides gets, and how far round it is turned.  Authored with the
# one side at north, the pair running north and south, the bend from north to east and the box's
# through legs north and south - then turned by quarters, which is what the blockstate's y does.
HUBS = {
    (): ('loose', 0),
    ('north',): ('end', 0), ('east',): ('end', 90),
    ('south',): ('end', 180), ('west',): ('end', 270),
    ('north', 'south'): ('line', 0), ('east', 'west'): ('line', 90),
    ('north', 'east'): ('bend', 0), ('east', 'south'): ('bend', 90),
    ('south', 'west'): ('bend', 180), ('north', 'west'): ('bend', 270),
    # three legs and four get the same box: it is square and symmetric, and one box with cables into
    # three of its walls is exactly what one with cables into four of them is
    ('north', 'east', 'south'): ('box', 0), ('east', 'south', 'west'): ('box', 0),
    ('north', 'south', 'west'): ('box', 0), ('north', 'east', 'west'): ('box', 0),
    ('north', 'east', 'south', 'west'): ('box', 0),
}

BUILDERS = {
    'loose': hub_loose,
    'end': hub_end,
    'line': hub_line,
    'bend': hub_bend,
    'box': hub_box,
}


def arm(base, near):
    """The pair from a block edge in to the middle piece."""
    return [Core('x', centre, near, HUB_LO, base) for centre in CORES]


def climb(base):
    """The pair going up the wall alongside, to reach a run on top of it.

    The elbow at the bottom is one full-section box a side rather than a mitred profile: a cable really
    is at its fattest where it is bent hardest, and there is no way to mitre anything in this format.
    """
    parts = [Part((centre - CORE_HALF, base, 0.0), (centre + CORE_HALF, base + CORE_TOP, WALL),
                  CORE, faces=['south', 'west', 'east'])
             for centre in CORES]
    return parts + [Riser(centre, base + CORE_TOP, 16.0) for centre in CORES]


# ---------------------------------------------------------------- the trench

def bedding():
    """The sand a buried cable is laid in, and the rim that keeps the block boundary solid.

    Four rim boxes rather than a ring, because two boxes that overlap at a corner would put two
    faces on the same plane and they would flicker against each other along every trench in the
    world.  They are cut so no two of them share any volume.
    """
    box = element((0.0, 0.0, 0.0), (16.0, GROUND, 16.0), {
        'down': face(TRENCH, (0, 0, 16, 16), cull='down'),
        'up': face(TRENCH, (0, 0, 16, 16)),
        'north': face(TRENCH, (0, 16 - GROUND, 16, 16), cull='north'),
        'south': face(TRENCH, (0, 16 - GROUND, 16, 16), cull='south'),
        'west': face(TRENCH, (0, 16 - GROUND, 16, 16), cull='west'),
        'east': face(TRENCH, (0, 16 - GROUND, 16, 16), cull='east'),
    })

    rims = []
    spans = (
        ((0.0, 0.0), (16.0, RIM), 'north'),
        ((0.0, 16.0 - RIM), (16.0, 16.0), 'south'),
        ((0.0, RIM), (RIM, 16.0 - RIM), 'west'),
        ((16.0 - RIM, RIM), (16.0, 16.0 - RIM), 'east'),
    )
    for (x0, z0), (x1, z1), outward in spans:
        faces = {
            'up': face(TRENCH, (x0, z0, x1, z1)),
            'down': face(TRENCH, (x0, z0, x1, z1)),
            'north': face(TRENCH, (x0, 0, x1, RIM), cull='north' if outward == 'north' else None),
            'south': face(TRENCH, (x0, 0, x1, RIM), cull='south' if outward == 'south' else None),
            'west': face(TRENCH, (z0, 0, z1, RIM), cull='west' if outward == 'west' else None),
            'east': face(TRENCH, (z0, 0, z1, RIM), cull='east' if outward == 'east' else None),
        }
        rims.append(element((x0, GROUND, z0), (x1, 16.0, z1), faces))

    return [box] + rims


# ---------------------------------------------------------------- the models and the blockstate

def model_of(parts, extra=(), ao=False):
    used = set()
    elements = list(extra)
    for part in parts:
        elements += part.elements()
    for e in elements:
        for entry in e['faces'].values():
            used.add(entry['texture'])

    model = {'parent': 'block/block',
             'textures': dict({'particle': CORE}, **{k[1:]: PATHS[k] for k in sorted(used | {CORE})}),
             'elements': elements}
    if not ao:
        model['ambientocclusion'] = False
    return model


def string_models(name):
    """Every model the string cable needs: a middle per pattern, an arm, a climb, and the buried set."""
    out = {}
    for buried, base, prefix in ((False, 0.0, ''), (True, GROUND, '_trench')):
        extra = bedding() if buried else ()
        for kind, builder in BUILDERS.items():
            out['%s%s_%s' % (name, prefix, kind)] = model_of(builder(base), extra, ao=buried)

        near = RIM if buried else 0.0
        out['%s%s_arm' % (name, prefix)] = model_of(arm(base, near))
        if not buried:
            out['%s_arm_up' % name] = model_of(arm(base, WALL))
            out['%s_climb' % name] = model_of(climb(base))

    return out


def string_blockstate(name):
    """Multipart, and the sixteen middles are an exact partition of the states.

    Each middle's ``when`` names all four sides - ``none`` for the ones it does not have and
    ``side|up`` for the ones it does - so for any state exactly one of the sixteen matches. That is
    what makes it safe to draw a bend as a bend: nothing else is drawn in the same place.
    """
    model = 'electricity:block/' + name
    parts = []
    turns = {'north': 0, 'east': 90, 'south': 180, 'west': 270}

    for buried, prefix in ((False, ''), (True, '_trench')):
        if buried:
            parts.append({'when': {'buried': 'true'},
                          'apply': {'model': model + '_trench_bed'}})

        for connected, (kind, turn) in sorted(HUBS.items()):
            when = {'buried': 'true' if buried else 'false'}
            for side in SIDES:
                when[side] = 'side|up' if side in connected else 'none'
            apply = {'model': '%s%s_%s' % (model, prefix, kind)}
            if turn:
                apply['y'] = turn
            parts.append({'when': when, 'apply': apply})

        for side, turn in turns.items():
            values = 'side|up' if buried else 'side'
            apply = {'model': '%s%s_arm' % (model, prefix)}
            if turn:
                apply['y'] = turn
            parts.append({'when': {'buried': 'true' if buried else 'false', side: values},
                          'apply': dict(apply)})
            # a trench has nothing to climb: the pair is already at the surface, so it meets a run on
            # top of the next block along without going anywhere
            if buried:
                continue
            for suffix in ('_arm_up', '_climb'):
                apply = {'model': model + suffix}
                if turn:
                    apply['y'] = turn
                parts.append({'when': {'buried': 'false', side: 'up'}, 'apply': apply})

    return {'multipart': parts}


# ---------------------------------------------------------------- the trunk, unchanged for now

TRUNK = dict(half=1.0, thick=1.0, texture='dc_trunk_line')
TRUNK_CABLE = '#cable'
TRUNK_GROUND = 15.0


def trunk_faces(texture, lo, hi, cull=None):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    faces = {'up': face(texture, (x0, z0, x1, z1))}
    for name, uv in (('north', (x0, 16 - y1, x1, 16 - y0)), ('south', (x0, 16 - y1, x1, 16 - y0)),
                     ('west', (z0, 16 - y1, z1, 16 - y0)), ('east', (z0, 16 - y1, z1, 16 - y0))):
        faces[name] = face(texture, uv, cull=name if name == cull else None)
    return faces


def trunk_models(name):
    half, thick = TRUNK['half'], TRUNK['thick']
    path = 'electricity:block/' + TRUNK['texture']
    flat = {'parent': 'block/block', 'ambientocclusion': False,
            'textures': {'particle': TRUNK_CABLE, 'cable': path}}
    trench = {'parent': 'block/block',
              'textures': {'particle': TRENCH, 'cable': path, 'trench': PATHS[TRENCH]}}

    def dot(y0, y1):
        lo, hi = (8 - half, y0, 8 - half), (8 + half, y1, 8 + half)
        return element(lo, hi, trunk_faces(TRUNK_CABLE, lo, hi))

    def bar(y0, y1, near):
        lo, hi = (8 - half, y0, near), (8 + half, y1, 8 - half)
        return element(lo, hi, trunk_faces(TRUNK_CABLE, lo, hi, cull='north' if near == 0.0 else None))

    def wall():
        lo, hi = (8 - half, 0.0, 0.0), (8 + half, 16.0, thick)
        return element(lo, hi, {
            'north': face(TRUNK_CABLE, (8 - half, 0, 8 + half, 16), cull='north'),
            'south': face(TRUNK_CABLE, (8 - half, 0, 8 + half, 16)),
            'west': face(TRUNK_CABLE, (0, 0, thick, 16)),
            'east': face(TRUNK_CABLE, (0, 0, thick, 16)),
        })

    old_ground = TRUNK_GROUND
    global GROUND
    kept, GROUND = GROUND, old_ground
    try:
        bed = bedding()
    finally:
        GROUND = kept

    return {
        name: dict(flat, elements=[dot(0.0, thick)]),
        name + '_arm': dict(flat, elements=[bar(0.0, thick, 0.0)]),
        name + '_climb': dict(flat, elements=[wall()]),
        name + '_trench': dict(trench, elements=bed + [dot(old_ground, 16.0)]),
        name + '_trench_arm': dict(trench, elements=[bar(old_ground, 16.0, RIM)]),
    }


def trunk_blockstate(name):
    model = 'electricity:block/' + name
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


# ---------------------------------------------------------------- checks

def check_textures(name, model):
    """Fails if any face names a texture the model does not declare.

    Worth a function of its own because of how this fails in the game: a block model's face carries the
    *name* of a texture, which is looked up in the model's own textures map and then up through its
    parents.  A raw resource location put there is looked up as a name, is not found, and silently
    resolves to the missing-texture chequerboard - no warning in the log, nothing on the console, just
    every cable in the world turned magenta.  Which is exactly what shipped once.
    """
    declared = set(model.get('textures', {}))
    for element in model.get('elements', ()):
        for side, entry in element['faces'].items():
            texture = entry['texture']
            if not texture.startswith('#'):
                raise SystemExit('%s: %s face names "%s", which is a path where a #name belongs'
                                 % (name, side, texture))
            if texture[1:] not in declared:
                raise SystemExit('%s: %s face wants #%s, which the model does not declare'
                                 % (name, side, texture[1:]))


def check_disjoint():
    """Proves no two boxes of the string cable ever share a pixel, in any of the 162 states.

    Every part of every piece that state draws, compared with every other - which matters because two
    of the faults this caught were *inside* one piece, where a check that skipped same-piece pairs saw
    nothing: a bend whose inside core turned one line too late and ran through the other one, and two
    plugs drawn at a real connector's width, which is too wide for the pair's spacing.

    The old model would have failed it too: its arm and its climb overlapped by two pixels wherever a
    run went up a wall.  Worth being mechanical about, because the symptom is two surfaces flickering
    against each other in one state out of a hundred and sixty-two.
    """
    problems = []
    for connected, (kind, turn) in HUBS.items():
        for values in itertools.product(*[('side', 'up') if s in connected else ('none',)
                                          for s in SIDES]):
            state = dict(zip(SIDES, values))
            boxes = [(turned(p, turn), '%s[%d]' % (kind, i))
                     for i, p in enumerate(BUILDERS[kind](0.0))]
            for side in SIDES:
                if state[side] == 'none':
                    continue

                quarter = {'north': 0, 'east': 90, 'south': 180, 'west': 270}[side]
                pieces = list(arm(0.0, WALL if state[side] == 'up' else 0.0))
                if state[side] == 'up':
                    pieces += climb(0.0)
                boxes += [(turned(p, quarter), '%s:%s[%d]' % (side, state[side], i))
                          for i, p in enumerate(pieces)]

            for (a, la), (b, lb) in itertools.combinations(boxes, 2):
                share = [min(a[1][i], b[1][i]) - max(a[0][i], b[0][i]) for i in range(3)]
                if all(v > 1e-6 for v in share):
                    problems.append('%s and %s share %.2f x %.2f x %.2f px'
                                    % (la, lb, share[0], share[1], share[2]))

    return sorted(set(problems))


def turned(part, quarter):
    """A part's bounding box turned about the block's centre, the way a blockstate's y turns it."""
    lo, hi = part.bounds()
    corners = [(x - 8.0, z - 8.0) for x in (lo[0], hi[0]) for z in (lo[2], hi[2])]
    for _ in range(quarter // 90):
        corners = [(-b, a) for a, b in corners]
    xs = [c[0] + 8.0 for c in corners]
    zs = [c[1] + 8.0 for c in corners]
    return (min(xs), lo[1], min(zs)), (max(xs), hi[1], max(zs))


def java():
    """The tables DcCableBlock declares, printed from the parts the model is built from.

    A box per part rather than per drawn step: a core's three steps are one cable as far as pointing at
    it goes, and the outline a player sees should be the cable and not its profile.
    """
    print('\n\t// ---- printed by tools/gen_cable_models.py --java ----\n')
    print('\tprivate static final Map<Integer, VoxelShape> HUBS = Map.ofEntries(')
    rows = []
    for connected, (kind, turn) in sorted(HUBS.items(), key=lambda kv: mask(kv[0])):
        boxes = [turned(p, turn) for p in BUILDERS[kind](0.0)]
        rows.append('\t\t\tMap.entry(0b%s, %s)' % (format(mask(connected), '04b'), shape(boxes)))
    print(',\n'.join(rows) + ');')

    for label, pieces in (('ARMS', lambda: arm(0.0, 0.0)),
                          ('ARMS_UP', lambda: arm(0.0, WALL)),
                          ('CLIMBS', climb0)):
        print('\n\tprivate static final Map<Direction, VoxelShape> %s = Map.of(' % label)
        rows = []
        for side, turn in (('NORTH', 0), ('EAST', 90), ('SOUTH', 180), ('WEST', 270)):
            boxes = [turned(p, turn) for p in pieces()]
            rows.append('\t\t\tDirection.%s, %s' % (side, shape(boxes)))
        print(',\n'.join(rows) + ');')


def climb0():
    return climb(0.0)


def mask(connected):
    return sum(1 << SIDES.index(s) for s in connected)


def shape(boxes):
    merged = []
    for lo, hi in boxes:
        merged.append('Block.box(%s)' % ', '.join('%.2f' % v for v in (lo[0], lo[1], lo[2],
                                                                      hi[0], hi[1], hi[2])))
    if len(merged) == 1:
        return merged[0]
    return 'Shapes.or(%s)' % (',\n\t\t\t\t\t'.join(merged))


def main():
    name = 'dc_string_cable'
    models = string_models(name)
    models[name + '_trench_bed'] = {'parent': 'block/block',
                                    'textures': {'particle': TRENCH, 'trench': PATHS[TRENCH]},
                                    'elements': bedding()}
    for file_name, model in sorted(models.items()):
        check_textures(file_name, model)
        write(os.path.join(BLOCK_MODELS, file_name + '.json'), model)
    print('%s: %d models, %d elements in all'
          % (name, len(models), sum(len(m['elements']) for m in models.values())))
    state = string_blockstate(name)
    write(os.path.join(BLOCKSTATES, name + '.json'), state)
    print('%s: %d blockstate parts' % (name, len(state['multipart'])))

    problems = check_disjoint()
    for line in problems:
        print('    OVERLAP %s' % line)
    print('no two pieces of a run share a pixel, in any of the 162 states' if not problems
          else '%d overlap(s)' % len(problems))

    trunk = 'dc_trunk_cable'
    for file_name, model in trunk_models(trunk).items():
        check_textures(file_name, model)
        write(os.path.join(BLOCK_MODELS, file_name + '.json'), model)
    write(os.path.join(BLOCKSTATES, trunk + '.json'), trunk_blockstate(trunk))
    print('%s: unchanged, 5 models' % trunk)

    for cable in (name, trunk):
        write(os.path.join(ITEM_MODELS, cable + '.json'),
              {'parent': 'minecraft:item/generated',
               'textures': {'layer0': 'electricity:item/' + cable}})

    if '--java' in sys.argv:
        java()


if __name__ == '__main__':
    main()
