#!/usr/bin/env python3
"""Generates every block texture the mod's own models wear.

    python3 tools/gen_block_textures.py                 # all of them
    python3 tools/gen_block_textures.py pv_module pole_concrete

Writes into src/main/resources/assets/electricity/textures/block/.

What changed, and why it is a separate file now
-----------------------------------------------
These used to live at the top of ``gen_pv_textures.py`` and be drawn at 128 pixels a tile out of
hard rectangles over per-pixel white noise.  A player looking at the game said the turbine and the
cabin read well and the rest did not, and the difference is not the resolution: it is that those
two are *materials* - a metal with a grain in it, a concrete with aggregate and a rust stain - and
these were pictures of materials.  So the drawing primitives moved into ``texlib.py``, which has
value noise over octaves, soft edges and a light direction, and every surface here is built from
those.  ``gen_pv_textures.py`` keeps the item sprites and the panel layouts, where sixteen hard
pixels is the right answer.

The sizes below are chosen by how close a player gets to the face, not by uniformity: a module's
glass is looked at from a metre and gets 1024, a cabinet's side sheet gets 512, and a switch boss
three centimetres across gets 128 because more texels than that are texels nobody can resolve.

Where a texture is shared, it is shared on purpose: the crossarm on a pole, a tracker's pier and a
mast are all hot-dip galvanised steel, and drawing three of those would be three surfaces that
should be one.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from texlib import (Canvas, Field, LIGHT, bevel, brushed, concrete, cross_hatch, dome, galvanised,
                    grime, groove, hash01, hex_head, louvre, mesh_screen, mix, mul, plate_label,
                    porcelain, powder, rubber, rust, screw, shade, streak, stretched,
                    warning_triangle, wood)

OUT = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'textures', 'block')

# The palette the whole plant is painted from.  One list, so a cabinet, a kiosk door and an
# inverter lid cannot drift into three different greys.
SHEET = (196, 200, 204, 255)          # RAL 7035 light grey: switchgear everywhere is this colour
MOSS = (46, 68, 52, 255)              # RAL 6005 moss green: the door colour on the cabin, matched
ALU = (170, 174, 180, 255)            # mill-finish aluminium
ZINC = (150, 155, 158, 255)           # hot-dip galvanised steel
DARK_ALU = (128, 132, 138, 255)       # a casting rather than a rolled sheet
CELL = (26, 32, 62, 255)              # a monocrystalline cell under an anti-reflective coat
GLASS_BLUE = (128, 160, 182, 255)
INK = (44, 47, 52, 255)
COPPER = (168, 96, 48, 255)


# ------------------------------------------------------------------ the laminate

def pv_module():
    """The sun side of a laminate: 144 half-cut cells, three ribbons apiece, under glass.

    Six columns of twenty-four half-cells with a bus gap across the middle, which is a 2278 by
    1134 module - the size nearly every utility-scale product has settled on.  The texture is
    square and the module is not, and that is deliberate: the face it is mapped onto is 0.42 by
    0.94 of a block, so a cell drawn a sixth of the width by a twenty-fourth of the height comes
    out very nearly the 189 by 95 millimetres a half-cut cell really is.

    What makes it read as glass rather than as a grid: the cells are not all the same colour,
    because a real laminate's cells are sorted by current and not by shade; the fingers are
    hairlines rather than pixels; and there is a broad soft reflection across it, which is the
    one thing every photograph of a panel has and no drawing of one did.
    """
    size = 1024
    c = Canvas(size, size, CELL)
    frame_w = size * 0.030
    gap = size * 0.006                # the white lane between cells, which is backsheet showing
    cols, rows = 6, 24

    # the backsheet the cells sit on, seen in the lanes between them
    back = Canvas(size, size)
    powder(back, (238, 239, 241, 255), salt=7, peel=4)
    c.each(lambda x, y: back.get(x, y))

    inner0 = frame_w + gap
    inner1 = size - frame_w - gap
    span_x = (inner1 - inner0) / cols
    span_y = (inner1 - inner0) / rows
    bus = size * 0.010                # the bus gap across the middle of a half-cut laminate

    grain = Field(size, cell=size / 3.0, octaves=3, salt=13)
    for row in range(rows):
        for col in range(cols):
            x0 = inner0 + col * span_x
            y0 = inner0 + row * span_y
            # the middle two rows are pushed apart by the bus gap
            y0 += -bus if row < rows / 2 else bus
            x1, y1 = x0 + span_x - gap, y0 + span_y - gap
            tint = (hash01(col, row, 7) - 0.5) * 16
            base = shade(CELL, tint)
            c.aa_rect(x0, y0, x1, y1, base)
            # the chamfer: a pseudo-square wafer is a round ingot with four flats cut off it, and
            # the corners it does not fill are the clearest sign of a monocrystalline cell
            chamfer = span_y * 0.42
            for cx, cy, sx, sy in ((x0, y0, 1, 1), (x1, y0, -1, 1), (x0, y1, 1, -1), (x1, y1, -1, -1)):
                for i in range(int(chamfer)):
                    t = i / max(1.0, chamfer)
                    c.aa_rect(cx, cy + sy * i, cx + sx * chamfer * (1.0 - t), cy + sy * (i + 1),
                              back.get(int(min(size - 1, max(0, cx))), int(min(size - 1, max(0, cy)))))
            # a very slight per-cell gradient, which is the coating thickness varying across a wafer
            for i in range(int(span_y)):
                t = i / max(1.0, span_y)
                c.aa_rect(x0, y0 + i, x1, y0 + i + 1, shade(base, -3 + t * 6), alpha=0.5)
            # the fine fingers: many, hairline, and slightly brighter than the cell
            fingers = 22
            for f in range(1, fingers):
                fy = y0 + (y1 - y0) * f / fingers
                c.aa_rect(x0 + span_x * 0.02, fy, x1 - span_x * 0.02, fy + size * 0.0012,
                          shade(base, 30), alpha=0.55)
            # three interconnect ribbons down the cell, tinned copper, catching the light
            for r in (0.2, 0.5, 0.8):
                rx = x0 + (x1 - x0) * r
                w = size * 0.0035
                c.aa_rect(rx - w, y0 - gap, rx + w, y1 + gap, (206, 210, 216, 255), alpha=0.92)
                c.aa_rect(rx - w * 0.35, y0 - gap, rx + w * 0.35, y1 + gap, (238, 242, 248, 255), alpha=0.8)

    # the two bus bars the strings are joined by, across the middle and along each end
    for y in (size * 0.5, inner0 - gap * 0.5, inner1 + gap * 0.5):
        c.aa_rect(inner0 - gap, y - size * 0.005, inner1 + gap, y + size * 0.005, (198, 203, 210, 255))
        c.aa_rect(inner0 - gap, y - size * 0.0018, inner1 + gap, y + size * 0.0018, (232, 236, 242, 255),
                  alpha=0.7)

    # The glass: a broad soft reflection of the sky, brighter at the top, plus the faint bloom of
    # an anti-reflective coat.  This is the pass that turns a grid of cells into a panel.
    sheen = Field(size, cell=size * 0.9, octaves=2, salt=29)

    def glaze(x, y, base):
        if base[3] == 0:
            return base
        down = y / size
        sky = (1.0 - down) ** 2 * 0.13 + sheen.at(x, y) * 0.05
        # a long diagonal highlight, the one a pane of glass outdoors always has
        band = math.exp(-(((x - y * 0.55) / size - 0.24) ** 2) / 0.010) * 0.16
        return mix(base, (188, 214, 240, 255), sky + band)

    c.over(glaze)

    # the anodised frame round the outside, with the glass line just inside it
    _frame_border(c, frame_w)
    grime(c, salt=31, amount=0.07, colour=(120, 118, 112, 255))
    return c


def _frame_border(c, width):
    """The module's aluminium frame, seen from the front: a flat top with a shadow inside it."""
    size = c.w
    edge = Canvas(size, size)
    brushed(edge, ALU, grain=11, blotch=7, salt=3)
    for i in range(int(width)):
        t = i / max(1.0, width)
        tone = 16 - t * 40
        for x0, y0, x1, y1 in ((0, i, size, i + 1), (0, size - i - 1, size, size - i),
                               (i, 0, i + 1, size), (size - i - 1, 0, size - i, size)):
            for y in range(int(y0), int(y1)):
                for x in range(int(x0), int(x1)):
                    c.blend(x, y, shade(edge.get(x, y), tone), 1.0)
    # the dark line where the laminate meets the frame's inner lip
    c.aa_rect(width, width, size - width, width + size * 0.004, (0, 0, 0, 255), alpha=0.45)
    c.aa_rect(width, size - width - size * 0.004, size - width, size - width, (0, 0, 0, 255), alpha=0.45)
    c.aa_rect(width, width, width + size * 0.004, size - width, (0, 0, 0, 255), alpha=0.45)
    c.aa_rect(size - width - size * 0.004, width, size - width, size - width, (0, 0, 0, 255), alpha=0.45)


