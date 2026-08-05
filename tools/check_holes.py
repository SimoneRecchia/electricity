#!/usr/bin/env python3
"""Nowhere a player stands can they see the inside of a cable run.

    python3 tools/check_holes.py            # every connection pattern, both gauges
    python3 tools/check_holes.py --write     # and a PNG per fault, marking it

Each pattern is composed the way the blockstate composes it - the middle, its arms, and the neighbours
whose own pieces close the arms - then rasterised twice from eye height all round: once culled by winding
the way RenderType.solid culls, once not culled at all.  A pixel the second pass covers and the first does
not has no front face on it, so what the player sees there is the inside of the model and the ground
through it.

Two faults were only ever reported from a screenshot, never by a tool here:

* a repeated ``o`` name, which made the game drop a part - check_obj_loading.py fails the build on that now
* a cap missing where nothing else closes the tube, which is this file

The renderer had shown neither, because it read the OBJ into a flat list and did not cull.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_cable_models as cable                        # noqa: E402
import render_blocks as rb                              # noqa: E402

# A run is a few pixels tall, so the camera has to be close for a hole to be more than one pixel; eight
# bearings at a standing player's eye height, and one from straight above.
BEARINGS = 8
EYE = 1.75
RANGE = 2.6
# Below this a fault is a rasterising artefact on a silhouette edge, not a hole a player can see.
FLOOR = 6


def views(at):
    import math

    centre = (at[0] + 0.5, 0.1, at[2] + 0.5)
    out = []
    for i in range(BEARINGS):
        angle = 2.0 * math.pi * i / BEARINGS
        out.append(((centre[0] + RANGE * math.cos(angle), EYE, centre[2] + RANGE * math.sin(angle)),
                    centre))
    out.append(((centre[0] + 0.1, 4.2, centre[2] + 0.1), centre))
    return out


def block(at):
    """The eight corners of the block being judged, so the arms' far ends fall outside the window."""
    return [(at[0] + x, at[1] + y, at[2] + z)
            for x in (0.0, 1.0) for y in (0.0, 1.0) for z in (0.0, 1.0)]


def main():
    write = '--write' in sys.argv
    problems = 0

    for prefix, gauge in ((rb.CABLE, 'string'), (rb.TRUNK, 'trunk')):
        for connected in sorted(cable.HUBS, key=lambda k: (len(k), k)):
            at = (1, 0, 1)
            triangles = [t for t in rb.composed(connected, at, prefix=prefix) if t[0] != 'ground']
            name = '+'.join(connected) or 'loose'
            worst, where = 0, None
            for index, (eye, target) in enumerate(views(at)):
                holes, size = rb.see_through(triangles, eye, target, inside=block(at))
                if len(holes) > worst:
                    worst, where = len(holes), (index, eye, target, holes, size)
            if worst <= FLOOR:
                continue

            index, eye, target, holes, size = where
            rows = sorted({h // size for h in holes})
            print('HOLE  %-7s %-22s %d px of inside visible from bearing %d, over %d scanline(s)'
                  % (gauge, name, worst, index, len(rows)))
            problems += 1
            if write:
                path = os.path.join(rb.OUT, 'hole_%s_%s.png' % (gauge, name.replace('+', '_')))
                rb.render(triangles + rb.ground(-2, -2, 5, 5, rb.SAND), eye, target, path, size=700)
                print('        %s' % path)

    if problems:
        print('\n%d patterns show their inside' % problems)
        return 1
    print('no connection pattern of either gauge shows its inside, from eye height or above')
    return 0


if __name__ == '__main__':
    sys.exit(main())
