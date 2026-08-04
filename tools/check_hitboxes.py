#!/usr/bin/env python3
"""Collision cut from the model, and the two ways it goes wrong.

    python3 tools/check_hitboxes.py           # what the Java declares against what the models draw
    python3 tools/check_hitboxes.py --java    # the same tables, ready to paste into the blocks

Why a script rather than a look
-------------------------------
Because one of these faults is invisible until you walk into it and the other is invisible until you
walk into nothing. An electric cabin is drawn a block wide, two and a bit deep and two and a half
tall; a utility pole is six tall with four-block arms. Collide with either as though it were a stack of
whole cubes and you get both faults at once: a player stands a third of a block out past the eave, on
air, and bumps into thin air beside the door.

So the model is the authority. A machine's collision is cut from its own geometry - every group it
draws, clipped to each block cell it reaches into - and this is what says the Java and the OBJ still
agree, to a hundredth of a pixel.

What is left out of a body, and why
-----------------------------------
``pivot_`` and ``rotate_`` groups. A pivot is one point, a marker for the renderer to turn something
about. A rotate group is a part that moves - a tracker's panel, an inverter's fan, an anemometer's cups
- and a block's collision cannot move with it, so a moving part gets none of its own and its machine
keeps a shape big enough for wherever the part can swing to. Those machines are listed in ``SWEPT``.

How small is too small
----------------------
Two thresholds, and what separates them is what a box costs. A box inside the machine's own block is
free, so a pixel of thickness is enough to keep it. Claiming the cell next door costs the player that
whole cell - it becomes a block they can no longer build in - so a cell is only claimed when the
machine puts at least ``MIN_CLAIM`` pixels of itself into it. Once a cell is claimed the cheap
threshold applies inside it, which is deliberate: an insulator standing on a claimed crossarm should
not have its bottom pixel and a half cut off and float.

Under the claim threshold a machine keeps its edge to itself. A cabin's roof overhangs the cell beside
it by nine tenths of a pixel and a power box's plinth hangs one and seven tenths below its own block;
taking a cubic metre of the world away for either would cost more than the ledge it saves.
"""

import collections
import glob
import math
import os
import re
import sys

MODELS = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')
BLOCKS = os.path.join('src', 'main', 'java', 'com', 'dooji', 'electricity', 'block')
RENDERERS = os.path.join('src', 'main', 'java', 'com', 'dooji', 'electricity', 'client', 'render', 'block')

# Groups that are a marker, a moving part, or something that is only sometimes there.
#
# ``harness`` and ``entry`` are the cable a machine grows when a reel is worked into it or a run is
# laid up against it.  They are left out for the same reason a laid run itself has no collision:
# DcCableBlock gives a surface run none, so a stub of the same cable standing proud of a machine
# should not be the one piece of copper in the mod a player can bump into - and it would be collision
# where, most of the time, nothing is drawn at all.
MOVING = ('pivot_', 'rotate_', 'harness', 'entry')

# Everything below is in pixels, the sixteenths the game itself is authored in.
# How thick a box has to be in every axis to be collision at all, in the machine's own cell and in one
# it would have to claim.
#
# A quarter of a pixel in the machine's own cell, which is thinner than it sounds and is deliberate: a
# ballasted table is two pixels tall in total, so a threshold of a whole pixel threw away its ballast,
# its frame and its glass and left the machine with no collision whatever.  The same threshold had a
# kiosk's plinth and its rain hood - both plainly visible, both under a pixel - collide with nothing.
MIN_OWN = 0.25
MIN_CLAIM = 2.0
# A gap this small is a seam in the model rather than a step, and the two boxes are merged. The cabin's
# roof sits two thousandths of a block above its body, which is a thirtieth of a pixel.
SEAM = 0.1
# What counts as the same number, once the tables have been rounded for printing.
EPS = 0.02

