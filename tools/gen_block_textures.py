#!/usr/bin/env python3
"""Every block texture in the mod.

    python3 tools/gen_block_textures.py

A surface is a material, not a picture of one: value noise, then the material, then the weathering.
Resolution follows how close a player gets to the face - see CLAUDE.md section 2.

SQUASH is the table check_model_textures reads: a texture drawn pre-squashed for a face that is not
square, and the ratio it was drawn for.  Anything round on a tile that goes on several shapes is a
fault - put the fixings in the geometry instead.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from texlib import (Canvas, Field, LIGHT, bevel, brushed, concrete, cross_hatch, dome, galvanised,
                    grime, groove, hash01, hex_head, louvre, mesh_screen, mix, plate_label,
                    polygon, porcelain, powder, rubber, rust, screw, shade, streak, stretched,
                    warning_triangle)

OUT = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'textures', 'block')

# The palette the whole plant is painted from.
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
    """The sun side of a laminate: 144 half-cut cells on nine round wires apiece, under glass."""
    size = 1024
    c = Canvas(size, size, CELL)
    frame_w = size * 0.030
    # The lane between two cells: what a stringer actually leaves, which is about 2 mm on a 190 mm
    gap = size * 0.0034
    cols, rows = 6, 24

    # The backsheet the cells sit on, seen in the lanes between them.
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
            # the chamfer: a pseudo-square wafer is a round ingot with four flats cut off it
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
            fingers = 22
            for f in range(1, fingers):
                fy = y0 + (y1 - y0) * f / fingers
                c.aa_rect(x0 + span_x * 0.02, fy, x1 - span_x * 0.02, fy + size * 0.0009,
                          shade(base, 22), alpha=0.16)
            # nine round wires: a round wire throws most of what hits it sideways rather than back
            # even at full width it would not be as bright as the flat ribbon it replaced
            for k in range(9):
                rx = x0 + (x1 - x0) * (k + 0.5) / 9.0
                w = size * 0.0006
                c.aa_rect(rx - w, y0 - gap, rx + w, y1 + gap, (168, 174, 186, 255), alpha=0.5)

    # The bus bars the strings are joined by, across the middle and along each end.
    for y in (size * 0.5, inner0 - gap * 0.5, inner1 + gap * 0.5):
        c.aa_rect(inner0 - gap, y - size * 0.0042, inner1 + gap, y + size * 0.0042, (188, 194, 202, 255))
        c.aa_rect(inner0 - gap, y - size * 0.0015, inner1 + gap, y + size * 0.0015, (222, 227, 234, 255),
                  alpha=0.7)

    # The glass: a broad soft reflection of the sky, brighter at the top
    # an anti-reflective coat.  This is the pass that turns a grid of cells into a panel.
    sheen = Field(size, cell=size * 0.9, octaves=2, salt=29)

    def glaze(x, y, base):
        if base[3] == 0:
            return base
        down = y / size
        # The sky landing on the glass
        sky = (1.0 - down) ** 2 * 0.085 + sheen.at(x, y) * 0.035
        # a long diagonal highlight
        band = math.exp(-(((x - y * 0.55) / size - 0.24) ** 2) / 0.010) * 0.115
        return mix(base, (176, 202, 230, 255), sky + band)

    c.over(glaze)

    # the anodised frame round the outside
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
    """The back of a monofacial module: white backsheet, junction box, two leads, a barcode."""
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
        screw(c, bx + bw * (0.22 + 0.56 * i), by + bh * 0.5, across(9.0), (86, 88, 92, 255),
              squash=SQUASH['pv_module_back'])

    # the two leads out of it - 4 mm cable
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

    # the earth mark and the two mounting-hole reinforcements
    for y in (along(520.0), along(1760.0)):
        for x in (across(120.0), across(1014.0)):
            c.aa_rect(x - across(26.0), y - along(26.0), x + across(26.0), y + along(26.0),
                      (206, 207, 210, 255), alpha=0.7)

    _frame_border(c, frame_w)
    grime(c, salt=59, amount=0.10, colour=(150, 146, 138, 255))
    return c


def pv_module_edge():
    """The frame in profile: the extrusion, not the cells."""
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
    # the drainage notch: one hole per frame
    c.aa_disc(size * 0.72, size * 0.50, size * 0.030, shade(ALU, -80))
    c.aa_disc(size * 0.72, size * 0.50, size * 0.020, (18, 18, 20, 255))
    streak(c, size * 0.72, size * 0.53, size * 0.90, size * 0.05, alpha=0.22, salt=61)
    grime(c, salt=67, amount=0.12)
    return c


# ------------------------------------------------------------------ the metals

def pv_frame():
    """Anodised aluminium rail: the racking every module is clamped to."""
    size = 512
    c = Canvas(size, size)
    brushed(c, ALU, grain=13, blotch=9, salt=3)
    # the slot: a dark channel with a bright lip either side, along the rail
    groove(c, 0, size * 0.44, size, 0, size * 0.020, dark=64, light=30)
    c.aa_rect(0, size * 0.40, size, size * 0.425, shade(ALU, 14))
    grime(c, salt=73, amount=0.10)
    return c


def pv_steel():
    """Hot-dip galvanised steel: piers, torque tubes, crossarms, masts."""
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
    """A flat galvanised plate: a base plate, a gland plate, a bolted flange face."""
    size = 256
    c = Canvas(size, size)
    galvanised(c, shade(ZINC, -6), salt=103)
    cross_hatch(c, 0, 0, size, size, size * 0.16, shade(ZINC, 14), width=size * 0.012, alpha=0.18)
    grime(c, salt=109, amount=0.18)
    return c


def pv_steel_end():
    """A tube end: the flange face a cylinder's cap shows."""
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
    """
    size = 512
    c = Canvas(size, size)
    powder(c, SHEET, salt=31)
    groove(c, 0, size * 0.50, size, 0, size * 0.016, dark=30, light=18)
    grime(c, salt=137, amount=0.13, colour=(96, 94, 88, 255))
    # rain runs down a vertical panel, so the dirt is streaked and not only patchy
    for i in range(6):
        streak(c, size * hash01(i, 4, 139), size * hash01(i, 5, 149) * 0.3, size, size * 0.03,
               alpha=0.11, salt=151 + i)
    return c


def pv_cabinet_top():
    """A cabinet's rain hood, seen from above: a sheet with a fall on it, a drip edge, rain marks."""
    size = 512
    c = Canvas(size, size)
    powder(c, shade(SHEET, 6), salt=157)

    # The fall: a cabinet lid is not flat
    # along the high edge, shading to the low one, which is what a sloped sheet under the sky looks like.
    for y in range(size):
        t = y / float(size)
        c.aa_rect(0, y, size, y + 1, (255, 255, 255, 255), alpha=(1.0 - t) * 0.13)
        c.aa_rect(0, y, size, y + 1, (0, 0, 0, 255), alpha=t * 0.07)

    # the fold that stiffens it, a third of the way down: one bright line and one dark
    fold = size * 0.34
    c.aa_rect(0, fold, size, fold + size * 0.006, shade(SHEET, 34))
    c.aa_rect(0, fold + size * 0.006, size, fold + size * 0.016, shade(SHEET, -34))

    # rain streaks down the fall
    streaks = stretched(size, along=size * 3.0, across=size / 40.0, octaves=2, salt=167)
    c.over(lambda x, y, base: shade(base, streaks.signed(y, x) * 13))

    # the drip edge all round, turned down over the sides
    for i in range(int(size * 0.035)):
        t = i / (size * 0.035)
        tone = -50 + t * 30
        for x0, y0, x1, y1 in ((0, i, size, i + 1), (0, size - i - 1, size, size - i),
                               (i, 0, i + 1, size), (size - i - 1, 0, size - i, size)):
            c.aa_rect(x0, y0, x1, y1, (0, 0, 0, 255), alpha=abs(tone) / 255.0 * 0.8)

    # the tide line along the low edge, where water stands before it goes over: a band, not a ring
    for i in range(int(size * 0.06)):
        t = i / (size * 0.06)
        c.aa_rect(0, size * 0.94 - i, size, size * 0.94 - i + 1, (96, 92, 84, 255),
                  alpha=(1.0 - t) * 0.16)
    grime(c, salt=181, amount=0.34, colour=(92, 88, 78, 255))
    return c


