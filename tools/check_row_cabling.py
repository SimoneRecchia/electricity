#!/usr/bin/env python3
"""Nothing a row's cabling draws passes through anything else the row draws.

    python3 tools/check_row_cabling.py
    python3 tools/check_row_cabling.py --worst      # and where the residue is

The models say where the cable is; PvArrayRenderer says which pieces of it are drawn together.  So this
composes every state the renderer can put a row in - harnessed or not, and what is at each end of its axis -
and measures the geometry of that state against itself, by marking the voxels each group's surface touches
at 1/128 of a block and counting the ones two groups both claim.

Two collisions are allowed and named: a cable inside the moulded joint that ends it, and a cable inside the
gland that seals it.  Everything else is a fault - it is what "the cables collide and do strange things"
looks like from the outside.

DRAWN mirrors PvArrayRenderer.drawn.  If the two ever disagree this proves nothing, so it is written the
same shape as the Java and the Java says so.
"""

import collections
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_pv_models as cabling                                                 # noqa: E402

MODELS = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')
RENDERER = os.path.join('src', 'main', 'java', 'com', 'dooji', 'electricity', 'client', 'render',
                        'block', 'PvArrayRenderer.java')

# A hundred and twenty-eighth of a block, which is an eighth of a pixel: fine enough that two cables 0.1 px
# apart are not called a collision, coarse enough to rasterise every state of four models.
GRID = 128

MOUNTINGS = ('pv_flat', 'pv_tilt', 'pv_track', 'pv_dual')
TRACKED = ('pv_track', 'pv_dual')

# What a group is made of, so a cable through a cable and a cable through a rack read differently.
CABLE = ('core', 'plug_plus', 'plug_minus')

# Where two parts are meant to share a voxel: a moulded joint and a gland both exist to swallow the end of
# the cable they hold, so the cable is inside them by design.
SWALLOWS = ('jbox', 'jbox_side', 'gland')

# The spine and the piece at each end of it are one cable, and they overlap on purpose - butted exactly, the
# two end caps would be coplanar discs fighting each other.  So the overlap is allowed, but only where the
# joint is: anywhere else along the run they would be two cables through each other.
CONTINUES = ('harness_lead_north', 'harness_lead_south', 'harness_entry_north', 'harness_entry_south',
             'harness_input')


def read_groups(path):
    """Every object's triangles, keyed by object name, the way the mod's own loader groups them.

    Split by object and then by material and merged, not replaced - RenderixSplitter's rule, not Forge's.
    A machine model is read by the mod's renderer, so a repeated name adds faces rather than dropping them.
    """
    verts = []
    groups = collections.OrderedDict()
    name, material = 'root', 'none'
    for line in open(path):
        parts = line.split()
        if not parts:
            continue
        if parts[0] == 'v':
            verts.append(tuple(float(v) for v in parts[1:4]))
        elif parts[0] == 'o':
            name = parts[1]
        elif parts[0] == 'usemtl':
            material = parts[1]
        elif parts[0] == 'f':
            points = [verts[int(t.split('/')[0]) - 1] for t in parts[1:]]
            for i in range(1, len(points) - 1):
                groups.setdefault((name, material), []).append((points[0], points[i], points[i + 1]))

    return groups


def voxels(triangles):
    """Every voxel the surface of these triangles touches, at GRID to the block."""
    marked = set()
    for a, b, c in triangles:
        # samples enough that no step along either edge exceeds one voxel
        longest = max(max(abs(b[i] - a[i]), abs(c[i] - a[i])) for i in range(3))
        steps = max(1, int(longest * GRID) + 1)
        for u in range(steps + 1):
            for v in range(steps + 1 - u):
                s, t = u / steps, v / steps
                point = tuple(a[i] + (b[i] - a[i]) * s + (c[i] - a[i]) * t for i in range(3))
                marked.add(tuple(int(point[i] * GRID) for i in range(3)))

    return marked


def drawn(group, harnessed, fed_north, fed_south, mid_north, mid_south):
    """Which groups the renderer draws, mirroring PvArrayRenderer.drawn."""
    if not group.startswith('harness'):
        return True
    if group.startswith('harness_plug_north'):
        return not harnessed and fed_north
    if group.startswith('harness_plug_south'):
        return not harnessed and fed_south
    if group.startswith('harness_entry_north'):
        return mid_north
    if group.startswith('harness_entry_south'):
        return harnessed and mid_south
    if group.startswith('harness_input'):
        return fed_north and not mid_north
    if group.startswith('harness_lead_north'):
        return harnessed and not mid_north and not fed_north
    if group.startswith('harness_lead_south'):
        return harnessed and not mid_south

    return harnessed


