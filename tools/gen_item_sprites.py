#!/usr/bin/env python3
"""Draws every item sprite in the mod.

    python3 tools/gen_item_sprites.py                 # all of them
    python3 tools/gen_item_sprites.py insulator cpu   # just these

Writes into src/main/resources/assets/electricity/textures/item/.

Why all of them, and why drawn
------------------------------
The set this replaces had no set about it.  Thirty-nine sprites at sixteen pixels, eight at thirty-two,
ten at a hundred and twenty-eight and one at *seventeen*; some drawn flat-on and some three-quarter; the
machines were renders of their own models and the parts were hand-painted, so a turbine and a bearing in
the same row of the inventory did not look like they belonged to the same mod.  And several were simply
not legible - the wrench was the worst, and the six turbines were unreadable at the size an item is
actually seen.

So every one is drawn here, from one kit, at one size, in one projection, under one light: see
``spritelib``.  Drawn rather than rendered on purpose - a render of a model is accurate and reads as mud
at sixteen pixels, because it has no exaggeration in it.  A drawing can make a bearing's balls bigger
than they are and a blade's twist stronger than it is, which is what makes either recognisable.

The organising idea for each one is the same as for the models: *what is the real object*.  A bearing is
two races and the balls between them.  A wafer is a disc with one flat cut off it.  An MC4 is a plug and
a socket that latch.  A pin insulator is two skirts, a head and a groove.  What makes a sprite legible is
almost always that it is a drawing of the right thing rather than a better drawing of the wrong one.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from spritelib import (LEFT, RIGHT, SIZE, TOP, finish, iso, iso_box, iso_cylinder, iso_disc,  # noqa
                       iso_plate, iso_tube, new, outline, sheen, speckle)
from texlib import Canvas, Field, hash01, mix, polygon, shade                            # noqa: E402

OUT = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'textures', 'item')

# One palette for all of them, so a steel part and a steel machine are the same steel.
STEEL = (166, 170, 176, 255)
DARK_STEEL = (120, 124, 130, 255)
ZINC = (150, 155, 158, 255)
ALU = (186, 190, 196, 255)
COPPER = (186, 108, 56, 255)
BRASS = (198, 164, 88, 255)
SHEET = (196, 200, 204, 255)
MOSS = (52, 76, 58, 255)
CELL = (30, 38, 72, 255)
GLASS = (150, 186, 208, 255)
PCB = (28, 92, 58, 255)
BLACK_POLY = (44, 45, 50, 255)
PORCELAIN = (126, 82, 50, 255)
CONCRETE = (172, 170, 164, 255)
SILICON = (74, 76, 82, 255)
RESIN = (214, 196, 148, 255)
RED = (168, 46, 40, 255)
GOLD = (208, 174, 84, 255)


# ------------------------------------------------------------------ raw stock

def steel_plate():
    """Two rolled plates, one across the other, which is how plate is stacked."""
    c = new()
    iso_plate(c, (-26, -20, 0), (52, 40), STEEL, thickness=7)
    iso_plate(c, (-20, -26, 7), (40, 52), shade(STEEL, 6), thickness=7)
    return finish(c, grain=(230, 232, 236, 255))


def steel_section():
    """A length of angle iron, seen down its length: a plate folded once, which is what a section is."""
    c = new()
    # the two legs of the angle, as two thin boxes meeting at the corner
    iso_box(c, (-30, -6, 0), (60, 12, 6), STEEL)
    iso_box(c, (-30, -6, 6), (60, 6, 26), shade(STEEL, -4))
    return finish(c, grain=(228, 230, 234, 255), salt=11)


def busbar():
    """A flat copper bar with two bolt holes, which is what a busbar is."""
    c = new()
    iso_plate(c, (-32, -9, 0), (64, 18), COPPER, thickness=6)
    for x in (-22, 18):
        iso_disc(c, (x, 0, 6.4), 5.0, (86, 44, 22, 255), tone=-10)
    return finish(c, grain=(232, 168, 116, 255), salt=13)


def copper_coil():
    """Wire wound on a bobbin, the winding proud of the flanges: copper first, steel second.

    Two goes at this.  Stacked discs read as a cone of pennies.  A bobbin whose flanges are wider than
    its winding reads as a steel drum with a stripe on it - which is true of a *part-wound* bobbin and
    useless as a sprite, because what the item is is the copper.  So the winding is wider than the
    flanges here, which is what a full one looks like, and the flanges are just visible at each end.
    """
    c = new()
    core = shade(STEEL, -22)
    iso_cylinder(c, (0, 0, 0), 20, 5, core, top=shade(core, 8))
    # the winding: turns lying against each other, wider than the flanges so the copper reads first
    for row in range(11):
        z = 5 + row * 3.2
        for k in range(6):
            y = -13 + k * 5.2
            iso_tube(c, (-24, y, z), (24, y, z), 2.4,
                     shade(COPPER, 12 if (row + k) % 2 else -14), steps=12)
    iso_cylinder(c, (0, 0, 40), 20, 5, core, top=shade(core, 24))
    iso_disc(c, (0, 0, 45), 7, (58, 60, 64, 255), tone=-16)
    # the tail, left long the way a delivered coil is
    iso_tube(c, (22, -16, 44), (42, -28, 38), 2.4, COPPER, steps=12)
    return finish(c, grain=(238, 176, 118, 255), salt=17)


def resin():
    """A reel of encapsulant film and a cake of wax: what a laminate is glued with.

    Drawn as a reel because that is how the film arrives and because a reel is a recognisable object -
    the first attempt was a cylinder and a box and read as neither.
    """
    c = new()
    film = (226, 202, 142, 255)
    # the reel on its side: two flanges with the wound film between them
    iso_cylinder(c, (2, 2, 0), 30, 5, shade(STEEL, -24))
    iso_cylinder(c, (2, 2, 5), 26, 22, film, top=shade(film, -16))
    # the spiral edge of the wound film, which is what says roll rather than drum
    for i in range(7):
        iso_disc(c, (2, 2, 27 - i * 0.4), 26 - i * 2.6, shade(film, -6 + (i % 2) * 14), tone=8)
    iso_cylinder(c, (2, 2, 27), 30, 5, shade(STEEL, -14), top=shade(STEEL, 6))
    iso_disc(c, (2, 2, 32), 9, (58, 60, 64, 255), tone=-14)
    # the cake of wax beside it, which is the other half of an encapsulant
    iso_box(c, (-36, -34, 0), (24, 20, 14), (238, 226, 176, 255),
            top=shade((238, 226, 176, 255), 16))
    sheen(c, 0, 0, SIZE, SIZE, alpha=0.14)
    return finish(c, grain=(250, 240, 200, 255), salt=19)


def silicon_ingot():
    """A monocrystalline ingot: a grown cylinder with the four flats already cut on it."""
    c = new()
    iso_cylinder(c, (0, 0, 0), 22, 54, SILICON, top=shade(SILICON, 20))
    # the flats, as two darker panels down the barrel
    for x0, x1 in ((-19, -8), (7, 18)):
        polygon(c, (iso(x0, -14, 54), iso(x1, -14, 54), iso(x1, -14, 4), iso(x0, -14, 4)),
                shade(SILICON, -26))
    sheen(c, 0, 0, SIZE, SIZE, alpha=0.20)
    return finish(c, grain=(140, 142, 150, 255), salt=23)


def silicon_wafer():
    """A pseudo-square wafer: a round slice with its four flats, mirror-polished.

    The chamfers were too big and the whole thing read as a dark diamond.  Smaller flats keep it round,
    and what actually says *wafer* is the polish - a lapped silicon surface is a mirror, so it carries a
    hard reflection of the sky across it and a bright rim where the light catches the edge.
    """
    c = new()
    chamfer = 5.0
    half = 33.0
    ring = []
    for sx, sy in ((1, 1), (-1, 1), (-1, -1), (1, -1)):
        ring.append((sx * half, sy * (half - chamfer)))
        ring.append((sx * (half - chamfer), sy * half))
    upper = [iso(x, y, 3.5) for x, y in ring]
    lower = [iso(x, y, 0) for x, y in ring]
    for i in range(len(upper)):
        j = (i + 1) % len(upper)
        polygon(c, (upper[i], upper[j], lower[j], lower[i]), shade(SILICON, RIGHT))
    polygon(c, upper, shade(SILICON, TOP + 10))
    # the reflection: a hard band across the polish, which is what a mirror does and a matt slice cannot
    inside = [iso(x * 0.94, y * 0.94, 3.6) for x, y in ring]
    xs = [p[0] for p in inside]
    ys = [p[1] for p in inside]
    for y in range(int(min(ys)), int(max(ys)) + 1):
        for x in range(int(min(xs)), int(max(xs)) + 1):
            if c.get(x, y)[3] < 8:
                continue
            band = math.exp(-(((x - y * 1.7) / SIZE + 0.30) ** 2) / 0.006)
            if band > 0.02:
                c.blend(x, y, (198, 214, 232, 255), band * 0.55)
    return finish(c, grain=(150, 152, 160, 255), salt=29)


def tempered_glass():
    """A pane on edge, with the green cast low-iron glass has when you look through its thickness."""
    c = new()
    iso_box(c, (-30, -4, 0), (60, 8, 52), GLASS)
    sheen(c, 0, 0, SIZE, SIZE, alpha=0.34)
    # the ground edge along the top, which is what tells tempered glass from a sheet
    iso_disc(c, (0, 0, 52), 3, (232, 244, 250, 255), tone=20, squash=0.5)
    return finish(c, grain=(226, 242, 248, 255), salt=31)


# ------------------------------------------------------------------ components

def solar_cell():
    """One half-cut cell: the blue square, its fingers and the wires across it."""
    c = new()
    iso_plate(c, (-34, -20, 0), (68, 40), CELL, thickness=3,
              top=shade(CELL, 10))
    for i in range(1, 13):
        y = -20 + 40 * i / 13.0
        polygon(c, (iso(-33, y, 3), iso(33, y, 3), iso(33, y + 1.1, 3), iso(-33, y + 1.1, 3)),
                shade(CELL, 34))
    for x in (-17, 0, 17):
        polygon(c, (iso(x - 1.4, -20, 3.2), iso(x + 1.4, -20, 3.2),
                    iso(x + 1.4, 20, 3.2), iso(x - 1.4, 20, 3.2)), (198, 204, 214, 255))
    sheen(c, 0, 0, SIZE, SIZE, alpha=0.20)
    return finish(c, grain=(70, 84, 140, 255), salt=37)


def bypass_diode():
    """An axial diode: a glass body with a cathode band and a lead out of each end."""
    c = new()
    iso_tube(c, (-40, 0, 12), (-14, 0, 12), 2.0, ZINC)
    iso_tube(c, (14, 0, 12), (40, 0, 12), 2.0, ZINC)
    iso_cylinder(c, (-14, 0, 6), 11, 0.1, BLACK_POLY)
    # the body lying down, so it is drawn as a tube rather than as a standing cylinder
    iso_tube(c, (-14, 0, 12), (14, 0, 12), 11.0, (58, 52, 48, 255), steps=24)
    iso_tube(c, (7, 0, 12), (12, 0, 12), 11.2, (226, 228, 232, 255), steps=12)
    return finish(c, grain=(120, 108, 100, 255), salt=41)


def mc4_connector():
    """A plug and a socket, latched: knurled nuts, the collars, and a tail out of each."""
    c = new()
    for y, positive in ((-11, True), (11, False)):
        iso_tube(c, (-40, y, 12), (-26, y, 12), 3.0, (30, 30, 34, 255))
        iso_tube(c, (-26, y, 12), (-12, y, 12), 8.0, BLACK_POLY, steps=18)
        for i in range(6):
            x = -25 + i * 2.2
            iso_tube(c, (x, y, 12), (x + 1.1, y, 12), 8.4, shade(BLACK_POLY, 26), steps=8)
        iso_tube(c, (-12, y, 12), (22, y, 12), 9.5, shade(BLACK_POLY, 6), steps=20)
        iso_tube(c, (22, y, 12), (27, y, 12), 9.5, RED if positive else (34, 34, 38, 255), steps=10)
        iso_tube(c, (27, y, 12), (36, y, 12), 6.0, shade(BLACK_POLY, -10), steps=12)
    return finish(c, grain=(96, 96, 104, 255), salt=43)


def junction_box():
    """A module's junction box: a black lid on a shallow body, with two leads out of it."""
    c = new()
    iso_box(c, (-24, -18, 0), (48, 36, 14), BLACK_POLY, top=shade(BLACK_POLY, 16))
    for i in range(2):
        x = -12 + i * 24
        iso_disc(c, (x, 0, 14.4), 4.0, shade(BLACK_POLY, -20))
    for y in (-9, 9):
        iso_tube(c, (24, y, 7), (44, y, 7), 3.0, (32, 32, 36, 255))
    return finish(c, grain=(90, 90, 98, 255), salt=47)