# Which block class draws which model.
BLOCK_CLASS = {
    'utility_pole': 'UtilityPoleBlock',
    'power_box': 'PowerBoxBlock',
    'electric_cabin': 'ElectricCabinBlock',
    'met_station': 'MetStationBlock',
    'wind_turbine': 'WindTurbineBlock',
    'pv_array': 'PvArrayBlock',
    'pv_inverter': 'PvInverterBlock',
    'pv_combiner': 'PvCombinerBlock',
}

# One block class, several models: which constant in its file holds the table cut from this one.
#
# Four mountings share PvArrayBlock, and a table per mounting in one file cannot be told apart by
# looking for ``new Cell(`` - the first pass at this compared the flat table's model against all four
# tables at once and reported everything as wrong.
MODEL_TABLE = {
    # the inverter declares one table and derives its two smaller sizes by scaling it, so the derived
    # ones are built with a Cell of their own that carries no literal box - and read unscoped, that empty
    # cell overwrites the real table and every box in it reads as missing
    'pv_inverter': 'CELLS',
    'pv_flat': 'FLAT_CELLS',
    'pv_tilt': 'TILT_CELLS',
    'pv_track': 'TRACK_CELLS',
    'pv_dual': 'DUAL_CELLS',
}

# Machines with no facing at all, and why. Nothing about them can be turned wrongly. Empty as it stands:
# every machine in the mod is placed facing somewhere.
NO_FACING = {}

# One model, several products: the directory name is not the block's name.
MODEL_BLOCK = {
    'electric_cab': 'electric_cabin',
    'met_mast': 'met_station',
    'pv_flat': 'pv_array',
    'pv_tilt': 'pv_array',
    'pv_track': 'pv_array',
    'pv_dual': 'pv_array',
}

# Models whose collision is a volume rather than a shape, and why. Their tables are printed for
# reference and not compared: a shape cut from where the geometry happens to be authored would be
# wrong a second later.
#
# Keyed by model rather than by block, because two of the four mountings that share PvArrayBlock do
# not move at all and are compared to the pixel.
SWEPT = {
    'pv_track':
        'the panel tracks the sun about its torque tube, so its collision has to cover the volume it '
        'sweeps rather than the place it was authored - a shape cut from the flat position would let '
        'a player fall through the row every afternoon',
    'pv_dual':
        'the frame turns about two axes, so the same holds and in one more direction',
    'wind_turbine':
        'the model carries its own tower, which in the world is a stack of turbine_tower blocks with '
        'their own collision, and a hundred metres of turning rotor above it that nothing should be '
        'able to stand on',
}


# Models whose tables are printed somewhere else, so this file would only disagree with itself.
#
# The string cable is sixteen pieces chosen by which sides connect, and its outline is a different
# shape in each - so its tables are keyed by that pattern and printed by the generator that draws the
# pieces, off the very parts it draws them from. Reading them back here would compare one derivation
# against another rather than the model against the game.
ELSEWHERE = {
    'dc_string_cable': 'printed by tools/gen_cable_models.py --java, per connection pattern',
}


def obj_groups(path):
    """Every object in an OBJ with the polygons it is made of."""
    verts = []
    groups = collections.OrderedDict()
    current = 'none'
    for line in open(path):
        if line.startswith('v '):
            verts.append(tuple(float(v) for v in line.split()[1:4]))
        elif line.startswith('o '):
            current = line.split(None, 1)[1].strip()
        elif line.startswith('f '):
            face = [verts[int(f.split('/')[0]) - 1] for f in line.split()[1:]]
            groups.setdefault(current, []).append(face)

    return groups


def bounds(faces):
    """The bounding box of a list of polygons."""
    lo = [9.0] * 3
    hi = [-9.0] * 3
    for face in faces:
        for point in face:
            for axis in range(3):
                lo[axis] = min(lo[axis], point[axis])
                hi[axis] = max(hi[axis], point[axis])

    return lo, hi