def mirrors_renderer():
    """That the Java still decides it the same way, clause for clause."""
    source = ' '.join(open(RENDERER).read().split())
    wanted = (
        ('harness_plug_north', 'return !harnessed && ends.fedNorth();'),
        ('harness_plug_south', 'return !harnessed && ends.fedSouth();'),
        ('harness_entry_north', 'return ends.midNorth();'),
        ('harness_entry_south', 'return harnessed && ends.midSouth();'),
        ('harness_input', 'return ends.fedNorth() && !ends.midNorth();'),
        ('harness_lead_north', 'return harnessed && !ends.midNorth() && !ends.fedNorth();'),
        ('harness_lead_south', 'return harnessed && !ends.midSouth();'),
    )
    missing = []
    for group, clause in wanted:
        if 'groupName.startsWith("%s")) %s' % (group, clause) not in source:
            missing.append(group)

    return missing


def states(tracked):
    """Every state the renderer can put a row in, as (label, predicate arguments)."""
    out = []
    for harnessed in (False, True):
        for fed_north in (False, True):
            for fed_south in (False, True):
                for mid_north in (False, True):
                    for mid_south in (False, True):
                        if tracked and (mid_north or mid_south):
                            # a tracked row has no entry groups: everything meets it in the middle already
                            continue
                        label = '%s%s%s%s%s' % ('H' if harnessed else '-', 'N' if fed_north else '-',
                                                'S' if fed_south else '-', 'n' if mid_north else '-',
                                                's' if mid_south else '-')
                        out.append((label, (harnessed, fed_north, fed_south, mid_north, mid_south)))

    return out


def joined(first, second):
    """Whether these two groups are one cable, the spine and the piece continuing it."""
    return 'harness' in (first, second) and (first in CONTINUES or second in CONTINUES)


def at_the_joint(voxel):
    """Whether a voxel is inside the station where the spine hands over, and not out along the run."""
    reach = (cabling.JOINT + 3.0 * cabling.SOCKET_LAP) * GRID
    inner = (cabling.JOINT - 3.0 * cabling.SOCKET_LAP) * GRID
    return inner <= abs(voxel[2] + 0.5) <= reach


def kind(material):
    if material in CABLE:
        return 'cable'
    if material in SWALLOWS:
        return 'fitting'

    return 'structure'


def check(name, verbose=False):
    groups = read_groups(os.path.join(MODELS, name, name + '.obj'))
    marks = {key: voxels(triangles) for key, triangles in groups.items()}
    worst = collections.Counter()
    problems = 0

    for label, args in states(name in TRACKED):
        live = [key for key in groups if drawn(key[0], *args)]
        for i, first in enumerate(live):
            for second in live[i + 1:]:
                if first[0] == second[0]:
                    # one group's own materials meet along the part they share
                    continue
                if kind(first[1]) == 'fitting' or kind(second[1]) == 'fitting':
                    continue
                if kind(first[1]) != 'cable' and kind(second[1]) != 'cable':
                    continue

                shared = marks[first] & marks[second]
                if joined(first[0], second[0]):
                    shared = {voxel for voxel in shared if not at_the_joint(voxel)}
                shared = len(shared)
                if shared:
                    problems += shared
                    worst['%s x %s' % (first[0], second[0])] = max(
                        worst['%s x %s' % (first[0], second[0])], shared)

    print('%-9s %3d states, %6d voxel(s) of cable through something' % (name, len(states(name in TRACKED)),
                                                                       problems))
    if verbose:
        for pair, count in worst.most_common(6):
            print('    %-56s %5d' % (pair, count))

    return problems


def main():
    verbose = '--worst' in sys.argv
    missing = mirrors_renderer()
    for group in missing:
        print('    STALE   PvArrayRenderer no longer decides %s the way this file does' % group)

    total = sum(check(name, verbose) for name in MOUNTINGS)
    if missing or total:
        print('\n%d voxel(s) of collision in the rows\' own cabling' % total)
        return 1

    print('\nnothing a row draws passes through anything else it draws, in any state')
    return 0


if __name__ == '__main__':
    sys.exit(main())
