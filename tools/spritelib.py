#!/usr/bin/env python3
"""The drawing kit every item sprite in the mod is built from.

Why a kit rather than sixty drawings
------------------------------------
Because the thing that makes a set of item sprites read as a set is not the quality of any one of them:
it is that they share a viewpoint, a light and a weight of line.  The sprites this replaces did not -
some were flat-on, some were three-quarter, some were sixteen pixels and some a hundred and twenty-eight,
one was *seventeen* - and the effect of that is that no two items in the inventory look like they came
out of the same mod, however carefully any single one was drawn.

So there is one projection, one light, one outline weight, and every item is composed from the same dozen
shapes.  Which also means an item takes ten lines instead of a hundred, and a change of house style is
one edit rather than sixty.

The house style
---------------
**Projection.** A true isometric: the two horizontal axes go up-and-out at thirty degrees and the
vertical goes straight up.  Every solid object is drawn as its top face and its two visible sides, at
three brightnesses, which is the oldest trick in pixel art and still the one that reads smallest.

**Light.** From the upper left, the same direction ``texlib.LIGHT`` points for every block texture in the
mod.  So the top of a thing is brightest, its left side is next and its right side is darkest.

**Outline.** A soft dark edge, half a pixel of it, found from the alpha rather than drawn by hand.  Not
a black keyline - these are meant to look like objects, not like stickers - but without *something* an
item is a grey shape on a grey inventory slot at the size this is actually seen.

**Size.** 128 pixels for all of them.  Eight times what the game needs at the size it draws an item,
which is the point: an item is also seen in a recipe book, in a tooltip and on the ground, and the same
sprite has to hold up there.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from texlib import Canvas, Field, hash01, mix, polygon, shade   # noqa: E402

SIZE = 128

# The isometric basis: x goes right-and-down, y goes right-and-up, z is straight up.  Written out
# because every sprite here depends on the three being consistent, down to the sign of the y term.
COS30 = math.cos(math.radians(30.0))
SIN30 = math.sin(math.radians(30.0))

# The three brightnesses a solid gets, top then left then right.  Wide apart on purpose: an object whose
# faces differ by ten levels reads as a flat blob once it is sixteen pixels across.
TOP, LEFT, RIGHT = 26, -10, -34


def iso(x, y, z):
    """A point in the isometric frame, in pixels from the middle of the tile."""
    return (SIZE * 0.5 + (x - y) * COS30, SIZE * 0.56 - (x + y) * SIN30 - z)


def face(c, corners, colour, tone):
    polygon(c, corners, shade(colour, tone))


def iso_box(c, at, size, colour, tone=(TOP, LEFT, RIGHT), top=None):
    """A box: its top face and its two visible sides.

    ``at`` is the near-bottom corner and ``size`` is (x, y, z).  ``top`` overrides the top face's colour,
    for anything whose lid is a different material from its body - a junction box, a cabinet, a module.
    """
    ox, oy, oz = at
    sx, sy, sz = size
    a = iso(ox, oy, oz + sz)
    b = iso(ox + sx, oy, oz + sz)
    d = iso(ox, oy + sy, oz + sz)
    e = iso(ox + sx, oy + sy, oz + sz)
    face(c, (a, b, e, d), top if top is not None else colour, tone[0])
    # the left side, which is the face of constant y
    face(c, (a, b, iso(ox + sx, oy, oz), iso(ox, oy, oz)), colour, tone[1])
    # and the right, of constant x
    face(c, (b, e, iso(ox + sx, oy + sy, oz), iso(ox + sx, oy, oz)), colour, tone[2])


def iso_disc(c, at, radius, colour, tone=TOP, steps=48, squash=SIN30):
    """A horizontal disc: the top or bottom of anything round, as the ellipse the projection makes."""
    x, y, z = at
    cx, cy = iso(x, y, z)
    points = [(cx + math.cos(2.0 * math.pi * i / steps) * radius * COS30,
               cy + math.sin(2.0 * math.pi * i / steps) * radius * squash * 2.0)
              for i in range(steps)]
    polygon(c, points, shade(colour, tone))
    return (cx, cy)


def iso_cylinder(c, at, radius, height, colour, top=None, steps=48):
    """A cylinder standing on its base: the barrel, then the top ellipse over it.

    The barrel is shaded across its width rather than in two flats, because a cylinder next to a box has
    to read as round or the two look like the same object.
    """
    x, y, z = at
    cx, cy = iso(x, y, z)
    rx, ry = radius * COS30, radius * SIN30 * 2.0
    for i in range(steps):
        t0, t1 = i / steps, (i + 1) / steps
        a0, a1 = math.pi * t0, math.pi * t1
        # the front half only: the back is hidden behind it
        x0 = cx + math.cos(a0) * rx
        x1 = cx + math.cos(a1) * rx
        y0 = cy + math.sin(a0) * ry
        y1 = cy + math.sin(a1) * ry
        lit = math.cos((t0 - 0.72) * math.pi)
        tone = RIGHT + max(0.0, lit) * (TOP - RIGHT + 12)
        polygon(c, ((x0, y0 - height), (x1, y1 - height), (x1, y1), (x0, y0)), shade(colour, tone))

    iso_disc(c, (x, y, z + height), radius, top if top is not None else colour, TOP + 6, steps)
    return (cx, cy)


def iso_tube(c, a, b, radius, colour, steps=20):
    """A round bar between two points in the isometric frame: a lead, a spindle, a length of cable."""
    p0, p1 = iso(*a), iso(*b)
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    length = math.hypot(dx, dy) or 1.0
    nx, ny = -dy / length, dx / length
    for i in range(steps):
        t0, t1 = -1.0 + 2.0 * i / steps, -1.0 + 2.0 * (i + 1) / steps
        lit = math.sqrt(max(0.0, 1.0 - ((t0 + t1) * 0.5) ** 2))
        tone = RIGHT + lit * (TOP - RIGHT + 10)
        polygon(c, ((p0[0] + nx * radius * t0, p0[1] + ny * radius * t0),
                    (p0[0] + nx * radius * t1, p0[1] + ny * radius * t1),
                    (p1[0] + nx * radius * t1, p1[1] + ny * radius * t1),
                    (p1[0] + nx * radius * t0, p1[1] + ny * radius * t0)), shade(colour, tone))


def iso_plate(c, at, size, colour, thickness=3.0, top=None):
    """A flat plate lying down: a wafer, a sheet, a laminate, a circuit board."""
    iso_box(c, at, (size[0], size[1], thickness), colour, top=top)


def sheen(c, x0, y0, x1, y1, alpha=0.16):
    """A soft diagonal highlight, for glass and for anything glazed."""
    for y in range(max(0, int(y0)), min(c.h, int(y1))):
        for x in range(max(0, int(x0)), min(c.w, int(x1))):
            band = math.exp(-(((x - y * 0.8) / SIZE - 0.06) ** 2) / 0.010)
            if band > 0.02 and c.get(x, y)[3] > 0:
                c.blend(x, y, (255, 255, 255, 255), band * alpha)


def speckle(c, colour, amount=0.10, salt=7, cell=6.0):
    """A little tonal noise over whatever is drawn, so no face is a flat fill."""
    field = Field(c.w, cell=cell, octaves=2, salt=salt)
    c.over(lambda x, y, base: base if base[3] == 0
           else mix(base, colour, max(0.0, field.signed(x, y)) * amount))


def outline(c, colour=(24, 22, 26, 255), width=1.6, alpha=0.85):
    """A soft dark edge round whatever is drawn, found from the alpha.

    Every one of these is seen at sixteen pixels on a slot whose colour the player has themselves
    chosen, and a grey object on a grey slot has no silhouette at all.  Soft rather than a keyline: the
    sprites are meant to read as objects and not as stickers.
    """
    alphas = [[c.get(x, y)[3] for x in range(c.w)] for y in range(c.h)]
    reach = int(math.ceil(width))
    edge = []
    for y in range(c.h):
        for x in range(c.w):
            if alphas[y][x] > 8:
                continue

            near = 0.0
            for dy in range(-reach, reach + 1):
                for dx in range(-reach, reach + 1):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < c.w and 0 <= ny < c.h and alphas[ny][nx] > 8:
                        d = math.hypot(dx, dy)
                        if d <= width:
                            near = max(near, 1.0 - d / (width + 0.5))
            if near > 0.0:
                edge.append((x, y, near))

    for x, y, near in edge:
        c.blend(x, y, colour, alpha * near)


def darken_edges(c, colour=(20, 18, 22, 255), width=1.2, alpha=0.45):
    """The same edge, but inside the shape: the contact shadow where two solids meet."""
    alphas = [[c.get(x, y)[3] for x in range(c.w)] for y in range(c.h)]
    for y in range(c.h):
        for x in range(c.w):
            if alphas[y][x] <= 8:
                continue

            open_side = False
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    nx, ny = x + dx, y + dy
                    if not (0 <= nx < c.w and 0 <= ny < c.h) or alphas[ny][nx] <= 8:
                        open_side = True
            if open_side:
                c.blend(x, y, colour, alpha)


def new():
    return Canvas(SIZE, SIZE)


def finish(c, grain=None, salt=3):
    """The passes every sprite ends with, so all of them are finished the same way."""
    if grain is not None:
        speckle(c, grain, amount=0.12, salt=salt)
    darken_edges(c)
    outline(c)
    return c
