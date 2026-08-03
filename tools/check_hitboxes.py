#!/usr/bin/env python3
"""Says where a machine is drawn solid and collides with nothing.

    python3 tools/check_hitboxes.py

Reads every OBJ the mod renders, works out which block cells each machine's *body* stands in, and
compares that against what the Java claims - the block's own shape plus whatever cells it fills with
{@code MachineShellBlock}. Anything a player could walk through is printed.

Why a script rather than a look
-------------------------------
Because this class of bug is invisible until you walk into it. An electric cabin is three blocks tall
and its collision was one cube at the bottom; a utility pole is six tall and had the same. Both look
perfectly solid from outside and both let a player through, and the only way to notice by eye is to try
every machine in the catalogue from every side.

What counts as a body
---------------------
Not every group. A met mast's instrument booms are two centimetres of steel holding a pyranometer out
in the air, and giving them collision would mean standing on a boom - so a group thinner than
``THIN`` in both horizontal axes is read as an arm rather than as a body and left out. The rule has to
exist, and stating it here is better than each block deciding for itself and drifting.

What counts as needing collision
--------------------------------
A cell the body fills at least ``FILL_SHARE`` of - below that it is mostly air, and a whole block of
collision there would leave a player standing half a metre from the thing they are up against. Or a cell
the body passes clean through as a post, however little of the cell that is: a mast half a block across
fills a quarter of every cell it goes through, which is under any threshold worth having, and a
six-block pole with no collision was the bug this was written for.
"""

import glob
import os
import re
import collections

MODELS = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')
BLOCKS = os.path.join('src', 'main', 'java', 'com', 'dooji', 'electricity', 'block')

# Thinner than this in both horizontal axes and a group is an arm, a boom or a bushing rather than a body.
THIN = 0.16
# How much of a cell a body has to fill before that cell is worth a block of collision.
FILL_SHARE = 0.45

# Which block class draws which model.
BLOCK_CLASS = {
    'utility_pole': 'UtilityPoleBlock',
    'power_box': 'PowerBoxBlock',
    'electric_cabin': 'ElectricCabinBlock',
    'met_station': 'MetStationBlock',
    'wind_turbine': 'WindTurbineBlock',
    'turbine_tower': 'TurbineTowerBlock',
    'pv_array': 'PvArrayBlock',
    'pv_inverter': 'PvInverterBlock',
    'pv_combiner': 'PvCombinerBlock',
}

# The inverters share one model and scale it per product, so the authored extents are the largest
# product's and the smaller ones sit inside them: checking the model as authored is the strict case.
# Machines whose cells are deliberately not checked, and the reason. Stated here rather than left as an
# absent entry, because "we decided not to" and "we forgot" have to look different.
EXEMPT = {
    'wind_turbine':
        'the model carries its own tower, which in the world is a stack of turbine_tower blocks with '
        'their own collision, and the rotor above it is a hundred metres of moving air that nothing '
        'should be able to stand on',
}


def obj_groups(path):
    """Every object in an OBJ with its own bounding box."""
    verts = []
    groups = collections.OrderedDict()
    current = 'none'
    for line in open(path):
        if line.startswith('v '):
            verts.append(tuple(float(v) for v in line.split()[1:4]))
        elif line.startswith('o '):
            current = line.split(None, 1)[1].strip()
        elif line.startswith('f '):
            box = groups.setdefault(current, [[9.0] * 3, [-9.0] * 3])
            for index in (int(f.split('/')[0]) for f in line.split()[1:]):
                point = verts[index - 1]
                for axis in range(3):
                    box[0][axis] = min(box[0][axis], point[axis])
                    box[1][axis] = max(box[1][axis], point[axis])

    return groups


def body_boxes(groups):
    """The groups that are structure, in block coordinates with the block's own corner at the origin."""
    kept = []
    for name, (lo, hi) in groups.items():
        if name.startswith('pivot_'):
            continue
        width, depth = hi[0] - lo[0], hi[2] - lo[2]
        if width < THIN and depth < THIN:
            continue

        kept.append((name, (lo[0] + 0.5, lo[1], lo[2] + 0.5), (hi[0] + 0.5, hi[1], hi[2] + 0.5)))

    return kept


def cells(boxes):
    """Which cells need something solid in them, as offsets from the machine's own block.

    Two ways a cell qualifies, and the second one is the whole reason this check works. Volume is the
    obvious test - a cabin's body fills most of nine cells - and it misses a mast completely: a pole half
    a block across fills a quarter of every cell it passes through, which is under any sensible
    threshold, and a six-block pole with no collision is exactly the bug this script was written for. So
    a body that passes clean through a cell from floor to ceiling counts however thin it is.
    """
    volume = {}
    through = set()
    for _, lo, hi in boxes:
        for x in range(int(lo[0] // 1), int((hi[0] - 1e-9) // 1) + 1):
            for y in range(int(lo[1] // 1), int((hi[1] - 1e-9) // 1) + 1):
                for z in range(int(lo[2] // 1), int((hi[2] - 1e-9) // 1) + 1):
                    across = min(hi[0], x + 1) - max(lo[0], x)
                    along = min(hi[2], z + 1) - max(lo[2], z)
                    height = min(hi[1], y + 1) - max(lo[1], y)
                    if across <= 0 or along <= 0 or height <= 0:
                        continue

                    key = (x, y, z)
                    volume[key] = volume.get(key, 0.0) + across * along * height
                    # a mast, rather than a door handle poking five centimetres into the cell next door:
                    # the part in this cell has to be a post in both axes as well as full height
                    if height >= 0.9 and min(across, along) >= THIN:
                        through.add(key)

    return {cell: share for cell, share in volume.items() if share >= FILL_SHARE or cell in through}


def claimed(java):
    """The cells a block says it fills: its own, plus every {@code new Cell(...)} it lists."""
    found = {(0, 0, 0)}
    for match in re.finditer(r'new Cell\((-?\d+),\s*(-?\d+),\s*(-?\d+)', java):
        found.add(tuple(int(g) for g in match.groups()))

    return found


def main():
    problems = 0
    for path in sorted(glob.glob(os.path.join(MODELS, '*', '*.obj'))):
        directory = os.path.basename(os.path.dirname(path))
        name = {'electric_cab': 'electric_cabin', 'met_mast': 'met_station'}.get(directory, directory)
        name = {'pv_flat': 'pv_array', 'pv_tilt': 'pv_array', 'pv_track': 'pv_array',
                'pv_dual': 'pv_array'}.get(name, name)
        if name in EXEMPT:
            print('%-16s not checked: %s' % (os.path.basename(path), EXEMPT[name]))
            continue

        block = BLOCK_CLASS.get(name)
        if block is None:
            print('%-16s no block class known, skipped' % name)
            continue

        java = open(os.path.join(BLOCKS, block + '.java')).read()
        boxes = body_boxes(obj_groups(path))
        occupied = cells(boxes)
        have = claimed(java)
        missing = sorted(cell for cell in occupied if cell not in have)

        tallest = max(hi[1] for _, _, hi in boxes)
        print('%-16s body %.2f tall, %d cell(s) mostly filled, %d claimed'
              % (os.path.basename(path), tallest, len(occupied), len(have)))
        for cell in missing:
            problems += 1
            print('    UNCOVERED cell %-12s %.0f%% of it is machine, and nothing collides there'
                  % (str(cell), 100.0 * occupied[cell]))

    print('\n%s' % ('every cell a body fills has something solid in it'
                    if problems == 0 else '%d cell(s) a player can walk through' % problems))


if __name__ == '__main__':
    main()