def pv_cabinet_door(plain=False):
    """An inverter's door leaf: the rating plate, the status lights and the louvre bank.

    there, at DISPLAY_LOW..DISPLAY_HIGH in gen_pv_models.  Move either and move both.
    """
    size = 512
    c = Canvas(size, size)
    squash = SQUASH['pv_cabinet_door']
    powder(c, SHEET, salt=191)
    bevel(c, size * 0.02, size * 0.02, size * 0.98, size * 0.98, size * 0.016, lift=30, drop=34)

    if not plain:
        plate_label(c, size * 0.08, size * 0.06, size * 0.56, size * 0.26, SHEET, lines=5)
        # run, grid, fault
        for i, colour in enumerate(((62, 196, 92, 255), (78, 168, 236, 255), (226, 92, 62, 255))):
            cx = size * (0.66 + i * 0.11)
            c.aa_disc(cx, size * 0.11, size * 0.032, (40, 42, 46, 255), squash=squash)
            dome(c, cx, size * 0.11, size * 0.025, colour, lift=40, drop=20, squash=squash)
            c.aa_rect(cx - size * 0.030, size * 0.185, cx + size * 0.030, size * 0.200,
                      shade(SHEET, -34))

    # the louvre bank, below the window, with its screen behind
    lx0, ly0, lx1, ly1 = size * 0.10, size * 0.745, size * 0.90, size * 0.955
    c.aa_rect(lx0 - size * 0.014, ly0 - size * 0.014, lx1 + size * 0.014, ly1 + size * 0.014,
              shade(SHEET, -18))
    bevel(c, lx0 - size * 0.014, ly0 - size * 0.014, lx1 + size * 0.014, ly1 + size * 0.014,
          size * 0.008, lift=24, drop=30)
    mesh_screen(c, lx0, ly0, lx1, ly1, size * 0.012, SHEET, alpha=0.9)
    louvre(c, lx0, ly0, lx1, ly1, 6, shade(SHEET, -4))

    grime(c, salt=193, amount=0.12, colour=(96, 94, 88, 255))
    for i in range(4):
        streak(c, size * (0.2 + 0.2 * i), size * 0.30, size, size * 0.025, alpha=0.09, salt=197 + i)
    return c