def pv_module_back():
    """The back of a monofacial module: white backsheet, junction box, two leads, a barcode.

    Not the front again, and not a bifacial back either - the products in this catalogue are
    framed monofacial laminates, so the back is a polymer sheet with the cell grid faintly
    showing through it and one junction box a third of the way down.
    """
    size = 512
    c = Canvas(size, size)
    powder(c, (232, 233, 235, 255), salt=41, peel=5)

    frame_w = size * 0.030
    inner0, inner1 = frame_w, size - frame_w
    cols, rows = 6, 24
    span_x = (inner1 - inner0) / cols
    span_y = (inner1 - inner0) / rows
    # the cells showing through the backsheet: a real one is thin enough that the grid reads as a
    # very slightly darker rectangle with a bright lane between
    for row in range(rows):
        for col in range(cols):
            x0 = inner0 + col * span_x + span_x * 0.04
            y0 = inner0 + row * span_y + span_y * 0.06
            c.aa_rect(x0, y0, x0 + span_x * 0.92, y0 + span_y * 0.88, (216, 217, 220, 255), alpha=0.75)

    # the junction box: a black polymer box with a lid, off centre where a real one is
    bx, by = size * 0.40, size * 0.30
    bw, bh = size * 0.20, size * 0.13
    c.aa_rect(bx, by, bx + bw, by + bh, (34, 35, 38, 255))
    bevel(c, bx, by, bx + bw, by + bh, size * 0.010, lift=34, drop=40)
    c.aa_rect(bx + bw * 0.1, by + bh * 0.16, bx + bw * 0.9, by + bh * 0.84, (26, 27, 30, 255))
    for i in range(4):
        screw(c, bx + bw * (0.12 + 0.25 * i), by + bh * 0.5, size * 0.006, (86, 88, 92, 255))
    # the two leads out of it, and the connectors on their ends
    for side, y in ((-1, by + bh * 0.35), (1, by + bh * 0.68)):
        c.aa_line(bx + bw, y, bx + bw + size * 0.13, y + side * size * 0.05, (28, 28, 32, 255),
                  width=size * 0.018)
        c.aa_line(bx + bw + size * 0.13, y + side * size * 0.05, bx + bw + size * 0.13,
                  y + side * size * 0.16, (28, 28, 32, 255), width=size * 0.018)
        cap = (188, 42, 38, 255) if side < 0 else (30, 30, 34, 255)
        c.aa_rect(bx + bw + size * 0.118, y + side * size * 0.155, bx + bw + size * 0.142,
                  y + side * size * 0.20, cap)

    # the label: a barcode and a block of type, which is what the back of every module carries
    lx, ly = size * 0.12, size * 0.60
    plate_label(c, lx, ly, lx + size * 0.34, ly + size * 0.14, (244, 245, 246, 255), lines=4)
    for i in range(26):
        w = size * (0.002 + hash01(i, 3, 53) * 0.004)
        x = lx + size * 0.02 + i * size * 0.0115
        c.aa_rect(x, ly + size * 0.105, x + w, ly + size * 0.132, INK)

    _frame_border(c, frame_w)
    grime(c, salt=59, amount=0.10, colour=(150, 146, 138, 255))
    return c


def pv_module_edge():
    """The frame in profile: the extrusion, not the cells.

    An aluminium module frame is a channel - a lip over the glass, a web with a groove pressed
    into it for stiffness, and a return at the bottom the mid-clamp grips.  Symmetric about the
    middle, so it reads the same whichever way up a face maps it, and with the drainage notch a
    real frame has.
    """
    size = 256
    c = Canvas(size, size)
    brushed(c, ALU, grain=12, blotch=8, salt=17)

    # the dark line of the laminate at each lip
    for y0 in (0.0, size * 0.90):
        c.aa_rect(0, y0, size, y0 + size * 0.10, shade(ALU, -58))
        c.aa_rect(0, y0 + size * (0.10 if y0 == 0 else -0.01), size, y0 + size * (0.115 if y0 == 0 else 0.005),
                  shade(ALU, 26))
    # the two grooves of the extrusion
    for y in (size * 0.30, size * 0.70):
        groove(c, 0, y, size, y, size * 0.022, dark=58, light=34)
    # the bright rib down the middle
    c.aa_rect(0, size * 0.48, size, size * 0.52, shade(ALU, 18))
    # the drainage notch: one hole per frame, and a stain under it
    c.aa_disc(size * 0.72, size * 0.50, size * 0.030, shade(ALU, -80))
    c.aa_disc(size * 0.72, size * 0.50, size * 0.020, (18, 18, 20, 255))
    streak(c, size * 0.72, size * 0.53, size * 0.90, size * 0.05, alpha=0.22, salt=61)
    grime(c, salt=67, amount=0.12)
    return c


# ------------------------------------------------------------------ the metals

def pv_frame():
    """Anodised aluminium rail: the racking every module is clamped to.

    A T-slot rail seen from outside is a flat face with the slot's shadow line down it and the
    bolt heads of the clamps along that line.
    """
    size = 512
    c = Canvas(size, size)
    brushed(c, ALU, grain=13, blotch=9, salt=3)
    # the slot: a dark channel with a bright lip either side, along the rail
    groove(c, 0, size * 0.44, size, 0, size * 0.020, dark=64, light=30)
    c.aa_rect(0, size * 0.40, size, size * 0.425, shade(ALU, 14))
    # the clamp bolts along it
    for i in range(4):
        hex_head(c, size * (0.12 + 0.25 * i), size * 0.465, size * 0.030, shade(ALU, -14), salt=71 + i)
    grime(c, salt=73, amount=0.10)
    return c


def pv_steel():
    """Hot-dip galvanised steel: piers, torque tubes, crossarms, masts.

    The one surface shared by every structural part in the mod, which is what it should be - they
    are all the same steel out of the same bath.  Spangle, a faint rolling direction, and a
    freckle of rust here and there, because a driven pier has been outdoors since the day it went in.
    """
    size = 512
    c = Canvas(size, size)
    galvanised(c, ZINC, salt=5)
    # the rolling direction, very faint, which is what keeps it from looking like stone
    roll = stretched(size, along=size * 1.6, across=size / 70.0, octaves=2, salt=83)
    c.over(lambda x, y, base: shade(base, roll.signed(x, y) * 6))
    for i in range(3):
        x = size * (0.15 + hash01(i, 1, 89) * 0.7)
        y = size * (0.10 + hash01(i, 2, 91) * 0.7)
        rust(c, x, y, x + size * 0.09, y + size * 0.06, salt=97 + i, alpha=0.30)
    grime(c, salt=101, amount=0.14)
    return c


def pv_plate():
    """A flat galvanised plate: a base plate, a gland plate, a bolted flange face.

    Its own texture rather than the sheet, because a plate is *seen as a plate*: it has a bolt
    near each corner and a wear ring where whatever stands on it has been standing.
    """
    size = 256
    c = Canvas(size, size)
    galvanised(c, shade(ZINC, -6), salt=103)
    cross_hatch(c, 0, 0, size, size, size * 0.16, shade(ZINC, 14), width=size * 0.012, alpha=0.18)
    for x, y in ((0.14, 0.14), (0.86, 0.14), (0.14, 0.86), (0.86, 0.86)):
        c.aa_disc(size * x, size * y, size * 0.055, shade(ZINC, -34), alpha=0.6)
        hex_head(c, size * x, size * y, size * 0.042, shade(ZINC, 6), salt=107)
    grime(c, salt=109, amount=0.18)
    return c