def power_module():
    """An IGBT module: a black package with a screwed baseplate and two busbar terminals."""
    c = new()
    iso_box(c, (-28, -20, 0), (56, 40, 6), DARK_STEEL)
    iso_box(c, (-26, -18, 6), (52, 36, 16), BLACK_POLY, top=shade(BLACK_POLY, 14))
    for x in (-16, 12) :
        iso_box(c, (x, -6, 22), (12, 12, 4), COPPER)
    for x, y in ((-24, -16), (16, -16), (-24, 12), (16, 12)):
        iso_disc(c, (x + 4, y + 4, 6.4), 3.4, shade(DARK_STEEL, 18))
    return finish(c, grain=(96, 96, 104, 255), salt=53)


def capacitor_bank():
    """Three film capacitors on a rail, terminals up: what a bank is.

    Apart, which the first attempt was not - three cans at nine pixels' spacing merge into one orange
    mass at the size an item is drawn.  Set along the isometric x axis with a can's width between them,
    each with its bright top and its two brass studs, they read as three.
    """
    c = new()
    body = (192, 98, 44, 255)
    iso_box(c, (-34, -10, 0), (68, 20, 5), shade(STEEL, -20), top=shade(STEEL, -4))
    for x in (-26, -2, 22):
        iso_cylinder(c, (x, 0, 5), 12, 38, body, top=shade(body, -22))
        iso_disc(c, (x, 0, 43), 12, shade(body, 22), tone=10)
        for tx, ty in ((-5, -5), (5, 5)):
            iso_cylinder(c, (x + tx, ty, 43), 3.0, 7, BRASS)
    return finish(c, grain=(236, 150, 96, 255), salt=59)


