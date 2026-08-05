#!/usr/bin/env python3
"""Item sprites and the GUI panel layouts.

    python3 tools/gen_pv_textures.py

Sprites are 16-pixel drawings with three-tone palettes, not renders: at item size an accurate render is
mud.  PANELS drives the GUI layouts that check_gui_fits.py measures.
"""

import math
import os
import struct
import zlib

OUT = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'textures')

# How many pixels a texture gets per unit of the drawing.
DETAIL = 4


# ------------------------------------------------------------------ the canvas

class Canvas:
    """An RGBA image with the handful of drawing operations these textures need."""

    def __init__(self, width, height, fill=(0, 0, 0, 0)):
        self.w = width
        self.h = height
        self.px = [list(fill) for _ in range(width * height)]

    def set(self, x, y, colour):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = list(colour) if len(colour) == 4 else list(colour) + [255]

    def get(self, x, y):
        return tuple(self.px[y * self.w + x])

    def rect(self, x0, y0, x1, y1, colour):
        for y in range(max(0, y0), min(self.h, y1)):
            for x in range(max(0, x0), min(self.w, x1)):
                self.set(x, y, colour)

    def outline(self, x0, y0, x1, y1, colour):
        for x in range(x0, x1):
            self.set(x, y0, colour)
            self.set(x, y1 - 1, colour)
        for y in range(y0, y1):
            self.set(x0, y, colour)
            self.set(x1 - 1, y, colour)

    def disc(self, cx, cy, radius, colour):
        for y in range(int(cy - radius), int(cy + radius) + 1):
            for x in range(int(cx - radius), int(cx + radius) + 1):
                if (x - cx) ** 2 + (y - cy) ** 2 <= radius * radius:
                    self.set(x, y, colour)

    def stroke(self, x0, y0, x1, y1, colour, width=1.0):
        """A thick line between two points, ends included."""
        dx, dy = x1 - x0, y1 - y0
        length = max(1e-6, (dx * dx + dy * dy) ** 0.5)
        half = width / 2.0
        lo_x = int(min(x0, x1) - half - 1)
        hi_x = int(max(x0, x1) + half + 2)
        lo_y = int(min(y0, y1) - half - 1)
        hi_y = int(max(y0, y1) + half + 2)

        for y in range(max(0, lo_y), min(self.h, hi_y)):
            for x in range(max(0, lo_x), min(self.w, hi_x)):
                px, py = x + 0.5, y + 0.5
                t = max(0.0, min(1.0, ((px - x0) * dx + (py - y0) * dy) / (length * length)))
                near_x, near_y = x0 + t * dx, y0 + t * dy
                if (px - near_x) ** 2 + (py - near_y) ** 2 <= half * half:
                    self.set(x, y, colour)

    def write(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        raw = bytearray()
        for y in range(self.h):
            raw.append(0)  # filter type 0: no filtering, which compresses well enough here
            for x in range(self.w):
                raw.extend(self.px[y * self.w + x])

        def chunk(kind, payload):
            data = kind + payload
            return struct.pack('>I', len(payload)) + data + struct.pack('>I', zlib.crc32(data) & 0xffffffff)

        png = b'\x89PNG\r\n\x1a\n'
        png += chunk(b'IHDR', struct.pack('>IIBBBBB', self.w, self.h, 8, 6, 0, 0, 0))
        png += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
        png += chunk(b'IEND', b'')
        with open(path, 'wb') as f:
            f.write(png)


def sprite(rows, palette):
    """A sixteen by sixteen from a picture of it, one character to a pixel."""
    c = Canvas(len(rows[0]), len(rows))
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            if key in palette:
                c.set(x, y, palette[key])

    return c


def noise(x, y, salt=0):
    """A deterministic hash in 0..1."""
    n = (x * 73856093) ^ (y * 19349663) ^ (salt * 83492791)
    n = (n ^ (n >> 13)) * 1274126177
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0


def shade(colour, amount):
    return tuple(max(0, min(255, int(c + amount))) for c in colour[:3]) + (colour[3] if len(colour) > 3 else 255,)


# ------------------------------------------------------------ block textures

def module():
    """A laminate: half-cut cells in a grid, busbars across them"""
    size = 64 * DETAIL
    c = Canvas(size, size, (18, 22, 44, 255))
    frame = (172, 176, 182, 255)
    busbar = (198, 202, 210, 255)

    c.rect(0, 0, size, size, (16, 20, 40, 255))
    # 4 by 8 cells, which at 64 pixels leaves each one 13 across and reads as a grid
    cells_x, cells_y = 4, 8
    inset = 4 * DETAIL
    cell_w = (size - 2 * inset) // cells_x
    cell_h = (size - 2 * inset) // cells_y

    for cy in range(cells_y):
        for cx in range(cells_x):
            x0 = inset + cx * cell_w
            y0 = inset + cy * cell_h
            # every cell a little different
            # sorted by current and not by colour
            tint = int(noise(cx, cy, 7) * 14) - 7
            base = shade((26, 34, 78, 255), tint)
            c.rect(x0, y0, x0 + cell_w - DETAIL // 2, y0 + cell_h - DETAIL // 2, base)
            # the chamfered corners of a pseudo-square wafer
            for dx, dy in ((0, 0), (cell_w - 2 * DETAIL, 0), (0, cell_h - 2 * DETAIL),
                           (cell_w - 2 * DETAIL, cell_h - 2 * DETAIL)):
                c.rect(x0 + dx, y0 + dy, x0 + dx + 2 * DETAIL, y0 + dy + DETAIL, (16, 20, 40, 255))

            # two busbars down each cell, and the fine fingers between them
            for bx in (cell_w // 3, 2 * cell_w // 3):
                c.rect(x0 + bx, y0, x0 + bx + max(1, DETAIL // 2), y0 + cell_h - DETAIL, busbar)
            for y in range(y0 + DETAIL, y0 + cell_h - 2 * DETAIL, 3 * DETAIL):
                c.rect(x0 + DETAIL, y, x0 + cell_w - 2 * DETAIL, y + max(1, DETAIL // 3), shade(base, 18))

    # the half-cut split: a ribbon bus across the middle of the laminate
    mid = size // 2
    c.rect(inset, mid - 2, size - inset, mid + 1, (14, 17, 34, 255))
    c.rect(inset, mid - DETAIL, size - inset, mid, busbar)

    # the frame
    for i, tint in enumerate((0, -22, -40)):
        c.rect(i * DETAIL, i * DETAIL, size - i * DETAIL, (i + 1) * DETAIL, shade(frame, tint))
        c.rect(i * DETAIL, size - (i + 1) * DETAIL, size - i * DETAIL, size - i * DETAIL, shade(frame, tint))
        c.rect(i * DETAIL, i * DETAIL, (i + 1) * DETAIL, size - i * DETAIL, shade(frame, tint))
        c.rect(size - (i + 1) * DETAIL, i * DETAIL, size - i * DETAIL, size - i * DETAIL, shade(frame, tint))
    return c


def module_back():
    """The rear of a glass-glass laminate: cells showing through, ribbons, a junction box."""
    size = 64 * DETAIL
    c = Canvas(size, size)
    pale = (206, 209, 214, 255)
    frame = (172, 176, 182, 255)

    for y in range(size):
        for x in range(size):
            c.set(x, y, shade(pale, int(noise(x, y, 41) * 8) - 4))

    cells_x, cells_y = 4, 8
    inset = 4 * DETAIL
    cell_w = (size - 2 * inset) // cells_x
    cell_h = (size - 2 * inset) // cells_y

    for cy in range(cells_y):
        for cx in range(cells_x):
            x0 = inset + cx * cell_w
            y0 = inset + cy * cell_h
            tint = int(noise(cx, cy, 43) * 10) - 5
            base = shade((132, 140, 156, 255), tint)
            c.rect(x0 + DETAIL, y0 + DETAIL, x0 + cell_w - 2 * DETAIL, y0 + cell_h - 2 * DETAIL, base)
            # the rear busbars, which on a back are wider and brighter than the front fingers
            for bx in (cell_w // 3, 2 * cell_w // 3):
                c.rect(x0 + bx, y0 + DETAIL, x0 + bx + DETAIL, y0 + cell_h - 2 * DETAIL, shade(base, 46))

    # the string ribbons running across the cell rows, and the half-cut split bus
    for y in (inset + 2 * cell_h, inset + 6 * cell_h):
        c.rect(inset, y - DETAIL, size - inset, y + DETAIL, shade(pale, 26))
    mid = size // 2
    c.rect(inset, mid - 2 * DETAIL, size - inset, mid + DETAIL, shade(pale, -18))

    # the junction box: one per module, off centre where a real one sits
    box_x, box_y = 24 * DETAIL, 46 * DETAIL
    c.rect(box_x, box_y, box_x + 16 * DETAIL, box_y + 12 * DETAIL, (44, 46, 50, 255))
    for i in range(DETAIL):
        c.outline(box_x + i, box_y + i, box_x + 16 * DETAIL - i, box_y + 12 * DETAIL - i, (26, 28, 30, 255))
    for gland in (3 * DETAIL, 10 * DETAIL):
        c.rect(box_x + gland, box_y + 12 * DETAIL, box_x + gland + 3 * DETAIL, box_y + 16 * DETAIL,
               (30, 32, 34, 255))

    for i, tint in enumerate((0, -22)):
        c.rect(i * DETAIL, i * DETAIL, size - i * DETAIL, (i + 1) * DETAIL, shade(frame, tint))
        c.rect(i * DETAIL, size - (i + 1) * DETAIL, size - i * DETAIL, size - i * DETAIL, shade(frame, tint))
        c.rect(i * DETAIL, i * DETAIL, (i + 1) * DETAIL, size - i * DETAIL, shade(frame, tint))
        c.rect(size - (i + 1) * DETAIL, i * DETAIL, size - i * DETAIL, size - i * DETAIL, shade(frame, tint))
    return c


def module_edge():
    """A module frame seen edge on: the aluminium channel, not the cells."""
    size = 32 * DETAIL
    c = Canvas(size, size)
    base = (170, 174, 180, 255)

    for y in range(size):
        for x in range(size):
            grain = int(noise(x, 0, 3) * 14) - 7 + int(noise(x, y, 13) * 5) - 2
            c.set(x, y, shade(base, grain))

    # the two grooves of the extrusion, and the bright rib between them
    for y in (size // 4, 3 * size // 4):
        c.rect(0, y - DETAIL, size, y + DETAIL, shade(base, -52))
        c.rect(0, y + DETAIL, size, y + 2 * DETAIL, shade(base, 20))
    c.rect(0, size // 2 - DETAIL, size, size // 2 + DETAIL, shade(base, 16))
    # and the dark line of the laminate itself, just inside each lip
    c.rect(0, 0, size, 2 * DETAIL, shade(base, -70))
    c.rect(0, size - 2 * DETAIL, size, size, shade(base, -70))
    return c


def anodised():
    """Anodised aluminium: light, slightly cool, with a faint drawn grain."""
    size = 32 * DETAIL
    c = Canvas(size, size)
    base = (170, 174, 180, 255)
    for y in range(size):
        for x in range(size):
            # the drawn grain runs along the extrusion, so it is a function of x and stays a line
            # however fine the tile is; the speckle over it is per pixel
            grain = int(noise(x // DETAIL, 0, 3) * 16) - 8 + int(noise(x, y, 11) * 6) - 3
            c.set(x, y, shade(base, grain))
    return c


def galvanised():
    """Galvanised steel: darker, mottled, with the spangle patches hot-dip leaves."""
    size = 32 * DETAIL
    c = Canvas(size, size)
    base = (146, 150, 154, 255)
    for y in range(size):
        for x in range(size):
            c.set(x, y, shade(base, int(noise(x, y, 5) * 20) - 10))

    # The spangle: crystals brighter than the sheet around them.
    for i in range(70):
        cx = int(noise(i, 1, 17) * size)
        cy = int(noise(i, 2, 19) * size)
        radius = 1 + int(noise(i, 3, 23) * 2.2 * DETAIL / 2)
        for y in range(cy - radius, cy + radius + 1):
            for x in range(cx - radius, cx + radius + 1):
                if 0 <= x < size and 0 <= y < size and (x - cx) ** 2 + (y - cy) ** 2 <= radius * radius:
                    c.set(x, y, shade(base, 22 + int(noise(x, y, 29) * 10)))
    return c


def cabinet():
    """Painted sheet steel: the pale grey every inverter and switchgear cabinet is."""
    size = 32 * DETAIL
    c = Canvas(size, size)
    base = (194, 197, 201, 255)
    for y in range(size):
        for x in range(size):
            c.set(x, y, shade(base, int(noise(x, y, 31) * 8) - 4))

    # a swage line, which is what stops a large flat panel drumming
    c.rect(0, size // 2 - DETAIL, size, size // 2, shade(base, -26))
    c.rect(0, size // 2, size, size // 2 + DETAIL, shade(base, 14))
    # fixings down the edges
    for y in range(3 * DETAIL, size, 8 * DETAIL):
        for x in (2 * DETAIL, size - 3 * DETAIL):
            c.disc(x, y, max(1, DETAIL // 2), shade(base, -46))
    return c


def cabinet_door():
    """The one face of an inverter a technician ever opens: louvres, handle, rating plate."""
    unit = DETAIL
    size = 32 * unit
    c = Canvas(size, size)
    base = (194, 197, 201, 255)
    for y in range(size):
        for x in range(size):
            c.set(x, y, shade(base, int(noise(x, y, 31) * 8) - 4))

    # the intake louvres: slots with a lit lower lip, which is what a pressed louvre does
    for i in range(5):
        y = (6 + i * 4) * unit
        c.rect(5 * unit, y, 21 * unit, y + 2 * unit, shade(base, -74))
        c.rect(5 * unit, y + 2 * unit, 21 * unit, y + 3 * unit, shade(base, 18))

    # the rating plate, and the handle recess with its bar
    c.rect(5 * unit, 26 * unit, 17 * unit, 30 * unit, shade(base, 26))
    for i in range(unit):
        c.outline(5 * unit + i, 26 * unit + i, 17 * unit - i, 30 * unit - i, shade(base, -40))
    for x in range(6 * unit, 16 * unit, 2 * unit):
        c.rect(x, 28 * unit, x + unit, 28 * unit + unit, shade(base, -30))

    c.rect(24 * unit, 11 * unit, 29 * unit, 21 * unit, shade(base, -34))
    for i in range(unit):
        c.outline(24 * unit + i, 11 * unit + i, 29 * unit - i, 21 * unit - i, shade(base, -58))
    c.rect(25 * unit, 13 * unit, 28 * unit, 19 * unit, shade(base, 22))

    # the hinges down the far edge
    for y in (7 * unit, 24 * unit):
        c.rect(0, y, 3 * unit, y + 3 * unit, shade(base, -50))
    return c


def cabinet_top():
    """The lid: a rain hood, fastened round its rim, with a lifting eye at each end."""
    unit = DETAIL
    size = 32 * unit
    c = Canvas(size, size)
    base = (188, 191, 195, 255)
    for y in range(size):
        for x in range(size):
            c.set(x, y, shade(base, int(noise(x, y, 47) * 8) - 4))

    # Ribs and fasteners on a repeating grid rather than a frame round the rim, and that is the whole
    for y in range(0, size, 8 * unit):
        c.rect(0, y, size, y + unit, shade(base, -40))
        c.rect(0, y + unit, size, y + 2 * unit, shade(base, 18))

    for y in range(4 * unit, size, 8 * unit):
        for x in range(4 * unit, size, 8 * unit):
            # a bolt head rather than four pixels: at this size it can be round
            c.disc(x + unit / 2.0, y + unit / 2.0, unit * 0.9, shade(base, -74))
            c.disc(x + unit / 2.0 - unit * 0.25, y + unit / 2.0 - unit * 0.25, unit * 0.5, shade(base, 12))
    return c


def combiner_door():
    """The door of a combiner box: the fuse window, the label, and the switch escutcheon."""
    unit = DETAIL
    size = 32 * unit
    c = Canvas(size, size)
    base = (198, 201, 205, 255)
    for y in range(size):
        for x in range(size):
            c.set(x, y, shade(base, int(noise(x, y, 43) * 8) - 4))

    def framed(x0, y0, x1, y1, fill, edge):
        c.rect(x0 * unit, y0 * unit, x1 * unit, y1 * unit, fill)
        for i in range(unit):
            c.outline(x0 * unit + i, y0 * unit + i, x1 * unit - i, y1 * unit - i, edge)

    # the window over the fuse ways
    framed(4, 5, 22, 17, (44, 52, 58, 255), shade(base, -62))
    for i in range(6):
        x = (6 + i * 3) * unit
        c.rect(x, 7 * unit, x + 2 * unit, 15 * unit, (168, 172, 178, 255))
        c.rect(x, 7 * unit, x + 2 * unit, 8 * unit, (206, 158, 62, 255))
        c.rect(x + 2 * unit - unit // 2, 7 * unit, x + 2 * unit, 15 * unit,
               shade((168, 172, 178, 255), -40))

    # the hazard label: a live-parts warning, which is the one notice a combiner box always carries
    framed(4, 20, 22, 27, (222, 186, 40, 255), (32, 30, 24, 255))
    for i in range(4):
        x = (6 + i * 4) * unit
        c.rect(x, 22 * unit, x + 2 * unit, 25 * unit, (32, 30, 24, 255))

    # the escutcheon the switch handle turns in, and its two marked positions
    framed(24, 9, 30, 21, shade(base, -34), shade(base, -58))
    c.rect(26 * unit, 11 * unit, 28 * unit, 13 * unit, (58, 138, 62, 255))
    c.rect(26 * unit, 17 * unit, 28 * unit, 19 * unit, (176, 54, 46, 255))

    # the hinges down the far edge
    for y in (6 * unit, 23 * unit):
        c.rect(0, y, 3 * unit, y + 3 * unit, shade(base, -50))
    return c


def switch_handle():
    """The moulded handle of a load-break switch: red, ribbed, on a black boss."""
    size = 16
    c = Canvas(size, size)
    body = (176, 46, 40, 255)
    for y in range(size):
        for x in range(size):
            c.set(x, y, shade(body, int(noise(x, y, 57) * 10) - 5))

    # the ribs a thumb grips, across the handle
    for y in range(2, size, 3):
        c.rect(0, y, size, y + 1, shade(body, -46))
        c.rect(0, y + 1, size, y + 2, shade(body, 30))

    # and the moulded edge down each side
    c.rect(0, 0, 2, size, shade(body, -60))
    c.rect(size - 2, 0, size, size, shade(body, 34))
    return c


def dc_section():
    """The direct-current section on the front of a cabinet: fuse ways, a rating label, a hazard strip."""
    c = Canvas(64, 32)
    base = (198, 201, 205, 255)
    for y in range(32):
        for x in range(64):
            c.set(x, y, shade(base, int(noise(x, y, 71) * 8) - 4))

    # the window over the fuse ways
    c.rect(3, 4, 61, 17, (44, 52, 58, 255))
    c.outline(3, 4, 61, 17, shade(base, -62))
    for i in range(9):
        x = 6 + i * 6
        c.rect(x, 6, x + 4, 15, (168, 172, 178, 255))
        c.rect(x, 6, x + 4, 8, (206, 158, 62, 255))
        c.rect(x + 3, 6, x + 4, 15, shade((168, 172, 178, 255), -46))

    # the rating label, and the live-parts warning beside it - the two things every section carries
    c.rect(3, 21, 30, 29, shade(base, 24))
    c.outline(3, 21, 30, 29, shade(base, -44))
    for x in range(6, 28, 3):
        c.rect(x, 24, x + 2, 26, shade(base, -34))

    c.rect(34, 21, 61, 29, (222, 186, 40, 255))
    c.outline(34, 21, 61, 29, (32, 30, 24, 255))
    for i in range(5):
        x = 37 + i * 5
        c.rect(x, 23, x + 2, 27, (32, 30, 24, 255))

    return c


def vent():
    """A cooling grille: dark behind, with the slats catching the light."""
    unit = DETAIL
    size = 32 * unit
    c = Canvas(size, size, (22, 24, 26, 255))
    slat = (150, 154, 158, 255)

    for i in range(8):
        y = (2 + i * 4) * unit
        for x in range(2 * unit, size - 2 * unit):
            c.rect(x, y, x + 1, y + unit, shade(slat, int(noise(x // unit, i, 53) * 12) - 6))
            c.rect(x, y + unit, x + 1, y + 2 * unit, shade(slat, -44))

    # the pressed frame round it
    for i in range(unit):
        c.outline(i, i, size - i, size - i, (176, 180, 184, 255))
    for i in range(unit, 2 * unit):
        c.outline(i, i, size - i, size - i, (138, 142, 146, 255))
    return c


def steel_end():
    """A welded end cap: a disc with the bead round its rim."""
    unit = DETAIL
    size = 16 * unit
    c = Canvas(size, size)
    base = (146, 150, 154, 255)
    for y in range(size):
        for x in range(size):
            c.set(x, y, shade(base, int(noise(x, y, 59) * 18) - 9))

    # Weld beads on a repeating grid rather than a rim round the edge and a boss in the middle.
    for y in range(0, size, 8 * unit):
        for x in range(0, size, 8 * unit):
            c.rect(x + unit, y + 3 * unit, x + 7 * unit, y + 5 * unit, shade(base, -34))
            c.rect(x + unit, y + 3 * unit, x + 7 * unit, y + 4 * unit, shade(base, 22))
            c.disc(x + 4 * unit, y + unit, unit * 0.8, shade(base, -50))
    return c


def dome():
    """A radiometer's glass dome from above: dark glass, one highlight"""
    size = 16
    c = Canvas(size, size)
    body = (230, 232, 236, 255)
    for y in range(size):
        for x in range(size):
            c.set(x, y, shade(body, int(noise(x, y, 61) * 6) - 3))

    centre = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            r = ((x - centre) ** 2 + (y - centre) ** 2) ** 0.5
            if r > 6.4:
                continue
            # the dome: darker towards its rim, because that is where the glass is thickest
            depth = int(38 * (r / 6.4) ** 2)
            c.set(x, y, shade((78, 104, 140, 255), -depth))

    c.disc(centre - 1.6, centre - 1.8, 1.4, (188, 206, 226, 255))
    c.set(int(centre - 2), int(centre - 2), (232, 240, 248, 255))
    return c


def display():
    """A monochrome LCD: near black, with a segment readout lit green."""
    size = 32
    c = Canvas(size, size, (16, 20, 18, 255))
    c.outline(0, 0, size, size, (40, 44, 42, 255))
    lit = (110, 220, 130, 255)
    dim = (26, 44, 30, 255)

    # four digits of seven segments, most of them lit: a running plant showing a number
    for d in range(4):
        x = 3 + d * 7
        segments = [
            (x, 8, x + 5, 9), (x + 4, 9, x + 5, 13), (x + 4, 14, x + 5, 18),
            (x, 18, x + 5, 19), (x, 14, x + 1, 18), (x, 9, x + 1, 13),
            (x, 13, x + 5, 14),
        ]
        pattern = [1, 1, 1, 1, 1, 0, 1] if d % 2 == 0 else [1, 1, 1, 0, 1, 1, 1]
        for on, (x0, y0, x1, y1) in zip(pattern, segments):
            c.rect(x0, y0, x1, y1, lit if on else dim)

    # a bar graph under it
    for i in range(10):
        c.rect(3 + i * 3, 23, 5 + i * 3, 27, lit if i < 7 else dim)
    return c


def instrument():
    """Instrument white: the paint radiometer bodies are finished in, and a machined band."""
    size = 32
    c = Canvas(size, size)
    base = (230, 232, 236, 255)
    for y in range(size):
        for x in range(size):
            c.set(x, y, shade(base, int(noise(x, y, 37) * 6) - 3))

    c.rect(0, size - 9, size, size - 6, shade(base, -60))
    c.rect(0, size - 6, size, size - 5, shade(base, -30))
    for x in range(1, size, 4):
        c.rect(x, size - 9, x + 1, size - 6, shade(base, -84))
    return c


# The two direct-current cables.

CABLE_BLACK = ((24, 24, 28, 255), (52, 52, 60, 255), (80, 80, 90, 255))
CABLE_RED = ((74, 22, 22, 255), (132, 40, 36, 255), (176, 66, 58, 255))
CLIP = (138, 142, 150, 255)


def cable_line(half, thick, armoured=False, flat=False):
    """A pair running the length of the tile, with the clip that holds it down across the middle."""
    c = Canvas(16, 16, CABLE_BLACK[0])
    left = int(8 - half)
    width = int(half)

    for index, palette in ((0, CABLE_RED), (1, CABLE_BLACK)):
        x0 = left + index * width
        for x in range(x0, x0 + width):
            # lit on the side the light comes from and shaded away from it
            if width < 2 or flat:
                # one pixel to a conductor
                colour = palette[1]
            else:
                across = (x - x0) / (width - 1.0)
                colour = palette[2] if across < 0.34 else (palette[1] if across < 0.7 else palette[0])
            c.rect(x, 0, x + 1, 16, colour)

    # the clip: a stainless hanger over both conductors
    for y in ((2, 6, 11) if armoured else (7,)):
        c.rect(left, y, left + 2 * width, y + 2, CLIP)
        c.rect(left, y, left + 2 * width, y + 1, shade(CLIP, 34))
        c.rect(left + width - 1, y, left + width + 1, y + 2, shade(CLIP, -46))

    return c


def cable_jacket():
    """A cable seen from the side: jacket, and nothing else."""
    c = Canvas(16, 16, CABLE_BLACK[0])
    for y in range(16):
        for x in range(16):
            c.set(x, y, shade(CABLE_BLACK[0], int(noise(x, y, 61) * 12) - 4))

    # the sheen a round jacket has along its length
    c.rect(0, 5, 16, 7, CABLE_BLACK[1])
    return c


def trench():
    """Sand bedding: what a buried cable is laid in and backfilled with."""
    c = Canvas(16, 16)
    base = (176, 158, 122, 255)
    for y in range(16):
        for x in range(16):
            grain = noise(x, y, 31)
            colour = shade(base, int(grain * 26) - 13)
            if grain > 0.94:
                # the odd pebble the sieve missed
                colour = shade(base, -34)
            c.set(x, y, colour)

    return c


# ------------------------------------------------------------- item textures

def item_combiner():
    """A box on a post with a red handle on it, which is what one looks like from ten metres."""
    c = Canvas(16, 16)
    dark, mid, light = STEEL

    # the post and its footing
    c.rect(7, 11, 9, 15, mid)
    c.rect(7, 11, 8, 15, light)
    c.rect(5, 14, 11, 15, dark)

    # the enclosure, lit from the top left the way every other item here is
    c.rect(2, 3, 14, 12, mid)
    c.rect(2, 3, 14, 4, light)
    c.rect(2, 11, 14, 12, dark)
    c.rect(13, 3, 14, 12, dark)

    # the fuse window
    c.rect(4, 5, 10, 9, (48, 56, 62, 255))
    for x in range(4, 10, 2):
        c.rect(x, 5, x + 1, 9, (170, 174, 180, 255))
        c.set(x, 5, (208, 160, 64, 255))

    # the handle, and the glands the strings come in through
    c.rect(11, 5, 12, 9, (176, 54, 46, 255))
    c.set(11, 5, (214, 96, 84, 255))
    for x in range(3, 13, 3):
        c.set(x, 12, dark)
    return c


def item_string_cable():
    """A coil of solar cable, which is how a reel of 6 mm² arrives."""
    c = Canvas(16, 16)
    c.disc(7.5, 7.5, 6.8, CABLE_BLACK[1])
    c.disc(7.5, 7.5, 5.6, CABLE_BLACK[0])
    c.disc(7.5, 7.5, 5.0, CABLE_RED[1])
    c.disc(7.5, 7.5, 3.8, CABLE_RED[0])
    c.disc(7.5, 7.5, 3.2, (0, 0, 0, 0))
    # the light on the upper left of each turn, which is what makes a flat ring look wound
    c.stroke(3.4, 5.4, 5.6, 3.2, CABLE_BLACK[2], 1.6)
    c.stroke(4.8, 6.2, 6.4, 4.6, CABLE_RED[2], 1.4)

    # the two tails, and the tie that keeps the rest of it a coil
    c.stroke(10.6, 11.4, 14.6, 14.6, CABLE_BLACK[1], 2.0)
    c.stroke(12.4, 9.6, 15.2, 11.4, CABLE_RED[1], 2.0)
    c.stroke(1.2, 8.4, 3.4, 8.4, CLIP, 2.0)
    return c


def item_trunk_cable():
    """A drum of 240 mm², because that is the only way a cable that heavy is delivered."""
    c = Canvas(16, 16)
    dark, mid, light = STEEL

    # the windings, wound onto the barrel between the flanges
    for y in range(3, 13):
        palette = CABLE_RED if y % 3 == 0 else CABLE_BLACK
        c.rect(4, y, 12, y + 1, palette[1])
        c.rect(4, y, 6, y + 1, palette[2])
        c.rect(11, y, 12, y + 1, palette[0])

    # the two flanges, which is what makes it a drum and not a bale
    for x0 in (2, 12):
        c.rect(x0, 1, x0 + 2, 15, mid)
        c.rect(x0, 1, x0 + 1, 15, light)
        c.rect(x0 + 1, 1, x0 + 2, 15, dark)

    # the spindle hole through both of them
    c.disc(3, 8, 1.2, (52, 54, 58, 255))
    c.disc(13, 8, 1.2, (52, 54, 58, 255))

    # the tail hanging off it
    c.stroke(12.5, 12.5, 15.0, 14.6, CABLE_RED[1], 2.4)
    return c


# Aluminium as a bare conductor reads, which is duller than the mill finish on a frame: a stranded
# surface scatters, so it never gets the specular a rolled section does.
BARE_ALU = ((104, 108, 114, 255), (156, 160, 166, 255), (198, 202, 208, 255))


def item_abc_conductor():
    """A coil of aerial bundled cable: four insulated cores laid up round a bare messenger."""
    c = Canvas(16, 16)
    c.disc(7.5, 7.5, 7.0, CABLE_BLACK[1])
    c.disc(7.5, 7.5, 5.8, CABLE_BLACK[0])
    c.disc(7.5, 7.5, 5.2, BARE_ALU[1])
    c.disc(7.5, 7.5, 4.2, CABLE_BLACK[1])
    c.disc(7.5, 7.5, 3.0, (0, 0, 0, 0))
    # the lay: three short lights across the turns
    for i in range(3):
        c.stroke(3.0 + i * 1.1, 5.8 - i * 1.1, 5.2 + i * 1.1, 3.4 - i * 1.1, CABLE_BLACK[2], 1.3)
    c.stroke(4.6, 6.0, 6.0, 4.6, BARE_ALU[2], 1.2)

    c.stroke(11.0, 11.6, 15.0, 14.8, CABLE_BLACK[1], 2.2)
    c.stroke(1.0, 8.6, 3.2, 8.6, CLIP, 2.0)
    return c


def item_mv_conductor():
    """A drum of bare all-aluminium-alloy conductor, one wire a phase."""
    c = Canvas(16, 16)
    dark, mid, light = STEEL
    for y in range(3, 13):
        c.rect(4, y, 12, y + 1, BARE_ALU[1])
        c.rect(4, y, 6, y + 1, BARE_ALU[2])
        c.rect(11, y, 12, y + 1, BARE_ALU[0])
    for x0 in (2, 12):
        c.rect(x0, 1, x0 + 2, 15, mid)
        c.rect(x0, 1, x0 + 1, 15, light)
        c.rect(x0 + 1, 1, x0 + 2, 15, dark)
    c.disc(3, 8, 1.2, (52, 54, 58, 255))
    c.disc(13, 8, 1.2, (52, 54, 58, 255))
    c.stroke(12.5, 11.5, 15.2, 13.8, BARE_ALU[1], 2.0)
    return c


def item_hv_conductor():
    """A drum of steel-reinforced conductor with a bundle spacer on it."""
    c = Canvas(16, 16)
    dark, mid, light = STEEL
    for y in range(2, 14):
        c.rect(3, y, 13, y + 1, BARE_ALU[1] if y % 4 else (96, 98, 104, 255))
        c.rect(3, y, 5, y + 1, BARE_ALU[2] if y % 4 else (120, 122, 128, 255))
        c.rect(12, y, 13, y + 1, BARE_ALU[0])
    for x0 in (1, 13):
        c.rect(x0, 0, x0 + 2, 16, mid)
        c.rect(x0, 0, x0 + 1, 16, light)
        c.rect(x0 + 1, 0, x0 + 2, 16, dark)

    # the spacer: a square frame with a sub-conductor clamped at each corner
    c.outline(5, 5, 11, 11, dark)
    c.rect(6, 6, 10, 10, (0, 0, 0, 0))
    for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
        c.rect(x, y, x + 1, y + 1, BARE_ALU[2])
    return c


def item_flat():
    c = Canvas(16, 16)
    c.rect(1, 9, 15, 12, (24, 32, 74, 255))
    c.outline(1, 9, 15, 12, (168, 172, 178, 255))
    for x in range(3, 14, 3):
        c.rect(x, 10, x + 1, 11, (198, 202, 210, 255))
    c.rect(2, 12, 4, 14, (146, 150, 154, 255))
    c.rect(12, 12, 14, 14, (146, 150, 154, 255))
    return c


def item_tilt():
    c = Canvas(16, 16)
    for i in range(12):
        y = 10 - i // 2
        c.rect(2 + i, y, 3 + i, y + 3, (24, 32, 74, 255))
        c.set(2 + i, y, (168, 172, 178, 255))
    c.rect(3, 11, 5, 14, (146, 150, 154, 255))
    c.rect(11, 7, 13, 14, (146, 150, 154, 255))
    return c


def item_track():
    c = Canvas(16, 16)
    for i in range(12):
        y = 5 + i // 3
        c.rect(2 + i, y, 3 + i, y + 3, (24, 32, 74, 255))
        c.set(2 + i, y, (168, 172, 178, 255))
    c.rect(7, 8, 9, 15, (146, 150, 154, 255))
    c.rect(6, 14, 10, 15, (146, 150, 154, 255))
    return c


def item_dual():
    c = Canvas(16, 16)
    c.rect(2, 3, 14, 6, (24, 32, 74, 255))
    c.outline(2, 3, 14, 6, (168, 172, 178, 255))
    c.rect(7, 6, 9, 8, (194, 197, 201, 255))
    c.rect(7, 8, 9, 15, (146, 150, 154, 255))
    c.rect(5, 14, 11, 15, (146, 150, 154, 255))
    return c


def item_inverter():
    c = Canvas(16, 16)
    c.rect(3, 2, 13, 15, (194, 197, 201, 255))
    c.outline(3, 2, 13, 15, (140, 144, 148, 255))
    c.rect(5, 4, 11, 7, (16, 20, 18, 255))
    for x in range(5, 11, 2):
        c.set(x, 5, (110, 220, 130, 255))
    c.rect(5, 9, 11, 10, (140, 144, 148, 255))
    c.rect(5, 11, 11, 12, (140, 144, 148, 255))
    c.disc(11, 13, 1, (146, 150, 154, 255))
    return c


STEEL = ((96, 100, 106, 255), (158, 163, 170, 255), (212, 216, 222, 255))
GRIP = ((104, 26, 24, 255), (172, 48, 42, 255), (214, 92, 78, 255))
INSTRUMENT = ((150, 154, 160, 255), (226, 229, 234, 255), (255, 255, 255, 255))


def limb(c, start, end, width, palette, margin=1.4):
    """A shaded bar: shadow underneath, body over it, highlight along the top-left edge."""
    dark, mid, light = palette
    x0, y0 = start
    x1, y1 = end
    c.stroke(x0 + margin / 3.0, y0 + margin / 3.0, x1 + margin / 3.0, y1 + margin / 3.0, dark, width + margin)
    c.stroke(x0, y0, x1, y1, mid, width)
    c.stroke(x0 - 0.55, y0 - 0.55, x1 - 0.55, y1 - 0.55, light, max(1.0, width - 2.0))


# The spanner, drawn.  D dark steel, S the body, L the lit edge, K the knurl on the worm block.
WRENCH = (
    '..........LSSD..',
    '.........LSSD...',
    '........LSSD....',
    '.......LSSD...LS',
    '......LSSD...LSS',
    '.....LSSD...LSSD',
    '....LSSSSSSSSSD.',
    '...LSSKKKKSSSD..',
    '...LSSKKKSSD....',
    '..LSSSSSSD......',
    '..LSSSD.........',
    '.LSSSD..........',
    '.LSSD...........',
    'LSSD............',
    'LS.D............',
    '.DD.............',
)


def item_wrench():
    """An adjustable spanner, all steel: a handle with a hanging hole, a knurled worm block, two jaws."""
    return sprite(WRENCH, {
        'D': STEEL[0],
        'S': STEEL[1],
        'L': STEEL[2],
        'K': (70, 74, 80, 255),
    })


def item_tower():
    """One section of tubular tower: a taper, a flange at each end"""
    c = Canvas(16, 16)
    dark, mid, light = STEEL

    for y in range(2, 14):
        # the taper: eight pixels across at the base, six at the top, which is about the ratio
        # a real tower section is built to
        half = 4.0 - 1.0 * (13 - y) / 11.0
        x0 = int(round(8 - half))
        x1 = int(round(8 + half))
        # four tones across the width, which is what makes a flat strip read as a tube
        c.rect(x0, y, x1, y + 1, mid)
        c.rect(x0, y, x0 + 1, y + 1, light)
        c.rect(x0 + 1, y, x0 + 2, y + 1, shade(light, -18))
        c.rect(x1 - 1, y, x1, y + 1, dark)
        c.rect(x1 - 2, y, x1 - 1, y + 1, shade(dark, 26))

    for y, half in ((1, 4.6), (13, 5.0)):
        x0, x1 = int(round(8 - half)), int(round(8 + half))
        c.rect(x0, y, x1, y + 2, mid)
        c.rect(x0, y, x1, y + 1, light)
        c.rect(x0, y + 1, x1, y + 2, dark)
        # the bolts through the flange, on its lit face where they can be seen
        for x in range(x0 + 1, x1 - 1, 2):
            c.set(x, y, (72, 76, 80, 255))

    return c


def item_met():
    """The mast, with the three instruments that identify it."""
    c = Canvas(16, 16)
    steel_dark, steel_mid, steel_light = STEEL
    shield_dark, shield_mid, shield_light = INSTRUMENT

    # the mast: two pixels, lit down the left edge
    c.rect(7, 3, 9, 14, steel_mid)
    c.rect(7, 3, 8, 14, steel_light)
    c.rect(8, 3, 9, 14, steel_dark)

    # a splayed footing
    c.rect(5, 13, 11, 15, steel_mid)
    c.rect(5, 13, 11, 14, steel_light)
    c.rect(5, 14, 11, 15, steel_dark)
    c.stroke(6.0, 13.0, 4.5, 15.0, steel_mid, 1.4)
    c.stroke(10.0, 13.0, 11.5, 15.0, steel_mid, 1.4)

    # the anemometer: a hub with three cups on it, on arms thin enough to leave gaps between them
    c.rect(7, 2, 9, 3, steel_dark)
    c.stroke(4.6, 1.6, 8.0, 2.4, steel_mid, 1.0)
    c.stroke(11.4, 1.6, 8.0, 2.4, steel_mid, 1.0)
    for cx, cy in ((3, 1), (12, 1), (8, 0)):
        c.disc(cx, cy, 1.2, shield_mid)
        c.set(cx - 1, cy - 1, shield_light)

    # the radiometer on its boom, held out clear of the mast's own shadow
    c.rect(9, 6, 13, 7, steel_mid)
    c.rect(11, 5, 15, 6, shield_mid)
    c.disc(13, 4, 1.4, (198, 224, 246, 255))
    c.set(12, 3, shield_light)

    # the radiation shield: a stack of plates with air between them
    for i in range(3):
        y = 7 + i * 2
        c.rect(2, y, 7, y + 1, shield_light if i == 0 else shield_mid)
        c.rect(2, y + 1, 7, y + 2, shield_dark)
    c.rect(6, 7, 7, 12, steel_dark)
    return c


# A turbine's own colours: the paint really is off-white, and it is off-white for a reason - a machine
PAINT = ((104, 110, 120, 255), (200, 207, 215, 255), (243, 246, 250, 255))


def blade(c, hub, direction, length, root, palette):
    """One blade: a taper from root to tip, with the light along its leading edge."""
    dark, mid, light = palette
    dx, dy = direction
    steps = max(6, int(length * 4))
    # the shadow pass reaches a pixel past the body rather than sitting under it
    # blade an edge against a light background instead of fading into it
    for offset, colour, thinner in ((0.0, dark, -0.9), (0.0, mid, 0.0), (-0.6, light, 0.9)):
        for i in range(steps + 1):
            along = i / steps
            radius = root * (1.0 - 0.66 * along) / 2.0 - thinner
            if radius < 0.35:
                continue

            c.disc(hub[0] + dx * length * along + offset, hub[1] + dy * length * along + offset,
                   radius, colour)


def item_turbine(rotor, length, height, hub, root, vane=False, cooler=True):
    """A machine's own nacelle and rotor, and no tower.

    catalogue's own story, which is that the C90 and the C112 share a nacelle because they share a
    """
    c = Canvas(32, 32)
    dark, mid, light = PAINT
    # A three-blade rotor reaches a full radius above its hub and half a radius below it
    centre = (16.0, 15.0 + 0.22 * rotor)

    # the nacelle first
    x0 = int(centre[0]) + 1
    x1 = int(centre[0] + 1 + length)
    top = int(centre[1] - height / 2.0)
    bottom = int(centre[1] + height / 2.0)
    c.rect(x0, top, x1, bottom, mid)
    c.rect(x0, top, x1, top + 1, light)
    c.rect(x0, bottom - 1, x1, bottom, dark)
    c.rect(x1 - 1, top, x1, bottom, dark)
    # the shell closes over the yaw deck at the back, so the corners come off rather than the whole end
    # being rounded - a disc there read as a ball stuck on the back of the machine
    for y in (top, bottom - 1):
        c.set(x1 - 1, y, (0, 0, 0, 0))
        c.set(x1 - 2, y, dark)

    if cooler:
        # the radiator, and it is on the roof of the rear half where a real one is
        # gearbox and main bearing, with nothing to cool
        c.rect(x0 + 4, top - 2, x1 - 2, top, shade(mid, -34))
        c.rect(x0 + 4, top - 2, x1 - 2, top - 1, shade(mid, -8))
        c.rect(x0 + 4, top - 2, x0 + 5, top, dark)

    if vane:
        # a tail boom and fin: how a small machine yaws
        boom = int(centre[1])
        c.rect(x1 - 1, boom - 1, x1 + 4, boom + 1, mid)
        c.rect(x1 - 1, boom - 1, x1 + 4, boom, light)
        for i in range(5):
            half = 1.6 + i * 1.1
            x = x1 + 3 + i
            c.rect(x, int(boom - half), x + 1, int(boom + half + 1), mid)
            c.set(x, int(boom - half), light)

    # the blades: one straight up and two down at a hundred and twenty degrees off it
    for angle in (-90.0, 30.0, 150.0):
        radians = math.radians(angle)
        blade(c, centre, (math.cos(radians), math.sin(radians)), rotor, root, PAINT)

    # the hub over the blade roots, so they meet inside it rather than crossing in the open.
    # cone, which is what is actually there: bright where it faces the light, dark round the rim
    c.disc(centre[0], centre[1], hub, dark)
    c.disc(centre[0] - 0.3, centre[1] - 0.4, hub - 0.6, mid)
    c.disc(centre[0] - 0.9, centre[1] - 1.0, hub - 1.4, light)
    return c


# One entry per machine in TurbineCatalog
TURBINES = {
    'sw_10': dict(rotor=10.5, length=5.0, height=4.0, hub=1.8, root=2.8, vane=True, cooler=False),
    'c52_085': dict(rotor=13.0, length=8.0, height=6.0, hub=2.3, root=4.0),
    'c80_20': dict(rotor=13.5, length=10.0, height=7.0, hub=2.5, root=3.6),
    'c90_30': dict(rotor=14.0, length=11.0, height=8.0, hub=2.7, root=3.5),
    'c112_30': dict(rotor=15.0, length=11.0, height=8.0, hub=2.7, root=2.9),
    'wind_turbine': dict(rotor=15.0, length=12.0, height=9.0, hub=2.9, root=3.2),
}


# ------------------------------------------------------------- panel layouts

PANELS = {
    'pv_inverter': dict(width=288, height=252, bar_x=12, bar_width=264, bars=(70, 98, 126), separators=(40, 142)),
    'pv_array': dict(width=288, height=232, bar_x=12, bar_width=264, bars=(70, 98, 126), separators=(40, 142)),
    'met_station': dict(width=288, height=208, bar_x=12, bar_width=264, bars=(), separators=(20, 176)),
    # shorter than the rest, because a combiner box is switchgear: one bar, and nothing to control
    'pv_combiner': dict(width=288, height=148, bar_x=12, bar_width=264, bars=(72,), separators=(40, 88)),
}


# Side of the sheet each panel is drawn into.
PANEL_SHEET = 512


def panel(spec):
    """A vanilla-looking dialog: the raised body, the bevel, and the wells for the bars."""
    c = Canvas(PANEL_SHEET, PANEL_SHEET)
    w, h = spec['width'], spec['height']

    body = (198, 198, 198, 255)
    light = (255, 255, 255, 255)
    dark = (85, 85, 85, 255)
    well = (139, 139, 139, 255)
    wellDark = (55, 55, 55, 255)

    c.rect(0, 0, w, h, body)
    # the vanilla bevel: two light edges top and left, two dark bottom and right
    c.rect(0, 0, w, 1, light)
    c.rect(0, 0, 1, h, light)
    c.rect(1, 1, w - 1, 2, light)
    c.rect(1, 1, 2, h - 1, light)
    c.rect(0, h - 1, w, h, dark)
    c.rect(w - 1, 0, w, h, dark)
    c.rect(1, h - 2, w - 1, h - 1, dark)
    c.rect(w - 2, 1, w - 1, h - 1, dark)

    for y in spec['separators']:
        c.rect(11, y, w - 11, y + 1, well)
        c.rect(11, y + 1, w - 11, y + 2, light)

    for y in spec['bars']:
        x0, x1 = spec['bar_x'], spec['bar_x'] + spec['bar_width']
        c.rect(x0 - 1, y - 1, x1 + 1, y + 11, wellDark)
        c.rect(x0, y, x1, y + 10, well)
        c.rect(x0, y + 9, x1, y + 10, light)
        c.rect(x1 - 1, y, x1, y + 10, light)

    return c


BLOCK_TEXTURES = {
    'dc_string_line': lambda: cable_line(1.0, 1),
    'dc_trunk_line': lambda: cable_line(1.0, 1, armoured=True),
    # the same pair filling the whole tile, for the stub on an array: the OBJ pipeline maps a face
    'dc_harness': lambda: cable_line(8.0, 1, flat=True),
    'dc_trench': trench,
    'dc_jacket': cable_jacket,
    'pv_combiner_door': combiner_door,
    'pv_dc_section': dc_section,
    'pv_switch': switch_handle,
    'pv_module': module,
    'pv_module_back': module_back,
    'pv_module_edge': module_edge,
    'pv_frame': anodised,
    'pv_steel': galvanised,
    'pv_steel_end': steel_end,
    'pv_cabinet': cabinet,
    'pv_cabinet_door': cabinet_door,
    'pv_cabinet_top': cabinet_top,
    'pv_vent': vent,
    'pv_display': display,
    'pv_instrument': instrument,
    'pv_dome': dome,
}


# ------------------------------------------------------------- the parts a machine is made of

COPPER = ((116, 62, 30, 255), (186, 108, 58, 255), (228, 156, 100, 255))
GLASS = ((84, 118, 138, 255), (152, 198, 216, 255), (218, 242, 250, 255))
POLYMER = ((78, 60, 34, 255), (154, 122, 66, 255), (214, 186, 128, 255))
SILICON = ((46, 50, 64, 255), (104, 112, 132, 255), (170, 180, 200, 255))
CELL = ((14, 20, 46, 255), (34, 46, 100, 255), (92, 112, 176, 255))
CIRCUIT = ((16, 58, 38, 255), (34, 108, 64, 255), (104, 180, 118, 255))
DARK = ((22, 24, 28, 255), (52, 56, 62, 255), (96, 102, 110, 255))
BRASS = ((124, 96, 30, 255), (192, 158, 62, 255), (232, 206, 120, 255))


def plate(c, x0, y0, x1, y1, palette, lip=2):
    """A sheet seen at a slight angle: face, a lit top edge, a dark near edge."""
    dark, mid, light = palette
    c.rect(x0, y0, x1, y1, mid)
    c.rect(x0, y0, x1, y0 + lip, light)
    c.rect(x0, y1 - lip, x1, y1, dark)
    c.rect(x1 - lip, y0, x1, y1, shade(mid, -22))


def stack_lines(c, x0, y0, x1, y1, step, colour):
    for y in range(y0, y1, step):
        c.rect(x0, y, x1, y + 1, colour)


def item_steel_plate():
    """Rolled sheet, stacked: two plates offset, which is how sheet is stored and how it reads."""
    c = Canvas(16, 16)
    plate(c, 2, 7, 13, 12, STEEL)
    plate(c, 4, 4, 15, 9, STEEL)
    return c


def item_steel_section():
    """An angle in profile: two webs at right angles"""
    c = Canvas(16, 16)
    dark, mid, light = STEEL
    c.rect(2, 3, 6, 14, mid)
    c.rect(2, 3, 4, 14, light)
    c.rect(2, 11, 14, 14, mid)
    c.rect(2, 11, 14, 12, light)
    c.rect(2, 13, 14, 14, dark)
    c.rect(5, 3, 6, 12, dark)
    return c


def item_tempered_glass():
    """A pane on edge: glass, and the one thing that says glass is what you can see through it."""
    c = Canvas(16, 16)
    dark, mid, light = GLASS
    c.rect(3, 2, 13, 14, (mid[0], mid[1], mid[2], 150))
    c.rect(3, 2, 13, 3, light)
    c.rect(3, 13, 13, 14, dark)
    c.rect(3, 2, 4, 14, light)
    c.rect(12, 2, 13, 14, dark)
    # the highlight across it, which is the whole trick for drawing glass at this size
    c.stroke(5.0, 11.0, 11.0, 4.0, (235, 250, 255, 210), 1.6)
    return c


def item_resin():
    """A roll of encapsulant film, wound on a core."""
    c = Canvas(16, 16)
    dark, mid, light = POLYMER
    c.rect(2, 5, 14, 12, mid)
    c.rect(2, 5, 14, 6, light)
    c.rect(2, 11, 14, 12, dark)
    for x in range(3, 13, 3):
        c.rect(x, 6, x + 1, 11, shade(mid, -26))
    # the loose end hanging off it
    c.stroke(13.5, 8.0, 15.0, 13.0, mid, 1.4)
    return c


def item_copper_coil():
    """Magnet wire wound on a bobbin: turns you can count, in copper."""
    c = Canvas(16, 16)
    dark, mid, light = COPPER
    c.rect(3, 3, 13, 13, mid)
    for y in range(3, 13, 2):
        c.rect(3, y, 13, y + 1, light)
        c.rect(3, y + 1, 13, y + 2, dark)
    # the bobbin flanges
    c.rect(2, 2, 4, 14, STEEL[1])
    c.rect(12, 2, 14, 14, STEEL[0])
    return c


def item_silicon_ingot():
    """A cast ingot: a bar with the crystal sheen silicon has."""
    c = Canvas(16, 16)
    dark, mid, light = SILICON
    c.rect(2, 6, 14, 12, mid)
    c.rect(2, 6, 14, 8, light)
    c.rect(2, 11, 14, 12, dark)
    c.rect(3, 4, 13, 7, shade(mid, 18))
    c.rect(3, 4, 13, 5, light)
    for x in range(4, 13, 4):
        c.rect(x, 8, x + 1, 11, shade(mid, -20))
    return c


def item_silicon_wafer():
    """A wafer: a thin square with its corners taken off"""
    c = Canvas(16, 16)
    dark, mid, light = SILICON
    c.rect(3, 3, 13, 13, mid)
    for x, y in ((3, 3), (11, 3), (3, 11), (11, 11)):
        c.rect(x, y, x + 2, y + 2, (0, 0, 0, 0))
        c.set(x + (1 if x < 8 else 0), y + (1 if y < 8 else 0), dark)
    c.rect(3, 3, 13, 4, light)
    c.rect(3, 12, 13, 13, dark)
    c.stroke(5.0, 10.0, 10.0, 5.0, shade(light, 20), 1.2)
    return c


def item_busbar():
    """Drawn copper bar with the bolt holes a bar is joined through."""
    c = Canvas(16, 16)
    dark, mid, light = COPPER
    c.rect(1, 6, 15, 11, mid)
    c.rect(1, 6, 15, 7, light)
    c.rect(1, 10, 15, 11, dark)
    for x in (3, 8, 12):
        c.disc(x, 8, 1.2, shade(dark, -20))
    return c


def item_solar_cell():
    """A cell: dark blue silicon with two busbars and the fingers between them."""
    c = Canvas(16, 16)
    dark, mid, light = CELL
    c.rect(2, 2, 14, 14, mid)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, (0, 0, 0, 0))
    for x in (5, 10):
        c.rect(x, 2, x + 1, 14, (198, 202, 210, 255))
    for y in range(3, 14, 3):
        c.rect(2, y, 14, y + 1, shade(mid, 26))
    c.rect(2, 2, 14, 3, light)
    return c


def item_bypass_diode():
    """A glass-bodied diode: a barrel, a band at the cathode, a lead each end."""
    c = Canvas(16, 16)
    c.rect(1, 8, 15, 9, STEEL[1])
    c.rect(4, 5, 12, 12, DARK[1])
    c.rect(4, 5, 12, 6, DARK[2])
    c.rect(4, 11, 12, 12, DARK[0])
    c.rect(9, 5, 11, 12, (206, 206, 210, 255))
    return c


def item_mc4_connector():
    """The plug and socket of a string connection"""
    c = Canvas(16, 16)
    dark, mid, light = DARK
    # the socket
    c.rect(2, 3, 8, 8, mid)
    c.rect(2, 3, 8, 4, light)
    c.rect(6, 5, 10, 7, STEEL[1])
    # the plug, and its lead
    c.rect(6, 9, 12, 14, mid)
    c.rect(6, 9, 12, 10, light)
    c.rect(3, 11, 7, 13, STEEL[0])
    c.stroke(2.0, 12.0, 0.5, 14.0, GRIP[1], 1.6)
    return c


def item_junction_box():
    """A module's junction box: a lid, the gland"""
    c = Canvas(16, 16)
    dark, mid, light = DARK
    c.rect(3, 3, 13, 11, mid)
    c.rect(3, 3, 13, 4, light)
    c.rect(3, 10, 13, 11, dark)
    for x in range(5, 12, 3):
        c.rect(x, 5, x + 2, 9, shade(mid, -18))
    for x, colour in ((5, GRIP[1]), (9, DARK[0])):
        c.rect(x, 11, x + 2, 13, STEEL[0])
        c.stroke(x + 1.0, 13.0, x + 1.0, 15.5, colour, 1.6)
    return c


def item_power_module():
    """A power module: dies on a substrate under a moulded lid, with the terminals out of the top."""
    c = Canvas(16, 16)
    dark, mid, light = DARK
    c.rect(2, 5, 14, 13, mid)
    c.rect(2, 5, 14, 6, light)
    c.rect(2, 12, 14, 13, dark)
    for x in (4, 8, 11):
        c.rect(x, 7, x + 3, 11, SILICON[1])
        c.rect(x, 7, x + 3, 8, SILICON[2])
    for x in (3, 12):
        c.rect(x, 2, x + 2, 6, COPPER[1])
        c.rect(x, 2, x + 2, 3, COPPER[2])
    return c


def item_capacitor_bank():
    """Three film capacitors in a row, banded, on a busbar."""
    c = Canvas(16, 16)
    for i, x in enumerate((2, 7, 11)):
        c.rect(x, 3, x + 4, 12, (56, 74, 120, 255))
        c.rect(x, 3, x + 4, 4, (96, 122, 176, 255))
        c.rect(x, 11, x + 4, 12, (34, 46, 78, 255))
        c.rect(x, 6, x + 4, 7, (206, 208, 214, 255))
    c.rect(1, 12, 15, 14, COPPER[1])
    c.rect(1, 13, 15, 14, COPPER[0])
    return c


def item_magnetic_core():
    """An E-core with a winding through the middle: laminations, and copper where the flux does work."""
    c = Canvas(16, 16)
    dark, mid, light = STEEL
    c.rect(2, 3, 14, 13, mid)
    c.rect(2, 3, 14, 4, light)
    c.rect(2, 12, 14, 13, dark)
    stack_lines(c, 2, 4, 14, 12, 2, shade(mid, -22))
    c.rect(5, 3, 11, 13, COPPER[1])
    for y in range(3, 13, 2):
        c.rect(5, y, 11, y + 1, COPPER[2])
    return c


def item_control_board():
    """A populated board: a processor, a header, and the traces between them."""
    c = Canvas(16, 16)
    dark, mid, light = CIRCUIT
    c.rect(1, 2, 15, 14, mid)
    c.rect(1, 2, 15, 3, light)
    c.rect(1, 13, 15, 14, dark)
    c.rect(4, 5, 10, 11, DARK[1])
    c.rect(4, 5, 10, 6, DARK[2])
    for x in range(5, 10, 2):
        c.rect(x, 4, x + 1, 5, BRASS[1])
        c.rect(x, 11, x + 1, 12, BRASS[1])
    for y in (4, 12):
        c.rect(11, y, 14, y + 1, BRASS[1])
    c.rect(11, 6, 14, 10, DARK[0])
    return c


def item_bearing():
    """A bearing from the end: an outer race, the balls, an inner race."""
    c = Canvas(16, 16)
    dark, mid, light = STEEL
    c.disc(8, 8, 7.0, mid)
    c.disc(8, 8, 7.0, mid)
    c.disc(7.4, 7.4, 6.4, light)
    c.disc(8, 8, 5.4, shade(mid, -30))
    for i in range(8):
        angle = i * math.pi / 4.0
        c.disc(8 + 4.4 * math.cos(angle), 8 + 4.4 * math.sin(angle), 1.1, light)
    c.disc(8, 8, 2.8, mid)
    c.disc(8, 8, 1.6, (0, 0, 0, 0))
    return c


def item_gear_set():
    """Two gears in mesh, which is the least a set can be."""
    c = Canvas(16, 16)
    dark, mid, light = STEEL

    def gear(cx, cy, radius, teeth):
        c.disc(cx, cy, radius, mid)
        c.disc(cx - 0.6, cy - 0.6, radius - 1.0, light)
        c.disc(cx, cy, radius - 2.0, shade(mid, -26))
        for i in range(teeth):
            angle = i * 2.0 * math.pi / teeth
            c.disc(cx + radius * math.cos(angle), cy + radius * math.sin(angle), 1.0, mid)
        c.disc(cx, cy, 1.2, dark)

    gear(5.5, 6.0, 4.2, 8)
    gear(11.5, 11.0, 3.4, 7)
    return c


def item_gpv_fuse():
    """A photovoltaic fuse: a barrel with a metal cap at each end and the element behind glass."""
    c = Canvas(16, 16)
    c.rect(2, 6, 14, 11, (222, 216, 200, 255))
    c.rect(2, 6, 14, 7, (244, 240, 230, 255))
    c.rect(2, 10, 14, 11, (186, 178, 160, 255))
    for x in (1, 12):
        c.rect(x, 5, x + 3, 12, STEEL[1])
        c.rect(x, 5, x + 3, 6, STEEL[2])
        c.rect(x, 11, x + 3, 12, STEEL[0])
    c.stroke(4.5, 8.5, 11.5, 8.5, (150, 120, 70, 255), 1.2)
    return c


def item_load_break_switch():
    """The switch itself: a moulded body, the handle, and the terminals it breaks between."""
    c = Canvas(16, 16)
    dark, mid, light = DARK
    c.rect(3, 5, 13, 14, mid)
    c.rect(3, 5, 13, 6, light)
    c.rect(3, 13, 13, 14, dark)
    for x in (4, 10):
        c.rect(x, 12, x + 2, 15, COPPER[1])
    # the handle, red because a load-break handle is red
    c.rect(7, 1, 10, 6, GRIP[1])
    c.rect(7, 1, 10, 2, GRIP[2])
    c.rect(6, 5, 11, 7, shade(mid, -20))
    return c


def item_sensor_head():
    """A glass-domed element on a machined body, which is what every radiometer looks like."""
    c = Canvas(16, 16)
    dark, mid, light = INSTRUMENT
    c.rect(3, 8, 13, 13, mid)
    c.rect(3, 8, 13, 9, light)
    c.rect(3, 12, 13, 13, dark)
    stack_lines(c, 3, 9, 13, 12, 2, shade(mid, -30))
    c.disc(8, 7, 4.2, GLASS[0])
    c.disc(8, 7, 3.4, GLASS[1])
    c.disc(6.6, 5.6, 1.4, GLASS[2])
    c.rect(2, 13, 14, 15, STEEL[0])
    return c


def item_pv_laminate():
    """A module: cells behind glass in a frame"""
    c = Canvas(16, 16)
    c.rect(1, 1, 15, 15, STEEL[1])
    c.rect(2, 2, 14, 14, CELL[1])
    for x in range(3, 14, 3):
        c.rect(x, 2, x + 1, 14, shade(CELL[1], -22))
    for y in range(4, 14, 3):
        c.rect(2, y, 14, y + 1, shade(CELL[1], 22))
    c.rect(2, 7, 14, 8, (188, 194, 204, 255))
    c.rect(1, 1, 15, 2, STEEL[2])
    c.rect(1, 14, 15, 15, STEEL[0])
    return c


def item_enclosure():
    """A sealed cabinet: a door with a gasket line round it and a quarter-turn latch."""
    c = Canvas(16, 16)
    dark, mid, light = ((150, 154, 160, 255), (198, 202, 208, 255), (226, 230, 236, 255))
    c.rect(2, 1, 14, 15, mid)
    c.rect(2, 1, 14, 2, light)
    c.rect(2, 14, 14, 15, dark)
    c.outline(3, 3, 13, 13, shade(mid, -30))
    c.rect(12, 7, 14, 10, shade(mid, -50))
    c.rect(12, 7, 14, 8, light)
    for y in (4, 11):
        c.rect(1, y, 3, y + 2, dark)
    return c


def item_dc_section():
    """A fuse way assembly: carriers in a row on a busbar, with the switch at the end."""
    c = Canvas(16, 16)
    c.rect(1, 2, 15, 14, (196, 200, 206, 255))
    c.rect(1, 2, 15, 3, (226, 230, 236, 255))
    c.rect(1, 13, 15, 14, (150, 154, 160, 255))
    for x in range(2, 11, 3):
        c.rect(x, 4, x + 2, 10, (222, 216, 200, 255))
        c.rect(x, 4, x + 2, 5, BRASS[1])
        c.rect(x, 9, x + 2, 10, STEEL[1])
    c.rect(1, 11, 15, 13, COPPER[1])
    c.rect(12, 3, 15, 10, DARK[1])
    c.rect(13, 4, 14, 7, GRIP[1])
    return c


def item_inverter_bridge():
    """Power modules bolted to a heatsink, with the DC link over them."""
    c = Canvas(16, 16)
    dark, mid, light = STEEL
    # the heatsink, which is what a bridge is mostly made of
    c.rect(1, 8, 15, 15, mid)
    for x in range(1, 15, 2):
        c.rect(x, 8, x + 1, 15, light)
        c.rect(x + 1, 8, x + 2, 15, shade(mid, -34))
    c.rect(1, 8, 15, 9, light)
    for x in (2, 6, 10):
        c.rect(x, 5, x + 4, 9, DARK[1])
        c.rect(x, 5, x + 4, 6, DARK[2])
    c.rect(2, 1, 14, 5, (56, 74, 120, 255))
    c.rect(2, 1, 14, 2, (96, 122, 176, 255))
    c.rect(2, 3, 14, 4, COPPER[1])
    return c


def item_mounting_rack():
    """Rails on posts: the frame an array is bolted to, seen from the end."""
    c = Canvas(16, 16)
    dark, mid, light = STEEL
    c.stroke(1.5, 11.0, 14.5, 4.0, mid, 2.6)
    c.stroke(1.5, 10.0, 14.5, 3.0, light, 1.0)
    for x, top in ((4.0, 9.5), (11.0, 6.0)):
        c.rect(int(x), int(top), int(x) + 2, 15, mid)
        c.rect(int(x), int(top), int(x) + 1, 15, light)
    c.rect(2, 14, 14, 15, dark)
    return c


def item_torque_tube():
    """A tube with a bearing on it"""
    c = Canvas(16, 16)
    dark, mid, light = STEEL
    c.rect(0, 6, 16, 11, mid)
    c.rect(0, 6, 16, 7, light)
    c.rect(0, 10, 16, 11, dark)
    for x in (3, 10):
        c.rect(x, 4, x + 3, 13, shade(mid, -26))
        c.rect(x, 4, x + 3, 5, light)
        c.rect(x, 12, x + 3, 13, dark)
    return c


def item_slew_drive():
    """A geared ring with a motor on it: what turns a tracker and what yaws a nacelle."""
    c = Canvas(16, 16)
    dark, mid, light = STEEL
    c.disc(7, 9, 6.4, mid)
    c.disc(6.4, 8.4, 5.6, light)
    c.disc(7, 9, 4.2, shade(mid, -30))
    for i in range(10):
        angle = i * 2.0 * math.pi / 10.0
        c.disc(7 + 6.4 * math.cos(angle), 9 + 6.4 * math.sin(angle), 0.9, mid)
    c.disc(7, 9, 1.8, dark)
    # the motor, off to one side where it drives the ring
    c.rect(10, 1, 15, 7, DARK[1])
    c.rect(10, 1, 15, 2, DARK[2])
    c.rect(11, 6, 14, 8, STEEL[0])
    return c


def item_generator_set():
    """A generator: a finned frame with the shaft out of one end and the terminal box on top."""
    c = Canvas(16, 16)
    dark, mid, light = STEEL
    c.rect(2, 4, 14, 13, mid)
    c.rect(2, 4, 14, 5, light)
    c.rect(2, 12, 14, 13, dark)
    for x in range(3, 14, 2):
        c.rect(x, 5, x + 1, 12, shade(mid, -28))
    c.rect(0, 7, 3, 10, STEEL[0])
    c.rect(5, 1, 11, 5, DARK[1])
    c.rect(5, 1, 11, 2, DARK[2])
    c.rect(6, 13, 10, 15, dark)
    return c


def item_gearbox():
    """A gearbox housing: a split casing on its feet, with the input shaft showing."""
    c = Canvas(16, 16)
    dark, mid, light = STEEL
    c.rect(2, 3, 14, 13, mid)
    c.rect(2, 3, 14, 4, light)
    c.rect(2, 12, 14, 13, dark)
    c.rect(2, 7, 14, 8, shade(mid, -40))
    for x in (3, 7, 11):
        c.disc(x + 0.5, 5.5, 0.9, shade(mid, -52))
        c.disc(x + 0.5, 10.5, 0.9, shade(mid, -52))
    c.rect(14, 6, 16, 9, STEEL[0])
    for x in (2, 11):
        c.rect(x, 13, x + 3, 15, dark)
    return c


def blade_sprite(long_blade):
    """A blade laid corner to corner: wide at the root, thin at the tip, with the spar showing."""
    c = Canvas(16, 16)
    dark, mid, light = PAINT
    root = (2.5, 13.5)
    tip = (13.5, 2.5) if long_blade else (11.0, 5.0)
    steps = 26
    for offset, colour, thinner in ((0.0, dark, -0.8), (0.0, mid, 0.0), (-0.6, light, 0.9)):
        for i in range(steps + 1):
            along = i / steps
            radius = (3.4 if long_blade else 3.0) * (1.0 - 0.78 * along) / 2.0 - thinner
            if radius < 0.35:
                continue
            c.disc(root[0] + (tip[0] - root[0]) * along + offset,
                   root[1] + (tip[1] - root[1]) * along + offset, radius, colour)

    # the root flange, which is the end that bolts to the hub
    c.disc(root[0], root[1], 2.4, STEEL[1])
    c.disc(root[0] - 0.6, root[1] - 0.6, 1.6, STEEL[2])
    return c


PARTS = {
    'steel_plate': item_steel_plate,
    'steel_section': item_steel_section,
    'tempered_glass': item_tempered_glass,
    'resin': item_resin,
    'copper_coil': item_copper_coil,
    'silicon_ingot': item_silicon_ingot,
    'silicon_wafer': item_silicon_wafer,
    'busbar': item_busbar,
    'solar_cell': item_solar_cell,
    'bypass_diode': item_bypass_diode,
    'mc4_connector': item_mc4_connector,
    'junction_box': item_junction_box,
    'power_module': item_power_module,
    'capacitor_bank': item_capacitor_bank,
    'magnetic_core': item_magnetic_core,
    'control_board': item_control_board,
    'bearing': item_bearing,
    'gear_set': item_gear_set,
    'gpv_fuse': item_gpv_fuse,
    'load_break_switch': item_load_break_switch,
    'sensor_head': item_sensor_head,
    'pv_laminate': item_pv_laminate,
    'enclosure': item_enclosure,
    'dc_section': item_dc_section,
    'inverter_bridge': item_inverter_bridge,
    'mounting_rack': item_mounting_rack,
    'torque_tube': item_torque_tube,
    'slew_drive': item_slew_drive,
    'generator_set': item_generator_set,
    'gearbox': item_gearbox,
    'turbine_blade': lambda: blade_sprite(False),
    'long_blade': lambda: blade_sprite(True),
}


ITEM_TEXTURES = {
    'pv_combiner': item_combiner,
    'dc_string_cable': item_string_cable,
    'dc_trunk_cable': item_trunk_cable,
    'abc_conductor': item_abc_conductor,
    'mv_conductor': item_mv_conductor,
    'hv_conductor': item_hv_conductor,
    'power_wrench': item_wrench,
    'turbine_tower': item_tower,
    'pv_flat': item_flat,
    'pv_tilt': item_tilt,
    'pv_track': item_track,
    'pv_dual': item_dual,
    'pv_inverter': item_inverter,
    'met_station': item_met,
}

# one per machine, built from the same drawing so a line of turbines in the inventory reads as a line
# of one maker's products
ITEM_TEXTURES.update({name: (lambda s=spec: item_turbine(**s)) for name, spec in TURBINES.items()})
# and the parts, which are a family of their own
ITEM_TEXTURES.update(PARTS)


def main():
    for name, builder in BLOCK_TEXTURES.items():
        path = os.path.join(OUT, 'block', name + '.png')
        builder().write(path)
        print('block/%s.png' % name)

    for name, builder in ITEM_TEXTURES.items():
        path = os.path.join(OUT, 'item', name + '.png')
        builder().write(path)
        print('item/%s.png' % name)

    for name, spec in PANELS.items():
        path = os.path.join(OUT, 'gui', name + '.png')
        panel(spec).write(path)
        print('gui/%s.png  %dx%d, bars at %s' % (name, spec['width'], spec['height'], spec['bars']))


if __name__ == '__main__':
    main()
