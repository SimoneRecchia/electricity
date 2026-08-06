#!/usr/bin/env python3
"""The overhead line conductors as ground-laid blocks, and the tables their block declares.

    python3 tools/gen_conductor_models.py
    python3 tools/gen_conductor_models.py --java     # the shape tables GroundConductorBlock declares

One family of pieces per conductor, because the three are three different objects: a street bundle is one
black cable, a medium-voltage phase is one bare wire, and a transmission phase is four sub-conductors on
spacers.  The radii are ConductorCatalog's own, so a run laid on the ground is exactly the conductor a
player sees strung between two towers - which is the whole point of being able to lay one.

Same two traps as the string cable, and the same two guards: Forge's block-model loader reads the block's
own frame (corner at the origin), and a uv past one samples the sprite next door in the atlas.
"""

import itertools
import json
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from modellib import (FITTING, HUBS, Mesh, QUARTERS, SIDES, arc, box, cylinder,  # noqa: E402
                      mask, shape, tube, write_mtl)

ASSETS = os.path.join('src', 'main', 'resources', 'assets', 'electricity')
BLOCKSTATES = os.path.join(ASSETS, 'blockstates')
BLOCK_MODELS = os.path.join(ASSETS, 'models', 'block')
ITEM_MODELS = os.path.join(ASSETS, 'models', 'item')


# Which tile each conductor's own surface takes: a jacketed bundle is black, a bare one is aluminium.
JACKETS = {'abc_conductor': 'line_abc', 'mv_conductor': 'line_alu', 'hv_conductor': 'line_alu'}

CATALOGUE = os.path.join('src', 'main', 'java', 'com', 'dooji', 'electricity', 'main', 'registry',
                         'ConductorCatalog.java')


def conductors():
    """The three conductors, read out of ConductorCatalog so this file cannot disagree with the renderer.

    The radius and the bundle spacing are what WireRenderer draws a strung span with, and a run laid on the
    ground has to be the same conductor or the two do not meet.  Parsed off the argument list rather than
    matched by a regex over four lines of it: the fields are at fixed positions in the record.
    """
    source = open(CATALOGUE).read()
    found = []
    for block in re.finditer(r'register\(new ConductorSpec\((.*?)\)\);', source, re.S):
        text = re.sub(r'//[^\n]*', '', block.group(1))
        fields = [f.strip() for f in text.split(',') if f.strip()]
        name = re.search(r'id\("(\w+)"\)', fields[0]).group(1)
        numbers = [f for f in fields[2:] if re.fullmatch(r'[\d.]+|0x[0-9A-Fa-f]+', f)]
        # crossSection, maxVolts, ampacity, ohms, subConductors, spacing, radius, colour, sag, span, per
        subs = int(float(numbers[4]))
        spacing = float(numbers[5])
        radius = float(numbers[6])
        found.append((name, radius, subs, spacing, JACKETS[name]))

    if len(found) != 3:
        raise SystemExit('gen_conductor_models: read %d conductors, expected 3' % len(found))

    return tuple(found)


CONDUCTORS = conductors()

MATERIALS = {name: '#' + name for name in ('conductor', 'fitting', 'spacer')}

# Where a piece's middle stops and its arms begin, in sixteenths.  Wider than the string cable's, because
# a transmission conductor turns on a far bigger radius than a 6 mm2 cable does.
HUB_LO = 4.6
HUB_HI = 16.0 - HUB_LO
BEND_STEPS = 10

# The dead-end clamp a run is terminated in: a compression body with a bolted palm and an anchor eye.
CLAMP = ((1.30, 1.55),      # the compression body, over the conductor
         (0.55, 1.05),      # the neck
         (1.40, 0.90))      # the palm the next fitting bolts to
CLAMP_START = 8.2
# And the parallel-groove clamp that joins a third leg, because a bare conductor is not spliced by hand.
TEE_HALF = 1.9


def out(value):
    return value / 16.0


def at(x, y, z):
    """A point in sixteenths, in the block's own frame: 0 to 1, corner at the origin."""
    return (out(x), out(y), out(z))