def pv_blank():
    """The plate that covers the DC aperture on a machine with no combiner in it."""
    size = 256
    c = Canvas(size, size)
    squash = SQUASH['pv_blank']
    powder(c, SHEET, salt=211)
    bevel(c, size * 0.03, size * 0.05, size * 0.97, size * 0.95, size * 0.020, lift=26, drop=30)
    for i in range(6):
        for y in (0.16, 0.84):
            screw(c, size * (0.09 + i * 0.164), size * y, size * 0.022, shade(SHEET, -40),
                  squash=squash)
    groove(c, 0, size * 0.5, size, 0, size * 0.010, dark=18, light=12)
    grime(c, salt=213, amount=0.14, colour=(94, 92, 86, 255))
    return c


def pv_combiner_door():
    """A string combiner's door: the fuse window, the switch escutcheon, the ways label."""
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
    # the frame round it
    for i in range(int(inset * 0.5)):
        t = i / (inset * 0.5)
        for x0, y0, x1, y1 in ((0, i, size, i + 1), (0, size - i - 1, size, size - i),
                               (i, 0, i + 1, size), (size - i - 1, 0, size - i, size)):
            c.aa_rect(x0, y0, x1, y1, (255, 255, 255, 255) if i < inset * 0.2 else (0, 0, 0, 255),
                      alpha=(0.12 if i < inset * 0.2 else 0.10) * (1 - t))
    grime(c, salt=227, amount=0.22, colour=(88, 86, 80, 255))
    return c


def pv_dc_section():
    """An inverter's direct-current compartment: fuse ways behind a window, glands under them."""
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
        dome(c, x, h * 0.85, w * 0.020, (46, 48, 52, 255), lift=30, drop=24,
             squash=SQUASH['pv_dc_section'])
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
    """A radiometer or a screen housing: white anodised aluminium, machined, with a black band."""
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
    plate_label(c, size * 0.46, size * 0.475, size * 0.90, size * 0.555, (86, 88, 92, 255), lines=2)
    grime(c, salt=257, amount=0.10, colour=(140, 138, 132, 255))
    return c


def pv_dome():
    """A radiometer's glass dome: the sky in it, a hard specular pip, and the horizon."""
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
            # the rim goes dark
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
# four diameters at worst, and terminated in MC4 connectors.

# Carbon-black cross-linked polyolefin.
JACKET = (48, 50, 56, 255)
CLEAT_STEEL = (154, 158, 164, 255)     # a stainless cable clip
# Glass-filled polyamide, the MC4 shell.
CONNECTOR_BODY = (54, 56, 61, 255)
CONNECTOR_RED = (150, 42, 36, 255)     # the collar that marks the positive pole
ENCLOSURE = (188, 192, 196, 255)       # a small polycarbonate junction box


def dc_core():
    """One core, as a tube's texture: the shading that makes a cylinder read as lit from one side."""
    size = 128
    c = Canvas(size, size)

    # one profile round the tube, then written down every row: the die marks are picked from a noise
    # field sampled along u only, so they are lines along the cable rather than rings round it
    marks = stretched(size, along=size * 3.0, across=size / 26.0, octaves=2, salt=281)
    profile = []
    for i in range(size):
        # _turned is where the gradient lives, shared with the fittings moulded onto this cable
        tone, gloss = _turned((i + 0.5) / size, matt=False)
        # the sheath's own grain, sampled across the turn so it comes out as fine longitudinal lines
        grain = marks.signed(0.0, i) * 9 + hash01(i, 0, 277) * 4 - 2
        profile.append(mix(shade(JACKET, tone + grain), (196, 204, 214, 255), gloss))

    for i in range(size):
        for j in range(size):
            c.set(i, j, profile[i])
    return c


def dc_cleat():
    """The stainless clip that holds a run down, once a block, the way a real one is cleated."""
    size = 128
    c = Canvas(size, size)
    brushed(c, CLEAT_STEEL, grain=9, blotch=8, salt=283, horizontal=False)
    # the rolled edges of the strap, top and bottom
    c.aa_rect(0, 0, size, size * 0.10, shade(CLEAT_STEEL, 22))
    c.aa_rect(0, size * 0.90, size, size, shade(CLEAT_STEEL, -30))
    # the black nylon liner showing at the strap's lip, which is what keeps it off the sheath
    c.aa_rect(0, size * 0.80, size, size * 0.90, (34, 34, 38, 255), alpha=0.8)
    # No screw drawn: the model has a real one now, turned out of geometry
    # was a second screw head wherever the strap's tile landed twice.
    grime(c, salt=289, amount=0.14, colour=(74, 72, 68, 255))
    return c


# A knurl is not a stripe: a flute tilts the surface, so it takes the light the tilt earns it and a flute
# on the shaded side stays shaded.  The figure is the tilt in radians, added to the cylinder's own angle.
def _flute(u, count, tilt):
    return math.sin(2.0 * math.pi * ((u * count) % 1.0)) * tilt


def _turned(u, count=0, tilt=0.0, matt=True):
    """A point round a turned part: the tone and the sheen its own angle to the light earns.

    Same construction as dc_core, and that is the point - shade_quads is off on every cable model, so a
    tile like this is the only lighting a fitting gets, and a plug lit on a different side from the cable
    it is moulded onto reads as two objects rather than one.  u zero is the top, which is where
    modellib.mc4 phases each segment and where tube carries its reference vector.
    """
    lit = max(0.0, math.cos(2.0 * math.pi * u + _flute(u, count, tilt)))
    if matt:
        # Polyamide against the jacket's polyolefin: a weaker gradient and a broad sheen instead of a
        return -6 + lit * lit * 38, lit ** 5 * 0.13
    return -8 + lit * lit * 44, lit ** 20 * 0.50


