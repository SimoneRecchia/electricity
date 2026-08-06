#!/usr/bin/env python3
"""Proves a tracker cannot hit itself at any angle.

    python3 tools/check_pv_clearance.py

Everything that moves sweeps a body of revolution about its axis, so a fixed part is safe if it never
meets that annulus.  Assumes a full turn, which is stricter than the drives reach.
"""

import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import objlib                                                                    # noqa: E402

MODELS = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')
ARRAY_BLOCK = os.path.join('src', 'main', 'java', 'com', 'dooji', 'electricity', 'block',
                           'PvArrayBlock.java')
EPS = 1.0e-4

# Which pivot marker each tracker turns its row about.  Read out of the model, not restated here: a copy
# of the figure went stale the moment the torque tube moved and this checker then measured the wrong axis.
PIVOTS = {'pv_track': 'pivot_tube', 'pv_dual': 'pivot_elevation'}
# Radius of the widest part of the dual axis's pedestal
PEDESTAL_RADIUS = 0.062


def read_faces(path):
    """Every face of every object in an OBJ, keyed by object name.  Merged, not replaced - see objlib."""
    return objlib.polygons(objlib.read(path))


def span(face, axis):
    values = [point[axis] for point in face]
    return min(values), max(values)


def annulus(face, plane, centre):
    """Nearest and furthest a face gets from a point, measured in one of the planes."""
    a, b = plane
    lo_a, hi_a = span(face, a)
    lo_b, hi_b = span(face, b)
    ca, cb = centre

    near_a = 0.0 if lo_a <= ca <= hi_a else min(abs(lo_a - ca), abs(hi_a - ca))
    near_b = 0.0 if lo_b <= cb <= hi_b else min(abs(lo_b - cb), abs(hi_b - cb))
    near = math.hypot(near_a, near_b)
    far = max(math.hypot(u - ca, v - cb) for u in (lo_a, hi_a) for v in (lo_b, hi_b))
    return near, far


def clears(fixed, moving, along, plane, centre):
    """Whether a fixed face can never be reached by a moving one turning about an axis."""
    fixed_along, moving_along = span(fixed, along), span(moving, along)
    if min(fixed_along[1], moving_along[1]) - max(fixed_along[0], moving_along[0]) <= EPS:
        return True

    fixed_near, fixed_far = annulus(fixed, plane, centre)
    moving_near, moving_far = annulus(moving, plane, centre)
    return fixed_far < moving_near - EPS or fixed_near > moving_far + EPS


def worst_overlap(fixed_faces, moving_faces, along, plane, centre):
    """How deeply the two families interfere, or None when they cannot."""
    depth = None
    for fixed in fixed_faces:
        for moving in moving_faces:
            if clears(fixed, moving, along, plane, centre):
                continue

            fixed_near, fixed_far = annulus(fixed, plane, centre)
            moving_near, moving_far = annulus(moving, plane, centre)
            overlap = min(fixed_far, moving_far) - max(fixed_near, moving_near)
            depth = overlap if depth is None else max(depth, overlap)

    return depth


def reaches_vertical_axis(faces, pivot_y):
    """Closest a group turning about z, then about y, can bring itself to the vertical axis."""
    closest = min(min(abs(point[2]) for point in face) for face in faces)
    radius = max(annulus(face, (0, 1), (0.0, pivot_y))[1] for face in faces)
    return closest, (pivot_y - radius, pivot_y + radius)


def pivot_height(objects, group):
    """Where a ``pivot_*`` marker sits: the renderer measures the same hinge off the same group."""
    faces = objects[group]
    return sum(point[1] for face in faces for point in face) / sum(len(face) for face in faces)


# Which table in PvArrayBlock holds each tracker's collision.  Its floor is the one figure in the Java that
# no other check watches: too high and a player walks in under the row it is meant to stop them at.
SWEPT = {'pv_track': 'TRACK_CELLS', 'pv_dual': 'DUAL_CELLS'}


