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
    """The sun side of a laminate: 144 half-cut cells on nine round wires apiece, under glass.

    Six columns of twenty-four half-cells with a bus gap across the middle, which is a 2278 by
    1134 module - the size nearly every utility-scale product has settled on.  The texture is
    square and the module is not, and that is deliberate: the face it is mapped onto is 0.42 by
    0.94 of a block, so a cell drawn a sixth of the width by a twenty-fourth of the height comes
    out very nearly the 189 by 95 millimetres a half-cut cell really is.

    What makes it read as glass rather than as a grid: the cells are not all the same colour,
    because a real laminate's cells are sorted by current and not by shade; the fingers are
    hairlines rather than pixels; and there is a broad soft reflection across it, which is the
    one thing every photograph of a panel has and no drawing of one did.

    Why it is darker than it was
    ---------------------------
    A field of these read as a pale grey-blue grid rather than as glass, and measuring the tile said
    why: it averaged (108, 113, 134), where a module in a photograph is nearer (34, 40, 66).  Three
    things were bright, and every one of them was bright because it was drawn to a *2016* module -
    three flat 1.6 mm ribbons a cell, a 2 mm backsheet lane, and fingers lifted thirty levels.

    Every product in this catalogue is a 2020s module, and those carry nine to sixteen *round* wires
    0.3 mm across instead of three flat ribbons.  So the wires are now nine hairlines, which is both
    what the datasheets say and a third of the silver: a module is dark because almost all of its
    front is cell.  The lane between cells came down to what a real stringer leaves, and the glass
    reflection at the top - the part a player reads as "white" first, because it is what the sky
    lands on - lost a third of its strength.
    """
    size = 1024
    c = Canvas(size, size, CELL)
    frame_w = size * 0.030
    # The lane between two cells: what a stringer actually leaves, which is about 2 mm on a 190 mm
    # cell - a fiftieth, not a twenty-fifth.  It reads as much wider than it is because it is the
    # brightest thing on the module.
    gap = size * 0.0034
    cols, rows = 6, 24

    # The backsheet the cells sit on, seen in the lanes between them.  Not the paper white it is on
    # the roll: this is the white seen *through* three millimetres of low-iron glass and a sheet of
    # encapsulant, which takes it down and blues it - and it is a sixth of the module's area, so a
    # backsheet drawn at its own brightness is most of why a field of these looked whitewashed.
    back = Canvas(size, size)
    powder(back, (204, 210, 220, 255), salt=7, peel=4)
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
            # The fingers and the wires, both drawn to the area they really cover.
            #
            # This is the whole of why the module used to read white, and it is an arithmetic mistake
            # rather than a drawing one.  A cell here is 158 pixels wide standing for 190 millimetres,
            # so one pixel is 1.2 mm - and a busbar wire is 0.3 mm and a finger 40 microns.  Both are
            # *sub-pixel*.  Drawn a whole pixel wide at full strength, nine wires covered 15% of the
            # cell instead of 1.4% and twenty-two fingers covered 65% of it instead of 1%, so most of
            # the front of the module was silver paint.
            #
            # A camera resolves a quarter-pixel wire as a quarter-strength pixel, so that is what is
            # drawn: a thin line at the alpha its real width earns.  It still reads as a fine grating
            # up close, and at any distance it reads as what it is - a dark cell with a sheen on it.
            fingers = 22
            for f in range(1, fingers):
                fy = y0 + (y1 - y0) * f / fingers
                c.aa_rect(x0 + span_x * 0.02, fy, x1 - span_x * 0.02, fy + size * 0.0009,
                          shade(base, 22), alpha=0.16)
            # nine round wires: a round wire throws most of what hits it sideways rather than back, so
            # even at full width it would not be as bright as the flat ribbon it replaced
            for k in range(9):
                rx = x0 + (x1 - x0) * (k + 0.5) / 9.0
                w = size * 0.0006
                c.aa_rect(rx - w, y0 - gap, rx + w, y1 + gap, (168, 174, 186, 255), alpha=0.5)

    # The bus bars the strings are joined by, across the middle and along each end.  These stay wide
    # because they really are - 6 mm of flat tinned ribbon - but they are three lines on the module
    # rather than eighteen a cell, so their brightness costs nothing.
    for y in (size * 0.5, inner0 - gap * 0.5, inner1 + gap * 0.5):
        c.aa_rect(inner0 - gap, y - size * 0.0042, inner1 + gap, y + size * 0.0042, (188, 194, 202, 255))
        c.aa_rect(inner0 - gap, y - size * 0.0015, inner1 + gap, y + size * 0.0015, (222, 227, 234, 255),
                  alpha=0.7)

    # The glass: a broad soft reflection of the sky, brighter at the top, plus the faint bloom of
    # an anti-reflective coat.  This is the pass that turns a grid of cells into a panel.
    sheen = Field(size, cell=size * 0.9, octaves=2, salt=29)

    def glaze(x, y, base):
        if base[3] == 0:
            return base
        down = y / size
        # The sky landing on the glass, strongest at the top of the module - which is the part a
        # player reads as "white" before anything else, because it is the part angled at the sky.
        # A third weaker than it was: at full strength the top band of every panel in a field went
        # to the reflection's own colour and the cells under it stopped showing through at all.
        sky = (1.0 - down) ** 2 * 0.085 + sheen.at(x, y) * 0.035
        # a long diagonal highlight, the one a pane of glass outdoors always has
        band = math.exp(-(((x - y * 0.55) / size - 0.24) ** 2) / 0.010) * 0.115
        return mix(base, (176, 202, 230, 255), sky + band)

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

    # Everything below is laid out in *fractions of the real module*, x across its 1134 mm and y along
    # its 2278 mm, because that is what this tile is: it goes on a face 0.42 by 0.94 of a block, so the
    # two axes are at 2.24 to one and anything drawn square on the tile comes out two and a quarter times
    # taller than it is wide.  The version this replaces drew a junction box at a fifth of the width by
    # an eighth of the height - 227 by 296 mm, half a metre of polymer - and it arrived on the face
    # stretched on top of that.
    def across(mm):
        return size * mm / 1134.0

    def along(mm):
        return size * mm / 2278.0

    # the junction box: 120 by 90 mm, a third of the way down, where a real one sits
    bx, by = across(430.0), along(560.0)
    bw, bh = across(120.0), along(90.0)
    c.aa_rect(bx, by, bx + bw, by + bh, (34, 35, 38, 255))
    bevel(c, bx, by, bx + bw, by + bh, size * 0.006, lift=34, drop=40)
    c.aa_rect(bx + bw * 0.10, by + bh * 0.20, bx + bw * 0.90, by + bh * 0.80, (26, 27, 30, 255))
    for i in range(2):
        screw(c, bx + bw * (0.22 + 0.56 * i), by + bh * 0.5, across(9.0), (86, 88, 92, 255))

    # the two leads out of it - 4 mm cable, so a hairline - and an MC4 on the end of each
    for side, y in ((-1, by + bh * 0.30), (1, by + bh * 0.70)):
        c.aa_line(bx + bw, y, bx + bw + across(90.0), y + side * along(120.0), (28, 28, 32, 255),
                  width=across(7.0))
        c.aa_line(bx + bw + across(90.0), y + side * along(120.0), bx + bw + across(90.0),
                  y + side * along(420.0), (28, 28, 32, 255), width=across(7.0))
        cap = (168, 44, 40, 255) if side < 0 else (30, 30, 34, 255)
        c.aa_rect(bx + bw + across(78.0), y + side * along(400.0), bx + bw + across(102.0),
                  y + side * along(530.0), cap)

    # the label: a barcode and a block of type, 200 by 120 mm, which is what the back of every module
    # carries and where it carries it
    lx, ly = across(160.0), along(1180.0)
    lw, lh = across(200.0), along(120.0)
    plate_label(c, lx, ly, lx + lw, ly + lh, (244, 245, 246, 255), lines=3)
    for i in range(26):
        w = across(2.0 + hash01(i, 3, 53) * 5.0)
        x = lx + lw * (0.06 + 0.86 * i / 26.0)
        c.aa_rect(x, ly + lh * 0.62, x + w, ly + lh * 0.92, (28, 28, 30, 255))

    # the earth mark and the two mounting-hole reinforcements, which are the only other things on it
    for y in (along(520.0), along(1760.0)):
        for x in (across(120.0), across(1014.0)):
            c.aa_rect(x - across(26.0), y - along(26.0), x + across(26.0), y + along(26.0),
                      (206, 207, 210, 255), alpha=0.7)

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
    """A cabinet's rain hood, seen from above: a sheet with a fall on it, a drip edge, rain marks.

    The one face of a cabinet a player looks straight down on, and the only one where dirt collects
    rather than running off - so it is the dirtiest surface in the mod.

    What is *not* on it any more is five rings of dried standing water.  Two things were wrong with
    them.  A hard ring reads as a bubble drawn on the lid rather than as a stain, which the version this
    replaces said in a comment and then drew anyway.  And a circle on this tile cannot stay a circle: it
    goes on the top of six different cabinets, none of them square - a combiner's is 2.26 to 1 - so
    every one of those rings came out an oval, which is what a player noticed first about the whole
    cabinet.

    So the marks here run in *straight lines* instead, which is both what rain does on a sloped sheet
    and the one kind of feature an uneven mapping cannot spoil: a fall across the lid, the fold that
    stiffens it, streaks down the fall, and the dirt that gathers along the low edge and in the corners.
    """
    size = 512
    c = Canvas(size, size)
    powder(c, shade(SHEET, 6), salt=157)

    # The fall: a cabinet lid is not flat, it is pitched a couple of degrees so water leaves it. Bright
    # along the high edge, shading to the low one, which is what a sloped sheet under the sky looks like.
    for y in range(size):
        t = y / float(size)
        c.aa_rect(0, y, size, y + 1, (255, 255, 255, 255), alpha=(1.0 - t) * 0.13)
        c.aa_rect(0, y, size, y + 1, (0, 0, 0, 255), alpha=t * 0.07)

    # the fold that stiffens it, a third of the way down: one bright line and one dark
    fold = size * 0.34
    c.aa_rect(0, fold, size, fold + size * 0.006, shade(SHEET, 34))
    c.aa_rect(0, fold + size * 0.006, size, fold + size * 0.016, shade(SHEET, -34))

    # rain streaks down the fall, thin and long: the grain of a surface water runs off
    streaks = stretched(size, along=size * 3.0, across=size / 40.0, octaves=2, salt=167)
    c.over(lambda x, y, base: shade(base, streaks.signed(y, x) * 13))

    # the drip edge all round, turned down over the sides
    for i in range(int(size * 0.035)):
        t = i / (size * 0.035)
        tone = -50 + t * 30
        for x0, y0, x1, y1 in ((0, i, size, i + 1), (0, size - i - 1, size, size - i),
                               (i, 0, i + 1, size), (size - i - 1, 0, size - i, size)):
            c.aa_rect(x0, y0, x1, y1, (0, 0, 0, 255), alpha=abs(tone) / 255.0 * 0.8)
    for x, y in ((0.09, 0.09), (0.91, 0.09), (0.09, 0.91), (0.91, 0.91)):
        hex_head(c, size * x, size * y, size * 0.024, shade(SHEET, -26), salt=163)

    # the tide line along the low edge, where water stands before it goes over: a band, not a ring
    for i in range(int(size * 0.06)):
        t = i / (size * 0.06)
        c.aa_rect(0, size * 0.94 - i, size, size * 0.94 - i + 1, (96, 92, 84, 255),
                  alpha=(1.0 - t) * 0.16)
    grime(c, salt=181, amount=0.34, colour=(92, 88, 78, 255))
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
#
# What a string cable is, and why the ones here are black
# -------------------------------------------------------
# A photovoltaic string is wired with two *single-core* cables, one per pole - H1Z2Z2-K to EN 50618,
# which is a flexible tinned-copper conductor under two layers of cross-linked polyolefin, rated
# 1500 V d.c. and -40 to +90 degrees.  A 6 mm2 one is 6.9 mm across: a 3.0 mm conductor, 0.7 mm of
# insulation and 0.8 mm of sheath.  They are clipped in pairs along the racking, bent on a radius of
# four diameters at worst, and terminated in MC4 connectors.
#
# And the cable is black.  Not a convention worth arguing with - the sheath is carbon-black loaded
# because that is what survives twenty-five years of ultraviolet, and EN 50618 cable is sold black.
# Red and black singles exist for small systems; a plant uses black and marks the poles at the
# connectors.
#
# Which is lucky, because a red core and a black one *cannot* be drawn on this block.  A run is built
# from an arm per side and a piece in the middle, and the arms are one model turned by quarters - so
# the core on the west of a north arm is the core on the north of an east arm.  Go round a bend and
# the outer core of the turn is the other arm's inner one; put four bends together and every core is
# identified with every other.  There is no assignment of two colours that survives it, and the
# version this replaces showed the proof: a corner drew red meeting black.
#
# So both cores are black, the polarity is on the connectors where the real one carries it, and the
# detail that used to go into a paint scheme goes into the shape instead.