# (v0, v1, tone off CONNECTOR_BODY or None for a strain relief, flutes, how deep they are cut).  The v
# are modellib.MC4's and MC4_JOINT's own - move one there and move it here.  Eight flutes round a 6 px nut
# and six ribs round a 7 px ring: a flute has to be about a pixel wide or it is a stripe.
PLUG_BANDS = (
    (0.0000, 0.1250, None, 0, 0.00),        # the strain relief
    (0.1250, 0.3068, 5, 8, 0.72),           # the cable gland nut
    (0.3068, 0.6136, 0, 0, 0.00),           # the body
    (0.6136, 0.8750, 8, 6, 0.80),           # the coupling ring
    (0.8750, 1.0000, -12, 0, 0.00),         # the nose
)
JOINT_BANDS = (
    (0.0000, 0.1111, None, 0, 0.00),        # the strain relief in
    (0.1111, 0.2917, 5, 8, 0.72),           # one gland nut
    (0.2917, 0.3611, 0, 0, 0.00),           # its body
    (0.3611, 0.6389, 8, 6, 0.80),           # the coupling collar, screwed home
    (0.6389, 0.7083, 0, 0, 0.00),           # the other body
    (0.7083, 0.8889, 5, 8, 0.72),           # the other gland nut
    (0.8889, 1.0000, None, 0, 0.00),        # and the strain relief out
)


def _moulded(bands, positive):
    """An MC4 moulding as a strip along its own length: a plug at a free end, or a mated joint mid-run.

    Nothing here is a picture of a feature - the knurl and the grip are tilts fed through _turned, so they
    are lit rather than painted, and a flute on the shaded side of the tube stays shaded.
    """
    # 256 rather than the 128 CLAUDE.md gives a connector: this tile is a strip along a length and its
    # flutes are turned detail, so u wants the samples.
    size = 256
    c = Canvas(size, size)
    # the moulding's own grain, along the part rather than round it - the same reason as dc_core's
    grain = stretched(size, along=size * 3.0, across=size / 22.0, octaves=2, salt=293)

    for j in range(size):
        v = (j + 0.5) / size
        index, band = next((k, b) for k, b in enumerate(bands) if b[0] <= v <= b[1])
        v0, v1, step, count, tilt = band
        across = (v - v0) / max(v1 - v0, 1e-6)
        # a joint's last band is its *far* strain relief, so its blend into the jacket runs the other way
        if index == len(bands) - 1 and step is None:
            across = 1.0 - across
        for i in range(size):
            u = (i + 0.5) / size
            tone, sheen = _turned(u, count, tilt)
            if step is None:
                # the jacket becoming the shell: the moulding grips the sheath, it is not butted to it
                colour = mix(JACKET, CONNECTOR_BODY, min(1.0, across * 1.6))
                # The polarity, as a ring at the gland: 2.5 mm of it, which is what a coding ring
                # measures, and the only thing that tells a player which pole a black plug is.  Over the
                # whole band it read as a red flange rather than a marking.
                if positive and across < 0.55:
                    colour = CONNECTOR_RED
            else:
                colour = shade(CONNECTOR_BODY, step)
            tone += grain.signed(0.0, i) * 7
            c.set(i, j, mix(shade(colour, tone), (208, 214, 222, 255), sheen))

    # the shoulder at every step in the profile: what tells a player the moulding has steps at all, since
    # shade_quads cannot draw the silhouette's own shadow
    for v0, _, _, _, _ in bands[1:]:
        c.aa_rect(0, size * v0 - size * 0.011, size, size * v0 + size * 0.007,
                  (0, 0, 0, 255), alpha=0.55)
    # and the mould's parting line, down the two sides where the tool splits
    for u in (0.25, 0.75):
        c.aa_rect(size * u - size * 0.006, size * bands[1][0], size * u + size * 0.006,
                  size * bands[-1][0], (0, 0, 0, 255), alpha=0.30)

    grime(c, salt=307, amount=0.10, colour=(70, 66, 60, 255))
    return c


def dc_gland():
    """An M16 cable gland, as a strip from the box wall out: the hex body, then the compression nut.

    A quarter of the strip is the hex and the rest the nut, which is gen_cable_models.gland's own split.
    """
    size = 256
    c = Canvas(size, size)
    split = 0.25

    for j in range(size):
        v = (j + 0.5) / size
        nut = v > split
        for i in range(size):
            u = (i + 0.5) / size
            # the hex takes six facets, so its light is stepped rather than swept, and a flat sits on top
            tone, sheen = _turned(u if nut else (math.floor(u * 6.0) + 0.5) / 6.0,
                                  20 if nut else 0, 0.62)
            colour = shade(CONNECTOR_BODY, tone + (4 if nut else -6))
            c.set(i, j, mix(colour, (204, 210, 218, 255), sheen))

    # the shoulder off the hex, and the sealing cone the cable is squeezed by at the tip
    c.aa_rect(0, size * split - size * 0.014, size, size * split + size * 0.008, (0, 0, 0, 255),
              alpha=0.45)
    c.aa_rect(0, size * 0.86, size, size, (0, 0, 0, 255), alpha=0.22)
    grime(c, salt=311, amount=0.16, colour=(66, 62, 56, 255))
    return c


def dc_connector_plus():
    return _moulded(PLUG_BANDS, True)


def dc_connector_minus():
    return _moulded(PLUG_BANDS, False)


def dc_joint_plus():
    return _moulded(JOINT_BANDS, True)