def magnetic_core():
    """A laminated core with a winding on it: the E and the copper round the middle limb."""
    c = new()
    iso_box(c, (-30, -14, 0), (60, 28, 10), DARK_STEEL, top=shade(DARK_STEEL, 20))
    for x in (-30, -6, 18):
        iso_box(c, (x, -14, 10), (12, 28, 26), shade(DARK_STEEL, 4))
    for i in range(7):
        iso_disc(c, (0, 0, 14 + i * 3.2), 15, COPPER, tone=TOP - (i % 2) * 18)
    iso_box(c, (-30, -14, 36), (60, 28, 8), shade(DARK_STEEL, 12))
    return finish(c, grain=(150, 154, 160, 255), salt=61)


def _board(c, chips):
    iso_plate(c, (-32, -24, 0), (64, 48), PCB, thickness=4, top=shade(PCB, 12))
    field = Field(SIZE, cell=7.0, octaves=1, salt=67)
    for i in range(9):
        y = -22 + 44 * i / 9.0
        polygon(c, (iso(-30, y, 4), iso(30, y, 4), iso(30, y + 1.0, 4), iso(-30, y + 1.0, 4)),
                (176, 140, 62, 255))
    for x, y, sx, sy, h, col in chips:
        iso_box(c, (x, y, 4), (sx, sy, h), col, top=shade(col, 14))
    return field


def circuit_board():
    """A populated board: the substrate, its tracks, a big package and some small ones."""
    c = new()
    _board(c, ((-18, -12, 22, 18, 7, BLACK_POLY), (10, 6, 12, 10, 5, (54, 54, 60, 255)),
               (10, -14, 10, 8, 4, (54, 54, 60, 255))))
    for x in (-19, 5):
        for i in range(5):
            iso_tube(c, (x + i * 4.4, -14, 6), (x + i * 4.4, -17, 6), 1.1, ZINC, steps=6)
    return finish(c, grain=(70, 150, 100, 255), salt=71)


def control_board():
    """A controller: the same board with a processor, a connector strip and an indicator."""
    c = new()
    _board(c, ((-14, -8, 26, 22, 6, (38, 40, 46, 255)),))
    iso_box(c, (-28, 12, 4), (52, 8, 8), (40, 40, 46, 255))
    for i in range(12):
        iso_tube(c, (-26 + i * 4.2, 14, 12), (-26 + i * 4.2, 18, 12), 1.2, GOLD, steps=6)
    iso_disc(c, (22, -14, 5), 4.0, (86, 216, 108, 255), tone=24)
    return finish(c, grain=(70, 150, 100, 255), salt=73)


def cpu():
    """A processor: a lidded package with a pin field under it and a keyed corner."""
    c = new()
    iso_box(c, (-26, -26, 0), (52, 52, 5), (48, 48, 54, 255), top=shade((48, 48, 54, 255), 10))
    iso_box(c, (-17, -17, 5), (34, 34, 6), (196, 200, 208, 255),
            top=shade((196, 200, 208, 255), 18))
    for i in range(7):
        for j in range(7):
            if (i + j) % 2:
                continue
            iso_disc(c, (-22 + i * 7.3, -22 + j * 7.3, 5.2), 1.9, GOLD, tone=16)
    iso_disc(c, (-21, -21, 5.4), 3.2, (208, 176, 88, 255), tone=26)
    return finish(c, grain=(120, 120, 130, 255), salt=79)


def screen():
    """A panel display: a dark glass front in a bezel, on a stand-off frame."""
    c = new()
    iso_box(c, (-30, -6, 0), (60, 12, 6), DARK_STEEL)
    iso_box(c, (-30, -6, 6), (60, 6, 40), (58, 60, 66, 255))
    polygon(c, (iso(-26, -6.2, 42), iso(26, -6.2, 42), iso(26, -6.2, 12), iso(-26, -6.2, 12)),
            (24, 42, 34, 255))
    for i in range(4):
        y = 16 + i * 6.5
        polygon(c, (iso(-22, -6.4, y), iso(20, -6.4, y), iso(20, -6.4, y + 2.4),
                    iso(-22, -6.4, y + 2.4)), (98, 214, 128, 255))
    return finish(c, grain=(120, 124, 134, 255), salt=83)


def insulator():
    """The mod's pin insulator, drawn: two skirts, the head, the tie groove and its spindle.

    The same object the models carry, and drawn to say so - the sprite it replaces was a grey cone that
    could have been anything, and the whole point of one insulator on every machine is that a player
    recognises it in the inventory as the thing on the pole.
    """
    c = new()
    # the spindle first, so the porcelain sits over it
    iso_cylinder(c, (0, 0, 0), 7, 14, ZINC)
    profile = ((34, 14), (34, 18), (22, 34), (18, 34), (18, 40), (28, 40),
               (28, 45), (17, 62), (16, 62), (16, 70), (20, 70), (20, 76),
               (16, 76), (16, 82), (20, 82), (20, 87), (13, 87), (13, 96), (18, 96), (15, 106))
    previous = None
    for radius, height in profile:
        if previous is not None and height > previous[1]:
            iso_cylinder(c, (0, 0, previous[1]), (radius + previous[0]) * 0.5,
                         height - previous[1], PORCELAIN)
        previous = (radius, height)
    iso_disc(c, (0, 0, 106), 15, shade(PORCELAIN, 18), tone=16)
    sheen(c, 0, 0, SIZE, SIZE, alpha=0.16)
    return finish(c, grain=(168, 118, 76, 255), salt=89)


def metal_casing():
    """A folded steel enclosure with its lid off: four sides, a base, and the flange round the top."""
    c = new()
    iso_box(c, (-26, -22, 0), (52, 44, 6), DARK_STEEL, top=shade(DARK_STEEL, 22))
    for at, size in (((-26, -22, 6), (52, 5, 26)), ((-26, 17, 6), (52, 5, 26)),
                     ((-26, -22, 6), (5, 44, 26)), ((21, -22, 6), (5, 44, 26))):
        iso_box(c, at, size, STEEL)
    return finish(c, grain=(228, 230, 234, 255), salt=97)


def motor_core():
    """A rotor: a laminated stack on a shaft, with the slots showing round it."""
    c = new()
    iso_cylinder(c, (0, 0, 0), 8, 12, ZINC)
    iso_cylinder(c, (0, 0, 12), 28, 34, DARK_STEEL, top=shade(DARK_STEEL, 22))
    for i in range(14):
        a = 2.0 * math.pi * i / 14.0
        if math.sin(a) > -0.1:
            continue
        iso_tube(c, (math.cos(a) * 26, math.sin(a) * 26, 13),
                 (math.cos(a) * 26, math.sin(a) * 26, 45), 2.0, shade(DARK_STEEL, -26), steps=6)
    iso_cylinder(c, (0, 0, 46), 8, 16, ZINC)
    return finish(c, grain=(150, 154, 162, 255), salt=101)


def bearing():
    """A ball bearing: the outer race, the inner one, and the balls between them."""
    c = new()
    iso_cylinder(c, (0, 0, 0), 36, 18, STEEL, top=shade(STEEL, -6))
    iso_disc(c, (0, 0, 18.4), 29, shade(STEEL, -30))
    for i in range(9):
        a = 2.0 * math.pi * i / 9.0
        cx, cy = iso(math.cos(a) * 24, math.sin(a) * 24, 19)
        for r, tone in ((6.0, -6), (4.0, 22), (1.8, 44)):
            c.aa_disc(cx - (6.0 - r) * 0.4, cy - (6.0 - r) * 0.4, r, shade(STEEL, tone),
                      squash=1.0)
    iso_cylinder(c, (0, 0, 16), 17, 6, shade(STEEL, 8), top=shade(STEEL, 14))
    iso_disc(c, (0, 0, 22.4), 9, (52, 54, 58, 255), tone=-10)
    return finish(c, grain=(232, 234, 238, 255), salt=103)