JACKET = (26, 27, 31, 255)             # carbon-black cross-linked polyolefin
CLEAT_STEEL = (154, 158, 164, 255)     # a stainless cable clip
CONNECTOR_BODY = (38, 39, 43, 255)     # glass-filled polyamide, the MC4 shell
CONNECTOR_RED = (150, 42, 36, 255)     # the collar that marks the positive pole
ENCLOSURE = (188, 192, 196, 255)       # a small polycarbonate junction box


def dc_core():
    """One core, as a tube's texture: the shading that makes a cylinder read as lit from one side.

    The u axis of a swept tube runs *around* it, so this drawing wraps - and that is the whole
    constraint.  Anything on it has to be periodic in x or there is a hard seam down the length of every
    cable in the world, which is exactly what the first version had: a cosine of the half-turn, bright
    at a third across and dark at both edges, so the two edges met at different values.

    So the brightness here is the real thing instead: a diffuse cylinder returns the cosine of the angle
    between its surface and the light, clamped at nothing, and that is periodic by construction.  The
    specular line sits just off the bright side, where a glossy sheath's is, and wraps with it.

    The v axis runs along the cable and carries only the extruder's die marks - nothing with a shape, so
    a face may stretch it as far as it likes.
    """
    size = 128
    c = Canvas(size, size)
    rubber(c, JACKET, salt=277, sheen=16)

    lit_at = 0.34                          # where round the tube the light lands
    for i in range(size):
        t = i / float(size)                # a whole turn, so t and t + 1 are the same place
        angle = 2.0 * math.pi * (t - lit_at)
        tone = -30 + max(0.0, math.cos(angle)) * 104
        gloss = max(0.0, math.cos(angle)) ** 26 * 0.42
        for j in range(size):
            base = shade(c.get(i, j), tone)
            c.set(i, j, mix(base, (196, 204, 214, 255), gloss))

    # the die marks, along the cable: fine, and the one thing that says which way it runs
    marks = stretched(size, along=size * 2.0, across=size / 30.0, octaves=2, salt=281)
    c.over(lambda x, y, base: shade(base, marks.signed(y, x) * 7))
    return c