def dc_joint_minus():
    return _moulded(JOINT_BANDS, False)


def dc_jbox():
    """A small junction box: where more than two runs meet, because a bare cross cannot exist.

    something - and what joins direct-current strings is a box with glands in it, IP68 polycarbonate
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
    """The junction box's walls: the same moulding"""
    size = 128
    c = Canvas(size, size)
    powder(c, shade(ENCLOSURE, -10), salt=317, peel=5)
    # the draft line every moulded wall carries, and the shadow under the lid's overhang
    c.aa_rect(0, 0, size, size * 0.09, shade(ENCLOSURE, -34), alpha=0.7)
    c.aa_rect(0, size * 0.09, size, size * 0.13, shade(ENCLOSURE, 16), alpha=0.5)
    grime(c, salt=319, amount=0.22, colour=(92, 90, 84, 255))
    return c


def _pair(c, y0, y1, armoured=False, salt=271):
    """Two cores side by side down a face, for the stubs the machines' own models carry."""
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


def _line_tile(armoured=False):
    """A cable texture for the vanilla JSON models, whose faces sample it in sixteenths.

    Which sixteenths is not a matter of taste - ``gen_cable_models.py`` writes the uv rectangles
    """
    size = 256
    unit = size / 16.0
    c = Canvas(size, size)
    rubber(c, (26, 27, 31, 255), salt=287, sheen=12)

    # the pair, running down the tile: one core a sixteenth wide
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
    """The backfill over a buried run: disturbed soil"""
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
    """A spun concrete distribution pole: pale grey, seamed, weathered, faintly rust-stained."""
    size = 512
    c = Canvas(size, size)
    concrete(c, (166, 164, 158, 255), salt=337)
    # the mould seam, twice across the tile because a pole has two of them
    for x in (size * 0.02, size * 0.52):
        c.aa_rect(x, 0, x + size * 0.012, size, (0, 0, 0, 255), alpha=0.22)
        c.aa_rect(x + size * 0.012, 0, x + size * 0.024, size, (255, 255, 255, 255), alpha=0.16)
    # the lifting hole and the bolt holes a crossarm is hung on
    # No holes drawn. This is a cylinder's tile on the pole and a footing's on a tower, so a circle on it
    # is an oval on one of the two; the rust stain that runs from a hole is what says there was one.
    for y in (size * 0.20, size * 0.62):
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
    warning_triangle(c, size * 0.5, size * 0.34, size * 0.42, squash=SQUASH['pole_plate'])
    # the pole number, stamped rather than printed
    for i in range(5):
        x = size * (0.18 + i * 0.14)
        c.aa_rect(x, size * 0.66, x + size * 0.075, size * 0.86, (72, 74, 78, 255), alpha=0.85)
        c.aa_rect(x + size * 0.012, size * 0.68, x + size * 0.063, size * 0.84,
                  (206, 208, 212, 255), alpha=0.6)
    grime(c, salt=383, amount=0.18)
    return c


def porcelain_brown():
    """A pin insulator's glaze, drawn for a surface of revolution: brown, glassy, lit from one side."""
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


# A square tile on a face that is not square stretches
# the face's own width over its height.
SQUASH = {
    'box_door': 0.277 / 0.585,              # the kiosk's leaf
    'box_leaf': 0.277 / 0.585,              # its plain second leaf, the same shape
    'pv_cabinet_door': 0.395 / 0.500,       # the inverter's, DOOR_LOW..DOOR_HIGH in gen_pv_models
    'pv_cabinet_leaf': 0.395 / 0.500,       # its plain second leaf
    'pv_blank': 0.810 / 0.327,              # the blanking plate, wider than tall
    'cab_door': 0.495 / 2.350,              # the cabin's leaf, which is a door a trolley goes through
    'cab_leaf': 0.495 / 2.350,
    'pv_dc_section': 1.14,                  # the DC compartment's own door
    'pv_module_back': 0.45,                 # a module's backsheet, two and a quarter times taller
    'pole_plate': 0.81,                     # the pole's number plate and danger sign
}
DOOR_SQUASH = SQUASH['box_door']


# ------------------------------------------------------------------ the kiosk

def box_door(plain=False):
    """The kiosk's door leaf: moss green, louvred low down, with a lock and an HV label."""
    size = 512
    c = Canvas(size, size)
    powder(c, MOSS, salt=401, peel=7)
    bevel(c, size * 0.03, size * 0.02, size * 0.97, size * 0.98, size * 0.020, lift=26, drop=34)

    # the louvre bank low on the leaf
    lx0, ly0, lx1, ly1 = size * 0.12, size * 0.62, size * 0.88, size * 0.90
    mesh_screen(c, lx0, ly0, lx1, ly1, size * 0.013, MOSS, alpha=0.95)
    louvre(c, lx0, ly0, lx1, ly1, 6, shade(MOSS, 8))
    bevel(c, lx0 - size * 0.012, ly0 - size * 0.012, lx1 + size * 0.012, ly1 + size * 0.012,
          size * 0.008, lift=20, drop=26)

    if not plain:
        # the high-voltage label, on the upper half where it can be read from a distance
        warning_triangle(c, size * 0.30, size * 0.22, size * 0.34, squash=DOOR_SQUASH)
        plate_label(c, size * 0.48, size * 0.18, size * 0.86, size * 0.40, shade(MOSS, 20), lines=3,
                    ink=(30, 32, 34, 255))

        # the lock: a triangular substation key
        lock_x, lock_y = size * 0.92, size * 0.50
        c.aa_disc(lock_x, lock_y, size * 0.040, shade(MOSS, -30), squash=DOOR_SQUASH)
        dome(c, lock_x, lock_y, size * 0.032, (150, 154, 160, 255), lift=40, drop=28,
             squash=DOOR_SQUASH)
        # the triangular substation key's socket
        polygon(c, ((lock_x, lock_y - size * 0.016 * DOOR_SQUASH),
                    (lock_x + size * 0.014, lock_y + size * 0.010 * DOOR_SQUASH),
                    (lock_x - size * 0.014, lock_y + size * 0.010 * DOOR_SQUASH)),
                (44, 46, 50, 255))

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
    """The kiosk's own painted sheet: the flanks, the back and the edges of its doors."""
    size = 512
    c = Canvas(size, size)
    powder(c, MOSS, salt=439, peel=7)
    groove(c, 0, size * 0.5, size, 0, size * 0.016, dark=26, light=16)
    grime(c, salt=443, amount=0.16, colour=(30, 36, 30, 255))
    return c