def pv_steel_end():
    """A tube end: the flange face a cylinder's cap shows.

    The pipeline maps a cap by the ring's own coordinates, so the circle inscribed in this
    texture is what is seen and the corners are never drawn.  Which means this wants to be a
    *round* picture - a flange plate with a bolt circle and the tube's wall thickness showing as
    a ring - and drawing a square plate here, as it was, put the plate's corners nowhere.
    """
    size = 256
    c = Canvas(size, size)
    galvanised(c, shade(ZINC, -4), salt=113, spangle=False)
    mid = size / 2.0
    # the flange, the wall of the tube inside it, and the dark bore
    c.aa_disc(mid, mid, size * 0.49, shade(ZINC, 8))
    c.aa_disc(mid, mid, size * 0.49, shade(ZINC, -40), alpha=0.9, inner=size * 0.44)
    c.aa_disc(mid, mid, size * 0.36, shade(ZINC, -12))
    c.aa_disc(mid, mid, size * 0.30, shade(ZINC, -66))
    # a bolt circle, because that is how a flange is made up
    for i in range(8):
        a = i * math.pi / 4 + math.pi / 8
        hex_head(c, mid + math.cos(a) * size * 0.41, mid + math.sin(a) * size * 0.41,
                 size * 0.038, shade(ZINC, 4), salt=127 + i)
    grime(c, salt=131, amount=0.16)
    return c


# ------------------------------------------------------------------ painted sheet

def pv_cabinet():
    """Powder-coated sheet steel: the side of every cabinet in the plant.

    RAL 7035, the light grey all switchgear is, with the orange peel a cured coat has, one swage
    line to stop a large panel drumming, and the fixings down each edge.
    """
    size = 512
    c = Canvas(size, size)
    powder(c, SHEET, salt=31)
    groove(c, 0, size * 0.50, size, 0, size * 0.016, dark=30, light=18)
    for i in range(5):
        y = size * (0.10 + 0.20 * i)
        for x in (size * 0.045, size * 0.955):
            screw(c, x, y, size * 0.016, shade(SHEET, -40))
    grime(c, salt=137, amount=0.13, colour=(96, 94, 88, 255))
    # rain runs down a vertical panel, so the dirt is streaked and not only patchy
    for i in range(6):
        streak(c, size * hash01(i, 4, 139), size * hash01(i, 5, 149) * 0.3, size, size * 0.03,
               alpha=0.11, salt=151 + i)
    return c


def pv_cabinet_top():
    """A cabinet's rain hood, seen from above: a crowned lid, a drip edge, standing water.

    The one face of a cabinet a player looks straight down on, and the only one where dirt
    collects rather than running off - so it is the dirtiest surface in the mod.
    """
    size = 512
    c = Canvas(size, size)
    powder(c, shade(SHEET, 6), salt=157)
    # the crown: a lid is domed so water leaves it, which reads as a bright ridge down the middle
    for y in range(size):
        t = abs(y / size - 0.5) * 2.0
        c.aa_rect(0, y, size, y + 1, (255, 255, 255, 255), alpha=(1.0 - t) * 0.10)
    # the drip edge all round, turned down over the sides
    for i in range(int(size * 0.035)):
        t = i / (size * 0.035)
        tone = -50 + t * 30
        for x0, y0, x1, y1 in ((0, i, size, i + 1), (0, size - i - 1, size, size - i),
                               (i, 0, i + 1, size), (size - i - 1, 0, size - i, size)):
            c.aa_rect(x0, y0, x1, y1, (0, 0, 0, 255), alpha=abs(tone) / 255.0 * 0.8)
    for x, y in ((0.09, 0.09), (0.91, 0.09), (0.09, 0.91), (0.91, 0.91)):
        hex_head(c, size * x, size * y, size * 0.024, shade(SHEET, -26), salt=163)
    # where water has stood and dried, which is a stain rather than a ring: a hard ring reads as
    # a bubble drawn on the lid
    for i in range(5):
        cx, cy = size * hash01(i, 6, 167), size * hash01(i, 7, 173)
        r = size * (0.08 + hash01(i, 8, 179) * 0.12)
        for k in range(6):
            c.aa_disc(cx, cy, r * (1.0 - k * 0.14), (104, 100, 92, 255), alpha=0.035)
        c.aa_disc(cx, cy, r, (92, 88, 80, 255), alpha=0.10, inner=r * 0.90)
    grime(c, salt=181, amount=0.30, colour=(92, 88, 78, 255))
    return c


def pv_cabinet_door(plain=False):
    """The one face of an inverter anybody stands in front of: plate, louvres, handle, LEDs.

    Everything on it is where it is on a real machine - the rating plate high on the left where
    it can be read, the status LEDs beside it, the ventilation low down where cool air is drawn
    in, the handle on the leading edge and the hinges opposite.

    ``plain`` drops the plate and the lights, which is the *second* leaf of a two-door cabinet.  A
    machine with a rating plate and a set of status lights on each of its two doors reads as two
    machines bolted together, and that is exactly how it looked when both leaves took this texture.
    """
    size = 512
    c = Canvas(size, size)
    powder(c, SHEET, salt=191)
    # the door's own line: a return all round, which is what makes it read as a door and not a face
    bevel(c, size * 0.02, size * 0.02, size * 0.98, size * 0.98, size * 0.016, lift=30, drop=34)

    if not plain:
        plate_label(c, size * 0.10, size * 0.10, size * 0.52, size * 0.30, SHEET, lines=5)
        # the status lights: run, grid, fault, which is the set every inverter has
        for i, colour in enumerate(((62, 196, 92, 255), (78, 168, 236, 255), (226, 92, 62, 255))):
            cy = size * (0.13 + i * 0.07)
            c.aa_disc(size * 0.60, cy, size * 0.026, (40, 42, 46, 255))
            dome(c, size * 0.60, cy, size * 0.020, colour, lift=40, drop=20)
            c.aa_rect(size * 0.64, cy - size * 0.006, size * 0.78, cy + size * 0.006,
                      shade(SHEET, -34))

    # the louvre panel low on the door, with its screen behind
    lx0, ly0, lx1, ly1 = size * 0.10, size * 0.55, size * 0.66, size * 0.88
    c.aa_rect(lx0 - size * 0.014, ly0 - size * 0.014, lx1 + size * 0.014, ly1 + size * 0.014,
              shade(SHEET, -18))
    bevel(c, lx0 - size * 0.014, ly0 - size * 0.014, lx1 + size * 0.014, ly1 + size * 0.014,
          size * 0.008, lift=24, drop=30)
    mesh_screen(c, lx0, ly0, lx1, ly1, size * 0.012, SHEET, alpha=0.9)
    louvre(c, lx0, ly0, lx1, ly1, 7, shade(SHEET, -4))

    # the handle, on the edge away from the hinges
    hx = size * 0.86
    c.aa_rect(hx - size * 0.05, size * 0.40, hx + size * 0.05, size * 0.62, shade(SHEET, -14))
    bevel(c, hx - size * 0.05, size * 0.40, hx + size * 0.05, size * 0.62, size * 0.010)
    c.aa_rect(hx - size * 0.022, size * 0.44, hx + size * 0.022, size * 0.58, (72, 74, 78, 255))
    dome(c, hx, size * 0.51, size * 0.030, (188, 192, 198, 255), lift=44, drop=30)
    # and the lock barrel under it
    c.aa_disc(hx, size * 0.66, size * 0.022, (86, 88, 92, 255))
    c.aa_rect(hx - size * 0.014, size * 0.657, hx + size * 0.014, size * 0.663, (30, 30, 32, 255))

    # the hinges
    for y in (size * 0.16, size * 0.84):
        c.aa_rect(size * 0.955, y - size * 0.05, size * 0.995, y + size * 0.05, shade(SHEET, -30))
        for k in range(2):
            screw(c, size * 0.975, y + (k - 0.5) * size * 0.06, size * 0.012, shade(SHEET, -50))

    grime(c, salt=193, amount=0.12, colour=(96, 94, 88, 255))
    for i in range(4):
        streak(c, size * (0.2 + 0.2 * i), size * 0.30, size, size * 0.025, alpha=0.09, salt=197 + i)
    return c