def dc_cleat():
    """The stainless clip that holds a run down, once a block, the way a real one is cleated.

    A cable cleat is a strap over the cable into a foot with one screw through it, and it is the only
    thing on a run that is not cable - so it is what gives a hundred metres of pair a rhythm.  Brushed
    rather than galvanised: these are 304 stainless with a nylon liner, because a clip on a plant is
    replaced never and rusting one would mark the cable it holds.
    """
    size = 128
    c = Canvas(size, size)
    brushed(c, CLEAT_STEEL, grain=9, blotch=8, salt=283, horizontal=False)
    # the rolled edges of the strap, top and bottom
    c.aa_rect(0, 0, size, size * 0.10, shade(CLEAT_STEEL, 22))
    c.aa_rect(0, size * 0.90, size, size, shade(CLEAT_STEEL, -30))
    # the black nylon liner showing at the strap's lip, which is what keeps it off the sheath
    c.aa_rect(0, size * 0.80, size, size * 0.90, (34, 34, 38, 255), alpha=0.8)
    # No screw drawn: the model has a real one now, turned out of geometry, and a painted one under it
    # was a second screw head wherever the strap's tile landed twice.
    grime(c, salt=289, amount=0.14, colour=(74, 72, 68, 255))
    return c


def _connector(positive):
    """An MC4 plug, drawn as a strip along its own length: the plug every string cable ends in.

    Glass-filled polyamide, about eighteen millimetres across and sixty-five long, in two pieces that
    latch.  The tile's **v axis is the plug from the cable end to the nose** and each part of the model
    takes its own band of it, which is the only way three segments of one fitting can carry three
    different things: a nut cannot show the latch window and a barrel cannot show the knurl.

      * v 0.00 to 0.12 - the collar, red for the positive pole and black for the negative, which is how
        a plant marks a cable that is black for its whole length
      * v 0.12 to 0.44 - the knurled gland nut that seals onto the sheath, ribbed because it is meant to
        be turned by hand
      * v 0.44 to 0.90 - the barrel, with the latch window and the tongue of the locking clip in it
      * v 0.90 to 1.00 - the nose

    The u axis wraps round the plug, so the knurl's ribs are drawn to tile in x.  The latch window is
    not, and should not be: there is one of them, on one side.
    """
    size = 128
    c = Canvas(size, size)
    rubber(c, CONNECTOR_BODY, salt=293, sheen=22)

    def band(v0, v1):
        return size * v0, size * v1

    # the collar
    y0, y1 = band(0.0, 0.12)
    collar = CONNECTOR_RED if positive else shade(CONNECTOR_BODY, -16)
    c.aa_rect(0, y0, size, y1, collar)
    c.aa_rect(0, y0, size, y0 + (y1 - y0) * 0.30, shade(collar, 26), alpha=0.8)
    c.aa_rect(0, y1 - (y1 - y0) * 0.18, size, y1, shade(collar, -30))

    # the knurled nut: axial ribs, sixteen of them, tiling in x so the seam does not show
    y0, y1 = band(0.12, 0.44)
    c.aa_rect(0, y0, size, y1, shade(CONNECTOR_BODY, 6))
    for i in range(16):
        x = size * i / 16.0
        c.aa_rect(x, y0, x + size / 16.0 * 0.55, y1, shade(CONNECTOR_BODY, 30))
        c.aa_rect(x + size / 16.0 * 0.55, y0, x + size / 16.0, y1, shade(CONNECTOR_BODY, -26))
    # the shoulder off the nut
    c.aa_rect(0, y1 - size * 0.018, size, y1, shade(CONNECTOR_BODY, -38))

    # the barrel, and the latch window in it
    y0, y1 = band(0.44, 0.90)
    c.aa_rect(0, y0, size, y1, shade(CONNECTOR_BODY, 2))
    c.aa_rect(0, y0, size, y0 + size * 0.016, shade(CONNECTOR_BODY, 22))
    c.aa_rect(size * 0.30, y0 + (y1 - y0) * 0.22, size * 0.70, y0 + (y1 - y0) * 0.62,
              (16, 16, 18, 255))
    c.aa_rect(size * 0.34, y0 + (y1 - y0) * 0.27, size * 0.66, y0 + (y1 - y0) * 0.44,
              shade(CONNECTOR_BODY, 32))

    # the nose
    y0, y1 = band(0.90, 1.0)
    c.aa_rect(0, y0, size, y1, shade(CONNECTOR_BODY, -14))
    c.aa_rect(0, y0, size, y0 + size * 0.014, shade(CONNECTOR_BODY, 18))

    grime(c, salt=307, amount=0.10, colour=(70, 66, 60, 255))
    return c