def box_plinth():
    """The concrete plinth a kiosk stands on: cast in place, dirty"""
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


# ------------------------------------------------------------------ the towers

TOWER_ZINC = (154, 158, 162, 255)


def tower_steel():
    """Galvanised rolled steel angle: what every member of a lattice tower is."""
    size = 256
    c = Canvas(size, size)
    galvanised(c, TOWER_ZINC, salt=347)
    # the rolled edges, which every angle has: a bright arris and a dark one
    c.aa_rect(0, 0, size, size * 0.045, shade(TOWER_ZINC, 26))
    c.aa_rect(0, size * 0.955, size, size, shade(TOWER_ZINC, -32))
    # No bolt heads. This tile clads every member of a lattice - legs, braces, crossarm ties - at a dozen
    # different aspects, so anything round on it is an oval on most of them. The bolted joints are
    # geometry, and what is left here is the punched line they sit in.
    for i in range(8):
        y = size * (0.08 + i * 0.118)
        c.aa_rect(size * 0.47, y - size * 0.010, size * 0.53, y + size * 0.010,
                  shade(TOWER_ZINC, -34))
    for i in range(4):
        streak(c, size * 0.5, size * (0.14 + i * 0.24), size * (0.30 + i * 0.24), size * 0.05,
               colour=(126, 84, 48, 255), alpha=0.26, salt=349 + i)
    grime(c, salt=353, amount=0.18, colour=(84, 82, 78, 255))
    return c


def tower_plate():
    """A gusset plate: the thicker steel a joint is bolted through"""
    size = 256
    c = Canvas(size, size)
    galvanised(c, shade(TOWER_ZINC, -8), salt=359)
    bevel(c, 0, 0, size, size, size * 0.05, lift=22, drop=28)
    # No bolt heads: this tile goes on gussets, footing plates and the peak fitting, at three aspects.
    # The punched holes they sit in are square enough to survive being stretched.
    for x, y in ((0.24, 0.24), (0.76, 0.24), (0.24, 0.76), (0.76, 0.76), (0.5, 0.5)):
        c.aa_rect(size * x - size * 0.026, size * y - size * 0.026,
                  size * x + size * 0.026, size * y + size * 0.026, shade(TOWER_ZINC, -38))
    rust(c, size * 0.1, size * 0.6, size * 0.5, size * 1.0, salt=361, alpha=0.30)
    grime(c, salt=367, amount=0.22, colour=(80, 78, 74, 255))
    return c


# ------------------------------------------------------------------ the register


# ------------------------------------------------------------------ the transformers

# The grey a utility paints a transformer tank. Darker than the switchgear grey and gloss rather than
# powder: a tank is painted for weather and for heat, not for a switchroom.
TANK = (128, 134, 138, 255)


def tx_tank():
    """A transformer tank's painted steel: gloss over welded plate, with the weld seams in it."""
    size = 512
    c = Canvas(size, size)
    brushed(c, TANK, grain=6, blotch=11, salt=601, horizontal=True)
    # the sheen a gloss coat has, brightest a third of the way down where the light lands
    sheen = Field(size, cell=size, octaves=1, salt=603)
    c.over(lambda x, y, base: shade(base, int(18 - 40 * min(1.0, abs(y / size - 0.38) * 2.2)
                                              + sheen.signed(x, y) * 4)))
    for y in (size * 0.34, size * 0.72):
        c.aa_rect(0, y, size, y + size * 0.010, shade(TANK, 20))
        c.aa_rect(0, y + size * 0.010, size, y + size * 0.020, shade(TANK, -26))
    c.aa_rect(size * 0.50, 0, size * 0.50 + size * 0.008, size, shade(TANK, 16))
    grime(c, salt=607, amount=0.16, colour=(62, 62, 58, 255))
    for i in range(5):
        streak(c, size * (0.12 + 0.19 * i), size * 0.30, size, size * 0.028, alpha=0.13, salt=611 + i)
    return c


def tx_tank_top():
    """The tank cover: the same paint and the fall the rain runs off by.

    No fittings drawn on it - the bushings, the relief valve and the eyebolts are all geometry, and a
    painted copy of one would be a second lying flat.
    """
    size = 512
    c = Canvas(size, size)
    brushed(c, shade(TANK, 8), grain=5, blotch=9, salt=613, horizontal=True)
    c.over(lambda x, y, base: shade(base, int(-18 + 30 * (1.0 - y / size))))
    groove(c, 0, size * 0.5, size, 0, size * 0.012, dark=22, light=14)
    grime(c, salt=617, amount=0.22, colour=(70, 70, 64, 255))
    return c


