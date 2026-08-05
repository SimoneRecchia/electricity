#!/usr/bin/env python3
"""Proves a tracker cannot hit itself at any angle.

    python3 tools/check_pv_clearance.py

Everything that moves sweeps a body of revolution about its axis, so a fixed part is safe if it never
meets that annulus.  Assumes a full turn, which is stricter than the drives reach.
"""

import math
import os
import sys

MODELS = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')
EPS = 1.0e-4

# Where each tracker's drives pivot, matching what the renderer measures off the model's
PIVOTS = {'pv_track': 0.62, 'pv_dual': 0.7475}
# Radius of the widest part of the dual axis's pedestal
PEDESTAL_RADIUS = 0.062


def read_faces(path):
    """Every face of every object in an OBJ, keyed by object name."""
    objects, current, verts = {}, None, []
    for line in open(path):
        if line.startswith('v '):
            verts.append(tuple(float(v) for v in line.split()[1:4]))
        elif line.startswith('o '):
            # accumulated rather than assigned: a part whose faces are not all the same
            current = line.split(None, 1)[1].strip()
            objects.setdefault(current, [])
        elif line.startswith('f ') and current is not None:
            objects[current].append([verts[int(t.split('/')[0]) - 1] for t in line.split()[1:]])

    return objects


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


def check(name):
    pivot_y = PIVOTS[name]
    objects = read_faces(os.path.join(MODELS, name, name + '.obj'))
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