def gear_set():
    """Two gears in mesh, which is what a set is: teeth, a hub and a keyway."""
    c = new()

    def gear(x, y, radius, teeth, z):
        iso_cylinder(c, (x, y, z), radius, 9, STEEL, top=shade(STEEL, 12))
        for i in range(teeth):
            a = 2.0 * math.pi * i / teeth
            cx, cy = iso(x + math.cos(a) * radius, y + math.sin(a) * radius, z + 9)
            c.aa_rect(cx - 3.2, cy - 2.4, cx + 3.2, cy + 2.4, shade(STEEL, 18))
        iso_disc(c, (x, y, z + 9.4), radius * 0.28, (60, 62, 66, 255), tone=-8)

    gear(-16, -16, 26, 14, 0)
    gear(20, 20, 18, 10, 4)
    return finish(c, grain=(230, 232, 236, 255), salt=107)


def gpv_fuse():
    """A photovoltaic fuse link: a glass barrel with silver end caps and the element inside."""
    c = new()
    iso_tube(c, (-36, 0, 14), (36, 0, 14), 12.0, (216, 228, 236, 255), steps=26)
    iso_tube(c, (-36, 0, 14), (-22, 0, 14), 13.0, (206, 210, 216, 255), steps=12)
    iso_tube(c, (22, 0, 14), (36, 0, 14), 13.0, (206, 210, 216, 255), steps=12)
    # the element, a zigzag down the middle
    for i in range(6):
        x0 = -20 + i * 7.0
        polygon(c, (iso(x0, -0.2, 14 + (4 if i % 2 else -4)),
                    iso(x0 + 7, -0.2, 14 - (4 if i % 2 else -4)),
                    iso(x0 + 7, -0.2, 12 - (4 if i % 2 else -4)),
                    iso(x0, -0.2, 12 + (4 if i % 2 else -4))), (210, 212, 218, 255))
    sheen(c, 0, 0, SIZE, SIZE, alpha=0.30)
    return finish(c, grain=(240, 248, 252, 255), salt=109)


def load_break_switch():
    """A load-break switch: the moulded base, the contacts and the operating handle."""
    c = new()
    iso_box(c, (-28, -18, 0), (56, 36, 12), (54, 56, 62, 255), top=shade((54, 56, 62, 255), 16))
    for x in (-20, 8):
        iso_cylinder(c, (x + 6, 0, 12), 9, 22, PORCELAIN)
        iso_disc(c, (x + 6, 0, 34), 6, BRASS, tone=18)
    iso_tube(c, (-14, 0, 40), (14, 0, 52), 4.0, RED, steps=16)
    iso_cylinder(c, (-14, 0, 34), 5, 8, DARK_STEEL)
    return finish(c, grain=(120, 124, 132, 255), salt=113)


def sensor_head():
    """A pyranometer head: the glass dome, the white body and its levelling feet."""
    c = new()
    iso_cylinder(c, (0, 0, 0), 26, 16, (236, 238, 242, 255), top=shade((236, 238, 242, 255), 10))
    cx, cy = iso(0, 0, 16)
    for r, tone, col in ((17, 0, GLASS), (13, 18, GLASS), (6, 40, (240, 250, 255, 255))):
        c.aa_disc(cx - (17 - r) * 0.35, cy - 8 - (17 - r) * 0.35, r, shade(col, tone))
    for i in range(3):
        a = 2.0 * math.pi * i / 3.0 + 0.4
        iso_tube(c, (math.cos(a) * 24, math.sin(a) * 24, 0),
                 (math.cos(a) * 30, math.sin(a) * 30, -6), 2.6, DARK_STEEL, steps=8)
    return finish(c, grain=(250, 250, 252, 255), salt=127)


# ------------------------------------------------------------------ assemblies

def pv_laminate():
    """A framed module: the cell grid under glass in an aluminium frame."""
    c = new()
    iso_plate(c, (-20, -38, 0), (40, 76), ALU, thickness=6, top=shade(ALU, 10))
    top = iso_plate
    # the glass inside the frame
    inner = [iso(-16, -34, 6.4), iso(16, -34, 6.4), iso(16, 34, 6.4), iso(-16, 34, 6.4)]
    polygon(c, inner, shade(CELL, 12))
    for row in range(10):
        y = -33 + 66 * row / 10.0
        polygon(c, (iso(-16, y, 6.6), iso(16, y, 6.6), iso(16, y + 1.2, 6.6),
                    iso(-16, y + 1.2, 6.6)), shade(CELL, -22))
    for x in (-5.5, 5.5):
        polygon(c, (iso(x, -34, 6.7), iso(x + 1.3, -34, 6.7), iso(x + 1.3, 34, 6.7),
                    iso(x, 34, 6.7)), shade(CELL, -26))
    sheen(c, 0, 0, SIZE, SIZE, alpha=0.22)
    return finish(c, grain=(80, 92, 140, 255), salt=131)


def enclosure():
    """A sheet-steel cabinet shell: the folded body, the flange and the lid gasket line."""
    c = new()
    iso_box(c, (-24, -18, 0), (48, 36, 52), SHEET, top=shade(SHEET, 18))
    polygon(c, (iso(-24, -18, 52.5), iso(24, -18, 52.5), iso(24, 18, 52.5), iso(-24, 18, 52.5)),
            shade(SHEET, 26))
    polygon(c, (iso(-20, -14, 53), iso(20, -14, 53), iso(20, 14, 53), iso(-20, 14, 53)),
            shade(SHEET, -8))
    for y in (-14, 14):
        for x in (-18, 18):
            iso_disc(c, (x, y, 53.4), 3.0, shade(SHEET, -30))
    return finish(c, grain=(236, 238, 242, 255), salt=137)


def dc_section():
    """A fuse rack: three ways of fuse holders on a busbar, which is what a section is."""
    c = new()
    iso_box(c, (-30, -16, 0), (60, 32, 8), (58, 60, 66, 255), top=shade((58, 60, 66, 255), 14))
    for i in range(3):
        x = -22 + i * 16
        iso_box(c, (x, -10, 8), (11, 20, 10), (44, 46, 52, 255))
        iso_cylinder(c, (x + 5.5, 0, 18), 5.0, 18, (216, 228, 236, 255))
        iso_disc(c, (x + 5.5, 0, 36), 5.2, (206, 210, 216, 255), tone=18)
    iso_box(c, (-30, 12, 8), (60, 5, 5), COPPER)
    return finish(c, grain=(120, 124, 132, 255), salt=139)


def inverter_bridge():
    """A power stack: the modules on their heatsink with the capacitors over them."""
    c = new()
    iso_box(c, (-30, -20, 0), (60, 40, 8), DARK_STEEL)
    for i in range(6):
        x = -28 + i * 10
        iso_box(c, (x, -20, 8), (5, 40, 22), shade(DARK_STEEL, 18))
    iso_box(c, (-26, -14, 30), (52, 28, 10), BLACK_POLY, top=shade(BLACK_POLY, 12))
    for x in (-18, 6):
        iso_cylinder(c, (x + 6, 0, 40), 9, 16, (188, 96, 44, 255))
    return finish(c, grain=(140, 144, 152, 255), salt=149)


