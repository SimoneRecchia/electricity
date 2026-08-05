#!/usr/bin/env python3
"""The electric cabin: the prefabricated substation kiosk a plant's machines collect into.

    python3 tools/gen_cab_models.py

Authored about the block's centre facing north, in the envelope the inherited model had - x plus and minus
0.55, z plus and minus 1.16, and 2.8 tall - because every cabin in every existing world has its collision
cells and its wire anchors hung off those figures.

The two fittings are ``insulator_input`` and ``insulator_output`` in that order, which ObjDefinitions names
and a wire is stored against the *index* of.  The names carry the direction as well: InsulatorPartHelper
reads "input" and "output" out of them to decide which end of the machine a fitting is.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from modellib import (FITTING, Mesh, bolt, box, clad_box, cylinder, eyebolt, lathe,   # noqa: E402
                      pin_insulator, write_mtl)

# every material this model uses, in the order the MTL declares them

OUT = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')

MATERIALS = {
    'panel': 'cab_panel.png',
    'roof': 'cab_roof.png',
    'door': 'cab_door.png',
    'leaf': 'cab_leaf.png',
    'sheet': 'box_sheet.png',
    'plinth': 'box_plinth.png',
    'vent': 'pv_vent.png',
    'steel': 'pv_steel.png',
    'plate': 'pv_plate.png',
    'porcelain': 'porcelain_brown.png',
    'arrester': 'cab_arrester.png',
}

USED = ('panel', 'roof', 'door', 'leaf', 'sheet', 'plinth', 'vent', 'steel', 'plate', 'porcelain',
        'arrester')

# The envelope, which is the inherited model's and cannot move without moving every cabin in every world.
HALF_X = 0.55
HALF_Z = 1.16
PLINTH = 0.14
EAVES = 2.55
ROOF = 2.72
FACE = -HALF_Z          # the doors are on the north face

# The two fittings, at the two ends of the roof: the machines come in at one and the line leaves at the
# other, which is what makes a substation a substation rather than a box with wires on it.
INSULATOR_DIAMETER = 0.166
INPUT_Z = 0.72
OUTPUT_Z = -0.72


def plinth(mesh):
    """The cast plinth, on the cable void every prefabricated kiosk is dropped onto."""
    clad_box(mesh, 'plinth', (-HALF_X - 0.035, 0.0, -HALF_Z - 0.035),
             (HALF_X + 0.035, PLINTH, HALF_Z + 0.035), {'up': 'plate', '*': 'plinth'}, uv_scale=1.4)
    # the chamfer round its top edge, which is what stops a cast edge chipping
    clad_box(mesh, 'plinth', (-HALF_X - 0.018, PLINTH, -HALF_Z - 0.018),
             (HALF_X + 0.018, PLINTH + 0.020, HALF_Z + 0.018), {'*': 'plinth'}, uv_scale=0.8)


def shell(mesh):
    """The walls: moulded panels between corner pilasters, which is how a kiosk is actually made."""
    # standing on the plinth's chamfer rather than on its top face, so the two do not share a plane over
    # the whole two and a half square blocks of it
    clad_box(mesh, 'body', (-HALF_X, PLINTH + 0.020, -HALF_Z), (HALF_X, EAVES, HALF_Z),
             {'up': 'roof', '*': 'panel'})
    # the four pilasters, standing a little proud, which is what the panels are bolted between
    # standing off the plinth's own top face rather than on it: two faces at one depth flicker
    for x in (-HALF_X - 0.024, HALF_X + 0.002):
        for z in (-HALF_Z - 0.024, HALF_Z + 0.002):
            clad_box(mesh, 'body', (x, PLINTH + 0.022, z), (x + 0.022, EAVES, z + 0.022),
                     {'*': 'panel'}, uv_scale=0.12)
    # and the panel joints down the long flanks: a vertical rib every half block
    # wholly outside the wall: a rib whose inner face is the wall's own plane draws a face there
    for i in range(4):
        z = -HALF_Z + (2 * HALF_Z) * (i + 1) / 5.0
        for x, sign in ((-HALF_X, -1), (HALF_X, 1)):
            near, far = x + sign * 0.002, x + sign * 0.014
            box(mesh, mesh.faces('body', 'panel'), (min(near, far), PLINTH + 0.02, z - 0.016),
                (max(near, far), EAVES - 0.02, z + 0.016), uv_scale=0.1)


def roof(mesh):
    """A flat roof with a fall to one side, a parapet on three, and a drip edge over the doors."""
    clad_box(mesh, 'roof', (-HALF_X - 0.05, EAVES, -HALF_Z - 0.05),
             (HALF_X + 0.05, ROOF, HALF_Z + 0.05), {'up': 'roof', '*': 'sheet'}, uv_scale=1.2)
    # the parapet, on the two flanks and the back: a flat roof drains to the front and not over its sides
    for lo, hi in (((-HALF_X - 0.05, ROOF, HALF_Z - 0.02), (HALF_X + 0.05, ROOF + 0.055, HALF_Z + 0.05)),
                   ((-HALF_X - 0.05, ROOF, -HALF_Z - 0.05), (-HALF_X + 0.02, ROOF + 0.055, HALF_Z + 0.05)),
                   ((HALF_X - 0.02, ROOF, -HALF_Z - 0.05), (HALF_X + 0.05, ROOF + 0.055, HALF_Z + 0.05))):
        clad_box(mesh, 'roof', lo, hi, {'*': 'sheet'}, uv_scale=0.4)
    # the drip edge over the doors, turned down so rain leaves the wall rather than running down it
    box(mesh, mesh.faces('roof', 'sheet'), (-HALF_X - 0.05, EAVES - 0.028, FACE - 0.05),
        (HALF_X + 0.05, EAVES + 0.004, FACE - 0.032), uv_scale=0.3)

    for x in (-HALF_X + 0.06, HALF_X - 0.06):
        for z in (-HALF_Z + 0.10, HALF_Z - 0.10):
            eyebolt(mesh, mesh.faces('roof', 'steel'), (x, ROOF, z), 0.020)


def doors(mesh):
    """Two leaves in a recessed bay, with the louvres low where cool air is drawn in.

    A substation kiosk's doors are its whole front, because what is behind them is switchgear that has to
    be got at with a trolley - and they open outwards, which is why the hinges are on the outside.
    """
    for side, x0, x1 in ((-1, -0.505, -0.010), (1, 0.010, 0.505)):
        clad_box(mesh, 'door', (x0, PLINTH + 0.06, FACE - 0.030), (x1, EAVES - 0.10, FACE),
                 {'north': 'door' if side < 0 else 'leaf', '*': 'sheet'})
        handle_x = x1 - 0.055 if side < 0 else x0 + 0.032
        box(mesh, mesh.faces('door', 'steel'), (handle_x, 1.12, FACE - 0.058),
            (handle_x + 0.023, 1.34, FACE - 0.030), uv_scale=0.12)
        hinge_x = x0 if side < 0 else x1 - 0.013
        for y in (PLINTH + 0.16, 1.24, EAVES - 0.22):
            box(mesh, mesh.faces('door', 'steel'), (hinge_x, y, FACE - 0.044),
                (hinge_x + 0.013, y + 0.070, FACE - 0.030), uv_scale=0.1)

    # the centre stile the two leaves shut against
    box(mesh, mesh.faces('body', 'steel'), (-0.013, PLINTH + 0.06, FACE - 0.034),
        (0.013, EAVES - 0.10, FACE - 0.026), uv_scale=0.1)
    # and the threshold they shut down onto
    box(mesh, mesh.faces('body', 'plate'), (-0.505, PLINTH + 0.002, FACE - 0.034),
        (0.505, PLINTH + 0.06, FACE - 0.008), uv_scale=0.3)


def ventilation(mesh):
    """Louvres low on one flank and high on the other: a transformer room vents by convection.

    Low in and high out, on opposite sides, because that is the only arrangement that moves air through a
    room with no fan in it - and a kiosk has no fan in it.

    A bank is *let into* the wall rather than stuck on it: it spans the wall's own plane, so its inner face
    is inside the body and its outer face outside, and neither lands on the wall's. Sitting against it
    instead put two faces at one depth, four times, which is a louvre bank that flickers.
    """
    for side_x, side, low, high in ((-HALF_X, 'west', PLINTH + 0.14, PLINTH + 0.70),
                                    (HALF_X, 'east', EAVES - 0.86, EAVES - 0.30)):
        clad_box(mesh, 'vent', (side_x - 0.005, low, -0.62), (side_x + 0.005, high, 0.62),
                 {side: 'vent', '*': 'sheet'})
        # the frame, wholly outside the wall so it does not land on that plane either
        near = side_x + (0.005 if side_x > 0 else -0.011)
        far = side_x + (0.011 if side_x > 0 else -0.005)
        for z in (-0.645, 0.62):
            box(mesh, mesh.faces('vent', 'panel'), (near, low - 0.025, z),
                (far, high + 0.025, z + 0.025), uv_scale=0.1)


def switchgear(mesh):
    """What a substation kiosk carries outside: the two bushings and the arresters beside the incomer.

    Surge arresters at the incoming end, because that is where a lightning surge arrives from - and three
    of them, one a phase, which is what tells them from anything else on a roof.
    """
    steel = mesh.faces('hardware', 'steel')
    for name, z in (('insulator_input', INPUT_Z), ('insulator_output', OUTPUT_Z)):
        # the pedestal the bushing is bolted to, two thousandths above the roof so no two faces coincide
        box(mesh, steel, (-0.075, ROOF + 0.002, z - 0.075), (0.075, ROOF + 0.020, z + 0.075),
            uv_scale=0.3)
        pin_insulator(mesh, mesh.faces(name, 'porcelain'), steel,
                      (0.0, ROOF + 0.062, z), INSULATOR_DIAMETER)

    # the three arresters, in a row across the roof beside the incomer
    for index, x in enumerate((-0.30, 0.0, 0.30)):
        arrester(mesh, index + 1, (x, ROOF + 0.020, INPUT_Z + 0.30))


def arrester(mesh, index, base):
    """One surge arrester: a polymer-housed stack of sheds on a base, with an earth lead off it."""
    housing = mesh.faces('arrester_%d' % index, 'arrester')
    steel = mesh.faces('arrester_%d' % index, 'steel')
    cx, cy, cz = base

    cylinder(mesh, steel, (cx, cy, cz), 'y', 0.052, 0.014, sides=FITTING, uv_scale=0.2, caps=steel)
    # eight sheds on a core, as one turning: a stack of cones has an annulus with nothing in it
    profile = [(0.030, 0.014)]
    for shed in range(8):
        base_y = 0.028 + shed * 0.030
        profile += [(0.030, base_y), (0.046, base_y + 0.009), (0.030, base_y + 0.016)]
    profile += [(0.026, 0.276), (0.0, 0.276)]
    lathe(mesh, housing, (cx, cy, cz), profile, sides=FITTING, uv_scale=1.0)
    # the terminal on top and the earth lead off the base, which is what an arrester is *for*
    cylinder(mesh, steel, (cx, cy + 0.276, cz), 'y', 0.016, 0.012, sides=FITTING, uv_scale=0.2,
             caps=steel)
    box(mesh, steel, (cx - 0.008, cy - 0.002, cz + 0.050), (cx + 0.008, cy + 0.030, cz + 0.062),
        uv_scale=0.1)


def signage(mesh):
    """The rating plate, the danger sign and the earth bar: the three things a kiosk always carries."""
    box(mesh, mesh.faces('signage', 'plate'), (0.20, 1.62, FACE - 0.036),
        (0.42, 1.92, FACE - 0.032), uv_scale=1.0)
    # the earth bar low on the back, with the downlead strap up the corner to the roof steelwork
    earth = mesh.faces('signage', 'steel')
    # clear of the gland plate, which owns x -0.34 to 0.34 of the same wall
    box(mesh, earth, (0.38, PLINTH + 0.10, HALF_Z), (0.52, PLINTH + 0.16, HALF_Z + 0.020),
        uv_scale=0.2)
    for i in range(2):
        bolt(mesh, earth, (0.42 + i * 0.06, PLINTH + 0.13, HALF_Z + 0.020), 'z', 0.010, 0.014,
             uv_scale=0.08)
    box(mesh, earth, (HALF_X - 0.026, PLINTH + 0.13, HALF_Z + 0.004),
        (HALF_X - 0.006, EAVES, HALF_Z + 0.016), uv_scale=0.1)


def cable_entry(mesh):
    """The gland plate on the back, where the collector cables come up out of the void."""
    clad_box(mesh, 'entryplate', (-0.34, PLINTH + 0.02, HALF_Z), (0.34, PLINTH + 0.34, HALF_Z + 0.026),
             {'south': 'plate', '*': 'sheet'}, uv_scale=0.6)
    glands = mesh.faces('entryplate', 'steel')
    for i in range(5):
        cylinder(mesh, glands, (-0.24 + i * 0.12, PLINTH + 0.014, HALF_Z + 0.013), 'y', 0.019, 0.022,
                 sides=FITTING, uv_scale=0.2, caps=glands)


def cab():
    mesh = Mesh()
    plinth(mesh)
    shell(mesh)
    roof(mesh)
    doors(mesh)
    ventilation(mesh)
    switchgear(mesh)
    signage(mesh)
    cable_entry(mesh)
    return mesh


def main():
    mesh = cab()
    directory = os.path.join(OUT, 'electric_cab')
    mesh.write(os.path.join(directory, 'cab.obj'), 'cab.mtl', 'gen_cab_models.py')
    write_mtl(os.path.join(directory, 'cab.mtl'), USED, MATERIALS, 'gen_cab_models.py')
    vertices, faces = mesh.stats()
    groups = ['%s_%s' % (o, m) for o, m, f in mesh.objects if f]
    print('electric_cab  %5d vertices, %5d faces, %2d groups' % (vertices, faces, len(groups)))
    print('the two fittings are insulator_input_porcelain and insulator_output_porcelain, in that order')


if __name__ == '__main__':
    main()
