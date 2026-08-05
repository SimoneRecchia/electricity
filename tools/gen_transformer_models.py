#!/usr/bin/env python3
"""The two transformers a plant's output actually passes through.

    python3 tools/gen_transformer_models.py
    python3 tools/gen_transformer_models.py --java     # the collision tables

Both authored about the block's centre, facing north, like every other machine the mod draws itself.

The machine transformer is a pad-mount unit: a generator makes 690 V and an inverter 800 V, and a
collector network runs at 33 kV, so every machine on a plant has one of these at its foot.  The
substation transformer is the big one, 33 kV to 400 kV, and only from it do the towers start.

What tells them apart is not size alone.  A pad-mount unit is *sealed* - corrugated tank walls are its
whole cooling surface, and there is no conservator on it.  A substation unit is too big for that: it has
bolted-on radiator banks, a conservator drum above them holding the oil's expansion, a Buchholz relay in
the pipe between, an on-load tap changer in its own compartment, and it stands in a bund that will hold
its entire oil fill if the tank ever splits.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from modellib import (FITTING, ROUND, Mesh, bolt, box, clad_box, cylinder, eyebolt,   # noqa: E402
                      emit, hemisphere, lathe, pin_insulator, tube)

OUT = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')

MATERIALS = {
    'tank': 'tx_tank.png',
    'tank_top': 'tx_tank_top.png',
    'fin': 'tx_fin.png',
    'steel': 'pv_steel.png',
    'plate': 'pv_plate.png',
    'porcelain': 'porcelain_brown.png',
    'concrete': 'box_plinth.png',
    'gravel': 'tx_gravel.png',
    'sign': 'pole_plate.png',
    'nameplate': 'tx_nameplate.png',
}

# ---------------------------------------------------------------- the pad-mount machine transformer
#
# 2500 kVA, 0.8/33 kV.  Sealed and corrugated: the tank wall *is* the radiator, which is why it has no
# fins bolted to it and no drum on top.
PAD_HALF_X = 0.34
PAD_HALF_Z = 0.24
PAD_BASE = 0.10               # the plinth it is bolted down to
PAD_TANK = 0.62               # the tank's own top
PAD_CORRUGATIONS = 18


def corrugated(mesh, faces, lo, hi, count, depth=0.018, skip_north=None, skip_east=None):
    """A tank wall pressed into vertical corrugations, which is a sealed transformer's whole cooling.

    Drawn as the panels rather than as a flat face with a pattern on it: the shadow between two
    corrugations is what makes one read as steel folded to get area, and a painted stripe does not.
    """
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    for side, z in (('north', z0), ('south', z1)):
        for i in range(count):
            a = x0 + (x1 - x0) * i / count
            b = x0 + (x1 - x0) * (i + 0.62) / count
            # a corrugation behind something bolted to the wall shares that thing's own plane
            if side == 'north' and skip_north and skip_north[0] < b and a < skip_north[1]:
                continue

            out = depth if side == 'south' else -depth
            box(mesh, faces, (a, y0, min(z, z + out)), (b, y1, max(z, z + out)), uv_scale=0.12)

    for side, x in (('west', x0), ('east', x1)):
        rows = max(3, int(count * (z1 - z0) / max(1e-6, x1 - x0)))
        for i in range(rows):
            a = z0 + (z1 - z0) * i / rows
            b = z0 + (z1 - z0) * (i + 0.62) / rows
            if side == 'east' and skip_east and skip_east[0] < b and a < skip_east[1]:
                continue

            out = depth if side == 'east' else -depth
            box(mesh, faces, (min(x, x + out), y0, a), (max(x, x + out), y1, b), uv_scale=0.12)


def bushing(mesh, index, centre, height, diameter, sheds=4):
    """An oil-to-air bushing: a porcelain body on a flange, which is how a winding leaves the tank.

    The mod's own ``pin_insulator`` is a *pin* insulator and is the wrong object here - a bushing is a
    tall body with sheds down it and a terminal on top, and it is what a player clicks a wire onto.
    """
    # A group per bushing, because ObjDefinitions names one per fitting and a wire hangs from that
    # group's own centre: all six in one group is a bounding box through the middle of the machine.
    porcelain = mesh.faces('bushing_%d' % index, 'porcelain')
    steel = mesh.faces('flange_%d' % index, 'steel')
    cx, cy, cz = centre

    # the flange it is bolted to the tank cover through
    cylinder(mesh, steel, (cx, cy, cz), 'y', diameter * 0.62, 0.012, sides=FITTING, uv_scale=0.2,
             caps=steel)
    for i in range(4):
        angle = math.radians(45.0 + i * 90.0)
        bolt(mesh, steel, (cx + math.cos(angle) * diameter * 0.5, cy + 0.012,
                           cz + math.sin(angle) * diameter * 0.5), 'y', 0.006, 0.010, uv_scale=0.08)

    # the body: a stack of sheds on a core, drawn as one turning so it cannot have a hole in it
    profile = [(diameter * 0.30, 0.0)]
    pitch = height / sheds
    for shed in range(sheds):
        base = 0.012 + shed * pitch
        profile += [(diameter * 0.30, base + pitch * 0.10),
                    (diameter * 0.50, base + pitch * 0.30),
                    (diameter * 0.30, base + pitch * 0.44),
                    (diameter * 0.30, base + pitch * 0.90)]
    profile += [(diameter * 0.26, 0.012 + height), (0.0, 0.012 + height)]
    lathe(mesh, porcelain, (cx, cy, cz), profile, sides=FITTING, uv_scale=1.0)

    # the terminal on top: the palm a conductor is bolted to
    top = cy + 0.012 + height
    cylinder(mesh, steel, (cx, top, cz), 'y', diameter * 0.20, 0.016, sides=FITTING, uv_scale=0.2,
             caps=steel)
    box(mesh, steel, (cx - diameter * 0.22, top + 0.016, cz - 0.010),
        (cx + diameter * 0.22, top + 0.026, cz + 0.010), uv_scale=0.15)


def machine_transformer():
    """A pad-mount unit at a machine's foot: 800 V in, 33 kV out.

    Sealed, corrugated, bolted to a plinth, with a cable box on the low-voltage side and three bushings
    on the high.  Which side is which is not decoration: the low-voltage side is where the machine's own
    cable arrives and the high side is where the collector network leaves.
    """
    mesh = Mesh()

    clad_box(mesh, 'plinth', (-PAD_HALF_X - 0.05, 0.0, -PAD_HALF_Z - 0.05),
             (PAD_HALF_X + 0.05, PAD_BASE, PAD_HALF_Z + 0.05), {'up': 'plate', '*': 'concrete'},
             uv_scale=0.7)

    tank = mesh.faces('tank', 'tank')
    clad_box(mesh, 'tank', (-PAD_HALF_X, PAD_BASE, -PAD_HALF_Z), (PAD_HALF_X, PAD_TANK, PAD_HALF_Z),
             {'up': 'tank_top', '*': 'tank'})
    corrugated(mesh, mesh.faces('tank', 'fin'),
               (-PAD_HALF_X, PAD_BASE + 0.04, -PAD_HALF_Z), (PAD_HALF_X, PAD_TANK - 0.05, PAD_HALF_Z),
               PAD_CORRUGATIONS, skip_north=(-0.18, 0.18), skip_east=(-0.06, 0.06))

    # the cover, which is a separate plate bolted down through a gasket
    clad_box(mesh, 'cover', (-PAD_HALF_X - 0.012, PAD_TANK, -PAD_HALF_Z - 0.012),
             (PAD_HALF_X + 0.012, PAD_TANK + 0.022, PAD_HALF_Z + 0.012),
             {'up': 'tank_top', '*': 'tank'}, uv_scale=0.6)
    steel = mesh.faces('cover', 'steel')
    for i in range(7):
        x = -PAD_HALF_X + 0.02 + i * (2 * PAD_HALF_X - 0.04) / 6.0
        for z in (-PAD_HALF_Z - 0.004, PAD_HALF_Z - 0.008):
            bolt(mesh, steel, (x, PAD_TANK + 0.022, z), 'y', 0.007, 0.012, uv_scale=0.08)

    # the three high-voltage bushings on the cover, at the phase spacing a 33 kV unit has
    for index, x in enumerate((-0.19, 0.0, 0.19)):
        bushing(mesh, index + 1, (x, PAD_TANK + 0.022, 0.10), 0.20, 0.072, sheds=4)

    # the low-voltage cable box on the front, which is where the machine's own cable arrives
    clad_box(mesh, 'cablebox', (-0.16, PAD_BASE + 0.06, -PAD_HALF_Z - 0.055),
             (0.16, PAD_BASE + 0.28, -PAD_HALF_Z), {'north': 'plate', '*': 'tank'}, uv_scale=0.5)
    glands = mesh.faces('cablebox', 'steel')
    for i in range(3):
        cylinder(mesh, glands, (-0.10 + i * 0.10, PAD_BASE + 0.052, -PAD_HALF_Z - 0.028), 'y', 0.018,
                 0.020, sides=FITTING, uv_scale=0.2, caps=glands)

    # the rating plate, and the two things every sealed unit carries: a pressure relief and a dial
    box(mesh, mesh.faces('nameplate', 'nameplate'), (0.20, PAD_BASE + 0.20, -PAD_HALF_Z - 0.006),
        (0.31, PAD_BASE + 0.34, -PAD_HALF_Z - 0.002), uv_scale=1.0)
    relief = mesh.faces('fittings', 'steel')
    cylinder(mesh, relief, (-0.26, PAD_TANK + 0.022, -0.10), 'y', 0.030, 0.026, sides=FITTING,
             uv_scale=0.2, caps=relief)
    hemisphere(mesh, relief, (-0.26, PAD_TANK + 0.048, -0.10), 0.030, sides=FITTING, rings=4,
               squash=0.5, uv_scale=0.3)
    # the oil level dial on the tank's flank, and the drain valve under it
    cylinder(mesh, mesh.faces('fittings', 'plate'), (PAD_HALF_X + 0.026, PAD_TANK - 0.10, 0.0), 'x',
             0.036, 0.006, sides=FITTING, uv_scale=1.0, caps=mesh.faces('fittings', 'plate'))
    cylinder(mesh, relief, (PAD_HALF_X + 0.030, PAD_BASE + 0.05, 0.0), 'x', 0.016, 0.010,
             sides=FITTING, uv_scale=0.2, caps=relief)

    # the earth bonding: a strap off each corner of the tank to the plinth, which is not optional
    for sx in (-1, 1):
        box(mesh, mesh.faces('earth', 'steel'),
            (sx * PAD_HALF_X + (0.002 if sx > 0 else -0.016), PAD_BASE - 0.002, -PAD_HALF_Z + 0.03),
            (sx * PAD_HALF_X + (0.016 if sx > 0 else -0.002), PAD_BASE + 0.10, -PAD_HALF_Z + 0.055),
            uv_scale=0.1)

    for x in (-PAD_HALF_X + 0.05, PAD_HALF_X - 0.05):
        for z in (-PAD_HALF_Z + 0.05, PAD_HALF_Z - 0.05):
            eyebolt(mesh, mesh.faces('cover', 'steel'), (x, PAD_TANK + 0.022, z), 0.014)

    return mesh


# ---------------------------------------------------------------- the substation transformer
#
# 63 MVA, 33/400 kV.  Two blocks wide and two tall: this is the biggest single object in the mod that is
# not a turbine, and it is the thing a transmission line actually starts from.
SUB_HALF_X = 0.70
SUB_HALF_Z = 0.44
SUB_BUND = 0.16               # the oil-retention bund it stands in
SUB_TANK = 1.42               # the tank's top
SUB_RADIATORS = 4             # banks a side
SUB_FINS = 11                 # fins a bank


def radiator(mesh, centre, half_z, height, fins):
    """One radiator bank: pressed fins on a header top and bottom.

    Bolted on rather than pressed into the wall, which is the visible difference between a unit this size
    and a sealed one: past a couple of megavolt-amperes the tank has nowhere near enough surface.
    """
    cx, cy, cz = centre
    steel = mesh.faces('radiator', 'steel')
    fin_faces = mesh.faces('radiator', 'fin')

    # the two headers, and the pipes back to the tank
    for y in (cy, cy + height):
        box(mesh, steel, (cx - 0.020, y - 0.022, cz - half_z), (cx + 0.020, y + 0.022, cz + half_z),
            uv_scale=0.2)
    for y in (cy + 0.006, cy + height - 0.006):
        cylinder(mesh, steel, (cx, y, cz + half_z * 0.7), 'x', 0.020, 0.030, sides=FITTING,
                 uv_scale=0.2, caps=steel)

    for i in range(fins):
        z = cz - half_z + (2 * half_z) * (i + 0.5) / fins
        box(mesh, fin_faces, (cx - 0.030, cy + 0.022, z - 0.006),
            (cx + 0.030, cy + height - 0.022, z + 0.006), uv_scale=0.3)


def substation_transformer():
    """A grid transformer: 33 kV in, 400 kV out, and the towers start here.

    Everything on it is on it because a unit this size cannot do without it: radiator banks because the
    tank has nowhere near the surface, a conservator to take the oil's expansion, a Buchholz relay in the
    pipe between them to catch gas coming off a fault, an on-load tap changer to hold the voltage as load
    swings, and a bund that will hold the whole oil fill.
    """
    mesh = Mesh()

    # the bund: a concrete pit with a gravel bed, which is what stops an oil fire being a catastrophe
    clad_box(mesh, 'bund', (-SUB_HALF_X - 0.22, 0.0, -SUB_HALF_Z - 0.22),
             (SUB_HALF_X + 0.22, SUB_BUND, SUB_HALF_Z + 0.22), {'up': 'gravel', '*': 'concrete'},
             uv_scale=1.2)
    for sx, sz, tx, tz in ((-1, -1, 1, -1), (1, -1, 1, 1), (1, 1, -1, 1), (-1, 1, -1, -1)):
        lo = (min(sx, tx) * (SUB_HALF_X + 0.22), SUB_BUND, min(sz, tz) * (SUB_HALF_Z + 0.22))
        hi = (max(sx, tx) * (SUB_HALF_X + 0.22), SUB_BUND + 0.06, max(sz, tz) * (SUB_HALF_Z + 0.22))
        wall = list(lo), list(hi)
        if sx == tx:
            wall[0][0] = sx * (SUB_HALF_X + 0.22) - (0.05 if sx > 0 else 0.0)
            wall[1][0] = sx * (SUB_HALF_X + 0.22) + (0.0 if sx > 0 else 0.05)
        else:
            wall[0][2] = sz * (SUB_HALF_Z + 0.22) - (0.05 if sz > 0 else 0.0)
            wall[1][2] = sz * (SUB_HALF_Z + 0.22) + (0.0 if sz > 0 else 0.05)
        clad_box(mesh, 'bund', tuple(wall[0]), tuple(wall[1]), {'*': 'concrete'}, uv_scale=0.5)

    # the rails the tank stands on, which is how a transformer is moved into place
    for z in (-SUB_HALF_Z + 0.12, SUB_HALF_Z - 0.12):
        box(mesh, mesh.faces('rails', 'steel'), (-SUB_HALF_X - 0.08, SUB_BUND, z - 0.035),
            (SUB_HALF_X + 0.08, SUB_BUND + 0.04, z + 0.035), uv_scale=1.0)

    clad_box(mesh, 'tank', (-SUB_HALF_X, SUB_BUND + 0.04, -SUB_HALF_Z),
             (SUB_HALF_X, SUB_TANK, SUB_HALF_Z), {'up': 'tank_top', '*': 'tank'})
    # the stiffeners a tank this size is ribbed with, so a two-metre flank does not read as a slab
    for i in range(5):
        x = -SUB_HALF_X + (2 * SUB_HALF_X) * (i + 0.5) / 5.0
        for z, out in ((-SUB_HALF_Z, -0.014), (SUB_HALF_Z, 0.014)):
            box(mesh, mesh.faces('tank', 'steel'), (x - 0.022, SUB_BUND + 0.06, min(z, z + out)),
                (x + 0.022, SUB_TANK - 0.03, max(z, z + out)), uv_scale=0.12)

    # the cover, bolted through a gasket
    clad_box(mesh, 'cover', (-SUB_HALF_X - 0.018, SUB_TANK, -SUB_HALF_Z - 0.018),
             (SUB_HALF_X + 0.018, SUB_TANK + 0.030, SUB_HALF_Z + 0.018),
             {'up': 'tank_top', '*': 'tank'}, uv_scale=0.8)
    steel = mesh.faces('cover', 'steel')
    for i in range(11):
        x = -SUB_HALF_X + 0.03 + i * (2 * SUB_HALF_X - 0.06) / 10.0
        for z in (-SUB_HALF_Z - 0.008, SUB_HALF_Z - 0.006):
            bolt(mesh, steel, (x, SUB_TANK + 0.030, z), 'y', 0.008, 0.014, uv_scale=0.08)

    # The four radiator banks a side, standing off the flanks on their pipes.
    for sz in (-1, 1):
        for i in range(SUB_RADIATORS):
            x = -SUB_HALF_X + 0.14 + i * (2 * SUB_HALF_X - 0.28) / (SUB_RADIATORS - 1)
            radiator(mesh, (x, SUB_BUND + 0.16, sz * (SUB_HALF_Z + 0.11)), 0.08,
                     SUB_TANK - SUB_BUND - 0.34, SUB_FINS)

    # the conservator: a drum above the radiators on two saddles, holding the oil's expansion
    drum = mesh.faces('conservator', 'tank')
    cylinder(mesh, drum, (0.0, SUB_TANK + 0.24, SUB_HALF_Z + 0.20), 'x', 0.11, SUB_HALF_X * 0.72,
             sides=ROUND, uv_scale=1.0, uv_along=2.0, caps=drum)
    for sx in (-1, 1):
        box(mesh, mesh.faces('conservator', 'steel'),
            (sx * SUB_HALF_X * 0.46 - 0.030, SUB_TANK + 0.030, SUB_HALF_Z + 0.12),
            (sx * SUB_HALF_X * 0.46 + 0.030, SUB_TANK + 0.16, SUB_HALF_Z + 0.28), uv_scale=0.2)
    # the oil level gauge on its end, and the breather hanging off it
    cylinder(mesh, mesh.faces('conservator', 'plate'),
             (SUB_HALF_X * 0.72 + 0.004, SUB_TANK + 0.24, SUB_HALF_Z + 0.20), 'x', 0.052, 0.006,
             sides=FITTING, uv_scale=1.0, caps=mesh.faces('conservator', 'plate'))

    # the Buchholz relay, in the pipe from the tank to the conservator: it is *in* that pipe or it is
    # not a Buchholz relay
    relay = mesh.faces('relay', 'steel')
    tube(mesh, relay, [(0.0, SUB_TANK + 0.030, SUB_HALF_Z + 0.05),
                       (0.0, SUB_TANK + 0.14, SUB_HALF_Z + 0.10),
                       (0.0, SUB_TANK + 0.24, SUB_HALF_Z + 0.13)], 0.022, sides=FITTING,
         uv_scale=1.0, caps=relay)
    box(mesh, relay, (-0.045, SUB_TANK + 0.13, SUB_HALF_Z + 0.075),
        (0.045, SUB_TANK + 0.19, SUB_HALF_Z + 0.135), uv_scale=0.3)

    # the on-load tap changer, in its own compartment on one flank with its drive cabinet under it
    clad_box(mesh, 'tapchanger', (-SUB_HALF_X - 0.075, SUB_BUND + 0.30, -0.20),
             (-SUB_HALF_X, SUB_TANK - 0.10, 0.20), {'west': 'plate', '*': 'tank'}, uv_scale=0.6)
    clad_box(mesh, 'tapchanger', (-SUB_HALF_X - 0.11, SUB_BUND + 0.04, -0.12),
             (-SUB_HALF_X - 0.010, SUB_BUND + 0.30, 0.12), {'west': 'plate', '*': 'tank'},
             uv_scale=0.5)

    # The three high-voltage bushings, tall because 400 kV needs the creepage - and the three
    # medium-voltage ones behind them, which are the same object at a third the height.
    for index, x in enumerate((-0.40, 0.0, 0.40)):
        bushing(mesh, index + 1, (x, SUB_TANK + 0.030, -0.16), 0.62, 0.13, sheds=9)
    for index, x in enumerate((-0.34, 0.0, 0.34)):
        bushing(mesh, index + 4, (x, SUB_TANK + 0.030, 0.24), 0.22, 0.082, sheds=4)

    # the rating plate and the danger sign, at the height they are read from
    box(mesh, mesh.faces('nameplate', 'nameplate'), (0.34, SUB_BUND + 0.42, -SUB_HALF_Z - 0.008),
        (0.56, SUB_BUND + 0.66, -SUB_HALF_Z - 0.003), uv_scale=1.0)
    box(mesh, mesh.faces('nameplate', 'sign'), (-0.10, SUB_BUND + 0.42, -SUB_HALF_Z - 0.008),
        (0.10, SUB_BUND + 0.66, -SUB_HALF_Z - 0.003), uv_scale=1.0)

    for x in (-SUB_HALF_X + 0.08, SUB_HALF_X - 0.08):
        for z in (-SUB_HALF_Z + 0.08, SUB_HALF_Z - 0.08):
            eyebolt(mesh, mesh.faces('cover', 'steel'), (x, SUB_TANK + 0.030, z), 0.020)

    return mesh


MODELS = [
    ('tx_machine', machine_transformer),
    ('tx_substation', substation_transformer),
]


def main():
    for _ in emit(OUT, MODELS, MATERIALS, 'gen_transformer_models.py'):
        pass


if __name__ == '__main__':
    main()