def pv_combiner_door():
    """A string combiner's door: the fuse window, the switch escutcheon, the ways label.

    A field combiner is a polyester enclosure with a window over the fuse ways so a technician
    can see which one has gone without opening it, and a rotary switch through the door to take
    the group off.  Both are on this face and nothing else is.
    """
    size = 512
    c = Canvas(size, size)
    powder(c, shade(SHEET, -6), salt=199)
    bevel(c, size * 0.03, size * 0.03, size * 0.97, size * 0.97, size * 0.018, lift=28, drop=32)

    # the window: smoked polycarbonate with the fuse holders behind it
    wx0, wy0, wx1, wy1 = size * 0.10, size * 0.16, size * 0.62, size * 0.56
    c.aa_rect(wx0, wy0, wx1, wy1, (34, 36, 40, 255))
    for i in range(8):
        x = wx0 + size * 0.035 + i * (wx1 - wx0 - size * 0.05) / 8
        c.aa_rect(x, wy0 + size * 0.06, x + size * 0.035, wy1 - size * 0.06, (58, 60, 66, 255))
        c.aa_rect(x, wy0 + size * 0.06, x + size * 0.035, wy0 + size * 0.10, (216, 176, 44, 255))
        c.aa_rect(x + size * 0.008, wy0 + size * 0.13, x + size * 0.027, wy1 - size * 0.10,
                  (196, 198, 202, 255))
    # the glazing: a reflection across the pane, which is what says there is glass in front
    for y in range(int(wy0), int(wy1)):
        t = (y - wy0) / (wy1 - wy0)
        c.aa_rect(wx0, y, wx1, y + 1, (150, 176, 204, 255), alpha=0.20 * (1.0 - t) ** 2 + 0.04)
    bevel(c, wx0, wy0, wx1, wy1, size * 0.012, lift=20, drop=44)

    # the isolator escutcheon: the yellow plate a red handle turns on
    ex, ey = size * 0.80, size * 0.30
    c.aa_rect(ex - size * 0.115, ey - size * 0.115, ex + size * 0.115, ey + size * 0.115,
              (222, 190, 32, 255))
    bevel(c, ex - size * 0.115, ey - size * 0.115, ex + size * 0.115, ey + size * 0.115, size * 0.010)
    c.aa_disc(ex, ey, size * 0.070, (40, 42, 46, 255))
    for label, ax, ay in (('on', 0.0, -1.0), ('off', 1.0, 0.0)):
        c.aa_rect(ex + ax * size * 0.088 - size * 0.010, ey + ay * size * 0.088 - size * 0.010,
                  ex + ax * size * 0.088 + size * 0.010, ey + ay * size * 0.088 + size * 0.010, INK)

    plate_label(c, size * 0.10, size * 0.66, size * 0.62, size * 0.86, shade(SHEET, -6), lines=4)
    # the ways count, as a row of numbered ticks under the window
    for i in range(8):
        x = size * 0.12 + i * size * 0.062
        c.aa_rect(x, size * 0.60, x + size * 0.018, size * 0.625, INK)

    for y in (size * 0.14, size * 0.86):
        c.aa_rect(size * 0.955, y - size * 0.05, size * 0.995, y + size * 0.05, shade(SHEET, -34))
    grime(c, salt=211, amount=0.14, colour=(94, 92, 86, 255))
    return c


def pv_vent():
    """A louvre panel with a screen behind it, framed: the cooling face of a cabinet."""
    size = 512
    c = Canvas(size, size)
    powder(c, shade(SHEET, -10), salt=223)
    inset = size * 0.07
    mesh_screen(c, inset, inset, size - inset, size - inset, size * 0.014, SHEET, alpha=0.95)
    louvre(c, inset, inset, size - inset, size - inset, 9, shade(SHEET, -6))
    # the frame round it, with the fixings that hold it on
    for i in range(int(inset * 0.5)):
        t = i / (inset * 0.5)
        for x0, y0, x1, y1 in ((0, i, size, i + 1), (0, size - i - 1, size, size - i),
                               (i, 0, i + 1, size), (size - i - 1, 0, size - i, size)):
            c.aa_rect(x0, y0, x1, y1, (255, 255, 255, 255) if i < inset * 0.2 else (0, 0, 0, 255),
                      alpha=(0.12 if i < inset * 0.2 else 0.10) * (1 - t))
    for x, y in ((0.045, 0.045), (0.955, 0.045), (0.045, 0.955), (0.955, 0.955)):
        screw(c, size * x, size * y, size * 0.018, shade(SHEET, -44))
    grime(c, salt=227, amount=0.22, colour=(88, 86, 80, 255))
    return c


def pv_dc_section():
    """An inverter's direct-current compartment: fuse ways behind a window, glands under them.

    Wider than it is tall, because that is the shape of the compartment across the bottom of a
    central inverter's front, and drawn at its own aspect so nothing is stretched onto it.
    """
    w, h = 512, 256
    c = Canvas(w, h)
    powder(c, shade(SHEET, -4), salt=229)
    bevel(c, w * 0.02, h * 0.04, w * 0.98, h * 0.96, w * 0.010, lift=26, drop=30)

    # the ways: a fuse holder per string, in a row, behind a smoked window
    wx0, wy0, wx1, wy1 = w * 0.06, h * 0.18, w * 0.72, h * 0.70
    c.aa_rect(wx0, wy0, wx1, wy1, (36, 38, 42, 255))
    ways = 10
    for i in range(ways):
        x = wx0 + w * 0.012 + i * (wx1 - wx0 - w * 0.024) / ways
        span = (wx1 - wx0 - w * 0.024) / ways
        c.aa_rect(x, wy0 + h * 0.08, x + span * 0.7, wy1 - h * 0.10, (62, 64, 70, 255))
        c.aa_rect(x, wy0 + h * 0.08, x + span * 0.7, wy0 + h * 0.18, (214, 174, 46, 255))
        c.aa_rect(x + span * 0.18, wy0 + h * 0.24, x + span * 0.52, wy1 - h * 0.18, (198, 200, 204, 255))
    for y in range(int(wy0), int(wy1)):
        t = (y - wy0) / (wy1 - wy0)
        c.aa_rect(wx0, y, wx1, y + 1, (148, 174, 202, 255), alpha=0.18 * (1.0 - t) ** 2 + 0.03)
    bevel(c, wx0, wy0, wx1, wy1, w * 0.008, lift=18, drop=40)

    plate_label(c, w * 0.76, h * 0.18, w * 0.95, h * 0.52, shade(SHEET, -4), lines=3)
    # the gland plate along the bottom, which is where every string actually arrives
    c.aa_rect(w * 0.04, h * 0.76, w * 0.96, h * 0.94, shade(ZINC, -8))
    for i in range(10):
        x = w * (0.08 + i * 0.088)
        dome(c, x, h * 0.85, w * 0.020, (46, 48, 52, 255), lift=30, drop=24)
    grime(c, salt=233, amount=0.16)
    return c


