#!/usr/bin/env python3
"""Generates the textures for the photovoltaic blocks, their items and their panels.

    python3 tools/gen_pv_textures.py

Writes into src/main/resources/assets/electricity/textures/.

Why by hand rather than with an imaging library: there is no PIL and no numpy on the
machine this was written on, and a PNG is not hard - a zlib stream of filtered
scanlines with three chunks around it.  Doing it this way also means the textures are
generated from the same description the geometry is, so a diff shows what changed
rather than a wall of binary.

Everything here is deterministic.  The mottling and the spangle come out of a hash of
the pixel coordinates rather than a random number generator, so running this twice
produces byte-identical files and a rebuild is not a diff.

The panel layouts at the bottom are the contract between this file and the screen
classes: the wells cut into the background are at the coordinates the Java constants
name, and if one moves the other has to.
"""

import os
import struct
import zlib

OUT = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'textures')


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


def noise(x, y, salt=0):
    """A deterministic hash in 0..1. Not a good generator, and it does not need to be."""
    n = (x * 73856093) ^ (y * 19349663) ^ (salt * 83492791)
    n = (n ^ (n >> 13)) * 1274126177
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0


def shade(colour, amount):
    return tuple(max(0, min(255, int(c + amount))) for c in colour[:3]) + (colour[3] if len(colour) > 3 else 255,)


# ------------------------------------------------------------ block textures

