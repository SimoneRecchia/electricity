#!/usr/bin/env python3
"""Drawing primitives for the block textures: noise, materials, weathering, fittings.

Everything is lit from LIGHT, shared by the whole mod, so a painted highlight and a model's shading
agree.  Build a surface as material, then weathering - never a flat colour.

``squash`` on the round primitives (aa_disc, dome, screw, warning_triangle) is the face's width over its
height, for a tile that lands on something not square.  See SQUASH in gen_block_textures.
"""

import math
import os
import struct
import zlib


# ------------------------------------------------------------------ hashing and noise

def hash01(x, y, salt=0):
    """A deterministic hash of two integers into 0..1."""
    n = (int(x) * 73856093) ^ (int(y) * 19349663) ^ (int(salt) * 83492791)
    n = (n ^ (n >> 13)) * 1274126177
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0


# kept under its old name because the item sprites are drawn with it
noise = hash01


def smoothstep(t):
    return t * t * (3.0 - 2.0 * t)


class Field:
    """Value noise over a lattice, summed over octaves, wrapping at the tile edge."""

    def __init__(self, size, cell=64, octaves=4, salt=0, persistence=0.5):
        self.size = size
        self.layers = []
        amplitude, total = 1.0, 0.0
        for octave in range(octaves):
            spacing = max(1.0, cell / (2 ** octave))
            self.layers.append((spacing, amplitude, salt * 31 + octave))
            total += amplitude
            amplitude *= persistence
        self.total = total

    def _lattice(self, gx, gy, spacing, salt):
        wrap = max(1, int(round(self.size / spacing)))
        return hash01(gx % wrap, gy % wrap, salt)

    def at(self, x, y):
        """The field at a pixel"""
        out = 0.0
        for spacing, amplitude, salt in self.layers:
            fx, fy = x / spacing, y / spacing
            gx, gy = int(math.floor(fx)), int(math.floor(fy))
            tx, ty = smoothstep(fx - gx), smoothstep(fy - gy)
            a = self._lattice(gx, gy, spacing, salt)
            b = self._lattice(gx + 1, gy, spacing, salt)
            c = self._lattice(gx, gy + 1, spacing, salt)
            d = self._lattice(gx + 1, gy + 1, spacing, salt)
            top = a + (b - a) * tx
            bottom = c + (d - c) * tx
            out += (top + (bottom - top) * ty) * amplitude
        return out / self.total

    def signed(self, x, y):
        """The same field centred on zero, which is what a shading term wants."""
        return self.at(x, y) * 2.0 - 1.0


def stretched(size, along, across, octaves=3, salt=0):
    """A field whose lattice is long in one axis: what a brushed or drawn grain is."""
    field = Field(size, cell=across, octaves=octaves, salt=salt)
    ratio = along / across

    class Stretched:
        def at(self, x, y):
            return field.at(x / ratio, y)

        def signed(self, x, y):
            return field.signed(x / ratio, y)

    return Stretched()


# ------------------------------------------------------------------ colour

def clamp8(v):
    return 0 if v < 0 else (255 if v > 255 else int(v))


def shade(colour, amount):
    """A colour lightened or darkened by an amount in 0..255, alpha kept."""
    return (clamp8(colour[0] + amount), clamp8(colour[1] + amount), clamp8(colour[2] + amount),
            colour[3] if len(colour) > 3 else 255)


def mul(colour, factor):
    return (clamp8(colour[0] * factor), clamp8(colour[1] * factor), clamp8(colour[2] * factor),
            colour[3] if len(colour) > 3 else 255)