def pv_display():
    """An inverter's screen: a monochrome panel behind glass, with a bar and a readout."""
    size = 256
    c = Canvas(size, size)
    powder(c, shade(SHEET, -30), salt=239)
    # the bezel, then the glass, then what is behind the glass
    c.aa_rect(size * 0.06, size * 0.10, size * 0.94, size * 0.90, (26, 30, 28, 255))
    bevel(c, size * 0.06, size * 0.10, size * 0.94, size * 0.90, size * 0.03, lift=16, drop=48)
    lit = (96, 226, 128, 255)
    # a four-digit readout
    for d in range(4):
        x = size * (0.14 + d * 0.19)
        for seg in ((0.0, 0.0, 0.13, 0.02), (0.0, 0.0, 0.02, 0.15), (0.11, 0.0, 0.13, 0.15),
                    (0.0, 0.14, 0.13, 0.16), (0.0, 0.14, 0.02, 0.30), (0.11, 0.14, 0.13, 0.30),
                    (0.0, 0.29, 0.13, 0.31)):
            on = hash01(d, int(seg[1] * 100) + int(seg[0] * 10), 241) > 0.28
            c.aa_rect(x + size * seg[0], size * 0.22 + size * seg[1], x + size * seg[2],
                      size * 0.22 + size * seg[3], lit if on else (34, 52, 40, 255))
    # a bar graph under it
    c.aa_rect(size * 0.14, size * 0.66, size * 0.86, size * 0.78, (32, 48, 38, 255))
    for i in range(12):
        x = size * (0.155 + i * 0.058)
        c.aa_rect(x, size * 0.675, x + size * 0.042, size * 0.765, lit if i < 8 else (38, 58, 44, 255))
    # the glass over it: one hard diagonal glare, which is the whole difference between a screen
    # and a green rectangle
    for y in range(int(size * 0.10), int(size * 0.90)):
        for x in range(int(size * 0.06), int(size * 0.94)):
            band = math.exp(-(((x - y * 0.8) / size + 0.10) ** 2) / 0.004)
            if band > 0.02:
                c.blend(x, y, (208, 226, 236, 255), band * 0.30)
    return c


def pv_instrument():
    """A radiometer or a screen housing: white anodised aluminium, machined, with a black band.

    Instrument white rather than cabinet grey, because that is what every radiation instrument
    in the world is painted - it has to reflect rather than absorb, or it measures its own warmth.
    """
    size = 256
    c = Canvas(size, size)
    brushed(c, (238, 240, 243, 255), grain=7, blotch=5, salt=251)
    # the turned rings a machined body carries
    for i in range(7):
        y = size * (0.10 + i * 0.13)
        c.aa_rect(0, y, size, y + size * 0.008, shade((238, 240, 243, 255), -26), alpha=0.6)
        c.aa_rect(0, y + size * 0.008, size, y + size * 0.016, (255, 255, 255, 255), alpha=0.5)
    # the black band round the middle, which is the level bubble and the label ring
    c.aa_rect(0, size * 0.46, size, size * 0.58, (42, 44, 48, 255))
    c.aa_disc(size * 0.30, size * 0.52, size * 0.035, (222, 226, 230, 255))
    c.aa_disc(size * 0.30, size * 0.52, size * 0.018, (150, 200, 170, 255))
    plate_label(c, size * 0.46, size * 0.475, size * 0.90, size * 0.555, (86, 88, 92, 255), lines=2)
    grime(c, salt=257, amount=0.10, colour=(140, 138, 132, 255))
    return c


def pv_dome():
    """A radiometer's glass dome: the sky in it, a hard specular pip, and the horizon.

    Seen from directly above nearly always, so it is drawn as a sphere lit from the same
    direction as everything else, on transparent ground so the corners of the face vanish.
    """
    size = 256
    c = Canvas(size, size)
    mid = size / 2.0
    radius = size * 0.47
    lx, ly = LIGHT
    for y in range(size):
        for x in range(size):
            dx, dy = (x + 0.5 - mid) / radius, (y + 0.5 - mid) / radius
            d2 = dx * dx + dy * dy
            if d2 > 1.0:
                continue
            nz = math.sqrt(1.0 - d2)
            # the sky the dome is reflecting, brighter at the top
            sky = mix((96, 128, 158, 255), (196, 222, 240, 255), 0.5 - dy * 0.6)
            lambert = max(0.0, dx * lx + dy * ly + nz * 0.6)
            colour = mix(sky, (255, 255, 255, 255), lambert ** 3 * 0.9)
            # the rim goes dark, which is a sphere seen edge on
            colour = mix(colour, (44, 58, 72, 255), max(0.0, d2 - 0.55) * 1.6)
            alpha = 1.0 if d2 < 0.92 else max(0.0, (1.0 - d2) / 0.08)
            c.blend(x, y, colour, alpha)
    dome(c, mid - radius * 0.30, mid - radius * 0.34, radius * 0.20, (255, 255, 255, 255),
         lift=40, drop=0, alpha=0.85)
    return c


def pv_switch():
    """A load-break handle: the black-on-yellow every isolator in the world is."""
    size = 128
    c = Canvas(size, size)
    powder(c, (222, 190, 32, 255), salt=263, peel=8)
    c.aa_rect(0, size * 0.40, size, size * 0.60, (32, 32, 34, 255))
    for i in range(4):
        x = size * (0.10 + i * 0.26)
        c.aa_rect(x, size * 0.10, x + size * 0.12, size * 0.30, (32, 32, 34, 255), alpha=0.85)
    grime(c, salt=269, amount=0.16)
    return c


# ------------------------------------------------------------------ cable

CONDUCTOR_RED = (146, 44, 38, 255)
CONDUCTOR_BLACK = (32, 32, 36, 255)


def _pair(c, y0, y1, armoured=False, salt=271):
    """Two conductors side by side down a face: the pair every direct-current run is."""
    size = c.w
    span = y1 - y0
    for i, colour in enumerate((CONDUCTOR_BLACK, CONDUCTOR_RED)):
        a = y0 + span * (0.03 + i * 0.49)
        b = y0 + span * (0.48 + i * 0.49)
        strand = Canvas(size, size)
        rubber(strand, colour, salt=salt + i, sheen=26)
        for y in range(int(a), int(b)):
            t = min(1.0, max(0.0, (y - a) / max(1.0, b - a)))
            lit = math.sin(t * math.pi) ** 1.4
            for x in range(size):
                c.blend(x, y, shade(strand.get(x, y), -14 + lit * 30), 1.0)
        # the moulded rib down a twin cable's back
        c.aa_rect(0, a + (b - a) * 0.46, size, a + (b - a) * 0.54, (0, 0, 0, 255), alpha=0.18)
    if armoured:
        # the armour bands a heavy cable carries, which is how the trunk is told from the string
        for i in range(6):
            x = size * (0.06 + i * 0.16)
            c.aa_rect(x, y0, x + size * 0.055, y1, (142, 146, 152, 255), alpha=0.85)
            c.aa_rect(x, y0, x + size * 0.018, y1, (188, 192, 198, 255), alpha=0.7)


def dc_harness():
    """The pair as an OBJ face wears it: filling the tile, since a face maps a whole texture."""
    size = 256
    c = Canvas(size, size)
    _pair(c, 0, size)
    return c


def dc_jacket():
    """The flanks and underside of a run: sheathing, and nothing drawn on it.

    Deliberately featureless.  It goes on five faces of every length of cable in the mod, so it has to
    be a *pattern* - and the printed legend a real cable carries put a light band across the middle of
    the tile, which is enough for check_model_textures.py to read it as a picture in a frame and, more
    to the point, is a stripe that lands somewhere different on every face it is mapped onto.
    """
    size = 256
    c = Canvas(size, size)
    rubber(c, (28, 29, 33, 255), salt=277, sheen=14)
    return c