class Wire:
    """One sub-conductor swept along a path in sixteenths."""

    def __init__(self, path, radius, material='conductor'):
        self.path = _dedupe([tuple(float(c) for c in p) for p in path])
        self.radius, self.material = radius, material

    def draw(self, mesh):
        faces = mesh.faces('conductor', self.material)
        tube(mesh, faces, [at(*p) for p in self.path], out(self.radius), sides=FITTING,
             uv_scale=1.0, uv_along=1.0, caps=faces)

    def boxes(self):
        result = []
        for i in range(len(self.path) - 1):
            a, b = self.path[i], self.path[i + 1]
            moving = [j for j in range(3) if abs(b[j] - a[j]) > 1e-6]
            lo, hi = [0.0] * 3, [0.0] * 3
            for j in range(3):
                pad = 0.0 if moving == [j] else self.radius
                lo[j] = min(a[j], b[j]) - pad
                hi[j] = max(a[j], b[j]) + pad
        # a single-segment path along one axis still has to claim its own thickness
            result.append((tuple(lo), tuple(hi)))
        return result

    def turned(self, quarter):
        return Wire([_spin(p, quarter) for p in self.path], self.radius, self.material)


class Solid:
    """A box of fitting: a clamp body, a spacer frame, an anchor eye."""

    def __init__(self, group, material, lo, hi, uv_scale=1.0):
        self.group, self.material = group, material
        self.lo, self.hi, self.uv_scale = tuple(lo), tuple(hi), uv_scale

    def draw(self, mesh):
        box(mesh, mesh.faces(self.group, self.material), at(*self.lo), at(*self.hi),
            uv_scale=self.uv_scale)

    def boxes(self):
        return [(self.lo, self.hi)]

    def turned(self, quarter):
        return _Boxes([_spin_box(b, quarter) for b in self.boxes()], self.group)


class Barrel:
    """A round fitting on the conductor's own axis: a compression clamp's body."""

    def __init__(self, group, material, centre, axis, radius, low, high, cap=True):
        self.group, self.material, self.centre, self.axis = group, material, centre, axis
        self.radius, self.low, self.high, self.cap = radius, low, high, cap

    def draw(self, mesh):
        faces = mesh.faces(self.group, self.material)
        base = list(self.centre)
        # cylinder() takes a centre and a *half* length.  Placed at self.low with the whole length as the
        # half, a clamp came out twice as long reaching back past low, while boxes() below said low..high -
        # the same fault gen_cable_models.Barrel had, and check_drawn is why that one was found.
        base['xyz'.index(self.axis)] = (self.low + self.high) / 2.0
        cylinder(mesh, faces, at(*base), self.axis, out(self.radius), out((self.high - self.low) / 2.0),
                 sides=FITTING, uv_scale=1.0, caps=faces if self.cap else None,
                 cap_ends=(1,) if self.cap else ())

    def boxes(self):
        index = 'xyz'.index(self.axis)
        lo, hi = [0.0] * 3, [0.0] * 3
        for i in range(3):
            if i == index:
                lo[i], hi[i] = self.low, self.high
            else:
                lo[i], hi[i] = self.centre[i] - self.radius, self.centre[i] + self.radius
        return [(tuple(lo), tuple(hi))]

    def turned(self, quarter):
        return _Boxes([_spin_box(b, quarter) for b in self.boxes()], self.group)


class _Boxes:
    """A part reduced to its boxes once turned, keeping the group it belongs to.

    The group matters to the check: a clamp body and the grooves in it are one fitting, so their boxes are
    allowed to share a volume - what would be a fault is two *different* fittings doing it.
    """

    def __init__(self, boxes, group):
        self._boxes, self.group = boxes, group

    def boxes(self):
        return self._boxes


def _spin(point, quarter):
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


# ---------------------------------------------------------------- the bundle
#
# A bundle rests on the ground, so its *lowest* sub-conductor's underside is on it.  For a single wire
# that is one lane on the axis; for a quad it is two lanes at two heights, which is a bundle lying down
# rather than a bundle in the air - and it is what a spacer holds apart.

