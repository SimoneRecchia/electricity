#!/usr/bin/env python3
"""The utility pole and the pad-mount kiosk.

    python3 tools/gen_grid_models.py

Both authored about the block's centre facing north.  The kiosk's mounting channels are part of its
body so they are there whether it stands on its plinth or hangs on a wall - PowerBoxBlock.BACKSET and
WALL_CELLS are the other half of that.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from modellib import (FITTING, ROUND, Mesh, angle, bolt, box, channel, clad_box, cylinder,
                      emit, hemisphere, ibeam, pin_insulator, strut)

OUT = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')

MATERIALS = {
    'concrete': 'pole_concrete.png',
    'steel': 'pv_steel.png',
    'plate': 'pv_plate.png',
    'porcelain': 'porcelain_brown.png',
    'sign': 'pole_plate.png',
    'cabinet': 'pv_cabinet.png',
    'cabinet_top': 'pv_cabinet_top.png',
    'door': 'box_door.png',
    'leaf': 'box_leaf.png',
    'sheet': 'box_sheet.png',
    'vent': 'pv_vent.png',
    'frame': 'pv_frame.png',
    'plinth': 'box_plinth.png',
}


# The one insulator, at one size.
INSULATOR_DIAMETER = 0.166


# ------------------------------------------------------------------ the utility pole

# The pole's own figures, kept where they can be read together.
SHAFT_TOP = 6.18
BASE_RADIUS = 0.245
TOP_RADIUS = 0.155
LOWER_ARM_Y = 4.72
UPPER_ARM_Y = 5.67
ARM_DEPTH = 0.187          # how deep the channel is, top to bottom
LOWER_ARM_HALF = 2.086     # how far the lower arm reaches from the shaft
UPPER_ARM_HALF = 1.677
# Where the eight insulators stand, in the order ObjDefinitions names them.
LOWER_PINS = (-0.79, -1.56, 0.79, 1.56)
UPPER_PINS = (-0.615, -1.19, 0.615, 1.19)


def shaft_radius(y):
    """The taper, read at a height: a spun pole is a cone, and everything bolted to it has to know."""
    return BASE_RADIUS + (TOP_RADIUS - BASE_RADIUS) * min(1.0, max(0.0, y / SHAFT_TOP))


def crossarm(mesh, y, half, name):
    """One crossarm: a channel across the pole, strapped to it, with a brace under each end."""
    steel = mesh.faces(name, 'steel')
    plate = mesh.faces(name, 'plate')
    radius = shaft_radius(y)

    # the texture tiled along the arm rather than stretched once over four blocks of it
    channel(mesh, steel, (-half, y, -0.075), (half, y + ARM_DEPTH, 0.075), along='x',
            opening='down', web=0.22, uv_scale=2.5)
    # the back plate the arm is clamped against
    box(mesh, plate, (-0.145, y - 0.030, -0.090), (0.145, y + ARM_DEPTH + 0.030, 0.090),
        uv_scale=0.4)
    for z in (-0.088, 0.076):
        for dy in (0.010, ARM_DEPTH - 0.010):
            bolt(mesh, plate, (-0.145, y + dy, z + 0.006), 'x', 0.014, 0.030, uv_scale=0.12)

    # the two braces, from the shaft below the arm out to a third of the way along it
    for side in (-1, 1):
        strut(mesh, steel, (side * (radius - 0.02), y - 0.44, 0.0),
              (side * half * 0.52, y + 0.008, 0.0), 0.022, 0.016, uv_scale=0.3)


def crossarm_insulator(mesh, index, x, y):
    """One of the pole's eight insulators: the mod's own ``pin_insulator``, on its spindle."""
    pin_insulator(mesh, mesh.faces('insulator_%d' % index, 'porcelain'),
                  mesh.faces('pin_%d' % index, 'steel'), (x, y + 0.050, 0.0), INSULATOR_DIAMETER)


def utility_pole():
    """A concrete distribution pole: two crossarms, eight pin insulators, and the hardware."""
    mesh = Mesh()

    concrete = mesh.faces('shaft', 'concrete')
    # the shaft: one tapered eighty-sided cone the whole height
    # rather than stretched over six blocks
    cylinder(mesh, concrete, (0.0, SHAFT_TOP / 2, 0.0), 'y', BASE_RADIUS, SHAFT_TOP / 2,
             sides=ROUND, uv_scale=1.0, uv_along=6.0, taper=TOP_RADIUS / BASE_RADIUS)
    # the cast collar at the foot
    cylinder(mesh, concrete, (0.0, 0.055, 0.0), 'y', BASE_RADIUS + 0.022, 0.055, sides=ROUND,
             uv_scale=0.5, caps=concrete)
    # and the cap: a shallow dome, because a flat-topped concrete pole fills with water and splits
    hemisphere(mesh, concrete, (0.0, SHAFT_TOP, 0.0), TOP_RADIUS, sides=ROUND, rings=4,
               squash=0.55, uv_scale=1.0)

    crossarm(mesh, LOWER_ARM_Y, LOWER_ARM_HALF, 'arm_lower')
    crossarm(mesh, UPPER_ARM_Y, UPPER_ARM_HALF, 'arm_upper')

    for i, x in enumerate(LOWER_PINS):
        crossarm_insulator(mesh, i + 1, x, LOWER_ARM_Y + ARM_DEPTH)
    for i, x in enumerate(UPPER_PINS):
        crossarm_insulator(mesh, i + 5, x, UPPER_ARM_Y + ARM_DEPTH)

    # the climbing steps: a peg through the shaft every two thirds of a block, alternating sides
    # is how a concrete pole is climbed.
    for i in range(7):
        y = 1.10 + i * 0.62
        side = 1 if i % 2 == 0 else -1
        radius = shaft_radius(y)
        bolt(mesh, mesh.faces('step_%d' % i, 'steel'), (side * (radius - 0.01), y, 0.0), 'x', 0.014,
             side * 0.11, uv_scale=0.12, washer=False)

    # the earth conductor: a flat strap down the back of the shaft with a clip every block, bonding
    # the arms to the electrode at the foot
    earth = mesh.faces('earth', 'steel')
    for i in range(6):
        y0 = 0.10 + i * 1.0
        radius = shaft_radius(y0 + 0.5)
        box(mesh, earth, (-0.014, y0, radius - 0.004), (0.014, y0 + 0.95, radius + 0.008),
            uv_scale=0.15)
        box(mesh, earth, (-0.026, y0 + 0.90, radius - 0.010), (0.026, y0 + 0.94, radius + 0.016),
            uv_scale=0.1)

    # the plate: the pole's number and the danger sign
    sign = mesh.faces('plate', 'sign')
    radius = shaft_radius(1.55)
    box(mesh, sign, (-0.105, 1.42, -radius - 0.012), (0.105, 1.68, -radius + 0.004), uv_scale=1.0)

    return mesh


# ------------------------------------------------------------------ the power box

def power_box():
    """A pad-mounted distribution kiosk: a plinth, a body, two doors, a hood and a bushing."""
    mesh = Mesh()
    body_x, body_z = 0.31, 0.17
    plinth_y = 0.055
    body_y = 0.70
    face = -body_z

    # the plinth: cast in place, wider than the body
    clad_box(mesh, 'plinth', (-body_x - 0.030, 0.0, -body_z - 0.030),
             (body_x + 0.030, plinth_y, body_z + 0.030), {'up': 'plate', '*': 'plinth'},
             uv_scale=0.8)

    clad_box(mesh, 'body', (-body_x, plinth_y, face), (body_x, body_y, body_z),
             {'up': 'cabinet_top', '*': 'cabinet'})
    # the corner posts the sheet is folded round
    for x in (-body_x - 0.008, body_x) :
        for z in (face - 0.008, body_z):
            box(mesh, mesh.faces('body', 'frame'), (x, plinth_y, z), (x + 0.008, body_y, z + 0.008),
                uv_scale=0.1)

    # the hood: crowned, overhanging on all four sides
    clad_box(mesh, 'hood', (-body_x - 0.032, body_y, face - 0.032),
             (body_x + 0.032, body_y + 0.038, body_z + 0.032),
             {'up': 'cabinet_top', '*': 'cabinet'}, uv_scale=0.7)
    box(mesh, mesh.faces('hood', 'cabinet'), (-body_x - 0.032, body_y - 0.016, face - 0.032),
        (body_x + 0.032, body_y + 0.006, face - 0.020), uv_scale=0.3)

    # The two door leaves, the stile between them, a handle on each and hinges on the outer edges.
    for side, x0, x1 in ((-1, -0.285, -0.008), (1, 0.008, 0.285)):
        clad_box(mesh, 'door', (x0, plinth_y + 0.030, face - 0.022), (x1, body_y - 0.030, face),
                 {'north': 'door' if side < 0 else 'leaf', '*': 'sheet'})
        handle_x = x1 - 0.045 if side < 0 else x0 + 0.025
        box(mesh, mesh.faces('door', 'frame'), (handle_x, 0.34, face - 0.042),
            (handle_x + 0.020, 0.44, face - 0.022), uv_scale=0.12)
        hinge_x = x0 if side < 0 else x1 - 0.010
        for y in (plinth_y + 0.075, body_y - 0.105):
            box(mesh, mesh.faces('door', 'steel'), (hinge_x, y, face - 0.032),
                (hinge_x + 0.010, y + 0.045, face - 0.022), uv_scale=0.1)
    box(mesh, mesh.faces('body', 'frame'), (-0.010, plinth_y + 0.030, face - 0.024),
        (0.010, body_y - 0.030, face - 0.018), uv_scale=0.1)

    # the ventilation on both flanks
    for x, side in ((body_x, 'east'), (-body_x - 0.003, 'west')):
        clad_box(mesh, 'vent', (x, 0.20, -0.115), (x + 0.003, 0.58, 0.115),
                 {side: 'vent', '*': 'cabinet'})

    # The mounting channels on the back
    for x in (-body_x + 0.055, body_x - 0.075):
        clad_box(mesh, 'body', (x, plinth_y + 0.055, body_z), (x + 0.020, body_y - 0.055, body_z + 0.018),
                 {'*': 'frame'}, uv_scale=0.15)
        for y in (plinth_y + 0.085, body_y - 0.095):
            bolt(mesh, mesh.faces('body', 'steel'), (x + 0.010, y, body_z + 0.018), 'z', 0.007, 0.012,
                 uv_scale=0.08)

    # The insulator on the roof: this is where the line arrives
    # and it is the same insulator the pole carries, at the same diameter, because it is the same part.
    steel = mesh.faces('hardware', 'steel')
    # two thousandths above the roof rather than exactly on it: two faces on one plane flicker
    box(mesh, steel, (-0.075, body_y + 0.002, -0.075), (0.075, body_y + 0.022, 0.075), uv_scale=0.3)
    pin_insulator(mesh, mesh.faces('insulator', 'porcelain'), steel,
                  (0.0, body_y + 0.062, 0.0), INSULATOR_DIAMETER)

    # the cable entry: a gland through the plinth and the conduit that reaches it
    conduit = mesh.faces('conduit', 'steel')
    cylinder(mesh, conduit, (0.16, 0.030, body_z + 0.020), 'y', 0.030, 0.030, sides=FITTING,
             uv_scale=0.25, caps=conduit)
    cylinder(mesh, conduit, (0.16, 0.075, body_z + 0.020), 'y', 0.022, 0.045, sides=FITTING,
             uv_scale=0.25, caps=conduit)

    return mesh


MODELS = [
    ('utility_pole', utility_pole),
    ('power_box', power_box),
]


def main():
    for _ in emit(OUT, MODELS, MATERIALS, 'gen_grid_models.py'):
        pass


if __name__ == '__main__':
    main()