def mounting_rack():
    """A rack frame: two rails on two posts with the clamps on them."""
    c = new()
    for y in (-18, 14):
        iso_box(c, (-30, y, 0), (60, 5, 5), ZINC)
    for x, y in ((-28, -18), (22, -18), (-28, 14), (22, 14)):
        iso_box(c, (x, y, 5), (6, 5, 30), shade(ZINC, 6))
    for y in (-18, 14):
        iso_box(c, (-30, y, 35), (60, 5, 6), shade(ZINC, 12))
    for x in (-22, -2, 18):
        iso_box(c, (x, -20, 41), (7, 42, 5), ALU)
    return finish(c, grain=(226, 228, 232, 255), salt=151)


def torque_tube():
    """A tracker's torque tube: a square section with a bearing housing at each end."""
    c = new()
    iso_box(c, (-34, -9, 10), (68, 18, 18), ZINC, top=shade(ZINC, 18))
    for x in (-38, 28):
        iso_box(c, (x, -13, 4), (12, 26, 30), DARK_STEEL, top=shade(DARK_STEEL, 16))
        iso_disc(c, (x + 6, 0, 34.4), 7.0, shade(DARK_STEEL, -22))
    return finish(c, grain=(224, 226, 230, 255), salt=157)


def slew_drive():
    """A slew drive: the ring gear, the worm housing across it and the motor on the end."""
    c = new()
    iso_cylinder(c, (0, 0, 0), 34, 14, DARK_STEEL, top=shade(DARK_STEEL, 8))
    for i in range(18):
        a = 2.0 * math.pi * i / 18.0
        cx, cy = iso(math.cos(a) * 34, math.sin(a) * 34, 14)
        c.aa_rect(cx - 2.6, cy - 2.0, cx + 2.6, cy + 2.0, shade(DARK_STEEL, 20))
    iso_disc(c, (0, 0, 14.4), 22, shade(DARK_STEEL, 18))
    iso_disc(c, (0, 0, 14.8), 9, (52, 54, 58, 255), tone=-12)
    iso_box(c, (-8, -40, 4), (16, 26, 18), STEEL, top=shade(STEEL, 14))
    iso_cylinder(c, (0, -44, 6), 9, 14, (54, 56, 62, 255))
    return finish(c, grain=(150, 154, 162, 255), salt=163)


def generator_set():
    """A generator: the finned frame, the terminal box on top and the shaft out of the end."""
    c = new()
    iso_tube(c, (-30, 0, 22), (30, 0, 22), 22.0, DARK_STEEL, steps=28)
    for i in range(9):
        x = -28 + i * 7
        iso_tube(c, (x, 0, 22), (x + 2.0, 0, 22), 24.0, shade(DARK_STEEL, 16), steps=14)
    iso_box(c, (-12, -8, 42), (24, 16, 12), (58, 60, 66, 255), top=shade((58, 60, 66, 255), 16))
    iso_tube(c, (30, 0, 22), (46, 0, 22), 5.0, ZINC, steps=10)
    return finish(c, grain=(150, 154, 162, 255), salt=167)


def gearbox():
    """A gearbox: the split housing, the flange bolts and the two shafts."""
    c = new()
    iso_box(c, (-26, -22, 0), (52, 44, 40), (108, 112, 118, 255),
            top=shade((108, 112, 118, 255), 20))
    polygon(c, (iso(-26, -22, 22), iso(26, -22, 22), iso(26, -22, 24), iso(-26, -22, 24)),
            shade((108, 112, 118, 255), -34))
    for x in (-22, -6, 10):
        iso_disc(c, (x + 3, -22, 23), 2.6, shade((108, 112, 118, 255), 26))
    iso_tube(c, (-26, 0, 20), (-44, 0, 20), 7.0, ZINC, steps=12)
    iso_tube(c, (26, 0, 20), (40, 0, 20), 4.0, ZINC, steps=10)
    return finish(c, grain=(160, 164, 170, 255), salt=173)


def _blade(c, length, chord):
    """A wind turbine blade: the root can, the shoulder where the chord is widest, and the taper out.

    Broad, and deliberately broader than scale: a real 60-metre blade at item size is one pixel wide at
    the tip and three at the root, which is a needle - and a needle with a ball on the end is what the
    first attempt looked like.  What makes a blade a blade is the *shoulder*, the widest point a fifth of
    the way out, and then the long taper - so that is what is exaggerated.

    Drawn flat rather than in the isometric frame, like the turbines, because a blade seen in three-
    quarter view is a line.
    """
    cx, cy = SIZE * 0.5, SIZE * 0.5
    steps = 40
    upper, lower, lead = [], [], []
    for i in range(steps + 1):
        t = i / steps
        # along the blade, from root at the lower left to tip at the upper right
        px = cx - length * 0.5 + length * t
        py = cy + length * 0.30 - length * 0.60 * t
        # the aerofoil's chord: a fast rise to the shoulder, then a long taper to a rounded tip
        if t < 0.18:
            width = chord * (0.42 + 0.58 * (t / 0.18))
        else:
            width = chord * (1.0 - 0.92 * ((t - 0.18) / 0.82) ** 0.85)
        upper.append((px - width * 0.52, py - width * 0.30))
        lower.append((px + width * 0.48, py + width * 0.70))
        lead.append((px - width * 0.30, py - width * 0.05))

    polygon(c, upper + lower[::-1], (232, 235, 240, 255))
    # the leading edge, lit: the strip that says which way round the aerofoil is
    polygon(c, upper + lead[::-1], (252, 253, 255, 255))
    # and the shadow along the trailing edge
    polygon(c, [(p[0] - chord * 0.10, p[1] - chord * 0.06) for p in lower] + lower[::-1],
            (196, 202, 212, 255))
    # the root can, which every blade is bolted on by
    root_x, root_y = upper[0][0] + chord * 0.1, upper[0][1] + chord * 0.5
    for r, tone in ((chord * 0.56, -18), (chord * 0.46, 8), (chord * 0.20, -26)):
        c.aa_disc(root_x, root_y, r, shade((228, 231, 236, 255), tone))


def turbine_blade():
    c = new()
    _blade(c, 104, 30)
    return finish(c, grain=(250, 250, 252, 255), salt=179)


def long_blade():
    c = new()
    _blade(c, 116, 26)
    return finish(c, grain=(250, 250, 252, 255), salt=181)


# ------------------------------------------------------------------ the machines

def _cabinet(c, colour, width=40, depth=30, height=62, door=None):
    iso_box(c, (-width * 0.5, -depth * 0.5, 0), (width, depth, 4), CONCRETE)
    iso_box(c, (-width * 0.5, -depth * 0.5, 4), (width, depth, height), colour,
            top=shade(colour, 18))
    # the hood, proud all round
    iso_box(c, (-width * 0.5 - 3, -depth * 0.5 - 3, height + 4), (width + 6, depth + 6, 4),
            shade(colour, 10), top=shade(colour, 26))
    if door is not None:
        for i, x in enumerate((-width * 0.5 + 2, 1)):
            polygon(c, (iso(x, -depth * 0.5 - 0.2, height - 2), iso(x + width * 0.5 - 3, -depth * 0.5 - 0.2, height - 2),
                        iso(x + width * 0.5 - 3, -depth * 0.5 - 0.2, 8), iso(x, -depth * 0.5 - 0.2, 8)),
                    shade(door, -6 if i else 0))