def clipped(face, axis, low, high):
    """One polygon cut to a slab, as the part of it between two planes.

    Sutherland-Hodgman against two parallel planes.  What it is for: a tilted plane's bounding box is
    not the plane - a rack at 25 degrees has a box seven pixels tall over its whole footprint, and
    collision cut from that is a wall of air in front of the low edge.  Cut into slabs and clipped, the
    same plane comes out as a staircase that follows it, which is how the game itself draws a slope.
    """
    for sign, limit in ((1, low), (-1, high)):
        out = []
        for index, point in enumerate(face):
            nxt = face[(index + 1) % len(face)]
            inside = sign * (point[axis] - limit) >= 0
            inside_next = sign * (nxt[axis] - limit) >= 0
            if inside:
                out.append(point)
            if inside != inside_next:
                span = nxt[axis] - point[axis]
                if abs(span) > 1e-9:
                    t = (limit - point[axis]) / span
                    out.append(tuple(point[k] + (nxt[k] - point[k]) * t for k in range(3)))

        face = out
        if not face:
            return []

    return face


# How thick a slice is, in blocks, and how much of a step in a group's own height it takes before the
# group is worth slicing at all.
#
# Two pixels.  One pixel is the grain the game's own models are authored on and would be exact, but a
# tilted rack came out as forty-four boxes at that width and a field of them is a great many boxes for
# the collision code to walk - and the difference between a one-pixel staircase and a two-pixel one is
# twelve centimetres of a step nobody can feel.  A step under a fiftieth of a block is a seam rather
# than a slope, and is not sliced at all.
SLICE = 2.0 / 16.0
SLOPE = 0.02


def sliced(faces):
    """A group as boxes: one if it fills its own bounding box, a staircase of them if it slopes.

    Sliced along whichever horizontal axis it slopes in, and only if it slopes: everything in these
    models that is axis-aligned - which is nearly all of it - comes out as the single box it always
    was, so the tables stay short.
    """
    lo, hi = bounds(faces)
    for axis in (0, 2):
        span = hi[axis] - lo[axis]
        if span < 3 * SLICE:
            continue

        steps = int(math.ceil(span / SLICE))
        slabs = []
        for step in range(steps):
            low = lo[axis] + step * SLICE
            high = min(hi[axis], low + SLICE)
            pieces = [clipped(face, axis, low, high) for face in faces]
            pieces = [piece for piece in pieces if piece]
            if not pieces:
                continue

            slab_lo, slab_hi = bounds(pieces)
            slab_lo[axis], slab_hi[axis] = low, high
            slabs.append((slab_lo, slab_hi))

        if len(slabs) < 3:
            continue

        # A slope is a group whose top climbs, or falls, all the way across it.  Monotone is the whole
        # of the test, and it is what tells the two cases apart:
        #
        #   * a rack's plane, a brace, a pier cut to a tilt - the top moves the same way throughout,
        #     and a single box round it is a wall of air in front of the low end
        #   * a round shaft with a dome on it, a cylinder, a cabinet - the top goes up and comes back
        #     down, or does not move at all, and one box is within half a pixel of the truth
        #
        # Without it every eighty-sided cone in the mod came out as eight boxes of staircase across its
        # own diameter, which is a hundred and twenty boxes on a pole and no more accurate for any of it.
        tops = [slab[1][1] for slab in slabs]
        climbs = all(b >= a - SLOPE for a, b in zip(tops, tops[1:]))
        falls = all(b <= a + SLOPE for a, b in zip(tops, tops[1:]))
        if (climbs or falls) and max(tops) - min(tops) > 4 * SLOPE:
            return slabs

    return [(lo, hi)]


def body(groups):
    """The groups that stand still, in pixels from the block's own corner, sloping parts sliced."""
    kept = []
    for name, faces in groups.items():
        if name.startswith(MOVING):
            continue

        for lo, hi in sliced(faces):
            kept.append((name, tuple(16.0 * v for v in (lo[0] + 0.5, lo[1], lo[2] + 0.5)),
                         tuple(16.0 * v for v in (hi[0] + 0.5, hi[1], hi[2] + 0.5))))

    return kept