def dc_connector_plus():
    return _connector(True)


def dc_connector_minus():
    return _connector(False)


def dc_jbox():
    """A small junction box: where more than two runs meet, because a bare cross cannot exist.

    Two cables can pass each other and two can turn a corner, but a third leg has to be *joined* to
    something - and what joins direct-current strings is a box with glands in it, IP68 polycarbonate
    with a screwed lid.  So the model puts one where four runs meet and this is its lid: the sheet,
    the four lid screws, and the moulded rib round the gasket.
    """
    size = 128
    c = Canvas(size, size)
    powder(c, ENCLOSURE, salt=311, peel=5)
    # the gasket rib inside the lid's edge, which is the one line a moulded lid always shows
    c.aa_rect(size * 0.10, size * 0.10, size * 0.90, size * 0.90, shade(ENCLOSURE, -12), alpha=0.5)
    c.aa_rect(size * 0.13, size * 0.13, size * 0.87, size * 0.87, shade(ENCLOSURE, 10), alpha=0.4)
    for x, y in ((0.06, 0.06), (0.94, 0.06), (0.06, 0.94), (0.94, 0.94)):
        screw(c, size * x, size * y, size * 0.045, shade(ENCLOSURE, -8))
    grime(c, salt=313, amount=0.18, colour=(96, 94, 88, 255))
    return c