def tx_fin():
    """A pressed radiator fin, or a corrugation of a sealed tank: rolled steel either way.

    Streaked hard: a fin is the first thing on a transformer to hold dirt and the last anybody washes.
    """
    size = 256
    c = Canvas(size, size)
    brushed(c, shade(TANK, -12), grain=9, blotch=6, salt=619, horizontal=False)
    for i in range(3):
        streak(c, size * (0.2 + 0.3 * i), 0, size, size * 0.05, alpha=0.20, salt=623 + i,
               colour=(58, 58, 54, 255))
    grime(c, salt=629, amount=0.28, colour=(56, 56, 52, 255))
    return c


def tx_gravel():
    """The gravel bed of an oil bund: what a transformer stands over so a leak drains rather than pools."""
    size = 256
    c = Canvas(size, size)
    concrete(c, (118, 114, 108, 255), salt=631, aggregate=True)
    stones = Field(size, cell=10, octaves=3, salt=637)
    c.over(lambda x, y, base: shade(base, int((stones.at(x, y) - 0.5) * 96)))
    grime(c, salt=641, amount=0.24, colour=(48, 46, 42, 255))
    return c


def tx_nameplate():
    """A transformer's rating plate: stainless, etched, and the one document on the machine."""
    size = 256
    c = Canvas(size, size)
    brushed(c, (196, 198, 202, 255), grain=6, blotch=4, salt=643)
    bevel(c, 0, 0, size, size, size * 0.05, lift=30, drop=34)
    c.aa_rect(size * 0.08, size * 0.08, size * 0.92, size * 0.20, shade((196, 198, 202, 255), -18))
    plate_label(c, size * 0.08, size * 0.24, size * 0.92, size * 0.90, (196, 198, 202, 255), lines=8,
                ink=(48, 50, 54, 255))
    grime(c, salt=647, amount=0.10, colour=(120, 118, 112, 255))
    return c



def line_abc():
    """Aerial bundled cable: four insulated cores laid up round a bare messenger, as one black bundle.

    Constant along v for the reason dc_core is - a tube wraps its tile once round and once along, and a run
    is far longer than it is round, so anything down the v axis arrives compressed into rings.  What that
    leaves is the lay: the helical line where two cores meet, which on a bundle is a stripe in u.
    """
    size = 128
    c = Canvas(size, size)
    lit_at = 0.0
    profile = []
    for i in range(size):
        t = i / float(size)
        angle = 2.0 * math.pi * (t - lit_at)
        lit = max(0.0, math.cos(angle))
        tone = -6 + lit * lit * 40
        # the four cores' own boundaries, at quarter turns: what says bundle rather than one cable
        seam = min(abs(((t * 4.0) % 1.0) - 0.5) * 2.0, 1.0)
        profile.append(mix(shade(JACKET, tone - int((1.0 - seam) * 26)),
                           (196, 204, 214, 255), lit ** 22 * 0.42))
    for i in range(size):
        for j in range(size):
            c.set(i, j, profile[i])
    return c


def line_alu():
    """Bare stranded aluminium, as a conductor's tile: the strands, and the grey they weather to.

    Constant along v, same as line_abc.  The strands run along the conductor, which is a stripe in u - and
    that is what a stranded surface looks like from anywhere a player stands.
    """
    size = 128
    c = Canvas(size, size)
    base = (146, 150, 156, 255)
    strands = 11
    profile = []
    for i in range(size):
        t = i / float(size)
        lit = max(0.0, math.cos(2.0 * math.pi * t))
        # each strand is its own little cylinder, so the tile is a cosine inside a cosine
        across = ((t * strands) % 1.0) * 2.0 - 1.0
        strand = math.sqrt(max(0.0, 1.0 - across * across))
        tone = -34 + lit * 46 + strand * 22
        profile.append(mix(shade(base, tone), (232, 236, 240, 255), lit ** 16 * strand * 0.34))
    for i in range(size):
        for j in range(size):
            c.set(i, j, profile[i])
    return c


def line_fitting():
    """A line fitting: an aluminium-alloy compression clamp or a bundle spacer, hot-dip galvanised."""
    size = 128
    c = Canvas(size, size)
    galvanised(c, (162, 166, 172, 255), salt=659, spangle=True)
    for y in (size * 0.30, size * 0.70):
        c.aa_rect(0, y, size, y + size * 0.03, shade((162, 166, 172, 255), -30))
    grime(c, salt=661, amount=0.20, colour=(78, 76, 72, 255))
    return c



def cab_panel():
    """A prefabricated kiosk's wall: a moulded panel in a light grey, weathered where the rain runs.

    Glass-reinforced concrete, which is what these are made of - so the surface is a fine aggregate rather
    than paint, and it holds dirt in the mould lines and at the foot.
    """
    size = 512
    c = Canvas(size, size)
    concrete(c, (176, 174, 168, 255), salt=671, aggregate=True)
    # the moulded relief every panel carries: a recessed field inside a raised margin
    c.aa_rect(size * 0.07, size * 0.06, size * 0.93, size * 0.94, shade((176, 174, 168, 255), -10),
              alpha=0.6)
    bevel(c, size * 0.07, size * 0.06, size * 0.93, size * 0.94, size * 0.016, lift=20, drop=26)
    grime(c, salt=673, amount=0.24, colour=(92, 90, 84, 255))
    # rain runs down a wall, and the foot of one is always the dirtiest part of it
    for i in range(6):
        streak(c, size * hash01(i, 9, 677), size * hash01(i, 10, 683) * 0.25, size, size * 0.035,
               alpha=0.16, salt=691 + i)
    for i in range(int(size * 0.10)):
        t = i / (size * 0.10)
        c.aa_rect(0, size - i - 1, size, size - i, (58, 56, 50, 255), alpha=0.22 * (1 - t))
    return c