def _line_tile(armoured=False):
    """A cable texture for the vanilla JSON models, whose faces sample it in sixteenths.

    Which sixteenths is not a matter of taste - ``gen_cable_models.py`` writes the uv rectangles
    and there are nine distinct ones.  The band it draws the run's top from is **x 7 to 9 over the
    whole height**, so the pair has to run *down* this tile and not across it; the flanks come off
    the bottom row, ``y 15 to 16``; and the climb takes strips out of the left column and the top
    row.  Everything else is filled with jacket rather than left clear, because a uv landing on a
    transparent pixel is a cable you can see through.
    """
    size = 256
    unit = size / 16.0
    c = Canvas(size, size)
    rubber(c, (26, 27, 31, 255), salt=287, sheen=12)

    # the pair, running down the tile: one conductor a sixteenth wide, the pair two
    for i, colour in enumerate((CONDUCTOR_BLACK, CONDUCTOR_RED)):
        x0 = (7 + i) * unit
        x1 = x0 + unit
        strand = Canvas(size, size)
        rubber(strand, colour, salt=283 + i, sheen=26)
        for x in range(int(x0), int(x1)):
            t = min(1.0, max(0.0, (x - x0) / unit))
            lit = math.sin(t * math.pi) ** 1.4
            for y in range(size):
                c.blend(x, y, shade(strand.get(x, y), -16 + lit * 34), 1.0)

    if armoured:
        # the armour bands, which is how a trunk is told from a string at a glance
        for i in range(7):
            y = size * (0.03 + i * 0.14)
            c.aa_rect(7 * unit, y, 9 * unit, y + unit * 0.55, (146, 150, 156, 255), alpha=0.9)
            c.aa_rect(7 * unit, y, 9 * unit, y + unit * 0.20, (192, 196, 202, 255), alpha=0.75)

    # the clips that hold a surface run down
    for i in range(3):
        y = size * (0.14 + i * 0.34)
        c.aa_rect(7 * unit - unit * 0.25, y, 9 * unit + unit * 0.25, y + unit * 0.9,
                  (140, 144, 150, 255))
        c.aa_rect(7 * unit - unit * 0.25, y, 9 * unit + unit * 0.25, y + unit * 0.3,
                  (186, 190, 196, 255), alpha=0.8)

    # the flank strip along the bottom row, which is what every side face samples: a round cable
    # seen edge on, so it is lit along its middle
    for y in range(int(15 * unit), size):
        t = (y - 15 * unit) / unit
        lit = math.sin(min(1.0, max(0.0, t)) * math.pi) ** 1.4
        for x in range(size):
            c.blend(x, y, shade((30, 31, 35, 255), -10 + lit * 26), 1.0)
    return c


def dc_string_line():
    return _line_tile(armoured=False)


def dc_trunk_line():
    return _line_tile(armoured=True)


def dc_trench():
    """The backfill over a buried run: disturbed soil, darker and looser than the ground round it."""
    size = 256
    c = Canvas(size, size)
    concrete(c, (114, 96, 74, 255), salt=293, aggregate=True)
    # the sand bed a cable is laid on shows as paler streaks through the spoil
    band = stretched(size, along=size * 1.2, across=size / 26.0, octaves=2, salt=307)
    c.over(lambda x, y, base: shade(base, band.signed(x, y) * 16))
    for i in range(12):
        cx, cy = size * hash01(i, 9, 311), size * hash01(i, 10, 313)
        c.aa_disc(cx, cy, size * (0.01 + hash01(i, 11, 317) * 0.03), (78, 66, 52, 255), alpha=0.5)
    grime(c, salt=331, amount=0.20, colour=(58, 48, 38, 255))
    return c


# ------------------------------------------------------------------ the pole

def pole_concrete():
    """A spun concrete distribution pole: pale grey, seamed, weathered, faintly rust-stained.

    Reinforced concrete rather than wood because that is what the model has always been and what
    most of Europe's medium-voltage distribution stands on.  A centrifugally cast pole has a
    seam down each side where the mould halves met, a very smooth skin with the aggregate just
    showing through it, and a rust bloom wherever a reinforcing bar sits too close to the surface.
    """
    size = 512
    c = Canvas(size, size)
    concrete(c, (166, 164, 158, 255), salt=337)
    # the mould seam, twice across the tile because a pole has two of them
    for x in (size * 0.02, size * 0.52):
        c.aa_rect(x, 0, x + size * 0.012, size, (0, 0, 0, 255), alpha=0.22)
        c.aa_rect(x + size * 0.012, 0, x + size * 0.024, size, (255, 255, 255, 255), alpha=0.16)
    # the lifting hole and the bolt holes a crossarm is hung on
    for y in (size * 0.20, size * 0.62):
        c.aa_disc(size * 0.27, y, size * 0.030, (52, 50, 46, 255))
        c.aa_disc(size * 0.27, y, size * 0.020, (24, 24, 22, 255))
        rust(c, size * 0.24, y, size * 0.31, y + size * 0.16, salt=347, alpha=0.4)
        streak(c, size * 0.27, y + size * 0.03, y + size * 0.34, size * 0.035,
               colour=(116, 72, 40, 255), alpha=0.30, salt=349)
    # water staining down the flats, and moss in the seam
    for i in range(7):
        streak(c, size * hash01(i, 12, 353), 0, size, size * 0.05, alpha=0.10, salt=359 + i)
    moss = Field(size, cell=size / 4.0, octaves=3, salt=367)
    c.over(lambda x, y, base: mix(base, (74, 88, 58, 255), max(0.0, moss.at(x, y) - 0.66) * 0.9))
    grime(c, salt=373, amount=0.16, colour=(96, 92, 84, 255))
    return c


def pole_plate():
    """The pole's number plate and danger sign, on one tile: aluminium, stamped, faded."""
    size = 256
    c = Canvas(size, size)
    brushed(c, (206, 208, 212, 255), grain=8, blotch=6, salt=379)
    bevel(c, 0, 0, size, size, size * 0.04, lift=26, drop=30)
    warning_triangle(c, size * 0.5, size * 0.34, size * 0.42)
    # the pole number, stamped rather than printed
    for i in range(5):
        x = size * (0.18 + i * 0.14)
        c.aa_rect(x, size * 0.66, x + size * 0.075, size * 0.86, (72, 74, 78, 255), alpha=0.85)
        c.aa_rect(x + size * 0.012, size * 0.68, x + size * 0.063, size * 0.84,
                  (206, 208, 212, 255), alpha=0.6)
    grime(c, salt=383, amount=0.18)
    return c


def porcelain_brown():
    """A pin insulator's glaze: the brown porcelain of medium-voltage distribution.

    Brown rather than the cabin's green, because that is what a line insulator is: the green
    glaze belongs to the bushings on a transformer housing and the two are different objects.

    Nothing directional is drawn on it, and that is the point.  The sheds are geometry - a stack
    of skirts turned at eighty sides - and the render type is ``entityCutoutNoCull``, whose shader
    lights a face by its own normal.  So a highlight painted down the tile would land on top of
    the one the geometry already gets, and a shed drawn across it would be a second set of
    skirts at right angles to the real ones.  What is left is what a glaze actually is: a deep,
    slightly uneven colour with a fine crazing in it.
    """
    size = 256
    c = Canvas(size, size)
    porcelain(c, (118, 76, 46, 255), salt=389)
    # the crazing: the hairline network a fired glaze cools into, very faint
    for i in range(40):
        x0, y0 = size * hash01(i, 17, 391), size * hash01(i, 18, 393)
        angle = hash01(i, 19, 395) * math.tau
        length = size * (0.04 + hash01(i, 20, 397) * 0.10)
        c.aa_line(x0, y0, x0 + math.cos(angle) * length, y0 + math.sin(angle) * length,
                  (72, 46, 28, 255), width=size * 0.004, alpha=0.30)
    grime(c, salt=397, amount=0.10, colour=(70, 60, 50, 255))
    return c


# ------------------------------------------------------------------ the kiosk