def pv_inverter():
    """A station inverter: the plinth, two doors, the hood and the roof fans."""
    c = new()
    _cabinet(c, SHEET, width=46, depth=30, height=58, door=shade(SHEET, -12))
    for x in (-11, 11):
        iso_disc(c, (x, 0, 67), 9, shade(SHEET, -26))
        for i in range(6):
            a = 2.0 * math.pi * i / 6.0
            iso_tube(c, (x, 0, 67.5), (x + math.cos(a) * 8, math.sin(a) * 8, 67.5), 1.4,
                     shade(SHEET, 22), steps=6)
    iso_cylinder(c, (19, 8, 66), 4, 9, PORCELAIN)
    polygon(c, (iso(-20, -15.4, 44), iso(-4, -15.4, 44), iso(-4, -15.4, 34), iso(-20, -15.4, 34)),
            (26, 44, 34, 255))
    return finish(c, grain=(236, 238, 242, 255), salt=191)


def pv_combiner():
    """A combiner box: a small enclosure with the switch handle on its door."""
    c = new()
    _cabinet(c, SHEET, width=34, depth=24, height=40, door=shade(SHEET, -10))
    iso_tube(c, (-4, -12.6, 26), (6, -12.6, 20), 3.2, RED, steps=10)
    for y in (-9, 9):
        iso_tube(c, (0, y, 0), (0, y, -8), 3.0, (30, 30, 34, 255), steps=8)
    return finish(c, grain=(236, 238, 242, 255), salt=193)


def _array(c, tilt, legs, colour=CELL):
    """A photovoltaic array: the modules in their plane on whatever holds them up."""
    for x, y in legs:
        iso_box(c, (x, y, 0), (5, 5, 22), ZINC)
    lift = 24
    corners = []
    for x, y, drop in ((-34, -22, 0), (34, -22, 0), (34, 22, tilt), (-34, 22, tilt)):
        corners.append(iso(x, y, lift + drop))
    polygon(c, corners, ALU)
    inner = []
    for x, y, drop in ((-31, -19, 0), (31, -19, 0), (31, 19, tilt), (-31, 19, tilt)):
        inner.append(iso(x, y, lift + drop + 1.5))
    polygon(c, inner, shade(colour, 12))
    for i in range(1, 6):
        t = i / 6.0
        a = (inner[0][0] + (inner[3][0] - inner[0][0]) * t, inner[0][1] + (inner[3][1] - inner[0][1]) * t)
        b = (inner[1][0] + (inner[2][0] - inner[1][0]) * t, inner[1][1] + (inner[2][1] - inner[1][1]) * t)
        polygon(c, (a, b, (b[0], b[1] + 1.6), (a[0], a[1] + 1.6)), shade(colour, -24))
    sheen(c, 0, 0, SIZE, SIZE, alpha=0.18)


def pv_flat():
    """A ballasted flat table: modules nearly level on four short feet, on their trays."""
    c = new()
    for x, y in ((-30, -18), (22, -18), (-30, 14), (22, 14)):
        iso_box(c, (x, y, 0), (14, 6, 5), (44, 44, 48, 255))
    _array(c, 4, ((-28, -16), (24, -16), (-28, 16), (24, 16)))
    return finish(c, grain=(80, 92, 140, 255), salt=197)


def pv_tilt():
    """A fixed tilted rack: the plane at twenty-five degrees on two rows of piers."""
    c = new()
    _array(c, 30, ((-28, -18), (24, -18), (-28, 16), (24, 16)))
    return finish(c, grain=(80, 92, 140, 255), salt=199)


def pv_track():
    """A single-axis tracker: one row on a torque tube down the middle of its piers."""
    c = new()
    for x in (-26, 20):
        iso_box(c, (x, -3, 0), (6, 6, 26), ZINC)
    iso_box(c, (-34, -4, 26), (68, 8, 8), shade(ZINC, 14), top=shade(ZINC, 26))
    _array(c, 14, ())
    return finish(c, grain=(80, 92, 140, 255), salt=211)


def pv_dual():
    """A dual-axis tracker: the pedestal, the slew ring and the frame turned two ways."""
    c = new()
    iso_cylinder(c, (0, 0, 0), 9, 26, ZINC)
    iso_cylinder(c, (0, 0, 26), 12, 6, DARK_STEEL, top=shade(DARK_STEEL, 16))
    _array(c, 22, ())
    return finish(c, grain=(80, 92, 140, 255), salt=223)


def met_station():
    """A meteorological mast: the tube, the instrument boom and the anemometer on top."""
    c = new()
    iso_box(c, (-14, -14, 0), (28, 28, 4), CONCRETE)
    iso_cylinder(c, (0, 0, 4), 4.5, 74, ZINC)
    iso_box(c, (-20, -3, 40), (22, 6, 4), shade(ZINC, 10))
    for x in (-18, -10):
        iso_cylinder(c, (x, 0, 44), 5, 5, (238, 240, 244, 255))
        cx, cy = iso(x, 0, 49)
        c.aa_disc(cx, cy - 3, 4.4, GLASS)
    for i in range(3):
        a = 2.0 * math.pi * i / 3.0 + 0.5
        iso_tube(c, (0, 0, 84), (math.cos(a) * 16, math.sin(a) * 16, 84), 1.8, STEEL, steps=8)
        cx, cy = iso(math.cos(a) * 16, math.sin(a) * 16, 84)
        c.aa_disc(cx, cy - 2, 4.0, shade(STEEL, 14))
    iso_box(c, (-9, -8, 8), (18, 16, 20), SHEET, top=shade(SHEET, 16))
    return finish(c, grain=(228, 230, 234, 255), salt=227)


def utility_pole():
    """A concrete distribution pole: the tapered shaft, two crossarms and the insulators."""
    c = new()
    iso_cylinder(c, (0, 0, 0), 7, 96, CONCRETE, top=shade(CONCRETE, 14))
    for z, half in ((56, 30), (76, 22)):
        iso_box(c, (-half, -3, z), (half * 2, 6, 6), ZINC, top=shade(ZINC, 18))
        for i in range(4):
            x = -half + 4 + i * (half * 2 - 8) / 3.0
            iso_cylinder(c, (x, 0, z + 6), 4.5, 9, PORCELAIN)
            iso_disc(c, (x, 0, z + 15), 3.2, shade(PORCELAIN, 18), tone=16)
    return finish(c, grain=(214, 212, 206, 255), salt=229)


def power_box():
    """A distribution kiosk: the plinth, two green doors, the hood and its insulator."""
    c = new()
    _cabinet(c, SHEET, width=40, depth=26, height=46, door=MOSS)
    iso_cylinder(c, (0, 0, 55), 5, 11, PORCELAIN)
    iso_disc(c, (0, 0, 66), 3.6, shade(PORCELAIN, 18), tone=16)
    return finish(c, grain=(236, 238, 242, 255), salt=233)


def cab():
    """The electric cabin: a housing with a pitched roof and an insulator either side."""
    c = new()
    iso_box(c, (-30, -22, 0), (60, 44, 6), CONCRETE)
    iso_box(c, (-28, -20, 6), (56, 40, 44), SHEET, top=shade(SHEET, 16))
    iso_box(c, (-31, -23, 50), (62, 46, 5), shade(SHEET, 8), top=shade(SHEET, 26))
    for i, x in enumerate((-26, 1)):
        polygon(c, (iso(x, -20.4, 44), iso(x + 25, -20.4, 44), iso(x + 25, -20.4, 10),
                    iso(x, -20.4, 10)), shade(MOSS, -4 if i else 0))
    for y in (-12, 12):
        iso_cylinder(c, (18, y, 55), 4.5, 10, PORCELAIN)
        iso_disc(c, (18, y, 65), 3.2, shade(PORCELAIN, 18), tone=16)
    return finish(c, grain=(236, 238, 242, 255), salt=239)


