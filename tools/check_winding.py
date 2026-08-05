#!/usr/bin/env python3
"""Proves no face is wound against its own normal, because the game culls by winding.

    python3 tools/check_winding.py

Minecraft's chunk renderer draws a quad only if it is wound anticlockwise as seen from the front.  It does
not read the ``vn`` a model states.  So a quad whose winding disagrees with its normal is not a shading
fault, it is a **hole**: you see straight through it to whatever is behind, because the far inside wall is
back-facing too.

Half of every box in this mod was wound inside out.  That is what the string cable looked like in the game
for four rounds of reports - a junction box with no lid, cleats that were open channels, glands that were
ribbed cups with sand between them - while every render here showed it solid, because render_blocks.py did
not cull.  Both are fixed: modellib.Mesh.quad rewinds a quad to agree with its normal, render_blocks.py
culls by winding the way a GPU does, and this fails the build if either ever regresses.
"""

import math
import os
import sys

MODELS = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')

# The turbine's own model came with the mod and is not generated - see CLAUDE.md section 6.  Its normals
# are what they are, and the mod's own renderer draws it, which does not cull.
INHERITED = {'wind_turbine.obj'}


def faces(path):
    """Every face as (group, points, stated normal)."""
    verts, norms, group = [], [], '?'
    for line in open(path):
        parts = line.split()
        if not parts:
            continue

        if parts[0] == 'v':
            verts.append(tuple(float(v) for v in parts[1:4]))
        elif parts[0] == 'vn':
            norms.append(tuple(float(v) for v in parts[1:4]))
        elif parts[0] == 'o':
            group = parts[1]
        elif parts[0] == 'f':
            tokens = [t.split('/') for t in parts[1:]]
            if len(tokens) < 3 or len(tokens[0]) < 3 or not tokens[0][2]:
                continue

            yield group, [verts[int(t[0]) - 1] for t in tokens], norms[int(tokens[0][2]) - 1]


def backwards(points, normal):
    """Whether the winding faces the other way from the normal the model states."""
    a = tuple(points[1][i] - points[0][i] for i in range(3))
    b = tuple(points[2][i] - points[0][i] for i in range(3))
    wound = (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    length = math.sqrt(sum(c * c for c in wound))
    if length < 1e-12:
        return False

    return sum(wound[i] / length * normal[i] for i in range(3)) < -1e-6


def main():
    problems, checked = [], 0
    for folder, _, names in os.walk(MODELS):
        for name in sorted(names):
            if not name.endswith('.obj') or name in INHERITED:
                continue

            counts, total = {}, 0
            for group, points, normal in faces(os.path.join(folder, name)):
                total += 1
                if backwards(points, normal):
                    counts[group] = counts.get(group, 0) + 1
            checked += 1
            for group, count in sorted(counts.items()):
                problems.append('%s: %s has %d of its faces wound against its normal, so the game '
                                'culls them and they are holes' % (name, group, count))

    print('%d models checked' % checked)
    for problem in problems:
        print('  %s' % problem)
    if problems:
        print('\n%d problem(s)' % len(problems))
        return 1

    print('every face is wound the way its normal points')
    return 0


if __name__ == '__main__':
    sys.exit(main())