def box_door(plain=False):
    """The kiosk's door leaf: moss green, louvred low down, with a lock and an HV label.

    The same green as the cabin's doors, because they are the same utility's equipment - a
    substation kiosk and a transformer housing in one field should read as one maker's kit.

    ``plain`` is the second leaf: the louvres and the hinges but no label and no lock, because a
    kiosk has one danger sign and one keyhole, not two of each.
    """
    size = 512
    c = Canvas(size, size)
    powder(c, MOSS, salt=401, peel=7)
    bevel(c, size * 0.03, size * 0.02, size * 0.97, size * 0.98, size * 0.020, lift=26, drop=34)

    # the louvre bank low on the leaf, where cool air is drawn in
    lx0, ly0, lx1, ly1 = size * 0.12, size * 0.62, size * 0.88, size * 0.90
    mesh_screen(c, lx0, ly0, lx1, ly1, size * 0.013, MOSS, alpha=0.95)
    louvre(c, lx0, ly0, lx1, ly1, 6, shade(MOSS, 8))
    bevel(c, lx0 - size * 0.012, ly0 - size * 0.012, lx1 + size * 0.012, ly1 + size * 0.012,
          size * 0.008, lift=20, drop=26)

    if not plain:
        # the high-voltage label, on the upper half where it can be read from a distance
        warning_triangle(c, size * 0.28, size * 0.28, size * 0.30)
        plate_label(c, size * 0.48, size * 0.18, size * 0.86, size * 0.40, shade(MOSS, 20), lines=3,
                    ink=(30, 32, 34, 255))

        # the lock: a triangular substation key, which is what these are all opened with
        lock_x, lock_y = size * 0.92, size * 0.50
        c.aa_disc(lock_x, lock_y, size * 0.040, shade(MOSS, -30))
        dome(c, lock_x, lock_y, size * 0.032, (150, 154, 160, 255), lift=40, drop=28)
        c.aa_rect(lock_x - size * 0.012, lock_y - size * 0.012, lock_x + size * 0.012,
                  lock_y + size * 0.012, (48, 50, 54, 255))

    # the hinges down the other edge, and the drip lip along the top
    for y in (size * 0.14, size * 0.50, size * 0.86):
        c.aa_rect(size * 0.035, y - size * 0.045, size * 0.075, y + size * 0.045, shade(MOSS, -26))
        for k in range(2):
            screw(c, size * 0.055, y + (k - 0.5) * size * 0.055, size * 0.012, shade(MOSS, -40))
    c.aa_rect(0, 0, size, size * 0.035, shade(MOSS, -22))

    grime(c, salt=409, amount=0.16, colour=(40, 44, 36, 255))
    for i in range(5):
        streak(c, size * (0.15 + 0.18 * i), size * 0.32, size, size * 0.03, alpha=0.10, salt=419 + i)
    return c


def box_sheet():
    """The kiosk's own painted sheet: the flanks, the back and the edges of its doors.

    A green door with aluminium edges is what happens when a door borrows the rack's material, and
    it is visible on every leaf from the side.  A door is painted all over.
    """
    size = 512
    c = Canvas(size, size)
    powder(c, MOSS, salt=439, peel=7)
    groove(c, 0, size * 0.5, size, 0, size * 0.016, dark=26, light=16)
    grime(c, salt=443, amount=0.16, colour=(30, 36, 30, 255))
    return c


def box_plinth():
    """The concrete plinth a kiosk stands on: cast in place, dirty, with the ground against it."""
    size = 256
    c = Canvas(size, size)
    concrete(c, (146, 144, 138, 255), salt=421)
    # the shuttering lines from the boards it was cast against
    for i in range(4):
        y = size * (0.14 + i * 0.26)
        c.aa_rect(0, y, size, y + size * 0.012, (0, 0, 0, 255), alpha=0.20)
        c.aa_rect(0, y + size * 0.012, size, y + size * 0.024, (255, 255, 255, 255), alpha=0.12)
    grime(c, salt=431, amount=0.34, colour=(70, 66, 58, 255))
    return c


def warning():
    """The high-voltage sign as its own decal, on transparent ground."""
    size = 512
    c = Canvas(size, size)
    warning_triangle(c, size * 0.5, size * 0.46, size * 0.86)
    grime(c, salt=433, amount=0.14, colour=(96, 90, 70, 255))
    return c


# ------------------------------------------------------------------ the lamp

LAMP_BODY = (142, 146, 152, 255)


def electric_lamp_body():
    """A cast aluminium luminaire housing: a finned heat sink, seen from the side.

    A pattern rather than a picture, and that is a constraint rather than a preference: it goes on all
    six faces of the head and on the small bosses and gasket frames round it, at whatever fraction of
    itself each of those takes.  So the fins run the whole height of the tile with no band across
    either end - the bands this had were what made a sixth of the texture read as a frame with its
    edges cut off.
    """
    size = 512
    c = Canvas(size, size)
    brushed(c, LAMP_BODY, grain=9, blotch=12, salt=439, horizontal=False)
    # the fins, edge to edge so the tile joins itself whichever part of it a face takes
    for i in range(10):
        x = size * (i * 0.10)
        c.aa_rect(x, 0, x + size * 0.048, size, shade(LAMP_BODY, 15))
        c.aa_rect(x + size * 0.048, 0, x + size * 0.066, size, shade(LAMP_BODY, -34))
    grime(c, salt=449, amount=0.20, colour=(84, 82, 78, 255))
    return c


def electric_lamp_top():
    """The luminaire from above: the heat sink, seen down its fins."""
    size = 512
    c = Canvas(size, size)
    brushed(c, shade(LAMP_BODY, -8), grain=8, blotch=10, salt=457)
    for i in range(13):
        y = size * (0.04 + i * 0.074)
        c.aa_rect(size * 0.06, y, size * 0.94, y + size * 0.030, shade(LAMP_BODY, 20))
        c.aa_rect(size * 0.06, y + size * 0.030, size * 0.94, y + size * 0.046, shade(LAMP_BODY, -44))
    # the gland where the supply comes in
    dome(c, size * 0.5, size * 0.5, size * 0.075, (52, 54, 58, 255), lift=34, drop=26)
    grime(c, salt=461, amount=0.34, colour=(80, 78, 72, 255))
    return c


# What each of the lamp's six states does to the light it makes. Colour, how bright the glass is,
# and how much of the diode array shows through - a dim lamp shows its diodes, a bright one is a
# sheet of light and you cannot see them at all.
LAMP_STATES = {
    'off': dict(colour=(214, 220, 226, 255), glow=0.00, diodes=1.00),
    'dim': dict(colour=(198, 158, 92, 255), glow=0.30, diodes=0.85),
    'warm': dict(colour=(240, 198, 128, 255), glow=0.55, diodes=0.60),
    'bright': dict(colour=(252, 240, 206, 255), glow=0.82, diodes=0.32),
    'overdrive': dict(colour=(255, 254, 244, 255), glow=1.00, diodes=0.12),
    'burnt': dict(colour=(96, 92, 88, 255), glow=0.00, diodes=1.00),
}


def lamp_lens(state):
    """The lens: a diode array behind flat glass, at one of the six brightnesses.

    Drawn rather than tinted: the diodes are on a white board behind a pane, so what changes
    between states is how much light comes off the board and how far it bleeds across the glass.
    A burnt lamp is the one state where the board itself changes - the diodes go dark grey and
    the glass is sooted.
    """
    spec = LAMP_STATES[state]
    size = 512
    c = Canvas(size, size)
    board = (238, 240, 242, 255) if state != 'burnt' else (74, 70, 66, 255)
    powder(c, board, salt=463, peel=4)

    rows = cols = 6
    inset = size * 0.10
    span = (size - 2 * inset) / cols
    for row in range(rows):
        for col in range(cols):
            cx = inset + span * (col + 0.5)
            cy = inset + span * (row + 0.5)
            r = span * 0.30
            if state == 'burnt':
                dome(c, cx, cy, r, (44, 42, 40, 255), lift=18, drop=14)
                continue
            # the diode: a phosphor square under a lens, which is yellow when it is off
            base = mix((216, 196, 92, 255), spec['colour'], spec['glow'])
            c.aa_rect(cx - r, cy - r, cx + r, cy + r, base)
            dome(c, cx, cy, r * 1.05, base, lift=int(30 + 70 * spec['glow']), drop=20,
                 alpha=spec['diodes'])
            if spec['glow'] > 0.0:
                # the halo each diode throws onto the glass, which is what makes an array read
                # as one panel of light once it is bright
                for k in range(3):
                    c.aa_disc(cx, cy, r * (1.5 + k * 0.9), spec['colour'],
                              alpha=spec['glow'] * 0.16 / (k + 1))

    # the frame the pane sits in
    c.aa_rect(0, 0, size, inset * 0.55, shade(LAMP_BODY, -10))
    c.aa_rect(0, size - inset * 0.55, size, size, shade(LAMP_BODY, -16))
    c.aa_rect(0, 0, inset * 0.55, size, shade(LAMP_BODY, -12))
    c.aa_rect(size - inset * 0.55, 0, size, size, shade(LAMP_BODY, -14))
    for x, y in ((0.06, 0.06), (0.94, 0.06), (0.06, 0.94), (0.94, 0.94)):
        screw(c, size * x, size * y, size * 0.020, shade(LAMP_BODY, 6))

    # the glass over all of it: a diagonal glare, and soot if the lamp has burnt out
    for y in range(size):
        for x in range(size):
            band = math.exp(-(((x - y * 0.7) / size - 0.30) ** 2) / 0.012)
            if band > 0.02:
                c.blend(x, y, (226, 236, 246, 255), band * (0.16 if spec['glow'] < 0.5 else 0.07))
    if state == 'burnt':
        grime(c, salt=467, amount=0.5, colour=(24, 22, 20, 255))
    return c