def module():
    """A laminate: half-cut cells in a grid, busbars across them, an anodised frame round it.

    The horizontal band across the middle is the half-cut split, which is the one
    feature that says at a glance which decade a module is from - a full-cell module
    has one continuous grid and a half-cut one is two panes with a gap.
    """
    size = 64
    c = Canvas(size, size, (18, 22, 44, 255))
    frame = (172, 176, 182, 255)
    busbar = (198, 202, 210, 255)

    c.rect(0, 0, size, size, (16, 20, 40, 255))
    # 4 by 8 cells, which at 64 pixels leaves each one 13 across and reads as a grid
    cells_x, cells_y = 4, 8
    inset = 4
    cell_w = (size - 2 * inset) // cells_x
    cell_h = (size - 2 * inset) // cells_y

    for cy in range(cells_y):
        for cx in range(cells_x):
            x0 = inset + cx * cell_w
            y0 = inset + cy * cell_h
            # every cell a little different, because a real laminate's cells are
            # sorted by current and not by colour
            tint = int(noise(cx, cy, 7) * 14) - 7
            base = shade((26, 34, 78, 255), tint)
            c.rect(x0, y0, x0 + cell_w - 1, y0 + cell_h - 1, base)
            # the corners of a pseudo-square wafer
            for dx, dy in ((0, 0), (cell_w - 2, 0), (0, cell_h - 2), (cell_w - 2, cell_h - 2)):
                c.set(x0 + dx, y0 + dy, (16, 20, 40, 255))
                c.set(x0 + dx + 1, y0 + dy, (16, 20, 40, 255))

            # two busbars down each cell, and the fine fingers between them
            for bx in (cell_w // 3, 2 * cell_h // 3):
                for y in range(y0, y0 + cell_h - 1):
                    c.set(x0 + bx, y, busbar)
            for y in range(y0 + 1, y0 + cell_h - 2, 3):
                for x in range(x0 + 1, x0 + cell_w - 2):
                    c.set(x, y, shade(base, 18))

    # the half-cut split: a ribbon bus across the middle of the laminate
    mid = size // 2
    c.rect(inset, mid - 2, size - inset, mid + 1, (14, 17, 34, 255))
    c.rect(inset, mid - 1, size - inset, mid, busbar)

    # the frame
    c.outline(0, 0, size, size, frame)
    c.outline(1, 1, size - 1, size - 1, shade(frame, -22))
    c.outline(2, 2, size - 2, size - 2, shade(frame, -40))
    return c


def anodised():
    """Anodised aluminium: light, slightly cool, with a faint drawn grain."""
    size = 32
    c = Canvas(size, size)
    base = (170, 174, 180, 255)
    for y in range(size):
        for x in range(size):
            grain = int(noise(x, 0, 3) * 16) - 8 + int(noise(x, y, 11) * 6) - 3
            c.set(x, y, shade(base, grain))
    return c


def galvanised():
    """Galvanised steel: darker, mottled, with the spangle patches hot-dip leaves."""
    size = 32
    c = Canvas(size, size)
    base = (146, 150, 154, 255)
    for y in range(size):
        for x in range(size):
            c.set(x, y, shade(base, int(noise(x, y, 5) * 20) - 10))

    # the spangle: crystals a few pixels across, brighter than the sheet around them
    for i in range(14):
        cx = int(noise(i, 1, 17) * size)
        cy = int(noise(i, 2, 19) * size)
        radius = 1 + int(noise(i, 3, 23) * 2)
        for y in range(cy - radius, cy + radius + 1):
            for x in range(cx - radius, cx + radius + 1):
                if 0 <= x < size and 0 <= y < size and (x - cx) ** 2 + (y - cy) ** 2 <= radius * radius:
                    c.set(x, y, shade(base, 22 + int(noise(x, y, 29) * 10)))
    return c


def cabinet():
    """Painted sheet steel: the pale grey every inverter and switchgear cabinet is."""
    size = 32
    c = Canvas(size, size)
    base = (194, 197, 201, 255)
    for y in range(size):
        for x in range(size):
            c.set(x, y, shade(base, int(noise(x, y, 31) * 8) - 4))

    # a swage line, which is what stops a large flat panel drumming
    c.rect(0, size // 2 - 1, size, size // 2, shade(base, -26))
    c.rect(0, size // 2, size, size // 2 + 1, shade(base, 14))
    # fixings down the edges
    for y in range(3, size, 8):
        c.set(2, y, shade(base, -46))
        c.set(size - 3, y, shade(base, -46))
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

    # a bar graph under it, which is what these displays put the load on
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


# ------------------------------------------------------------- item textures

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


def item_met():
    c = Canvas(16, 16)
    c.rect(7, 3, 9, 15, (146, 150, 154, 255))
    c.rect(5, 14, 11, 15, (146, 150, 154, 255))
    c.rect(9, 6, 14, 7, (146, 150, 154, 255))
    c.disc(12, 5, 1, (230, 232, 236, 255))
    c.rect(4, 9, 8, 10, (230, 232, 236, 255))
    for dx, dy in ((-3, -1), (3, -1), (0, 2)):
        c.disc(8 + dx, 2 + dy, 1, (230, 232, 236, 255))
    return c


# ------------------------------------------------------------- panel layouts
#
# These are the contract with the screen classes.  Each entry is the panel's size and
# the wells cut into it for bars, and the Java constants have to name the same
# numbers - so if a bar moves here it moves there.

PANELS = {
    'pv_inverter': dict(width=248, height=232, bar_x=12, bar_width=224, bars=(70, 98, 126), separators=(40, 142)),
    'pv_array': dict(width=248, height=232, bar_x=12, bar_width=224, bars=(70, 98, 126), separators=(40, 142)),
    'met_station': dict(width=224, height=196, bar_x=12, bar_width=200, bars=(), separators=(20, 178)),
}


def panel(spec):
    """A vanilla-looking dialog: the raised body, the bevel, and the wells for the bars."""
    c = Canvas(256, 256)
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
    'pv_module': module,
    'pv_frame': anodised,
    'pv_steel': galvanised,
    'pv_cabinet': cabinet,
    'pv_display': display,
    'pv_instrument': instrument,
}

ITEM_TEXTURES = {
    'pv_flat': item_flat,
    'pv_tilt': item_tilt,
    'pv_track': item_track,
    'pv_dual': item_dual,
    'pv_inverter': item_inverter,
    'met_station': item_met,
}


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