def lanes(radius, subs, spacing):
    """Each sub-conductor as (x offset from the middle, y of its axis), in sixteenths."""
    if subs == 1:
        return ((0.0, radius),)

    half = spacing * 0.5
    return ((-half, radius), (half, radius),
            (-half, radius + spacing), (half, radius + spacing))


def spec_px(spec):
    """One conductor's figures in sixteenths, from the blocks ConductorCatalog states them in."""
    name, radius, subs, spacing, jacket = spec
    return name, radius * 16.0, subs, spacing * 16.0, jacket


# ---------------------------------------------------------------- the pieces

def piece_line(spec):
    """Straight through: the run, and a spacer if it is a bundle."""
    _, radius, subs, spacing, _ = spec_px(spec)
    parts = [Wire([(8.0 + dx, y, HUB_LO), (8.0 + dx, y, HUB_HI)], radius)
             for dx, y in lanes(radius, subs, spacing)]
    if subs > 1:
        parts += spacer(spec, 8.0)
    return parts


def spacer(spec, z):
    """A bundle spacer: the frame that holds four sub-conductors apart, which is why a quad is a quad.

    Without spacers a bundle collapses under its own magnetic attraction, and the whole reason for
    bundling - a lower surface field, so it does not ionise the air round it - goes with it.
    """
    _, radius, subs, spacing, _ = spec_px(spec)
    parts = []
    half = spacing * 0.5
    lo_y, hi_y = radius, radius + spacing
    for dx in (-half, half):
        parts.append(Solid('spacer', 'spacer', (8.0 + dx - 0.18, lo_y, z - 0.16),
                           (8.0 + dx + 0.18, hi_y, z + 0.16), uv_scale=0.5))
    parts.append(Solid('spacer', 'spacer', (8.0 - half - 0.18, (lo_y + hi_y) / 2 - 0.16, z - 0.16),
                       (8.0 + half + 0.18, (lo_y + hi_y) / 2 + 0.16, z + 0.16), uv_scale=0.5))
    return parts


def piece_bend(spec):
    """Two adjacent sides: the run turns on a radius, north to east.

    A bare conductor turns on a far wider radius than an insulated cable, so the arc fills the middle -
    each sub-conductor's own arc is tangent to the lane it leaves on, the same construction the string
    cable's bend uses, so all of them come out concentric.
    """
    _, radius, subs, spacing, _ = spec_px(spec)
    parts = []
    for dx, y in lanes(radius, subs, spacing):
        # A sub-conductor coming in on lane x = 8 + dx leaves on lane z = 8 - dx: the bundle swaps sides
        # round a corner, which is what puts every arc on the same centre, (HUB_HI, HUB_LO). Leaving on
        # its own lane instead needs a radius that is tangent to neither, and the arcs cross.
        turn_radius = HUB_HI - (8.0 + dx)
        turn = arc((HUB_HI, y, HUB_LO), turn_radius, (0, 2), 180.0, 90.0, BEND_STEPS)
        parts.append(Wire([(8.0 + dx, y, HUB_LO)] + [(p[0], y, p[2]) for p in turn]
                          + [(HUB_HI, y, 8.0 - dx)], radius))
    return parts