def cab_roof():
    """The kiosk's roof: a felted flat roof, which is what a prefabricated one always has."""
    size = 512
    c = Canvas(size, size)
    concrete(c, (128, 126, 122, 255), salt=701, aggregate=False)
    grain = stretched(size, along=size * 1.2, across=size / 34.0, octaves=3, salt=703)
    c.over(lambda x, y, base: shade(base, int(grain.signed(x, y) * 14)))
    # the laps of the felt, which run one way and overlap by a hand's width
    for i in range(6):
        y = size * (i + 0.5) / 6.0
        c.aa_rect(0, y, size, y + size * 0.006, shade((128, 126, 122, 255), -26))
        c.aa_rect(0, y + size * 0.006, size, y + size * 0.014, shade((128, 126, 122, 255), 12))
    # and the standing water a flat roof always has some of
    grime(c, salt=709, amount=0.30, colour=(64, 66, 62, 255), cell=90)
    return c


def cab_arrester():
    """A surge arrester's housing: grey silicone rubber, which is what a polymer-housed one is."""
    size = 128
    c = Canvas(size, size)
    base = (96, 98, 102, 255)
    profile = []
    for i in range(size):
        t = i / float(size)
        lit = max(0.0, math.cos(2.0 * math.pi * t))
        profile.append(mix(shade(base, int(-14 + lit * lit * 54)), (208, 212, 216, 255),
                           lit ** 20 * 0.30))
    for i in range(size):
        for j in range(size):
            c.set(i, j, profile[i])
    return c



def cab_door(plain=False):
    """The cabin's door leaf: full height, moss green, louvred low, with the danger sign on it.

    Its own tile rather than the kiosk's, because a cabin's leaf is half a block wide and two and a third
    tall - a door a switchgear trolley goes through - and a tile drawn for a leaf a third that height puts
    every circle on it four times too tall.  SQUASH['cab_door'] is that ratio and check_model_textures
    measures the faces against it.
    """
    size = 512
    c = Canvas(size, size)
    squash = SQUASH['cab_door']
    powder(c, MOSS, salt=713, peel=8)
    bevel(c, size * 0.03, size * 0.01, size * 0.97, size * 0.99, size * 0.014, lift=24, drop=32)

    # the louvre bank low on the leaf, where cool air is drawn in
    lx0, ly0, lx1, ly1 = size * 0.14, size * 0.70, size * 0.86, size * 0.92
    mesh_screen(c, lx0, ly0, lx1, ly1, size * 0.010, MOSS, alpha=0.95)
    louvre(c, lx0, ly0, lx1, ly1, 4, shade(MOSS, 8))
    bevel(c, lx0 - size * 0.010, ly0 - size * 0.010, lx1 + size * 0.010, ly1 + size * 0.010,
          size * 0.006, lift=18, drop=24)

    if not plain:
        warning_triangle(c, size * 0.5, size * 0.30, size * 0.30, squash=squash)
        plate_label(c, size * 0.22, size * 0.40, size * 0.78, size * 0.50, shade(MOSS, 20), lines=2,
                    ink=(28, 30, 32, 255))
        # the padlock hasp every substation door is locked with
        c.aa_rect(size * 0.10, size * 0.545, size * 0.22, size * 0.565, shade(MOSS, -34))
        c.aa_disc(size * 0.16, size * 0.555, size * 0.030, shade(MOSS, -44), squash=squash)

    grime(c, salt=719, amount=0.20, colour=(38, 44, 34, 255))
    for i in range(4):
        streak(c, size * (0.18 + 0.22 * i), size * 0.36, size, size * 0.026, alpha=0.13, salt=727 + i)
    return c


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
    'pv_blank': pv_blank,
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
    'dc_joint_plus': dc_joint_plus,
    'dc_joint_minus': dc_joint_minus,
    'dc_gland': dc_gland,
    'dc_jbox': dc_jbox,
    'dc_jbox_side': dc_jbox_side,
    'dc_trunk_line': dc_trunk_line,
    'dc_trench': dc_trench,
    'pole_concrete': pole_concrete,
    'pole_plate': pole_plate,
    'porcelain_brown': porcelain_brown,
    'box_door': box_door,
    'box_leaf': lambda: box_door(plain=True),
    'box_sheet': box_sheet,
    'box_plinth': box_plinth,
    'tower_steel': tower_steel,
    'tower_plate': tower_plate,
    'tx_tank': tx_tank,
    'tx_tank_top': tx_tank_top,
    'tx_fin': tx_fin,
    'tx_gravel': tx_gravel,
    'tx_nameplate': tx_nameplate,
    'line_abc': line_abc,
    'line_alu': line_alu,
    'line_fitting': line_fitting,
    'cab_panel': cab_panel,
    'cab_roof': cab_roof,
    'cab_arrester': cab_arrester,
    'cab_door': cab_door,
    'cab_leaf': lambda: cab_door(plain=True),
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