def declared_floor(name):
    """The lowest y the tracker's collision starts at in PvArrayBlock, in blocks.

    The whole table, not one named shape: the two are a single box from the floor now, and a box that
    *contains* the sweep is the proposition - it used to have to start exactly where the sweep reached,
    which only held while the pier had a box of its own underneath.
    """
    source = open(ARRAY_BLOCK).read()
    table = re.search(r'%s = List\.of\((.*?)\n\n' % SWEPT[name], source, re.S)
    if table is None:
        return None
    floors = [float(box.split(',')[1]) for box in re.findall(r'Block\.box\(([^)]*)\)', table.group(1))]
    return min(floors) / 16.0 if floors else None


def swept_floor(moving, pivot_y):
    """How low the turning parts can ever reach: the pivot less the furthest any of them is from it."""
    radius = max(annulus(face, (0, 1), (0.0, pivot_y))[1]
                 for group, faces in moving.items() if not group.startswith('rotate_azimuth')
                 for face in faces)
    return pivot_y - radius


def check(name):
    objects = read_faces(os.path.join(MODELS, name, name + '.obj'))
    pivot_y = pivot_height(objects, PIVOTS[name])
    fixed = {k: v for k, v in objects.items() if not k.startswith('rotate_')}
    moving = {k: v for k, v in objects.items() if k.startswith('rotate_')}

    print('=== %s, pivot y=%.4f ===' % (name, pivot_y))
    problems = []

    # everything that turns about the z axis, against every fixed part
    for moving_name, moving_faces in sorted(moving.items()):
        if moving_name.startswith('rotate_azimuth'):
            continue

        for fixed_name, fixed_faces in sorted(fixed.items()):
            depth = worst_overlap(fixed_faces, moving_faces, 2, (0, 1), (0.0, pivot_y))
            if depth is None:
                continue
            if moving_name.startswith('rotate_tube'):
                print('  bearing  %-24s turns inside %-16s sharing %.4f' % (moving_name, fixed_name, depth))
                continue

            problems.append('%s sweeps through %s by %.4f' % (moving_name, fixed_name, depth))

    # the azimuth collar turns about the vertical
    # in the horizontal plane
    for moving_name, moving_faces in sorted(moving.items()):
        if not moving_name.startswith('rotate_azimuth'):
            continue

        for fixed_name, fixed_faces in sorted(fixed.items()):
            depth = worst_overlap(fixed_faces, moving_faces, 1, (0, 2), (0.0, 0.0))
            if depth is None:
                continue

            print('  bearing  %-24s turns on   %-16s sharing %.4f' % (moving_name, fixed_name, depth))

    # and the elevation frame, which turns about z and is then carried round by the azimuth
    elevation = [f for k, v in moving.items() if k.startswith('rotate_elevation') for f in v]
    if elevation and 'pedestal' in fixed:
        closest, (low, high) = reaches_vertical_axis(elevation, pivot_y)
        pedestal_y = (min(min(p[1] for p in f) for f in fixed['pedestal']),
                      max(max(p[1] for p in f) for f in fixed['pedestal']))
        overlaps_y = min(high, pedestal_y[1]) - max(low, pedestal_y[0]) > EPS
        print('  frame gets within %.3f of the vertical axis against a pedestal of %.3f%s'
              % (closest, PEDESTAL_RADIUS, ', and their heights overlap' if overlaps_y else ''))
        if overlaps_y and closest < PEDESTAL_RADIUS - EPS:
            problems.append('the elevation frame sweeps into the pedestal')

    floor, declared = swept_floor(moving, pivot_y), declared_floor(name)
    print('  swept    the turning parts reach down to %.2f px, and %s starts at %s'
          % (floor * 16.0, SWEPT[name], '%.2f' % (declared * 16.0) if declared is not None else 'none'))
    if declared is None or declared > floor + 0.01 / 16.0:
        problems.append('%s starts above where the sweep reaches, so a player gets in under the row'
                        % SWEPT[name])

    for problem in problems:
        print('  CLASH    %s' % problem)

    return not problems


def main():
    if all(check(name) for name in PIVOTS):
        print('\nno clash possible at any angle')
        return 0

    print('\nCLASHES FOUND')
    return 1


if __name__ == '__main__':
    sys.exit(main())