def pieces(boxes):
    """Every group cut at the cell boundaries: {cell: [(name, box in that cell's own pixels)]}."""
    out = collections.defaultdict(list)
    for name, lo, hi in boxes:
        spans = [range(int(lo[axis] // 16), int((hi[axis] - 1e-6) // 16) + 1) for axis in range(3)]
        for x in spans[0]:
            for y in spans[1]:
                for z in spans[2]:
                    cell = (x, y, z)
                    piece = []
                    for axis, index in enumerate(cell):
                        low = max(lo[axis], 16.0 * index) - 16.0 * index
                        high = min(hi[axis], 16.0 * index + 16.0) - 16.0 * index
                        piece.append((low, high))

                    if min(high - low for low, high in piece) < MIN_OWN - 1e-6:
                        continue

                    out[cell].append((name, tuple(round(v, 2) for low, high in piece for v in (low, high))))

    return out


def claimed(cut):
    """The cells worth having, own block first: one box of ``MIN_CLAIM`` in it is what earns the rest."""
    out = {}
    for cell, parts in cut.items():
        earned = cell == (0, 0, 0) or any(
            min(box[1] - box[0], box[3] - box[2], box[5] - box[4]) >= MIN_CLAIM - 1e-6 for _, box in parts)
        if earned:
            out[cell] = tidy([box for _, box in parts])

    return out


def swaps(box):
    """The box as (x0, y0, z0, x1, y1, z1) read as three spans."""
    return ((box[0], box[1]), (box[2], box[3]), (box[4], box[5]))


def contains(outer, inner):
    return all(o[0] <= i[0] + 1e-6 and i[1] <= o[1] + 1e-6 for o, i in zip(swaps(outer), swaps(inner)))


def merge(first, second):
    """The two boxes as one, when they are the same in two axes and meet in the third. Else None."""
    a, b = swaps(first), swaps(second)
    for axis in range(3):
        others = [n for n in range(3) if n != axis]
        if any(abs(a[n][0] - b[n][0]) > EPS or abs(a[n][1] - b[n][1]) > EPS for n in others):
            continue
        if max(a[axis][0], b[axis][0]) - min(a[axis][1], b[axis][1]) > SEAM:
            continue

        span = (min(a[axis][0], b[axis][0]), max(a[axis][1], b[axis][1]))
        joined = [a[n] if n != axis else span for n in range(3)]
        return tuple(round(v, 2) for low, high in joined for v in (low, high))

    return None


def tidy(boxes):
    """The same solid with as few boxes as it takes: duplicates gone, insides gone, seams closed."""
    boxes = list(dict.fromkeys(boxes))
    boxes = [box for index, box in enumerate(boxes)
             if not any(other != box and contains(other, box) for other in boxes[:index] + boxes[index + 1:])]

    joining = True
    while joining:
        joining = False
        for i in range(len(boxes)):
            for j in range(i + 1, len(boxes)):
                joined = merge(boxes[i], boxes[j])
                if joined is None:
                    continue

                boxes[i] = joined
                del boxes[j]
                joining = True
                break

            if joining:
                break

    return sorted(boxes)


def order(cell):
    """Cells in the order a person reads a machine: bottom up, then out from the middle."""
    return (cell[1], abs(cell[0]) + abs(cell[2]), cell[0], cell[2])


def java(cells):
    """The cell table as it goes into the block."""
    lines = []
    for cell in sorted(cells, key=order):
        boxes = cells[cell]
        shapes = ['Shapes.block()' if box == (0.0, 16.0, 0.0, 16.0, 0.0, 16.0)
                  else 'Block.box(%s)' % ', '.join('%.2f' % box[n] for n in (0, 2, 4, 1, 3, 5))
                  for box in boxes]
        if len(shapes) == 1:
            body_text = shapes[0]
        else:
            body_text = 'Shapes.or(%s)' % (',\n\t\t\t\t\t'.join(shapes))

        lines.append('\t\t\tnew Cell(%d, %d, %d, %s),' % (cell[0], cell[1], cell[2], body_text))

    return '\n'.join(lines)


def scoped(text, constant):
    """Just the initialiser of one named constant, so a file with four tables can be read one at a time."""
    if constant is None:
        return text

    match = re.search(r'%s\s*=\s*List\.of\(' % re.escape(constant), text)
    if match is None:
        return ''

    depth, index = 1, match.end()
    while depth and index < len(text):
        depth += {'(': 1, ')': -1}.get(text[index], 0)
        index += 1

    return text[match.end():index - 1]


def declared(text):
    """The cells a block's own table declares, read back out of the Java."""
    out = {}
    for match in re.finditer(r'new Cell\(', text):
        depth, index = 1, match.end()
        while depth:
            depth += {'(': 1, ')': -1}.get(text[index], 0)
            index += 1

        inside = text[match.end():index - 1]
        head = inside.split(',', 3)
        cell = tuple(int(value) for value in head[:3])
        shapes = [(0.0, 16.0, 0.0, 16.0, 0.0, 16.0)] if 'Shapes.block()' in head[3] else []
        for numbers in re.findall(r'Block\.box\(([^)]*)\)', head[3]):
            values = [float(value) for value in numbers.split(',')]
            shapes.append((values[0], values[3], values[1], values[4], values[2], values[5]))

        out[cell] = sorted(tuple(round(v, 2) for v in shape) for shape in shapes)

    return out


def differences(want, have):
    """What the Java would have to change for the two tables to be the same solid."""
    faults = []
    for cell in sorted(set(want) | set(have), key=order):
        mine, theirs = list(want.get(cell, [])), list(have.get(cell, []))
        for box in list(mine):
            match = next((other for other in theirs if all(abs(a - b) <= EPS for a, b in zip(box, other))), None)
            if match is not None:
                mine.remove(box)
                theirs.remove(match)

        for box in mine:
            faults.append('cell %-11s the model fills %s and nothing there collides' % (str(cell), show(box)))
        for box in theirs:
            faults.append('cell %-11s collision at %s where the model draws nothing' % (str(cell), show(box)))

    return faults


def show(box):
    return 'x %.2f..%.2f y %.2f..%.2f z %.2f..%.2f' % box


# get2DDataValue, the order the game numbers the horizontal facings in and the order every quarter-turn
# in the mod is worked out from.
FACING_ORDER = {'SOUTH': 0, 'WEST': 1, 'NORTH': 2, 'EAST': 3}

def turn(authored, facing):
    """The angle the geometry is turned by to face this way, the way every renderer here works it out."""
    return ((FACING_ORDER[authored] - FACING_ORDER[facing]) % 4) * 90


def implied(mapping):
    """The facing a table of angles says the geometry was modelled at, or None if it is not a turn of one."""
    for authored in FACING_ORDER:
        if all(turn(authored, facing) == angle for facing, angle in mapping.items()):
            return authored

    return None


def anchor_turns(text):
    """The angles a block entity turns its wire anchors by, read out of its own switch."""
    match = re.search(r'case EAST -> ([\d.]+)f;\s*case SOUTH -> ([\d.]+)f;\s*'
                      r'case WEST -> ([\d.]+)f;\s*default -> ([\d.]+)f;', text)
    if match is None:
        return None

    return dict(zip(('EAST', 'SOUTH', 'WEST', 'NORTH'), (int(float(value)) for value in match.groups())))


def authored(java):
    """The facing a block says its model was drawn at."""
    match = re.search(r'AUTHORED = Direction\.(\w+)', java)
    return match.group(1).lower() if match else None


def facing_faults(name, block, java):
    """Everything that has to agree about which way a machine's geometry was modelled, and whether it does.

    The fault this catches happened: the renderer knew the cabin's model faces east, the collision assumed
    north, and so a cabin's cells stood at a right angle to the cabin for as long as they existed. Three
    places hold this one fact - the renderer that turns the model, the block entity that turns the wire
    anchors, and the cells - so the block declares it and the others have to be reading the same thing.
    """
    faults = []
    if name in NO_FACING:
        return []

    said = authored(java)
    if said is None:
        return ['no AUTHORED on the block, so nothing reading its model knows which way it faces']

    authored_name = said.upper()
    renderer = os.path.join(RENDERERS, block.replace('Block', 'Renderer') + '.java')
    if os.path.exists(renderer):
        text = open(renderer).read()
        if ('%s.AUTHORED' % block) not in text:
            faults.append('the renderer works the authored facing out for itself rather than reading '
                          '%s.AUTHORED, so the two can drift apart' % block)

    entity = os.path.join(BLOCKS, block.replace('Block', 'BlockEntity') + '.java')
    if os.path.exists(entity):
        mapping = anchor_turns(open(entity).read())
        if mapping is not None:
            implies = implied(mapping)
            if implies != authored_name:
                faults.append('the wire anchors are turned as though the model faced %s, and the block '
                              'says %s' % (implies or 'no facing at all', authored_name))

    return faults


def square(boxes):
    """Whether a cell's contents are the same after a quarter turn, and so cannot be turned wrongly."""
    turned = sorted(tuple(round(v, 2) for v in (16.0 - box[3], 16.0 - box[2], box[4], box[5], box[0], box[1]))
                    for box in boxes)
    return turned == sorted(boxes)


def reach():
    """How far from its machine a cell is allowed to be, read off the interface that stores the offset."""
    text = open(os.path.join(BLOCKS, 'MachineShell.java')).read()
    return (int(re.search(r'REACH_SIDE = (\d+)', text).group(1)),
            int(re.search(r'REACH_UP = (\d+)', text).group(1)))


def main():
    printing = '--java' in sys.argv
    faults = 0
    side, up = reach()
    hand_written = []
    for path in sorted(glob.glob(os.path.join(MODELS, '*', '*.obj'))):
        directory = os.path.basename(os.path.dirname(path))
        if directory in ELSEWHERE:
            continue

        name = MODEL_BLOCK.get(directory, directory)
        block = BLOCK_CLASS.get(name)
        if block is None:
            print('%-16s no block class known, skipped' % name)
            continue

        cells = claimed(pieces(body(obj_groups(path))))
        source = open(os.path.join(BLOCKS, block + '.java')).read()
        print('%-16s %d cell(s), %d box(es), modelled facing %s' % (
            os.path.basename(path), len(cells), sum(len(boxes) for boxes in cells.values()),
            authored(source) or 'nowhere in particular'))
        if printing:
            print(java(cells))

        if directory in SWEPT:
            print('    not compared: %s' % SWEPT[directory])
            continue

        for fault in facing_faults(name, block, source):
            faults += 1
            print('    %s' % fault)

        have = declared(scoped(source, MODEL_TABLE.get(directory)))
        if not have:
            outside = [cell for cell in cells if cell != (0, 0, 0)]
            if outside:
                faults += len(outside)
                print('    %d cell(s) of machine outside its own block and no table to claim them: %s'
                      % (len(outside), ', '.join(str(cell) for cell in sorted(outside, key=order))))
            else:
                hand_written.append(name)
                print('    one cell, shape declared by hand: not compared, --java prints what the model says')
                if not square(cells[(0, 0, 0)]):
                    print('        and the model is not the same after a quarter turn, so a shape that '
                          'does not turn cannot match it at every facing - see docs/model-audit.md')
            continue

        for fault in differences(cells, have):
            faults += 1
            print('    %s' % fault)

        # a cell further out than the shell block can store an offset for would throw on placement
        for cell in sorted(have, key=order):
            if abs(cell[0]) > side or abs(cell[2]) > side or not 0 <= cell[1] <= up:
                faults += 1
                print('    cell %-11s is further from its machine than REACH_SIDE %d / REACH_UP %d allow'
                      % (str(cell), side, up))

    print('\n%s' % ('the models and the tables agree, to a hundredth of a pixel'
                    if faults == 0 else '%d difference(s) between the models and the collision' % faults))
    if hand_written:
        print('%d machine(s) fit inside their own block and keep a shape written by hand: %s'
              % (len(hand_written), ', '.join(hand_written)))
    return 1 if faults else 0


if __name__ == '__main__':
    sys.exit(main())