# ------------------------------------------------------------------ the workbench

BENCH_STEEL = (78, 92, 112, 255)      # the blue-grey every workshop cabinet in Europe is


def workbench_top():
    """The bench's working surface: a steel-clad top, marked by everything done on it.

    The wear is the point.  A clean plate reads as a texture; a plate with a scribed grid, a
    burn where an iron was stood, scratches that all run the way a hand pulls, and four bolts
    holding the vice on reads as a bench somebody works at.
    """
    size = 512
    c = Canvas(size, size)
    brushed(c, (152, 156, 162, 255), grain=10, blotch=14, salt=479)
    # the scribed rule along the front edge
    c.aa_rect(0, size * 0.90, size, size * 0.93, shade((152, 156, 162, 255), -30), alpha=0.7)
    for i in range(17):
        x = size * i / 16.0
        long = i % 4 == 0
        c.aa_rect(x, size * (0.86 if long else 0.88), x + size * 0.004, size * 0.90,
                  (56, 58, 62, 255), alpha=0.8)
    # the vice's bolt pattern, at the left front corner where a vice goes
    for x, y in ((0.12, 0.16), (0.26, 0.16), (0.12, 0.30), (0.26, 0.30)):
        c.aa_disc(size * x, size * y, size * 0.030, shade((152, 156, 162, 255), -34), alpha=0.6)
        hex_head(c, size * x, size * y, size * 0.024, (128, 132, 138, 255), salt=487)
    # scratches, all running the way a hand drags
    for i in range(70):
        x0 = size * hash01(i, 13, 491)
        y0 = size * hash01(i, 14, 499)
        length = size * (0.03 + hash01(i, 15, 503) * 0.22)
        skew = (hash01(i, 16, 509) - 0.5) * 0.5
        c.aa_line(x0, y0, x0 + length, y0 + length * skew, (206, 210, 216, 255),
                  width=size * 0.0025, alpha=0.35)
    # a burn ring and an oil patch
    c.aa_disc(size * 0.68, size * 0.34, size * 0.075, (92, 84, 74, 255), alpha=0.35)
    c.aa_disc(size * 0.68, size * 0.34, size * 0.075, (58, 52, 44, 255), alpha=0.4, inner=size * 0.058)
    oil = Field(size, cell=size / 5.0, octaves=3, salt=521)
    c.over(lambda x, y, base: mix(base, (44, 42, 40, 255), max(0.0, oil.at(x, y) - 0.70) * 0.8))
    grime(c, salt=523, amount=0.16, colour=(70, 68, 64, 255))
    return c


def workbench_front():
    """The bench's drawer stack: three fronts, their handles, and a label on each."""
    size = 512
    c = Canvas(size, size)
    powder(c, BENCH_STEEL, salt=541, peel=7)
    for i in range(3):
        y0 = size * (0.06 + i * 0.30)
        y1 = y0 + size * 0.26
        c.aa_rect(size * 0.05, y0, size * 0.95, y1, shade(BENCH_STEEL, 8))
        bevel(c, size * 0.05, y0, size * 0.95, y1, size * 0.014, lift=24, drop=32)
        # the handle: a pressed channel across the front, which is what a tool cabinet has
        hy = y0 + (y1 - y0) * 0.62
        c.aa_rect(size * 0.22, hy, size * 0.78, hy + size * 0.055, shade(BENCH_STEEL, -34))
        c.aa_rect(size * 0.22, hy, size * 0.78, hy + size * 0.018, shade(BENCH_STEEL, 26))
        plate_label(c, size * 0.28, y0 + (y1 - y0) * 0.16, size * 0.72, y0 + (y1 - y0) * 0.42,
                    shade(BENCH_STEEL, 30), lines=2, ink=(24, 26, 30, 255))
    grime(c, salt=547, amount=0.18, colour=(28, 34, 42, 255))
    return c


def workbench_side():
    """The bench's flank: plain sheet with a swage line and the frame showing through it."""
    size = 512
    c = Canvas(size, size)
    powder(c, BENCH_STEEL, salt=557, peel=7)
    groove(c, 0, size * 0.36, size, 0, size * 0.018, dark=34, light=20)
    groove(c, 0, size * 0.68, size, 0, size * 0.018, dark=34, light=20)
    for i in range(4):
        y = size * (0.12 + i * 0.26)
        for x in (size * 0.06, size * 0.94):
            screw(c, x, y, size * 0.017, shade(BENCH_STEEL, -40))
    grime(c, salt=563, amount=0.22, colour=(26, 32, 40, 255))
    return c


def workbench_back():
    """The tool board behind the bench: perforated steel with the shadows of what hangs on it."""
    size = 512
    c = Canvas(size, size)
    powder(c, shade(BENCH_STEEL, 12), salt=569, peel=6)
    step = size / 14.0
    y = step * 0.6
    while y < size - step * 0.4:
        x = step * 0.6
        while x < size - step * 0.4:
            c.aa_disc(x, y, size * 0.011, (26, 30, 36, 255))
            c.aa_disc(x - size * 0.003, y - size * 0.003, size * 0.010, (18, 20, 24, 255))
            x += step
        y += step
    grime(c, salt=571, amount=0.20, colour=(24, 30, 38, 255))
    return c


# ------------------------------------------------------------------ the register

TEXTURES = {
    'pv_module': pv_module,
    'pv_module_back': pv_module_back,
    'pv_module_edge': pv_module_edge,
    'pv_frame': pv_frame,
    'pv_steel': pv_steel,
    'pv_steel_end': pv_steel_end,
    'pv_plate': pv_plate,
    'pv_cabinet': pv_cabinet,
    'pv_cabinet_top': pv_cabinet_top,
    'pv_cabinet_door': pv_cabinet_door,
    'pv_cabinet_leaf': lambda: pv_cabinet_door(plain=True),
    'pv_combiner_door': pv_combiner_door,
    'pv_vent': pv_vent,
    'pv_dc_section': pv_dc_section,
    'pv_display': pv_display,
    'pv_instrument': pv_instrument,
    'pv_dome': pv_dome,
    'pv_switch': pv_switch,
    'dc_harness': dc_harness,
    'dc_jacket': dc_jacket,
    'dc_string_line': dc_string_line,
    'dc_trunk_line': dc_trunk_line,
    'dc_trench': dc_trench,
    'pole_concrete': pole_concrete,
    'pole_plate': pole_plate,
    'porcelain_brown': porcelain_brown,
    'box_door': box_door,
    'box_leaf': lambda: box_door(plain=True),
    'box_sheet': box_sheet,
    'box_plinth': box_plinth,
    'warning': warning,
    'electric_lamp_body': electric_lamp_body,
    'electric_lamp_top': electric_lamp_top,
    'workbench_top': workbench_top,
    'workbench_front': workbench_front,
    'workbench_side': workbench_side,
    'workbench_back': workbench_back,
}

TEXTURES.update({'electric_lamp_%s' % state: (lambda s=state: lamp_lens(s)) for state in LAMP_STATES})


def main():
    wanted = [a for a in sys.argv[1:] if not a.startswith('-')]
    for name, builder in TEXTURES.items():
        if wanted and name not in wanted:
            continue
        path = os.path.join(OUT, name + '.png')
        canvas = builder()
        canvas.write(path)
        print('block/%-26s %4d x %4d  %5d KB' % (name + '.png', canvas.w, canvas.h,
                                                 os.path.getsize(path) // 1024))


if __name__ == '__main__':
    main()
