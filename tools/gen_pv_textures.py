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

    def stroke(self, x0, y0, x1, y1, colour, width=1.0):
        """A thick line between two points, ends included.

        Every pixel within half the width of the segment, which draws a capsule rather than a
        chain of squares - a stepped diagonal is what makes a hand tool look like a staircase at
        sixteen pixels.  Colour may be transparent, which is how a jaw gets its mouth cut out.
        """
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
    """A sixteen by sixteen from a picture of it, one character to a pixel.

    Some shapes cannot be got at with strokes and discs.  A spanner's head is one: it needs two jaws a
    pixel and a half apart with a straight gap between them, and every attempt to build that out of
    capsules ended with the shadow passes welding the mouth shut.  At this size the honest way is to draw
    it, and a map of characters is a drawing that can be read in a diff.
    """
    c = Canvas(len(rows[0]), len(rows))
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            if key in palette:
                c.set(x, y, palette[key])

    return c


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


def module_back():
    """The rear of a glass-glass laminate: cells showing through, ribbons, a junction box.

    Not the front texture again, which is what the underside of every module used to be.
    A bifacial module's back is mostly pale - the encapsulant between the cells is clear
    and the ground shows through it - with the cells themselves reading as grey-blue
    squares and the interconnect ribbons bright across them.  And one junction box, which
    is the feature that says at a glance which side you are looking at.
    """
    size = 64
    c = Canvas(size, size)
    pale = (206, 209, 214, 255)
    frame = (172, 176, 182, 255)

    for y in range(size):
        for x in range(size):
            c.set(x, y, shade(pale, int(noise(x, y, 41) * 8) - 4))

    cells_x, cells_y = 4, 8
    inset = 4
    cell_w = (size - 2 * inset) // cells_x
    cell_h = (size - 2 * inset) // cells_y

    for cy in range(cells_y):
        for cx in range(cells_x):
            x0 = inset + cx * cell_w
            y0 = inset + cy * cell_h
            tint = int(noise(cx, cy, 43) * 10) - 5
            base = shade((132, 140, 156, 255), tint)
            c.rect(x0 + 1, y0 + 1, x0 + cell_w - 2, y0 + cell_h - 2, base)
            # the rear busbars, which on a back are wider and brighter than the front fingers
            for bx in (cell_w // 3, 2 * cell_w // 3):
                for y in range(y0 + 1, y0 + cell_h - 2):
                    c.set(x0 + bx, y, shade(base, 46))

    # the string ribbons running across the cell rows, and the half-cut split bus
    for y in (inset + 2 * cell_h, inset + 6 * cell_h):
        c.rect(inset, y - 1, size - inset, y + 1, shade(pale, 26))
    mid = size // 2
    c.rect(inset, mid - 2, size - inset, mid + 1, shade(pale, -18))

    # the junction box: one per module, off centre where a real one sits
    c.rect(24, 46, 40, 58, (44, 46, 50, 255))
    c.outline(24, 46, 40, 58, (26, 28, 30, 255))
    c.rect(27, 58, 30, 62, (30, 32, 34, 255))
    c.rect(34, 58, 37, 62, (30, 32, 34, 255))

    c.outline(0, 0, size, size, frame)
    c.outline(1, 1, size - 1, size - 1, shade(frame, -22))
    return c


def module_edge():
    """A module frame seen edge on: the aluminium channel, not the cells.

    This is the texture that used to be the cell grid, which is why a panel looked like it
    was made of cells all the way through when you stood beside it.  A frame in profile is
    an extrusion: a lip at the glass, a web, and a return at the bottom that the clamp
    grips.  Banded across the short axis, and symmetric about the middle so it reads the
    same whichever way up the face is mapped.
    """
    size = 32
    c = Canvas(size, size)
    base = (170, 174, 180, 255)

    for y in range(size):
        for x in range(size):
            grain = int(noise(x, 0, 3) * 14) - 7 + int(noise(x, y, 13) * 5) - 2
            c.set(x, y, shade(base, grain))

    # the two grooves of the extrusion, and the bright rib between them
    for y in (size // 4, 3 * size // 4):
        c.rect(0, y - 1, size, y + 1, shade(base, -52))
        c.rect(0, y + 1, size, y + 2, shade(base, 20))
    c.rect(0, size // 2 - 1, size, size // 2 + 1, shade(base, 16))
    # and the dark line of the laminate itself, just inside each lip
    c.rect(0, 0, size, 2, shade(base, -70))
    c.rect(0, size - 2, size, size, shade(base, -70))
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


def cabinet_door():
    """The one face of an inverter a technician ever opens: louvres, handle, rating plate.

    Every other face of the cabinet is plain sheet, and giving them all the door was what
    made the machine look the same from behind as from in front.
    """
    size = 32
    c = Canvas(size, size)
    base = (194, 197, 201, 255)
    for y in range(size):
        for x in range(size):
            c.set(x, y, shade(base, int(noise(x, y, 31) * 8) - 4))

    # the intake louvres: slots with a lit lower lip, which is what a pressed louvre does
    for i in range(5):
        y = 6 + i * 4
        c.rect(5, y, 21, y + 2, shade(base, -74))
        c.rect(5, y + 2, 21, y + 3, shade(base, 18))

    # the rating plate, and the handle recess with its bar
    c.rect(5, 26, 17, 30, shade(base, 26))
    c.outline(5, 26, 17, 30, shade(base, -40))
    for x in range(6, 16, 2):
        c.set(x, 28, shade(base, -30))

    c.rect(24, 11, 29, 21, shade(base, -34))
    c.outline(24, 11, 29, 21, shade(base, -58))
    c.rect(25, 13, 28, 19, shade(base, 22))

    # the hinges down the far edge
    for y in (7, 24):
        c.rect(0, y, 3, y + 3, shade(base, -50))
    return c


def cabinet_top():
    """The lid: a rain hood, fastened round its rim, with a lifting eye at each end.

    Seen from above and from nowhere else, which is exactly why it was worth its own
    texture - a swage line meant for a vertical panel reads as a dent on a roof.
    """
    size = 32
    c = Canvas(size, size)
    base = (188, 191, 195, 255)
    for y in range(size):
        for x in range(size):
            c.set(x, y, shade(base, int(noise(x, y, 47) * 8) - 4))

    # the drip edge, and the gutter inside it that takes the water to the ends
    c.outline(0, 0, size, size, shade(base, -46))
    c.outline(1, 1, size - 1, size - 1, shade(base, 16))
    c.outline(4, 4, size - 4, size - 4, shade(base, -26))

    # fasteners round the rim
    for i in range(6, size - 4, 6):
        for x, y in ((i, 2), (i, size - 3), (2, i), (size - 3, i)):
            c.set(x, y, shade(base, -70))

    # two lifting eyes, which is how a cabinet this size arrives on site
    for cx in (10, 21):
        c.disc(cx, 16, 3, shade(base, -34))
        c.disc(cx, 16, 1, shade(base, -80))
    return c


def combiner_door():
    """The door of a combiner box: the fuse window, the label, and the switch escutcheon.

    A field combiner's door is the one thing on a solar farm that is *meant* to be read at close
    range - it carries the number of ways, the fuse rating, and the notice telling you the strings
    are still live with the switch open, because they are: a photovoltaic string is lit whenever
    the sun is on it and there is nothing at the array end to turn off.
    """
    size = 32
    c = Canvas(size, size)
    base = (198, 201, 205, 255)
    for y in range(size):
        for x in range(size):
            c.set(x, y, shade(base, int(noise(x, y, 43) * 8) - 4))

    # the window over the fuse ways, with the fuse carriers behind it
    c.rect(4, 5, 22, 17, (44, 52, 58, 255))
    c.outline(4, 5, 22, 17, shade(base, -62))
    for i in range(6):
        x = 6 + i * 3
        c.rect(x, 7, x + 2, 15, (168, 172, 178, 255))
        c.rect(x, 7, x + 2, 8, (206, 158, 62, 255))

    # the hazard label: a live-parts warning, which is the one notice a combiner box always carries
    c.rect(4, 20, 22, 27, (222, 186, 40, 255))
    c.outline(4, 20, 22, 27, (32, 30, 24, 255))
    for i in range(4):
        x = 6 + i * 4
        c.rect(x, 22, x + 2, 25, (32, 30, 24, 255))

    # the escutcheon the switch handle turns in, and its two marked positions
    c.rect(24, 9, 30, 21, shade(base, -34))
    c.outline(24, 9, 30, 21, shade(base, -58))
    c.rect(26, 11, 28, 13, (58, 138, 62, 255))
    c.rect(26, 17, 28, 19, (176, 54, 46, 255))

    # the hinges down the far edge
    for y in (6, 23):
        c.rect(0, y, 3, y + 3, shade(base, -50))
    return c


def switch_handle():
    """The moulded handle of a load-break switch: red, ribbed, on a black boss.

    It used to be drawn in the hazard-sign texture, which put a yellow triangle sticker in the middle of
    the door where a handle belongs.  Red because that is what a load-break handle is - the one control
    on the whole box, and the colour is a convention rather than a decoration: red means it breaks load.
    """
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


def vent():
    """A cooling grille: dark behind, with the slats catching the light.

    The fan is drawn behind this, so what the eye needs is depth - the gaps have to be
    darker than anything else on the machine, or the grille reads as a painted panel.
    """
    size = 32
    c = Canvas(size, size, (22, 24, 26, 255))
    slat = (150, 154, 158, 255)

    for i in range(8):
        y = 2 + i * 4
        for x in range(2, size - 2):
            c.set(x, y, shade(slat, int(noise(x, i, 53) * 12) - 6))
            c.set(x, y + 1, shade(slat, -44))

    # the pressed frame round it
    c.outline(0, 0, size, size, (176, 180, 184, 255))
    c.outline(1, 1, size - 1, size - 1, (138, 142, 146, 255))
    return c


def steel_end():
    """A welded end cap: a disc with the bead round its rim.

    The tubes used to be open at both ends, which from above is a hole through the mast.
    """
    size = 16
    c = Canvas(size, size)
    base = (146, 150, 154, 255)
    for y in range(size):
        for x in range(size):
            c.set(x, y, shade(base, int(noise(x, y, 59) * 18) - 9))

    c.outline(0, 0, size, size, shade(base, -40))
    c.outline(1, 1, size - 1, size - 1, shade(base, 24))
    c.disc(size // 2, size // 2, 1, shade(base, -50))
    return c


def dome():
    """A radiometer's glass dome from above: dark glass, one highlight, the white body round it.

    The only angle it is ever seen from, so it is drawn for that angle and no other.
    """
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


# The two direct-current cables.  Positive is red and negative is black, which is the marking a
# North American plant uses and the only pair of colours that reads at three pixels.  Nothing in
# these is transparent: the block models sit in the solid render layer, where a transparent texel
# comes out black, and a jacket-coloured field costs nothing.

CABLE_BLACK = ((24, 24, 28, 255), (52, 52, 60, 255), (80, 80, 90, 255))
CABLE_RED = ((74, 22, 22, 255), (132, 40, 36, 255), (176, 66, 58, 255))
CLIP = (138, 142, 150, 255)


def cable_line(half, thick, armoured=False, flat=False):
    """A pair running the length of the tile, with the clip that holds it down across the middle.

    One texture serves the whole run.  The conductors sit at fixed columns, so a corner piece and
    the arm leading into it take different rectangles out of the same image and the pair still
    lines up across the seam - which is the trick that makes a multipart cable look continuous.

    ``thick`` is how many pixels of highlight each conductor gets, which is what makes a 240 mm²
    trunk read as heavier than a 6 mm² string rather than merely wider.
    """
    c = Canvas(16, 16, CABLE_BLACK[0])
    left = int(8 - half)
    width = int(half)

    for index, palette in ((0, CABLE_RED), (1, CABLE_BLACK)):
        x0 = left + index * width
        for x in range(x0, x0 + width):
            # lit on the side the light comes from and shaded away from it, by position rather than by
            # a pixel count - a conductor only two pixels wide has no room for a count to work with,
            # and that is the width they are drawn at now
            if width < 2 or flat:
                # one pixel to a conductor, which is the width they are drawn at: there is no room for
                # a lit side and a shaded one, so it gets the body colour and the jacket reads by hue.
                # ``flat`` asks for the same treatment at any width, which is what the stub on a machine
                # needs: the pipeline maps a whole texture across a face, so a gradient drawn over eight
                # columns would come out as a gradient where the run beside it is two flat colours
                colour = palette[1]
            else:
                across = (x - x0) / (width - 1.0)
                colour = palette[2] if across < 0.34 else (palette[1] if across < 0.7 else palette[0])
            c.rect(x, 0, x + 1, 16, colour)

    # the clip: a stainless hanger over both conductors, which is what a real run is held by and
    # what stops this reading as two painted lines.  A trunk gets three of them rather than one and a
    # darker jacket, because it is armoured and because that is how it is told from a string cable now
    # that the two are drawn the same size
    for y in ((2, 6, 11) if armoured else (7,)):
        c.rect(left, y, left + 2 * width, y + 2, CLIP)
        c.rect(left, y, left + 2 * width, y + 1, shade(CLIP, 34))
        c.rect(left + width - 1, y, left + width + 1, y + 2, shade(CLIP, -46))

    return c


def cable_jacket():
    """A cable seen from the side: jacket, and nothing else.

    The OBJ pipeline maps a whole texture across every face of a box, so a stub given the pair texture
    showed the pair on its sides as well - two conductors squeezed into a face one pixel tall, which is
    not what the side of a cable looks like.  This is what it looks like.
    """
    c = Canvas(16, 16, CABLE_BLACK[0])
    for y in range(16):
        for x in range(16):
            c.set(x, y, shade(CABLE_BLACK[0], int(noise(x, y, 61) * 12) - 4))

    # the sheen a round jacket has along its length
    c.rect(0, 5, 16, 7, CABLE_BLACK[1])
    return c


def trench():
    """Sand bedding: what a buried cable is laid in and backfilled with.

    Sand rather than the soil that came out of the trench, because a cable laid straight onto
    stones is a cable with a stone pressing into its insulation - so a real trench gets a bed of
    sand under and over it, and that is what shows at the surface afterwards.
    """
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

    # the fuse window, with the carriers behind it
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
    """A coil of solar cable, which is how a reel of 6 mm² arrives.

    Two turns rather than one, because the item has to say *pair*: a single loop of black reads as
    rope.  The tails leave it the way they leave a coil that has been unwound from once.
    """
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
    """A drum of 240 mm², because that is the only way a cable that heavy is delivered.

    Deliberately a different object from the string coil rather than a fatter version of it: two
    sprites that differ by half a pixel of ring thickness are two sprites nobody can tell apart in
    a hotbar, and a drum against a coil is exactly the distinction the yard makes.
    """
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
    """A shaded bar: shadow underneath, body over it, highlight along the top-left edge.

    Three passes rather than one, because a flat silhouette at sixteen pixels reads as a cut-out
    and a tool wants to look like metal.  The light comes from the top left, which is where
    Minecraft's own item sprites put it.

    ``margin`` is how far the shadow pass reaches past the body, and it matters more than it looks:
    two bars a pixel and a half apart with a shadow reaching seven tenths of a pixel each way have no
    gap left between them, which is how the first wrench's jaws welded themselves shut.
    """
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
    """An adjustable spanner, all steel: a handle with a hanging hole, a knurled worm block, two jaws.

    Three attempts before this one, and the first two failed the same way - the head.  Cutting a mouth
    out of a solid blob tapers the jaws and a tapered mouth reads as a beak; a closed ring reads as a
    ring rather than as a wrench.  What makes the real tool recognisable is three parts in a row down the
    diagonal and a mouth that is *straight*: a long fixed jaw, a short sliding one, and a gap between
    them that does not narrow.  Two pixels of gap, held for the whole length of the jaws, which is a
    thing that has to be drawn rather than derived.

    Laid corner to corner because {@code item/handheld} rotates a sprite about its lower left and wants
    the working end at the upper right - so the mouth opens away from the hand, the way one is held.
    """
    return sprite(WRENCH, {
        'D': STEEL[0],
        'S': STEEL[1],
        'L': STEEL[2],
        'K': (70, 74, 80, 255),
    })


def item_tower():
    """One section of tubular tower: a taper, a flange at each end, and the bolts through it.

    A tower is stacked by hand in this mod, so the item is a *section* and not a tower - which is
    why the flanges are the thing the sprite leads with.  It used to be drawn as a generic lump of
    metal casing, which said nothing about what it was for.
    """
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
    """The mast, with the three instruments that identify it.

    Redrawn with mass in it: the old sprite was a one-pixel mast and three pale dots, which at
    sixteen pixels was a smudge.  What makes a met mast recognisable is the cup anemometer on top,
    a radiation shield on one side and a radiometer on a boom on the other, so those three are the
    ones drawn and the rest of the instruments are left to the block.
    """
    c = Canvas(16, 16)
    steel_dark, steel_mid, steel_light = STEEL
    shield_dark, shield_mid, shield_light = INSTRUMENT

    # the mast: two pixels, lit down the left edge
    c.rect(7, 3, 9, 14, steel_mid)
    c.rect(7, 3, 8, 14, steel_light)
    c.rect(8, 3, 9, 14, steel_dark)

    # a splayed footing, because a ten-metre mast needs one
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


# ------------------------------------------------------------- panel layouts
#
# These are the contract with the screen classes.  Each entry is the panel's size and
# the wells cut into it for bars, and the Java constants have to name the same
# numbers - so if a bar moves here it moves there.

PANELS = {
    'pv_inverter': dict(width=288, height=232, bar_x=12, bar_width=264, bars=(70, 98, 126), separators=(40, 142)),
    'pv_array': dict(width=288, height=232, bar_x=12, bar_width=264, bars=(70, 98, 126), separators=(40, 142)),
    'met_station': dict(width=288, height=208, bar_x=12, bar_width=264, bars=(), separators=(20, 176)),
    # shorter than the rest, because a combiner box is switchgear: one bar, and nothing to control
    'pv_combiner': dict(width=288, height=148, bar_x=12, bar_width=264, bars=(72,), separators=(40, 88)),
}


# Side of the sheet each panel is drawn into.  Five hundred and twelve rather than the two hundred
# and fifty-six a vanilla GUI uses, because two columns of a label and a right-aligned value need
# more width than that and the alternative was abbreviations nobody would read.  PlantScreen blits
# with the texture size passed explicitly, so this only has to be a power of two.
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
    # across a whole texture, so a pair drawn six columns wide would come out six columns wide on a
    # stub that has to match a laid run exactly
    'dc_harness': lambda: cable_line(8.0, 1, flat=True),
    'dc_trench': trench,
    'dc_jacket': cable_jacket,
    'pv_combiner_door': combiner_door,
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

ITEM_TEXTURES = {
    'pv_combiner': item_combiner,
    'dc_string_cable': item_string_cable,
    'dc_trunk_cable': item_trunk_cable,
    'power_wrench': item_wrench,
    'turbine_tower': item_tower,
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
