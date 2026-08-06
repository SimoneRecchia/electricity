#!/usr/bin/env python3
"""Collision cut from the model, against what the block declares.

    python3 tools/check_hitboxes.py           # compare
    python3 tools/check_hitboxes.py --java    # print the tables, ready to paste

The model is the authority: every group a machine draws, clipped to each cell it reaches into.

Tables to know about.  MOVING is what gets no collision (markers, moving parts, cable stubs).  MIN_OWN
and MIN_CLAIM are the thresholds - a box in the machine's own cell is free, claiming the cell next door
costs the player that cell.  SWEPT and ELSEWHERE are what is not compared, and why.  MODEL_TABLE scopes
the search when a block derives other shapes from one table.  RECTANGLES is what a machine's collision is
made of.

There is no table for a machine whose *collision* changes with its state, and that is a decision: the
inverter used to need one, to prove its direct-current compartment could be taken back out of the shape,
and now that the cabinet is one rectangle the compartment is 0.6 px of its front face.  A machine that
grows a part a player can walk into would need it again.
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
# 'flag_' is a breaker's position indicator: two plates in one place, one drawn per state, and both far
# under MIN_OWN anyway.
MOVING = ('pivot_', 'rotate_', 'harness', 'entry', 'flag_')

# Everything below is in pixels
MIN_OWN = 0.25
MIN_CLAIM = 2.0
# A gap this small is a seam in the model rather than a step, and the two boxes are merged.
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
    'tx_machine': 'TransformerBlock',
    'tx_substation': 'TransformerBlock',
    'mv_disconnector': 'SwitchgearBlock',
    'mv_breaker': 'SwitchgearBlock',
}

# One block class, several models: which constant in its file holds the table cut from this one.
MODEL_TABLE = {
    # the inverter declares one table and derives its two smaller sizes by scaling it, so the derived
    'pv_inverter': 'CELLS',
    # and the kiosk, for the same reason: its wall-mounted shape is derived from CELLS in code
    'power_box': 'CELLS',
    'pv_flat': 'FLAT_CELLS',
    'pv_tilt': 'TILT_CELLS',
    'pv_track': 'TRACK_CELLS',
    'pv_dual': 'DUAL_CELLS',
    'electric_cab': 'CELLS',
    # one block class, two machines: a table each, told apart by name
    'tx_machine': 'MACHINE_CELLS',
    'tx_substation': 'SUBSTATION_CELLS',
    # one block class, two switches, and what the two collide as is not the same shape
    'mv_disconnector': 'DISCONNECTOR_CELLS',
    'mv_breaker': 'BREAKER_CELLS',
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

# Models whose collision is a volume rather than a shape, and why.
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


# What a machine's collision is *made of*: one rectangle per part of the object, each the union of the model
# groups it covers.  A hitbox is what a player points at and walks into, not a drawing of the machine - an
# inverter is a sheet-steel cabinet, and a cabinet is a rectangle.  Cut from every group instead, and sliced
# wherever it sloped, a fixed-tilt array came out as twenty-three boxes of staircase.
#
# Every group a model draws has to appear here exactly once, or this file fails: a part added to a model is
# then a decision about its collision rather than a silent extra box.
# A porcelain fitting is the exception to the merging: **every insulator, bushing and arrester gets a
# rectangle of its own**, cut to it. It is the part of a machine a player aims at - a wire is hung on one
# by pointing at it - and swept into the roof rectangle beside its neighbours it became a slab of air
# with the fittings somewhere inside. That is also why the model splits them one group apiece.
RECTANGLES = {
    'pv_inverter': (
        # the cabinet, its plinth and everything let into its faces are one box; the roof fittings the
        # only thing that stands clear of it
        ('cabinet', ('plinth', 'cabinet', 'hood', 'door', 'display', 'grille', 'section', 'blank',
                     'fan_guard')),
        ('roof', ('hardware',)),
        ('insulator', ('insulator',)),
    ),
    # The three the object has: what it stands on, what holds it up, and the box itself.  Merging the post
    # into the enclosure would put a wall of air beside a column a player walks past.
    'pv_combiner': (
        ('base', ('base',)),
        ('post', ('post',)),
        ('enclosure', ('enclosure', 'door', 'glands')),
    ),
    'power_box': (
        ('cabinet', ('plinth', 'body', 'hood', 'door', 'vent', 'conduit')),
        ('top', ('hardware',)),
        ('insulator', ('insulator',)),
    ),
    # One box each: a ballasted table is 2.5 px tall in all and a fixed rack 8.5, so there is no gap under
    # either that a player could have been in.  Two boxes bought nothing but a second box.
    'pv_flat': (
        ('table', ('ballast', 'frame', 'modules')),
    ),
    'pv_tilt': (
        ('rack', ('piers', 'purlin_0', 'purlin_1', 'rails', 'modules')),
    ),
    'met_mast': (
        ('mast', ('mast', 'logger', 'anemometer')),
        ('boom', ('boom', 'pyranometer', 'diffuse', 'albedometer', 'shield', 'snow')),
    ),
    'tx_machine': (
        ('tank', ('plinth', 'tank', 'cover', 'cablebox', 'nameplate', 'fittings', 'earth')),
        ('bushing_1', ('bushing_1', 'flange_1')),
        ('bushing_2', ('bushing_2', 'flange_2')),
        ('bushing_3', ('bushing_3', 'flange_3')),
    ),
    'tx_substation': (
        ('bund', ('bund', 'rails')),
        ('tank', ('tank', 'cover', 'radiator', 'tapchanger', 'nameplate')),
        ('top', ('conservator', 'relay')),
        ('bushing_1', ('bushing_1', 'flange_1')),
        ('bushing_2', ('bushing_2', 'flange_2')),
        ('bushing_3', ('bushing_3', 'flange_3')),
        ('bushing_4', ('bushing_4', 'flange_4')),
        ('bushing_5', ('bushing_5', 'flange_5')),
        ('bushing_6', ('bushing_6', 'flange_6')),
    ),
    'electric_cab': (
        ('plinth', ('plinth',)),
        ('body', ('body', 'door', 'vent', 'signage')),
        ('roof', ('roof', 'hardware')),
        # a column apiece: the two bushings and the three arresters are five separate porcelain stacks on
        # the roof, and one box round the lot of them is a wall across it
        ('insulator_input', ('insulator_input',)),
        ('insulator_output', ('insulator_output',)),
        ('arrester_1', ('arrester_1',)),
        ('arrester_2', ('arrester_2',)),
        ('arrester_3', ('arrester_3',)),
    ),
    # A disconnector is a frame, six posts and the shaft that gangs them.  The shaft is its own rectangle
    # because it is 0.4 wide and 0.03 thick over the top of the posts: swept into the frame it would be a
    # solid block from the ground to the blades.
    'mv_disconnector': (
        ('frame', ('pad', 'frame', 'earth')),
        ('shaft', ('shaft',)),
        ('insulator_1', ('insulator_1',)),
        ('insulator_2', ('insulator_2',)),
        ('insulator_3', ('insulator_3',)),
        ('insulator_4', ('insulator_4',)),
        ('insulator_5', ('insulator_5',)),
        ('insulator_6', ('insulator_6',)),
    ),
    # And a breaker is a box with three poles standing on it, which is one rectangle: the poles cover nearly
    # all of the box's own footprint, so a second box would be air.  The bushings stick out past it.
    'mv_breaker': (
        ('cabinet', ('pad', 'cabinet', 'door', 'earth', 'pole_1', 'pole_2', 'pole_3')),
        ('insulator_1', ('insulator_1',)),
        ('insulator_2', ('insulator_2',)),
        ('insulator_3', ('insulator_3',)),
        ('insulator_4', ('insulator_4',)),
        ('insulator_5', ('insulator_5',)),
        ('insulator_6', ('insulator_6',)),
    ),
    'utility_pole': (
        # the steps are what a player climbs, and they are within a pixel of the shaft either side of it
        ('shaft', ('shaft', 'earth', 'plate',
                   'step_0', 'step_1', 'step_2', 'step_3', 'step_4', 'step_5', 'step_6')),
        ('arm_lower', ('arm_lower',)),
        ('arm_upper', ('arm_upper',)),
        # the pin goes with the insulator it carries: the spindle is what stands it off the arm
        ('insulator_1', ('insulator_1', 'pin_1')),
        ('insulator_2', ('insulator_2', 'pin_2')),
        ('insulator_3', ('insulator_3', 'pin_3')),
        ('insulator_4', ('insulator_4', 'pin_4')),
        ('insulator_5', ('insulator_5', 'pin_5')),
        ('insulator_6', ('insulator_6', 'pin_6')),
        ('insulator_7', ('insulator_7', 'pin_7')),
        ('insulator_8', ('insulator_8', 'pin_8')),
    ),
}


# Models whose tables are printed somewhere else, so this file would only disagree with itself.
ELSEWHERE = {
    'dc_string_cable': 'printed by tools/gen_cable_models.py --java, per connection pattern',
    # A lattice is mostly air. Cut from every group it draws, its diagonals sweep 308 of the 400 cells in
    # its bounding volume - a tower you cannot walk into and three hundred shell blocks a placement. So
    # gen_tower_models authors the collision from the legs, the crossarms, the peak and the footings, and
    # prints it: 78 cells, and between the braces you can walk.
    'lattice_suspension': 'printed by tools/gen_tower_models.py --java, authored from the structure',
    'lattice_tension': 'printed by tools/gen_tower_models.py --java, authored from the structure',
    'lattice_terminal': 'printed by tools/gen_tower_models.py --java, authored from the structure',
    # Ground-laid conductor, for the same reason the string cable is here: a piece per connection pattern,
    # so the tables are keyed by pattern and printed by the generator that draws them - which also proves
    # GroundConductorBlock still declares them.
    'abc_conductor_run': 'printed by tools/gen_conductor_models.py --java, per connection pattern',
    'mv_conductor_run': 'printed by tools/gen_conductor_models.py --java, per connection pattern',
    'hv_conductor_run': 'printed by tools/gen_conductor_models.py --java, per connection pattern',
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


def body(groups, model):
    """The object's parts as one box each, in pixels from the block's own corner.

    RECTANGLES says which model groups make up each part; a model not named there keeps a box per group,
    which is what the swept ones and the towers want since nothing compares theirs anyway.
    """
    still = {name: faces for name, faces in groups.items() if not name.startswith(MOVING)}
    plan = RECTANGLES.get(model) or tuple((name, (name,)) for name in still)

    named = [g for _, members in plan for g in members]
    missing = [g for g in named if g not in still]
    loose = [g for g in still if g not in named]
    if missing or loose:
        raise SystemExit(
            'check_hitboxes: RECTANGLES for %s names %s, which the model does not draw%s'
            % (model, ', '.join(missing) or 'nothing',
               ', and leaves %s in no rectangle' % ', '.join(loose) if loose else '')
            if missing else
            'check_hitboxes: RECTANGLES for %s leaves %s in no rectangle - decide which box it belongs to'
            % (model, ', '.join(loose)))

    kept = []
    for name, members in plan:
        lo, hi = bounds([face for g in members for face in still[g]])
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
    """The angle the geometry is turned by to face this way"""
    return ((FACING_ORDER[authored] - FACING_ORDER[facing]) % 4) * 90


def implied(mapping):
    """The facing a table of angles says the geometry was modelled at"""
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
    """Everything that has to agree about which way a machine's geometry was modelled, and whether it does."""
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

        groups = obj_groups(path)
        cells = claimed(pieces(body(groups, directory)))
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