def piece_end(spec):
    """One side: the run is dead-ended in a compression clamp with an anchor eye on it.

    A clamp is far fatter than the conductor it is pressed onto, so a clamp resting on the ground holds its
    axis higher and the conductor rises into it - the same thing gen_cable_models does with an MC4, and for
    the same reason: coaxial at the conductor's own height, the clamp body is below the ground.
    """
    _, radius, subs, spacing, _ = spec_px(spec)
    clamp_radius = max(r for _, r in CLAMP)
    # A bundle *splays* at a dead end, because each sub-conductor gets its own clamp and four clamps do not
    # fit at the bundle's own spacing. Which is what a real termination does, and it is visible on any
    # tension tower: the four wires spread into a fan at the clamps and close up again down the span.
    gap = clamp_radius * 2.2
    parts = []
    for dx, y in lanes(radius, subs, spacing):
        if subs == 1:
            cx, cy = 8.0, clamp_radius
        else:
            cx = 8.0 + math.copysign(gap * 0.5, dx)
            cy = clamp_radius + (gap if y > radius + 1e-6 else 0.0)

        parts.append(Wire([(8.0 + dx, y, HUB_LO), (8.0 + dx, y, CLAMP_START - 3.4),
                           (cx, cy, CLAMP_START - 1.2), (cx, cy, CLAMP_START)], radius))
        cursor = CLAMP_START
        for length, body in CLAMP:
            parts.append(Barrel('clamp', 'fitting', (cx, cy, 0.0), 'z',
                                max(body, radius + 0.10), cursor, cursor + length,
                                cap=length == CLAMP[-1][0]))
            cursor += length
    if subs > 1:
        parts += spacer(spec, HUB_LO + 1.2)
    return parts


def piece_loose(spec):
    """Nothing connected: a length of conductor lying where it was pulled off the drum."""
    _, radius, subs, spacing, _ = spec_px(spec)
    parts = []
    for dx, y in lanes(radius, subs, spacing):
        parts.append(Wire([(8.0 + dx, y, 3.0), (8.0 + dx, y, 13.0)], radius))
    if subs > 1:
        parts += spacer(spec, 5.0) + spacer(spec, 11.0)
    return parts


def piece_tee(spec):
    """Three sides: a parallel-groove clamp, which is how a third leg is actually joined on."""
    return clamp_block(spec, ('north', 'east', 'south'))


def piece_cross(spec):
    """Four sides: the same clamp with the fourth groove used."""
    return clamp_block(spec, SIDES)


def clamp_block(spec, connected):
    """A bolted clamp body with a groove for every leg that arrives at it."""
    _, radius, subs, spacing, _ = spec_px(spec)
    top = radius + (spacing if subs > 1 else 0.0) + radius + 0.6
    parts = [Solid('clamp', 'fitting', (8.0 - TEE_HALF, 0.0, 8.0 - TEE_HALF),
                   (8.0 + TEE_HALF, max(top, 1.4), 8.0 + TEE_HALF), uv_scale=1.0)]
    for side in connected:
        axis = 'z' if side in ('north', 'south') else 'x'
        near = side in ('north', 'west')
        low, high = (8.0 - TEE_HALF - 0.7, 8.0 - TEE_HALF) if near \
            else (8.0 + TEE_HALF, 8.0 + TEE_HALF + 0.7)
        for dx, y in lanes(radius, subs, spacing):
            middle = [8.0, y, 8.0]
            middle[0 if axis == 'z' else 2] = 8.0 + dx
            parts.append(Barrel('clamp', 'fitting', tuple(middle), axis, radius + 0.24, low, high,
                                cap=False))
    return parts


def piece_arm(spec):
    """From the block edge in to the middle: the run crossing the boundary."""
    _, radius, subs, spacing, _ = spec_px(spec)
    parts = [Wire([(8.0 + dx, y, 0.0), (8.0 + dx, y, HUB_LO)], radius)
             for dx, y in lanes(radius, subs, spacing)]
    return parts


# Which pattern gets which of the six, and how far round: modellib.HUBS, shared with the two cable gauges.
MIDDLES = {'loose': piece_loose, 'end': piece_end, 'line': piece_line, 'bend': piece_bend,
           'tee': piece_tee, 'cross': piece_cross}


# ---------------------------------------------------------------- writing

def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def textures_for(spec):
    jacket = spec_px(spec)[4]
    return {'conductor': 'electricity:block/' + jacket,
            'fitting': 'electricity:block/line_fitting',
            'spacer': 'electricity:block/line_fitting',
            'particle': 'electricity:block/' + jacket}


def block_name(spec):
    """The block's registry name.

    Not the conductor's own id: that belongs to the *reel*, which is the item, and a block and an item
    cannot share one. ``_run`` because that is what a length of conductor lying on the ground is called.
    """
    return spec_px(spec)[0] + '_run'