def turbine_tower():
    """A tower section: a rolled cone with a bolted flange at each end and a door low down."""
    c = new()
    iso_cylinder(c, (0, 0, 4), 20, 88, (238, 240, 244, 255), top=shade((238, 240, 244, 255), -6))
    iso_cylinder(c, (0, 0, 0), 23, 5, DARK_STEEL)
    iso_cylinder(c, (0, 0, 90), 17, 5, DARK_STEEL, top=shade(DARK_STEEL, 18))
    polygon(c, (iso(-6, -19, 30), iso(6, -19, 30), iso(6, -19, 10), iso(-6, -19, 10)),
            shade((238, 240, 244, 255), -30))
    return finish(c, grain=(250, 250, 252, 255), salt=241)


def _turbine(c, tower_height, rotor, hub_length):
    """A turbine, drawn small: the tower, the nacelle and three blades from the hub.

    Drawn flat-on rather than isometric, and it is the one exception in the set: a turbine seen in
    three-quarter view has two of its three blades pointing away from the viewer, and at the size an item
    is drawn that is a white smudge.  Face on, the rotor is a recognisable three-pointed star, which is
    what a turbine *is* to anyone looking at one.
    """
    ground = SIZE * 0.94
    hub_y = ground - tower_height
    base_w, top_w = 9.0, 4.5
    polygon(c, ((SIZE * 0.5 - base_w * 0.5, ground), (SIZE * 0.5 + base_w * 0.5, ground),
                (SIZE * 0.5 + top_w * 0.5, hub_y), (SIZE * 0.5 - top_w * 0.5, hub_y)),
            (238, 240, 244, 255))
    polygon(c, ((SIZE * 0.5 - base_w * 0.5, ground), (SIZE * 0.5 - base_w * 0.15, ground),
                (SIZE * 0.5 - top_w * 0.15, hub_y), (SIZE * 0.5 - top_w * 0.5, hub_y)),
            (254, 254, 255, 255))
    # the nacelle, side on behind the rotor
    c.aa_rect(SIZE * 0.5 - 5, hub_y - 5, SIZE * 0.5 + hub_length, hub_y + 5, (232, 234, 238, 255))
    for i in range(3):
        a = math.radians(-90.0 + i * 120.0)
        tip = (SIZE * 0.5 + math.cos(a) * rotor, hub_y + math.sin(a) * rotor)
        across = (-math.sin(a), math.cos(a))
        root = 4.2
        polygon(c, ((SIZE * 0.5 + across[0] * root, hub_y + across[1] * root),
                    (SIZE * 0.5 - across[0] * root, hub_y - across[1] * root),
                    (tip[0] - across[0] * 0.8, tip[1] - across[1] * 0.8),
                    (tip[0] + across[0] * 0.8, tip[1] + across[1] * 0.8)),
                (246, 247, 250, 255))
        polygon(c, ((SIZE * 0.5 + across[0] * root, hub_y + across[1] * root),
                    (SIZE * 0.5 + across[0] * root * 0.35, hub_y + across[1] * root * 0.35),
                    (tip[0] + across[0] * 0.3, tip[1] + across[1] * 0.3),
                    (tip[0] + across[0] * 0.8, tip[1] + across[1] * 0.8)),
                (206, 210, 218, 255))
    c.aa_disc(SIZE * 0.5, hub_y, 6.0, (236, 238, 242, 255))
    c.aa_disc(SIZE * 0.5 - 1.4, hub_y - 1.4, 3.4, (252, 252, 254, 255))


def sw_10():
    """Small wind: a short tower, a small rotor and a tail vane instead of a yaw drive."""
    c = new()
    _turbine(c, 58, 26, 8)
    ground = SIZE * 0.94
    hub_y = ground - 58
    polygon(c, ((SIZE * 0.5 + 8, hub_y - 3), (SIZE * 0.5 + 26, hub_y - 13),
                (SIZE * 0.5 + 26, hub_y + 7), (SIZE * 0.5 + 8, hub_y + 3)),
            (196, 202, 212, 255))
    return finish(c, grain=(250, 250, 252, 255), salt=251)


def c52_085():
    c = new()
    _turbine(c, 66, 34, 11)
    return finish(c, grain=(250, 250, 252, 255), salt=257)


def c80_20():
    c = new()
    _turbine(c, 74, 44, 13)
    return finish(c, grain=(250, 250, 252, 255), salt=263)


def c90_30():
    c = new()
    _turbine(c, 78, 48, 14)
    return finish(c, grain=(250, 250, 252, 255), salt=269)


def c112_30():
    c = new()
    _turbine(c, 80, 52, 12)
    return finish(c, grain=(250, 250, 252, 255), salt=271)


def wind_turbine():
    c = new()
    _turbine(c, 84, 56, 15)
    return finish(c, grain=(250, 250, 252, 255), salt=277)


# ------------------------------------------------------------------ cable and tools

def _coil(c, colour, turns=3, radius=34, thickness=7.0, band=None):
    """A coil of cable, hung the way one is delivered: turns crossing at the bottom."""
    for i in range(turns):
        r = radius - i * 4.5
        steps = 60
        for k in range(steps):
            t0, t1 = k / steps, (k + 1) / steps
            a0, a1 = t0 * math.tau, t1 * math.tau
            x0, y0 = math.cos(a0) * r, math.sin(a0) * r * 0.62 - i * 3.0
            x1, y1 = math.cos(a1) * r, math.sin(a1) * r * 0.62 - i * 3.0
            lit = 0.5 + 0.5 * math.cos(a0 - 2.2)
            polygon(c, ((SIZE * 0.5 + x0, SIZE * 0.56 + y0 - thickness * 0.5),
                        (SIZE * 0.5 + x1, SIZE * 0.56 + y1 - thickness * 0.5),
                        (SIZE * 0.5 + x1, SIZE * 0.56 + y1 + thickness * 0.5),
                        (SIZE * 0.5 + x0, SIZE * 0.56 + y0 + thickness * 0.5)),
                    shade(colour, RIGHT + lit * (TOP - RIGHT + 20)))
    if band is not None:
        c.aa_rect(SIZE * 0.5 - 6, SIZE * 0.56 - radius * 0.62 - 12, SIZE * 0.5 + 6,
                  SIZE * 0.56 - radius * 0.62 + 12, band)


def dc_string_cable():
    """A coil of string cable, with a plug on the end: two black singles, as the real ones are."""
    c = new()
    _coil(c, (34, 35, 40, 255), turns=3, radius=36, thickness=6.0)
    iso_tube(c, (18, -30, 8), (40, -30, 8), 5.0, BLACK_POLY, steps=14)
    iso_tube(c, (40, -30, 8), (46, -30, 8), 5.0, RED, steps=8)
    return finish(c, grain=(110, 110, 118, 255), salt=281)


def dc_trunk_cable():
    """A coil of trunk cable: thicker, armoured, and banded where it is labelled."""
    c = new()
    _coil(c, (40, 41, 46, 255), turns=2, radius=36, thickness=11.0,
          band=(206, 168, 60, 255))
    return finish(c, grain=(120, 120, 128, 255), salt=283)