def mix(a, b, t):
    t = 0.0 if t < 0 else (1.0 if t > 1 else t)
    return tuple(clamp8(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (
        clamp8((a[3] if len(a) > 3 else 255) + ((b[3] if len(b) > 3 else 255) - (a[3] if len(a) > 3 else 255)) * t),)


# The light this file shades everything by: over the viewer's left shoulder and a little above,
LIGHT = (-0.55, -0.62)


# ------------------------------------------------------------------ the canvas

class Canvas:
    """An RGBA image with the drawing operations these textures need."""

    def __init__(self, width, height, fill=(0, 0, 0, 0)):
        self.w = width
        self.h = height
        self.px = [list(fill) for _ in range(width * height)]

    # ---- hard-edged, unchanged

    def set(self, x, y, colour):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = list(colour) if len(colour) == 4 else list(colour) + [255]

    def get(self, x, y):
        return tuple(self.px[y * self.w + x])

    def rect(self, x0, y0, x1, y1, colour):
        for y in range(max(0, int(y0)), min(self.h, int(y1))):
            for x in range(max(0, int(x0)), min(self.w, int(x1))):
                self.set(x, y, colour)

    def outline(self, x0, y0, x1, y1, colour):
        x0, y0, x1, y1 = int(x0), int(y0), int(x1), int(y1)
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
        """A capsule between two points: every pixel within half the width of the segment."""
        dx, dy = x1 - x0, y1 - y0
        length = max(1e-6, (dx * dx + dy * dy) ** 0.5)
        half = width / 2.0
        for y in range(max(0, int(min(y0, y1) - half - 1)), min(self.h, int(max(y0, y1) + half + 2))):
            for x in range(max(0, int(min(x0, x1) - half - 1)), min(self.w, int(max(x0, x1) + half + 2))):
                px, py = x + 0.5, y + 0.5
                t = max(0.0, min(1.0, ((px - x0) * dx + (py - y0) * dy) / (length * length)))
                if (px - (x0 + t * dx)) ** 2 + (py - (y0 + t * dy)) ** 2 <= half * half:
                    self.set(x, y, colour)

    # ---- blended and anti-aliased

    def blend(self, x, y, colour, alpha=1.0):
        if not (0 <= x < self.w and 0 <= y < self.h) or alpha <= 0.0:
            return
        if alpha >= 1.0 and (len(colour) < 4 or colour[3] == 255):
            self.px[y * self.w + x] = list(colour[:3]) + [255]
            return
        a = alpha * ((colour[3] if len(colour) > 3 else 255) / 255.0)
        base = self.px[y * self.w + x]
        out_a = a + (base[3] / 255.0) * (1 - a)
        if out_a <= 0.0:
            self.px[y * self.w + x] = [0, 0, 0, 0]
            return
        for i in range(3):
            self.px[y * self.w + x][i] = clamp8((colour[i] * a + base[i] * (base[3] / 255.0) * (1 - a)) / out_a)
        self.px[y * self.w + x][3] = clamp8(out_a * 255)

    def aa_rect(self, x0, y0, x1, y1, colour, alpha=1.0):
        """A rectangle at real coordinates, its boundary pixels covered in proportion."""
        if x1 < x0:
            x0, x1 = x1, x0
        if y1 < y0:
            y0, y1 = y1, y0
        for y in range(max(0, int(math.floor(y0))), min(self.h, int(math.ceil(y1)))):
            cover_y = min(y + 1.0, y1) - max(float(y), y0)
            if cover_y <= 0:
                continue
            for x in range(max(0, int(math.floor(x0))), min(self.w, int(math.ceil(x1)))):
                cover_x = min(x + 1.0, x1) - max(float(x), x0)
                if cover_x <= 0:
                    continue
                self.blend(x, y, colour, alpha * cover_x * cover_y)

    def aa_disc(self, cx, cy, radius, colour, alpha=1.0, inner=0.0, squash=1.0):
        """A disc, or a ring if ``inner`` is given, with a one-pixel soft rim."""
        ry = radius * squash
        for y in range(max(0, int(cy - ry - 1)), min(self.h, int(cy + ry + 2))):
            for x in range(max(0, int(cx - radius - 1)), min(self.w, int(cx + radius + 2))):
                d = math.hypot(x + 0.5 - cx, (y + 0.5 - cy) / max(1e-6, squash))
                cover = min(1.0, max(0.0, radius + 0.5 - d))
                if inner > 0.0:
                    cover = min(cover, max(0.0, min(1.0, d - inner + 0.5)))
                if cover > 0.0:
                    self.blend(x, y, colour, alpha * cover)

    def aa_line(self, x0, y0, x1, y1, colour, width=1.0, alpha=1.0):
        dx, dy = x1 - x0, y1 - y0
        length2 = max(1e-9, dx * dx + dy * dy)
        half = width / 2.0
        for y in range(max(0, int(min(y0, y1) - half - 1)), min(self.h, int(max(y0, y1) + half + 2))):
            for x in range(max(0, int(min(x0, x1) - half - 1)), min(self.w, int(max(x0, x1) + half + 2))):
                px, py = x + 0.5, y + 0.5
                t = max(0.0, min(1.0, ((px - x0) * dx + (py - y0) * dy) / length2))
                d = math.hypot(px - (x0 + t * dx), py - (y0 + t * dy))
                cover = min(1.0, max(0.0, half + 0.5 - d))
                if cover > 0.0:
                    self.blend(x, y, colour, alpha * cover)

    def each(self, fn):
        """Calls fn(x, y) for every pixel and sets what it returns, or leaves the pixel if None."""
        for y in range(self.h):
            for x in range(self.w):
                colour = fn(x, y)
                if colour is not None:
                    self.px[y * self.w + x] = list(colour) if len(colour) == 4 else list(colour) + [255]

    def over(self, fn):
        """Calls fn(x, y, colour) for every pixel with what is already there."""
        for y in range(self.h):
            for x in range(self.w):
                base = tuple(self.px[y * self.w + x])
                colour = fn(x, y, base)
                if colour is not None:
                    self.px[y * self.w + x] = list(colour) if len(colour) == 4 else list(colour) + [255]

    def write(self, path):
        os.makedirs(os.path.dirname(path) or '.', exist_ok=True)
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
    """A small texture from a picture of it, one character to a pixel."""
    c = Canvas(len(rows[0]), len(rows))
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            if key in palette:
                c.set(x, y, palette[key])
    return c


# ------------------------------------------------------------------ materials

def brushed(c, base=(168, 172, 178, 255), grain=13, blotch=9, salt=3, horizontal=True):
    """Mill-finish or anodised aluminium: pale, slightly cool, drawn along one axis."""
    fine = stretched(c.w, along=c.w * 0.9, across=max(2.0, c.w / 90.0), octaves=2, salt=salt)
    coarse = Field(c.w, cell=c.w / 2.2, octaves=3, salt=salt + 11)

    def pixel(x, y):
        u, v = (x, y) if horizontal else (y, x)
        return shade(base, fine.signed(u, v) * grain + coarse.signed(x, y) * blotch)

    c.each(pixel)


def galvanised(c, base=(150, 155, 158, 255), salt=5, spangle=True):
    """Hot-dip galvanised steel: cooler and more mottled than aluminium, with zinc spangle."""
    coarse = Field(c.w, cell=c.w / 3.0, octaves=4, salt=salt)
    fine = Field(c.w, cell=max(2.0, c.w / 120.0), octaves=2, salt=salt + 7)

    def pixel(x, y):
        return shade(base, coarse.signed(x, y) * 14 + fine.signed(x, y) * 7)

    c.each(pixel)
    if not spangle:
        return

    count = int(c.w * c.w / 900)
    for i in range(count):
        cx = hash01(i, 1, salt + 17) * c.w
        cy = hash01(i, 2, salt + 19) * c.h
        radius = c.w / 130.0 * (0.6 + hash01(i, 3, salt + 23) * 1.9)
        # a crystal is a facet of zinc
        # them are turned away from it, which is what stops a galvanised sheet reading as sleet
        lift = (hash01(i, 4, salt + 29) - 0.45) * 26
        c.aa_disc(cx, cy, radius, shade(base, lift), alpha=0.45)
        c.aa_disc(cx - radius * 0.25, cy - radius * 0.25, radius * 0.6, shade(base, lift * 1.6),
                  alpha=0.35)


def powder(c, base=(198, 202, 206, 255), salt=31, peel=6):
    """Powder-coated sheet steel: flat colour with the orange peel a cured coat always has."""
    peel_field = Field(c.w, cell=max(3.0, c.w / 64.0), octaves=2, salt=salt)
    drift = Field(c.w, cell=c.w / 1.5, octaves=2, salt=salt + 5)

    def pixel(x, y):
        return shade(base, peel_field.signed(x, y) * peel + drift.signed(x, y) * 4)

    c.each(pixel)


def concrete(c, base=(158, 156, 150, 255), salt=61, aggregate=True):
    """Cast concrete: pale grey, blotchy, with aggregate showing through the skin."""
    coarse = Field(c.w, cell=c.w / 2.5, octaves=4, salt=salt)
    fine = Field(c.w, cell=max(2.0, c.w / 150.0), octaves=2, salt=salt + 3)

    def pixel(x, y):
        return shade(base, coarse.signed(x, y) * 16 + fine.signed(x, y) * 9)

    c.each(pixel)
    if not aggregate:
        return

    for i in range(int(c.w * c.w / 500)):
        cx = hash01(i, 5, salt + 41) * c.w
        cy = hash01(i, 6, salt + 43) * c.h
        radius = c.w / 220.0 * (0.7 + hash01(i, 7, salt + 47) * 2.4)
        tint = -18 + hash01(i, 8, salt + 53) * 34
        c.aa_disc(cx, cy, radius, shade(base, tint), alpha=0.5)


def rubber(c, base=(30, 31, 35, 255), salt=83, sheen=10):
    """Cable sheathing: near black, faintly ribbed along its length, with a soft sheen."""
    grain = stretched(c.w, along=c.w * 2.0, across=max(2.0, c.w / 80.0), octaves=2, salt=salt)

    def pixel(x, y):
        # the sheen is where a round cable catches the light, so it is a function of across only
        across = (y + 0.5) / c.h
        lit = math.sin(across * math.pi) ** 2
        return shade(base, grain.signed(x, y) * 5 + lit * sheen)

    c.each(pixel)


def porcelain(c, base=(112, 74, 46, 255), salt=97):
    """Glazed porcelain: the brown of an insulator, glassy"""
    depth = Field(c.w, cell=c.w / 2.0, octaves=3, salt=salt)
    fine = Field(c.w, cell=max(2.0, c.w / 200.0), octaves=1, salt=salt + 3)

    def pixel(x, y):
        return shade(base, depth.signed(x, y) * 12 + fine.signed(x, y) * 4)

    c.each(pixel)


# ------------------------------------------------------------------ weathering

def grime(c, salt=101, amount=0.20, colour=(58, 54, 48, 255), cell=None):
    """Dirt in the low places: a soft, patchy darkening over whatever is already drawn."""
    field = Field(c.w, cell=cell or c.w / 1.6, octaves=4, salt=salt)

    def pixel(x, y, base):
        strength = max(0.0, field.at(x, y) - 0.5) * 2.0 * amount
        return mix(base, colour, strength) if base[3] else base

    c.over(pixel)


def streak(c, x, top, bottom, width, colour=(96, 84, 66, 255), alpha=0.30, salt=113):
    """A run of dirt down a face from a fixing or a lip: strongest at the top"""
    field = Field(c.w, cell=max(3.0, c.w / 40.0), octaves=2, salt=salt)
    for y in range(max(0, int(top)), min(c.h, int(bottom))):
        t = (y - top) / max(1.0, bottom - top)
        fade = (1.0 - t) ** 1.6
        half = width * (0.5 + t * 0.9)
        for px in range(max(0, int(x - half)), min(c.w, int(x + half) + 1)):
            edge = 1.0 - abs(px + 0.5 - x) / max(1e-6, half)
            if edge <= 0:
                continue
            wobble = 0.6 + field.at(px, y) * 0.8
            c.blend(px, y, colour, alpha * fade * edge * wobble)


def rust(c, x0, y0, x1, y1, salt=127, alpha=0.5):
    """A patch of rust bleed, for a steel edge that has been outdoors a decade."""
    field = Field(c.w, cell=max(4.0, (x1 - x0) / 2.5), octaves=3, salt=salt)
    colours = ((118, 62, 30, 255), (146, 84, 38, 255), (92, 48, 28, 255))
    for y in range(max(0, int(y0)), min(c.h, int(y1))):
        for x in range(max(0, int(x0)), min(c.w, int(x1))):
            v = field.at(x, y)
            if v < 0.55:
                continue
            strength = (v - 0.55) / 0.45
            colour = colours[int(hash01(x, y, salt + 3) * 3) % 3]
            c.blend(x, y, colour, alpha * strength)


# ------------------------------------------------------------------ features

def bevel(c, x0, y0, x1, y1, depth, lift=26, drop=30):
    """A raised panel: lit on the two edges facing the light"""
    lx, ly = LIGHT
    for i in range(int(depth)):
        t = 1.0 - i / max(1.0, depth)
        top = lift * t if ly < 0 else -drop * t
        bottom = -drop * t if ly < 0 else lift * t
        left = lift * t if lx < 0 else -drop * t
        right = -drop * t if lx < 0 else lift * t
        c.aa_rect(x0 + i, y0 + i, x1 - i, y0 + i + 1, (255, 255, 255, 255) if top > 0 else (0, 0, 0, 255),
                  alpha=abs(top) / 255.0 * 1.6)
        c.aa_rect(x0 + i, y1 - i - 1, x1 - i, y1 - i, (255, 255, 255, 255) if bottom > 0 else (0, 0, 0, 255),
                  alpha=abs(bottom) / 255.0 * 1.6)
        c.aa_rect(x0 + i, y0 + i, x0 + i + 1, y1 - i, (255, 255, 255, 255) if left > 0 else (0, 0, 0, 255),
                  alpha=abs(left) / 255.0 * 1.6)
        c.aa_rect(x1 - i - 1, y0 + i, x1 - i, y1 - i, (255, 255, 255, 255) if right > 0 else (0, 0, 0, 255),
                  alpha=abs(right) / 255.0 * 1.6)


def groove(c, x0, y0, x1, y1, width, dark=44, light=30, vertical=False):
    """A pressed line: a dark side and a bright side"""
    if vertical:
        c.aa_rect(x0, y0, x0 + width, y1, (0, 0, 0, 255), alpha=dark / 255.0)
        c.aa_rect(x0 + width, y0, x0 + 2 * width, y1, (255, 255, 255, 255), alpha=light / 255.0)
    else:
        c.aa_rect(x0, y0, x1, y0 + width, (0, 0, 0, 255), alpha=dark / 255.0)
        c.aa_rect(x0, y0 + width, x1, y0 + 2 * width, (255, 255, 255, 255), alpha=light / 255.0)


def dome(c, cx, cy, radius, base, lift=54, drop=40, alpha=1.0, squash=1.0):
    """A hemisphere shaded from the light: a bolt head, a rivet, a boss, an instrument dome."""
    lx, ly = LIGHT
    ry = radius * squash
    for y in range(max(0, int(cy - ry - 1)), min(c.h, int(cy + ry + 2))):
        for x in range(max(0, int(cx - radius - 1)), min(c.w, int(cx + radius + 2))):
            dx, dy = (x + 0.5 - cx) / radius, (y + 0.5 - cy) / max(1e-6, ry)
            d2 = dx * dx + dy * dy
            if d2 > 1.25:
                continue
            cover = min(1.0, max(0.0, (1.0 - math.sqrt(d2)) * radius + 0.5))
            if cover <= 0.0:
                continue
            nz = math.sqrt(max(0.0, 1.0 - min(1.0, d2)))
            lambert = max(0.0, dx * lx + dy * ly + nz * 0.62)
            tone = -drop + (lift + drop) * lambert
            # the specular pip, which is what makes a dome read as glossy rather than as a disc
            spec = max(0.0, lambert - 0.86) * 5.0
            c.blend(x, y, shade(base, tone + spec * 90), alpha * cover)


def hex_head(c, cx, cy, radius, base, salt=137):
    """A hexagon bolt head with a lit face"""
    c.aa_disc(cx, cy, radius * 1.32, shade(base, -16), alpha=0.55)
    points = [(cx + radius * math.cos(math.pi / 6 + i * math.pi / 3),
               cy + radius * math.sin(math.pi / 6 + i * math.pi / 3)) for i in range(6)]
    # filled as a fan of thick lines, which at these radii is indistinguishable from a polygon fill
    for i in range(6):
        a, b = points[i], points[(i + 1) % 6]
        c.aa_line(cx, cy, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2, shade(base, 6), width=radius * 1.1)
    lx, ly = LIGHT
    for i in range(6):
        a, b = points[i], points[(i + 1) % 6]
        mx, my = (a[0] + b[0]) / 2 - cx, (a[1] + b[1]) / 2 - cy
        length = math.hypot(mx, my) or 1.0
        lambert = (mx / length) * lx + (my / length) * ly
        c.aa_line(a[0], a[1], b[0], b[1], shade(base, 30 if lambert > 0 else -34),
                  width=max(1.0, radius * 0.30))
    dome(c, cx, cy, radius * 0.42, shade(base, 10), lift=30, drop=18)


def screw(c, cx, cy, radius, base, squash=1.0):
    """A pan-head screw with a cross slot.  ``squash`` as ``aa_disc`` takes it: face width over height."""
    dome(c, cx, cy, radius, base, lift=40, drop=30, squash=squash)
    for angle in (0.35, 0.35 + math.pi / 2):
        c.aa_line(cx - math.cos(angle) * radius * 0.72, cy - math.sin(angle) * radius * 0.72 * squash,
                  cx + math.cos(angle) * radius * 0.72, cy + math.sin(angle) * radius * 0.72 * squash,
                  shade(base, -62), width=max(1.0, radius * 0.34 * min(1.0, squash)))


def louvre(c, x0, y0, x1, y1, count, base, depth=None):
    """A stack of pressed louvre blades: shadow under the lip, light on the blade."""
    height = (y1 - y0) / count
    depth = depth or max(1.0, height * 0.22)
    for i in range(count):
        top = y0 + i * height
        c.aa_rect(x0, top, x1, top + height, shade(base, 4))
        # the slot: dark at the top where the blade above overhangs it
        c.aa_rect(x0, top + depth, x1, top + height - depth * 1.6, shade(base, -66))
        c.aa_rect(x0, top + depth, x1, top + depth + depth * 0.7, (0, 0, 0, 255), alpha=0.42)
        # the blade's own lower lip, catching the light
        c.aa_rect(x0, top + height - depth * 1.6, x1, top + height - depth * 0.5, shade(base, 26))


def plate_label(c, x0, y0, x1, y1, base, lines=3, ink=(52, 56, 62, 255)):
    """A rating plate: a bright etched rectangle with rows of text too small to read."""
    c.aa_rect(x0, y0, x1, y1, shade(base, 34))
    bevel(c, x0, y0, x1, y1, max(1.0, (y1 - y0) * 0.06))
    step = (y1 - y0) / (lines + 1)
    for i in range(lines):
        y = y0 + step * (i + 0.8)
        width = (x1 - x0) * (0.74 if i % 2 else 0.56)
        c.aa_rect(x0 + (x1 - x0) * 0.12, y, x0 + (x1 - x0) * 0.12 + width, y + max(1.0, step * 0.16), ink)


def polygon(c, points, colour, alpha=1.0):
    """A filled polygon by scanline, sampled four times a row so the edges come out soft."""
    if len(points) < 3:
        return

    lo = max(0, int(min(p[1] for p in points)))
    hi = min(c.h, int(max(p[1] for p in points)) + 1)
    for y in range(lo, hi):
        cover = {}
        for sub in range(4):
            scan = y + (sub + 0.5) / 4.0
            crossings = []
            for i in range(len(points)):
                (x0, y0), (x1, y1) = points[i], points[(i + 1) % len(points)]
                if (y0 <= scan < y1) or (y1 <= scan < y0):
                    crossings.append(x0 + (x1 - x0) * (scan - y0) / (y1 - y0))
            crossings.sort()
            for k in range(0, len(crossings) - 1, 2):
                a, b = crossings[k], crossings[k + 1]
                for x in range(max(0, int(a)), min(c.w, int(b) + 1)):
                    part = min(x + 1.0, b) - max(float(x), a)
                    if part > 0.0:
                        cover[x] = cover.get(x, 0.0) + part / 4.0

        for x, amount in cover.items():
            c.blend(x, y, colour, alpha * min(1.0, amount))


# The lightning arrow of IEC 60417-5036
BOLT = (
    ((0.06, -0.44), (0.26, -0.44), (0.02, 0.05), (-0.18, 0.05)),      # the upper stroke
    ((0.00, 0.00), (0.18, 0.00), (-0.04, 0.33), (-0.22, 0.33)),       # the lower one, stepped right
    ((0.02, 0.26), (-0.26, 0.19), (-0.09, 0.50)),                     # the head
)


def warning_triangle(c, cx, cy, size, salt=149, squash=1.0):
    """The lightning-arrow triangle every piece of switchgear in the world carries."""
    yellow = (232, 196, 24, 255)
    black = (26, 26, 28, 255)
    half = size * 0.525
    height = size * 1.05 * math.sqrt(3.0) / 2.0 * squash

    def triangle(scale):
        return ((cx, cy - height * 0.5 * scale),
                (cx + half * scale, cy + height * 0.5 * scale),
                (cx - half * scale, cy + height * 0.5 * scale))

    polygon(c, triangle(1.0), black)
    polygon(c, triangle(0.78), yellow)
    # The arrow is scaled to sit inside the yellow rather than drawn at the sign's own size: written out
    fit = 0.52
    for piece in BOLT:
        polygon(c, [(cx + x * size * fit, cy + (y * fit + 0.045) * size * squash)
                    for x, y in piece], black)


def cross_hatch(c, x0, y0, x1, y1, step, colour, width=1.0, alpha=0.5):
    """Diagonal hatching, for a tread plate or a mesh screen."""
    span = (x1 - x0) + (y1 - y0)
    n = int(span / step) + 1
    for i in range(-n, n):
        c.aa_line(x0 + i * step, y0, x0 + i * step + (y1 - y0), y1, colour, width=width, alpha=alpha)
        c.aa_line(x0 + i * step, y1, x0 + i * step + (y1 - y0), y0, colour, width=width, alpha=alpha)


def mesh_screen(c, x0, y0, x1, y1, step, base, alpha=0.6):
    """An insect screen behind a louvre or a vent: a fine grid, dark, with the holes showing."""
    c.aa_rect(x0, y0, x1, y1, shade(base, -70), alpha=alpha)
    x = x0
    while x < x1:
        c.aa_rect(x, y0, x + max(1.0, step * 0.35), y1, shade(base, -10), alpha=alpha * 0.8)
        x += step
    y = y0
    while y < y1:
        c.aa_rect(x0, y, x1, y + max(1.0, step * 0.35), shade(base, -22), alpha=alpha * 0.8)
        y += step