def write_piece(spec, kind, parts):
    name = '%s_%s' % (block_name(spec), kind)
    directory = os.path.join(ASSETS, 'models', block_name(spec))
    mesh = Mesh()
    for part in parts:
        part.draw(mesh)

    seen = []
    for _, material, faces in mesh.objects:
        if faces and material not in seen:
            seen.append(material)

    check_inside(name, mesh)
    mesh.write(os.path.join(directory, kind + '.obj'), kind + '.mtl', 'gen_conductor_models.py')
    write_mtl(os.path.join(directory, kind + '.mtl'), seen, MATERIALS, 'gen_conductor_models.py')
    write(os.path.join(BLOCK_MODELS, name + '.json'), {
        'loader': 'forge:obj',
        'model': 'electricity:models/%s/%s.obj' % (block_name(spec), kind),
        # the same two settings the string cable needs, for the same two reasons
        'flip_v': False,
        'automatic_culling': False,
        'shade_quads': False,
        'textures': textures_for(spec),
    })
    return mesh.stats()[1]


def check_inside(name, mesh):
    """Fails if a piece leaves its block or its sprite - the two faults invisible outside the game."""
    margin = 1.0 / 16.0
    for x, y, z in mesh.v:
        if not all(-margin <= v <= 1.0 + margin for v in (x, y, z)):
            raise SystemExit('%s: vertex (%.3f, %.3f, %.3f) is outside the block' % (name, x, y, z))
    for u, v in mesh.vt:
        if not (-1e-6 <= u <= 1.0 + 1e-6 and -1e-6 <= v <= 1.0 + 1e-6):
            raise SystemExit('%s: uv (%.3f, %.3f) leaves its sprite' % (name, u, v))


def blockstate(spec):
    name = block_name(spec)
    parts = []
    for connected, (kind, turn) in sorted(HUBS.items()):
        when = {side: 'true' if side in connected else 'false' for side in SIDES}
        apply = {'model': 'electricity:block/%s_%s' % (name, kind)}
        if turn:
            apply['y'] = turn
        parts.append({'when': when, 'apply': apply})

    for side, turn in QUARTERS.items():
        apply = {'model': 'electricity:block/%s_arm' % name}
        if turn:
            apply['y'] = turn
        parts.append({'when': {side: 'true'}, 'apply': apply})

    return {'multipart': parts}


# ---------------------------------------------------------------- the Java tables

def tables():
    """Every shape GroundConductorBlock declares, keyed by conductor and by pattern."""
    out_tables = {}
    for spec in CONDUCTORS:
        name = spec_px(spec)[0]  # the table is named after the conductor, not the block
        hubs = {}
        for connected, (kind, turn) in sorted(HUBS.items(), key=lambda kv: mask(kv[0])):
            boxes = [b for p in MIDDLES[kind](spec) for b in p.turned(turn).boxes()]
            hubs['0b%s' % format(mask(connected), '04b')] = shape(boxes)
        arms = {}
        for side, turn in (('NORTH', 0), ('EAST', 90), ('SOUTH', 180), ('WEST', 270)):
            arms['Direction.%s' % side] = shape([b for p in piece_arm(spec)
                                                 for b in p.turned(turn).boxes()])
        out_tables[name] = (hubs, arms)
    return out_tables


def java():
    print('\n\t// ---- printed by tools/gen_conductor_models.py --java ----')
    for name, (hubs, arms) in tables().items():
        upper = name.split('_')[0].upper()
        print('\n\tprivate static final Map<Integer, VoxelShape> %s_HUBS = Map.ofEntries(' % upper)
        print(',\n'.join('\t\t\tMap.entry(%s, %s)' % item for item in hubs.items()) + ');')
        print('\n\tprivate static final Map<Direction, VoxelShape> %s_ARMS = Map.of(' % upper)
        print(',\n'.join('\t\t\t%s, %s' % item for item in arms.items()) + ');')