def power_wrench():
    """A ratcheting torque wrench: the head, the shaft and the grip.

    The sprite it replaces was the worst in the set - a grey L with a notch in it.  What makes a wrench
    read as a wrench is the *ring* at the end of the head and the swell of the grip, so both are drawn
    generously: this is a drawing, and a drawing may exaggerate.
    """
    c = new()
    ax, ay = SIZE * 0.30, SIZE * 0.30
    bx, by = SIZE * 0.72, SIZE * 0.76
    dx, dy = bx - ax, by - ay
    length = math.hypot(dx, dy)
    nx, ny = -dy / length, dx / length

    # the shaft
    polygon(c, ((ax + nx * 6, ay + ny * 6), (ax - nx * 6, ay - ny * 6),
                (bx - nx * 8, by - ny * 8), (bx + nx * 8, by + ny * 8)), STEEL)
    polygon(c, ((ax + nx * 6, ay + ny * 6), (ax + nx * 1.5, ay + ny * 1.5),
                (bx + nx * 2, by + ny * 2), (bx + nx * 8, by + ny * 8)), shade(STEEL, 22))

    # the ring head, and the socket in it
    c.aa_disc(ax, ay, 19, STEEL)
    c.aa_disc(ax - 2, ay - 2, 17, shade(STEEL, 20))
    c.aa_disc(ax, ay, 10, (52, 54, 58, 255))
    for i in range(6):
        a = math.radians(i * 60.0 + 15.0)
        c.aa_disc(ax + math.cos(a) * 8, ay + math.sin(a) * 8, 3.4, (36, 38, 42, 255))
    # the ratchet lever
    c.aa_disc(ax + nx * 14, ay + ny * 14, 5.0, (44, 46, 50, 255))

    # the grip: rubber, swelling towards the end, with the finger mouldings on it
    for i in range(14):
        t = i / 13.0
        px = bx - dx * 0.30 * (1.0 - t)
        py = by - dy * 0.30 * (1.0 - t)
        width = 9.0 + 3.0 * math.sin(t * math.pi)
        c.aa_disc(px, py, width, shade((42, 44, 48, 255), int(-8 + 18 * (1 - t))))
    for i in range(4):
        t = 0.18 + i * 0.20
        px, py = bx - dx * 0.30 * (1.0 - t), by - dy * 0.30 * (1.0 - t)
        polygon(c, ((px + nx * 9, py + ny * 9), (px - nx * 9, py - ny * 9),
                    (px - nx * 9 + dx * 0.02, py - ny * 9 + dy * 0.02),
                    (px + nx * 9 + dx * 0.02, py + ny * 9 + dy * 0.02)), (26, 28, 32, 255))
    c.aa_disc(bx, by, 6.5, (196, 60, 44, 255))
    return finish(c, grain=(226, 228, 232, 255), salt=293)


def wire():
    """A hank of bare stranded conductor: what the mod's own wire item is until the line conductors land.

    Drawn in the same style as everything else rather than left at the size it was, because one sprite out
    of sixty at a different resolution and a different viewpoint is exactly what made the old set look
    like a collection instead of a set.
    """
    c = new()
    _coil(c, (176, 122, 74, 255), turns=4, radius=34, thickness=5.0)
    # the strands, which is what tells a conductor from a rod: a few lighter lines across the turns
    for i in range(9):
        a = 0.5 + i * 0.62
        x0, y0 = math.cos(a) * 30, math.sin(a) * 30 * 0.62
        c.aa_line(SIZE * 0.5 + x0, SIZE * 0.56 + y0 - 3, SIZE * 0.5 + x0 * 0.86,
                  SIZE * 0.56 + y0 * 0.86 + 3, shade((176, 122, 74, 255), 26), width=1.4, alpha=0.6)
    iso_tube(c, (20, -28, 8), (42, -32, 4), 3.0, (176, 122, 74, 255), steps=12)
    return finish(c, grain=(232, 176, 124, 255), salt=317)


def weather_tablet():
    """A ruggedised tablet: the rubber bumper, the screen and the map on it."""
    c = new()
    body = (48, 50, 56, 255)
    c.aa_rect(SIZE * 0.20, SIZE * 0.12, SIZE * 0.80, SIZE * 0.88, body)
    for x in (SIZE * 0.20, SIZE * 0.76):
        c.aa_rect(x, SIZE * 0.12, x + SIZE * 0.04, SIZE * 0.88, (32, 34, 38, 255))
    c.aa_rect(SIZE * 0.26, SIZE * 0.18, SIZE * 0.74, SIZE * 0.78, (18, 26, 30, 255))
    field = Field(SIZE, cell=13.0, octaves=3, salt=307)
    for y in range(int(SIZE * 0.19), int(SIZE * 0.77)):
        for x in range(int(SIZE * 0.27), int(SIZE * 0.73)):
            v = field.at(x, y)
            if v > 0.62:
                c.blend(x, y, (74, 190, 132, 255), min(1.0, (v - 0.62) * 5.0))
            elif v > 0.5:
                c.blend(x, y, (52, 110, 168, 255), 0.6)
    c.aa_rect(SIZE * 0.26, SIZE * 0.80, SIZE * 0.74, SIZE * 0.85, (72, 76, 84, 255))
    sheen(c, 0, 0, SIZE, SIZE, alpha=0.18)
    return finish(c, grain=(120, 124, 132, 255), salt=311)


SPRITES = {
    'steel_plate': steel_plate, 'steel_section': steel_section, 'busbar': busbar,
    'copper_coil': copper_coil, 'resin': resin, 'silicon_ingot': silicon_ingot,
    'silicon_wafer': silicon_wafer, 'tempered_glass': tempered_glass,
    'solar_cell': solar_cell, 'bypass_diode': bypass_diode, 'mc4_connector': mc4_connector,
    'junction_box': junction_box, 'power_module': power_module, 'capacitor_bank': capacitor_bank,
    'magnetic_core': magnetic_core, 'circuit_board': circuit_board, 'control_board': control_board,
    'cpu': cpu, 'screen': screen, 'insulator': insulator, 'metal_casing': metal_casing,
    'motor_core': motor_core, 'bearing': bearing, 'gear_set': gear_set, 'gpv_fuse': gpv_fuse,
    'load_break_switch': load_break_switch, 'sensor_head': sensor_head,
    'pv_laminate': pv_laminate, 'enclosure': enclosure, 'dc_section': dc_section,
    'inverter_bridge': inverter_bridge, 'mounting_rack': mounting_rack,
    'torque_tube': torque_tube, 'slew_drive': slew_drive, 'generator_set': generator_set,
    'gearbox': gearbox, 'turbine_blade': turbine_blade, 'long_blade': long_blade,
    'pv_inverter': pv_inverter, 'pv_combiner': pv_combiner, 'pv_flat': pv_flat,
    'pv_tilt': pv_tilt, 'pv_track': pv_track, 'pv_dual': pv_dual, 'met_station': met_station,
    'utility_pole': utility_pole, 'power_box': power_box, 'cab': cab,
    'turbine_tower': turbine_tower, 'sw_10': sw_10, 'c52_085': c52_085, 'c80_20': c80_20,
    'c90_30': c90_30, 'c112_30': c112_30, 'wind_turbine': wind_turbine,
    'dc_string_cable': dc_string_cable, 'dc_trunk_cable': dc_trunk_cable,
    'power_wrench': power_wrench, 'weather_tablet': weather_tablet, 'wire': wire,
}


def main():
    wanted = [a for a in sys.argv[1:] if not a.startswith('-')]
    for name, builder in sorted(SPRITES.items()):
        if wanted and name not in wanted:
            continue

        canvas = builder()
        path = os.path.join(OUT, name + '.png')
        canvas.write(path)
        print('item/%-22s %3d x %3d  %4d KB' % (name + '.png', canvas.w, canvas.h,
                                                os.path.getsize(path) // 1024))
    print('%d sprites' % len(SPRITES))


if __name__ == '__main__':
    main()