def dc_jbox_side():
    """The junction box's walls: the same moulding, without the lid's screws on it.

    A box has four screws and they are all in the lid, so a wall wearing the lid's tile is a wall with
    four screws that are not there.
    """
    size = 128
    c = Canvas(size, size)
    powder(c, shade(ENCLOSURE, -10), salt=317, peel=5)
    # the draft line every moulded wall carries, and the shadow under the lid's overhang
    c.aa_rect(0, 0, size, size * 0.09, shade(ENCLOSURE, -34), alpha=0.7)
    c.aa_rect(0, size * 0.09, size, size * 0.13, shade(ENCLOSURE, 16), alpha=0.5)
    grime(c, salt=319, amount=0.22, colour=(92, 90, 84, 255))
    return c


def _pair(c, y0, y1, armoured=False, salt=271):
    """Two cores side by side down a face, for the stubs the machines' own models carry.

    The OBJ machines draw a short length of cable where a run leaves them, and one face there takes a
    whole texture - so this is the pair laid out to fill whatever band it is given, rather than the
    across-the-cable gradient the block models sample.
    """
    size = c.w
    span = y1 - y0
    for i in range(2):
        a = y0 + span * (0.04 + i * 0.49)
        b = y0 + span * (0.47 + i * 0.49)
        strand = Canvas(size, size)
        rubber(strand, JACKET, salt=salt + i, sheen=18)
        for y in range(int(a), int(b)):
            t = min(1.0, max(0.0, (y - a) / max(1.0, b - a)))
            # the same cylinder as a core's own tile, so a stub and the run it feeds are lit alike
            lit = max(0.0, math.cos((t - 0.38) * math.pi * 1.05))
            for x in range(size):
                c.blend(x, y, shade(strand.get(x, y), -40 + lit * 70), 1.0)
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

    This is the *trunk*'s tile, and the string cable no longer uses it: the string is drawn from round
    cores that sample ``dc_core`` across their own width, and the trunk is still one painted bar.

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

    # the pair, running down the tile: one core a sixteenth wide, the pair two
    for i in range(2):
        x0 = (7 + i) * unit
        x1 = x0 + unit
        strand = Canvas(size, size)
        rubber(strand, JACKET, salt=283 + i, sheen=22)
        for x in range(int(x0), int(x1)):
            t = min(1.0, max(0.0, (x - x0) / unit))
            lit = max(0.0, math.cos((t - 0.38) * math.pi * 1.05))
            for y in range(size):
                c.blend(x, y, shade(strand.get(x, y), -42 + lit * 72), 1.0)

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
    """A pin insulator's glaze, drawn for a surface of revolution: brown, glassy, lit from one side.

    Brown rather than the cabin's green, because that is what a line insulator is: the green glaze
    belongs to the bushings on a transformer housing and the two are different objects.

    The u axis of a lathe runs *around* the turning and the v axis along its profile, and that decides
    everything on this tile.  The insulator used to wear a flat, even glaze with a little crazing on it
    and it read as a brown blob - the sheds were there in the geometry and nothing in the shading said
    so.  Two reasons: the tile was sampled over half its width, so the wrap did not close, and a matt
    even colour gives a round object no edge at all.

    So what is drawn now is a **lit cylinder**, periodic in u by construction: the cosine of the angle
    round the turning, clamped at nothing, with the narrow specular a glaze really has just off the
    bright side and a soft bounce on the dark side, which is what stops porcelain going black where it
    turns away.  Every shed then has a highlight running round it and reads as a separate skirt.

    Along v there is only the fired colour's own unevenness and the hairline crazing a glaze cools into -
    nothing with a shape, so a profile may stretch it as far as it likes.
    """
    size = 256
    c = Canvas(size, size)
    porcelain(c, (116, 74, 44, 255), salt=389)

    lit_at = 0.30
    for i in range(size):
        t = i / float(size)
        angle = 2.0 * math.pi * (t - lit_at)
        face = math.cos(angle)
        tone = -34 + max(0.0, face) * 66
        # the bounce off the ground and the sky on the dark side: a glaze is never black round the back
        tone += max(0.0, -face) * 10
        gloss = max(0.0, face) ** 30 * 0.40
        for j in range(size):
            base = shade(c.get(i, j), tone)
            c.set(i, j, mix(base, (226, 206, 182, 255), gloss))

    # the crazing: the hairline network a fired glaze cools into, very faint
    for k in range(40):
        x0, y0 = size * hash01(k, 17, 391), size * hash01(k, 18, 393)
        theta = hash01(k, 19, 395) * math.tau
        length = size * (0.04 + hash01(k, 20, 397) * 0.10)
        c.aa_line(x0, y0, x0 + math.cos(theta) * length, y0 + math.sin(theta) * length,
                  (70, 44, 26, 255), width=size * 0.004, alpha=0.26)
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
    'dc_core': dc_core,
    'dc_cleat': dc_cleat,
    'dc_connector_plus': dc_connector_plus,
    'dc_connector_minus': dc_connector_minus,
    'dc_jbox': dc_jbox,
    'dc_jbox_side': dc_jbox_side,
    'dc_harness': dc_harness,
    'dc_jacket': dc_jacket,
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
}


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