def check_java():
    """Proves GroundConductorBlock still declares what this draws, for all three conductors."""
    path = os.path.join('src', 'main', 'java', 'com', 'dooji', 'electricity', 'block',
                        'GroundConductorBlock.java')
    if not os.path.exists(path):
        return ['GroundConductorBlock.java is not there yet']

    source = ' '.join(open(path).read().split())
    problems = []
    for name, (hubs, arms) in tables().items():
        for key, text in list(hubs.items()) + list(arms.items()):
            if ' '.join(('%s, %s' % (key, text)).split()) not in source:
                problems.append('%s %s is not what GroundConductorBlock declares' % (name, key))
    return problems


def check_disjoint():
    """Proves nothing a conductor draws touches anything else it draws, in any pattern."""
    problems = []
    for spec in CONDUCTORS:
        name = spec_px(spec)[0]
        for connected, (kind, turn) in HUBS.items():
            labelled = [(p.turned(turn), '%s[%d]' % (kind, i))
                        for i, p in enumerate(MIDDLES[kind](spec))]
            for side in connected:
                labelled += [(p.turned(QUARTERS[side]), '%s:%s[%d]' % (kind, side, i))
                             for i, p in enumerate(piece_arm(spec))]

            for (a, la), (b, lb) in itertools.combinations(labelled, 2):
                if isinstance(a, Wire) and isinstance(b, Wire):
                    if _jointed(a.path, b.path):
                        continue

                    gap = _path_gap(a.path, b.path)
                    if gap < a.radius + b.radius - 1e-6:
                        problems.append('%s: %s and %s come within %.2f px of %.2f'
                                        % (name, la, lb, gap, a.radius + b.radius))
                    continue

                # a fitting is *meant* to hold the conductor, so a clamp containing one is not a clash
                if isinstance(a, Wire) or isinstance(b, Wire):
                    continue

                # and one fitting's own parts are one object: a clamp body with grooves in it
                if getattr(a, 'group', None) == getattr(b, 'group', None):
                    continue

                for one, other in itertools.product(a.boxes(), b.boxes()):
                    share = [min(one[1][i], other[1][i]) - max(one[0][i], other[0][i])
                             for i in range(3)]
                    if all(v > 1e-6 for v in share):
                        problems.append('%s: %s and %s share %.2f x %.2f x %.2f px'
                                        % (name, la, lb, share[0], share[1], share[2]))
    return sorted(set(problems))


def _jointed(first, second):
    return any(math.dist(a, b) < 1e-6 for a in (first[0], first[-1]) for b in (second[0], second[-1]))


def _path_gap(first, second):
    def samples(path):
        for i in range(len(path) - 1):
            a, b = path[i], path[i + 1]
            steps = max(2, int(math.dist(a, b) * 6))
            for k in range(steps + 1):
                t = k / steps
                yield tuple(a[j] + (b[j] - a[j]) * t for j in range(3))

    return min(math.dist(p, q) for p in samples(first) for q in samples(second))


def main():
    faces = pieces = 0
    for spec in CONDUCTORS:
        name = block_name(spec)
        directory = os.path.join(ASSETS, 'models', name)
        if os.path.isdir(directory):
            for stale in os.listdir(directory):
                os.remove(os.path.join(directory, stale))

        for kind, builder in MIDDLES.items():
            faces += write_piece(spec, kind, builder(spec))
            pieces += 1
        faces += write_piece(spec, 'arm', piece_arm(spec))
        pieces += 1

        write(os.path.join(BLOCKSTATES, name + '.json'), blockstate(spec))

    print('%d OBJ pieces over %d conductors, %d faces in all' % (pieces, len(CONDUCTORS), faces))

    problems = check_disjoint()
    for line in problems:
        print('    OVERLAP %s' % line)
    if not problems:
        print('nothing a conductor draws touches anything else it draws, in any pattern')

    stale = check_java()
    for line in stale:
        print('    TABLE %s' % line)
    if not stale:
        print('GroundConductorBlock declares the same shapes this draws')

    if '--java' in sys.argv:
        java()


if __name__ == '__main__':
    main()
