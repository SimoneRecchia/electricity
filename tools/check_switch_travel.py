#!/usr/bin/env python3
"""A disconnector's blade turns on its hinge, grips its contact, and touches nothing else at any angle.

    python3 tools/check_switch_travel.py

The blade is the one part of this mod that travels through its own machine, and the travel is what a player
watches.  So this turns it through every degree the renderer will, marks the voxels its surface touches at an
eighth of a pixel, and asks what else is there.  Two things are meant to be there: the hinge it turns on and
the shaft that gangs it, at every angle, and the fixed contact it grips, at the closed end only.

The angle is read out of SwitchgearRenderer, and the hinge out of the model's own pivot marker, for the same
reason check_pv_clearance reads the torque tube's: a second copy of either figure goes stale the moment one
of them moves.
"""

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from check_row_cabling import read_groups, voxels                                # noqa: E402

MODELS = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')
RENDERER = os.path.join('src', 'main', 'java', 'com', 'dooji', 'electricity', 'client', 'render',
                        'block', 'SwitchgearRenderer.java')

# Which models have something that travels.  A breaker's contacts are inside a sealed bottle, so it has
# nothing here at all - which is the difference between the two objects.
TRAVELS = ('mv_disconnector',)

# How many positions along the travel to test, and how far in from the closed end the blade is still in its
# contact.  It withdraws *along* the contact for the first few degrees, which is what a real one does: the
# contact stands 0.042 above the palm and the blade has to rise off the whole of it before it is clear, which
# on a blade 0.31 from its hinge is seven degrees.
STEPS = 28
GRIPPING = 8.0

# What the blade is bolted to and turns on, so it shares voxels with it at every angle by construction.
HINGED = ('shaft', 'insulator_4', 'insulator_5', 'insulator_6')
# And the fixed contact it closes onto, which it may only be touching while it is closed.
GRIPPED = ('insulator_1', 'insulator_2', 'insulator_3')


def open_degrees():
    """How far the renderer swings it."""
    match = re.search(r'OPEN_DEGREES = ([0-9.]+)f', open(RENDERER).read())
    if match is None:
        raise SystemExit('check_switch_travel: SwitchgearRenderer no longer declares OPEN_DEGREES')

    return float(match.group(1))


def hinge(groups):
    """Where the pivot marker is, which is the axis the renderer measures its own turn from."""
    points = [point for key, triangles in groups.items() if key[0] == 'pivot_blade'
              for triangle in triangles for point in triangle]
    if not points:
        raise SystemExit('check_switch_travel: the model has no pivot_blade marker')

    return (sum(p[1] for p in points) / len(points), sum(p[2] for p in points) / len(points))


def turned(triangles, axis, degrees):
    """Every triangle turned about the hinge, which runs along x."""
    import math

    angle = math.radians(degrees)
    cos, sin = math.cos(angle), math.sin(angle)
    hinge_y, hinge_z = axis
    out = []
    for triangle in triangles:
        moved = []
        for x, y, z in triangle:
            dy, dz = y - hinge_y, z - hinge_z
            moved.append((x, hinge_y + dy * cos - dz * sin, hinge_z + dy * sin + dz * cos))
        out.append(tuple(moved))

    return out


def check(name):
    groups = read_groups(os.path.join(MODELS, name, name + '.obj'))
    axis = hinge(groups)
    limit = open_degrees()

    moving = {key: triangles for key, triangles in groups.items() if key[0].startswith('rotate_')}
    fixed = {key: voxels(triangles) for key, triangles in groups.items()
             if not key[0].startswith('rotate_') and not key[0].startswith('pivot_')}

    problems = []
    for step in range(STEPS + 1):
        degrees = limit * step / STEPS
        swept = set()
        for triangles in moving.values():
            swept |= voxels(turned(triangles, axis, degrees))

        for key, marks in fixed.items():
            shared = len(swept & marks)
            if not shared:
                continue
            if any(key[0] == part for part in HINGED):
                continue
            if degrees <= GRIPPING and any(key[0] == part for part in GRIPPED):
                continue

            problems.append('at %.0f degrees the blade shares %d voxel(s) with %s:%s'
                            % (degrees, shared, key[0], key[1]))

    print('%-16s %d positions over %.0f degrees, hinge at y=%.3f z=%.3f'
          % (name, STEPS + 1, limit, axis[0], axis[1]))
    for problem in sorted(set(problems)):
        print('    FOULS   %s' % problem)

    return len(set(problems))


def main():
    problems = sum(check(name) for name in TRAVELS)
    if problems:
        print('\n%d position(s) where the travel fouls something' % problems)
        return 1

    print('\nthe blade grips its contact and clears everything else, the whole way open')
    return 0


if __name__ == '__main__':
    sys.exit(main())
