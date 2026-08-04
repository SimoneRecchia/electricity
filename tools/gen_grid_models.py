#!/usr/bin/env python3
"""Generates the OBJ geometry for the two grid machines the mod used to inherit as art.

    python3 tools/gen_grid_models.py

Writes into src/main/resources/assets/electricity/models/{utility_pole,power_box}/.

Why these two are generated now
-------------------------------
They came from a modelling package, and it showed in different ways.  The pole was a square post
with two flat bars on it and eight insulators, 2364 faces of which nearly all were the insulators;
the power box was 43 faces - a cuboid with a door drawn on one side, no plinth, no roof, no vent, no
hinge.  Neither could be measured, neither could be changed, and both were the two objects a player
looks at most closely, because they are the two at head height beside a path.

Both are now built from the same figures as everything else, at the turbine's own subdivision, and
both are authored facing **north** like the rest of the mod's models.  That is worth stating for the
pole in particular: its old geometry was *mirrored* rather than turned, so it needed a rotation table
of its own that no other machine used, and three separate pieces of code had to know about it.  A
model authored the ordinary way needs none of that.

The pole is concrete rather than wood
------------------------------------
Because that is what the inherited model was, and because spun reinforced concrete is what most of
Europe's medium-voltage distribution stands on.  A centrifugally cast pole is round, tapered, has a
mould seam down each side, and weathers by staining rather than by splitting - all of which is in
``pole_concrete.png``.  The hardware on it is hot-dip galvanised and the insulators are brown glazed
porcelain, which is the ordinary combination.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from modellib import (FITTING, ROUND, Mesh, angle, bolt, box, channel, clad_box, cylinder,
                      hemisphere, ibeam, sheds, strut, write_mtl)

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


# ------------------------------------------------------------------ the utility pole

# The pole's own figures, kept where they can be read together.  The heights are the inherited
# model's, because the collision cells, the wire anchors and every existing pole in every world are
# hung off them: a pole whose arms moved half a block would take the wires with it.
SHAFT_TOP = 6.18
BASE_RADIUS = 0.245
TOP_RADIUS = 0.155
LOWER_ARM_Y = 4.72
UPPER_ARM_Y = 5.67
ARM_DEPTH = 0.187          # how deep the channel is, top to bottom
LOWER_ARM_HALF = 2.086     # how far the lower arm reaches from the shaft
UPPER_ARM_HALF = 1.677
# Where the eight insulators stand, in the order ObjDefinitions names them.  Four on the lower arm
# and four on the upper, inner pair then outer pair, negative side first - the same order the
# inherited model happened to have, so an existing pole's wires stay on the insulators they were
# hung from.
LOWER_PINS = (-0.79, -1.56, 0.79, 1.56)
UPPER_PINS = (-0.615, -1.19, 0.615, 1.19)


def shaft_radius(y):
    """The taper, read at a height: a spun pole is a cone, and everything bolted to it has to know."""
    return BASE_RADIUS + (TOP_RADIUS - BASE_RADIUS) * min(1.0, max(0.0, y / SHAFT_TOP))


def crossarm(mesh, y, half, name):
    """One crossarm: a channel across the pole, strapped to it, with a brace under each end.

    A channel with the open side down, which is what a real steel crossarm is and why it does not
    fill with water.  The straps are the two U-bolts that actually hold it on, and the braces are
    what stop it turning about them - an arm without braces droops, and a drooping arm is the first
    thing anybody notices about a badly drawn pole.
    """
    steel = mesh.faces(name, 'steel')
    plate = mesh.faces(name, 'plate')
    radius = shaft_radius(y)

    # the texture tiled along the arm rather than stretched once over four blocks of it
    channel(mesh, steel, (-half, y, -0.075), (half, y + ARM_DEPTH, 0.075), along='x',
            opening='down', web=0.22, uv_scale=2.5)
    # the back plate the arm is clamped against, curved to the shaft only as much as a flat plate is
    box(mesh, plate, (-0.145, y - 0.030, -0.090), (0.145, y + ARM_DEPTH + 0.030, 0.090),
        uv_scale=0.4)
    for z in (-0.088, 0.076):
        for dy in (0.010, ARM_DEPTH - 0.010):
            bolt(mesh, plate, (-0.145, y + dy, z + 0.006), 'x', 0.014, 0.030, uv_scale=0.12)

    # the two braces, from the shaft below the arm out to a third of the way along it
    for side in (-1, 1):
        strut(mesh, steel, (side * (radius - 0.02), y - 0.44, 0.0),
              (side * half * 0.52, y + 0.008, 0.0), 0.022, 0.016, uv_scale=0.3)


def pin_insulator(mesh, index, x, y):
    """A pin insulator on a crossarm: the pin, the cap, and the shed stack.

    Its own object per insulator, and one material inside it, because ``ObjDefinitions`` names one
    group per insulator and the wire hangs from that group's centre.  The pin is in the arm's own
    group for the same reason - a steel spindle inside the insulator's bounding box would drag the
    anchor down towards the arm.
    """
    porcelain = mesh.faces('insulator_%d' % index, 'porcelain')
    # the shed stack: three skirts, 32 sides, which is what the turbine's own insulator carries
    sheds(mesh, porcelain, (x, y + 0.055, 0.0), 0.083, 0.195, count=3, sides=FITTING, uv_scale=0.5)
    # the tie wire groove at the top, where the conductor actually sits
    cylinder(mesh, porcelain, (x, y + 0.250, 0.0), 'y', 0.052, 0.014, sides=FITTING, uv_scale=0.3,
             caps=porcelain)

    # the spindle, in an object of its own per insulator.  Not in one 'hardware' group with every other
    # small steel part on the pole: a group's bounding box is what the collision is cut from, and one
    # holding both the spindles out on the arms and the climbing steps up the shaft is a slab four
    # blocks wide and five tall through the middle of the pole
    pin = mesh.faces('pin_%d' % index, 'steel')
    cylinder(mesh, pin, (x, y + 0.028, 0.0), 'y', 0.026, 0.028, sides=FITTING, uv_scale=0.2,
             caps=pin, cap_ends=(1,))


def utility_pole():
    """A concrete distribution pole: two crossarms, eight pin insulators, and the hardware.

    Authored facing north, so the arms run east-west and the conductors run the way the pole faces.
    """
    mesh = Mesh()

    concrete = mesh.faces('shaft', 'concrete')
    # the shaft: one tapered eighty-sided cone the whole height, with the texture repeated up it
    # rather than stretched over six blocks
    cylinder(mesh, concrete, (0.0, SHAFT_TOP / 2, 0.0), 'y', BASE_RADIUS, SHAFT_TOP / 2,
             sides=ROUND, uv_scale=1.0, uv_along=6.0, taper=TOP_RADIUS / BASE_RADIUS)
    # the cast collar at the foot, which is what a pole set in a socket foundation stands in
    cylinder(mesh, concrete, (0.0, 0.055, 0.0), 'y', BASE_RADIUS + 0.022, 0.055, sides=ROUND,
             uv_scale=0.5, caps=concrete)
    # and the cap: a shallow dome, because a flat-topped concrete pole fills with water and splits
    hemisphere(mesh, concrete, (0.0, SHAFT_TOP, 0.0), TOP_RADIUS, sides=ROUND, rings=4,
               squash=0.55, uv_scale=1.0)

    crossarm(mesh, LOWER_ARM_Y, LOWER_ARM_HALF, 'arm_lower')
    crossarm(mesh, UPPER_ARM_Y, UPPER_ARM_HALF, 'arm_upper')

    for i, x in enumerate(LOWER_PINS):
        pin_insulator(mesh, i + 1, x, LOWER_ARM_Y + ARM_DEPTH)
    for i, x in enumerate(UPPER_PINS):
        pin_insulator(mesh, i + 5, x, UPPER_ARM_Y + ARM_DEPTH)

    # the climbing steps: a peg through the shaft every two thirds of a block, alternating sides, which
    # is how a concrete pole is climbed.  One object each, for the reason the spindles are
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

    # the plate: the pole's number and the danger sign, at the height they are read from
    sign = mesh.faces('plate', 'sign')
    radius = shaft_radius(1.55)
    box(mesh, sign, (-0.105, 1.42, -radius - 0.012), (0.105, 1.68, -radius + 0.004), uv_scale=1.0)

    return mesh


# ------------------------------------------------------------------ the power box

def power_box():
    """A pad-mounted distribution kiosk: a plinth, a body, two doors, a hood and a bushing.

    What the block does is distribute in a radius and bridge to Forge Energy, and what that is in
    the world is a street cabinet: a sheet steel body on a cast plinth, two green door leaves with
    louvres low down where cool air is drawn in, a crowned hood overhanging them so rain runs clear
    of the seals, and one bushing on the roof where the line comes in.

    Centred in its block, which the inherited model was not: that one hugged the west edge and hung
    a sixteenth of a block outside it, so a kiosk placed against a wall was half inside the wall.
    """
    mesh = Mesh()
    body_x, body_z = 0.31, 0.17
    plinth_y = 0.055
    body_y = 0.70
    face = -body_z

    # the plinth: cast in place, wider than the body, with the ground against it
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

    # the hood: crowned, overhanging on all four sides, with the drip edge turned down
    clad_box(mesh, 'hood', (-body_x - 0.032, body_y, face - 0.032),
             (body_x + 0.032, body_y + 0.038, body_z + 0.032),
             {'up': 'cabinet_top', '*': 'cabinet'}, uv_scale=0.7)
    box(mesh, mesh.faces('hood', 'cabinet'), (-body_x - 0.032, body_y - 0.016, face - 0.032),
        (body_x + 0.032, body_y + 0.006, face - 0.020), uv_scale=0.3)

    # The two door leaves, the stile between them, a handle on each and hinges on the outer edges.
    # The danger sign and the keyhole are on the left leaf only - a kiosk has one of each - and the
    # leaves are painted on their edges too, which they were not when they borrowed the rack's
    # aluminium for everything but their front.
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

    # the ventilation on both flanks, on the outside of the sheet rather than inside it
    for x, side in ((body_x, 'east'), (-body_x - 0.003, 'west')):
        clad_box(mesh, 'vent', (x, 0.20, -0.115), (x + 0.003, 0.58, 0.115),
                 {side: 'vent', '*': 'cabinet'})

    # the bushing on the roof: this is where the line arrives, so it is where the wire hangs from
    porcelain = mesh.faces('insulator', 'porcelain')
    steel = mesh.faces('hardware', 'steel')
    cylinder(mesh, steel, (0.0, body_y + 0.038, 0.0), 'y', 0.060, 0.014, sides=FITTING,
             uv_scale=0.3, caps=steel)
    sheds(mesh, porcelain, (0.0, body_y + 0.052, 0.0), 0.072, 0.150, count=3, sides=FITTING,
          uv_scale=0.5)
    # the terminal on top of it, which is the thing a conductor is actually bolted to
    cylinder(mesh, steel, (0.0, body_y + 0.205, 0.0), 'y', 0.026, 0.016, sides=FITTING,
             uv_scale=0.2, caps=steel)

    # the cable entry: a gland through the plinth and the conduit that reaches it
    conduit = mesh.faces('conduit', 'steel')
    cylinder(mesh, conduit, (0.16, 0.030, body_z + 0.020), 'y', 0.030, 0.030, sides=FITTING,
             uv_scale=0.25, caps=conduit)
    cylinder(mesh, conduit, (0.16, 0.075, body_z + 0.020), 'y', 0.022, 0.045, sides=FITTING,
             uv_scale=0.25, caps=conduit)

    return mesh


MODELS = [
    ('utility_pole', utility_pole, ('concrete', 'steel', 'plate', 'porcelain', 'sign')),
    ('power_box', power_box, ('plinth', 'plate', 'cabinet', 'cabinet_top', 'door', 'leaf', 'sheet',
                              'frame', 'steel', 'vent', 'porcelain')),
]


def main():
    for name, builder, materials in MODELS:
        mesh = builder()
        directory = os.path.join(OUT, name)
        mesh.write(os.path.join(directory, name + '.obj'), name + '.mtl', 'gen_grid_models.py')
        write_mtl(os.path.join(directory, name + '.mtl'), materials, MATERIALS,
                  'gen_grid_models.py')
        vertices, faces = mesh.stats()
        groups = ['%s_%s' % (obj, material) for obj, material, face_list in mesh.objects if face_list]
        print('%-14s %4d vertices, %4d faces, %2d groups' % (name, vertices, faces, len(groups)))
        print('               %s' % ', '.join(groups))


if __name__ == '__main__':
    main()
