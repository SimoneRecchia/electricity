#!/usr/bin/env python3
"""The plant control cabinet: the box a plant is actually run from.

    python3 tools/gen_control_models.py          # writes the model
    python3 tools/check_hitboxes.py --java       # prints the cells PlantControllerBlock declares

A floor-standing IP54 enclosure on a plinth, one door on a piano hinge with a swing handle, and on that
door the only things an operator ever touches: an HMI panel, three lamps and a mushroom stop.  Behind the
louvre under it is the DIN rail every plant has - an industrial Ethernet switch, two serial gateways, the
controller itself and its 24 V supply - and on the roof the radio antenna and the fibre gland plate that
bring the rest of the plant to it.

WIDTH / DEPTH / TOP are the figures everything follows from: 800 x 400 x 2000 mm over a 100 mm plinth,
which is a standard enclosure, so the cabinet is two blocks tall and the antenna reaches into the second.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from modellib import (FITTING, HEX, ROUND, Mesh, bolt, box, clad_box,  # noqa: E402
                      cylinder, emit)

OUT = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')

# Reused sheets: a control cabinet is the same powder-coated steel as an inverter's, and the mod already
# draws that.  A new texture for the same material would be a second answer to a settled question.
MATERIALS = {
    'cabinet': 'pv_cabinet.png',
    'cabinet_leaf': 'pv_cabinet_leaf.png',
    'cabinet_top': 'pv_cabinet_top.png',
    'vent': 'pv_vent.png',
    'display': 'pv_display.png',
    'instrument': 'pv_instrument.png',
    'plate': 'pv_plate.png',
    'steel': 'pv_steel.png',
    'gland': 'dc_gland.png',
    # the three lamps an operator reads the cabinet by, and the mushroom stop beside them
    'lamp_run': 'lamp_run.png',
    'lamp_remote': 'lamp_remote.png',
    'lamp_alarm': 'lamp_alarm.png',
}

# 800 x 400 mm, 2000 mm tall, on a 100 mm plinth.  In blocks, at the mod's ten metres to the block.
HALF_WIDTH = 0.40
HALF_DEPTH = 0.20
PLINTH = 0.06
TOP = PLINTH + 1.24
# The antenna: a bracket on the roof and a 500 mm whip, which is what a plant's radio link stands on.
WHIP_TOP = TOP + 0.62


def cabinet():
    mesh = Mesh()
    steel = mesh.faces('cabinet', 'steel')
    plate = mesh.faces('plinth', 'plate')

    # ---- the plinth: a rolled channel the enclosure is bolted down to, set back off its own face
    box(mesh, plate, (-HALF_WIDTH + 0.03, 0.0, -HALF_DEPTH + 0.03),
        (HALF_WIDTH - 0.03, PLINTH, HALF_DEPTH - 0.03), uv_scale=0.6)
    for x in (-HALF_WIDTH + 0.10, HALF_WIDTH - 0.10):
        for z in (-HALF_DEPTH + 0.08, HALF_DEPTH - 0.08):
            bolt(mesh, plate, (x, PLINTH, z), 'y', 0.014, 0.024, uv_scale=0.12)

    # ---- the body: three walls and a roof, the fourth side being the door
    clad_box(mesh, 'cabinet', (-HALF_WIDTH, PLINTH, -HALF_DEPTH), (HALF_WIDTH, TOP, HALF_DEPTH),
             {'up': 'cabinet_top', '*': 'cabinet'}, uv_scale=1.0)
    # the roof's drip edge, which is why rain does not sit on the door seal
    box(mesh, mesh.faces('cabinet', 'cabinet_top'),
        (-HALF_WIDTH - 0.02, TOP, -HALF_DEPTH - 0.02), (HALF_WIDTH + 0.02, TOP + 0.02, HALF_DEPTH + 0.02),
        uv_scale=0.5)

    # ---- the door, proud of the face it shuts against, on a piano hinge down one edge
    door_lo, door_hi = PLINTH + 0.05, TOP - 0.05
    face = -HALF_DEPTH - 0.022
    clad_box(mesh, 'door', (-HALF_WIDTH + 0.03, door_lo, face), (HALF_WIDTH - 0.03, door_hi, -HALF_DEPTH),
             {'*': 'cabinet_leaf'}, uv_scale=1.0)
    hinge = mesh.faces('door', 'steel')
    for y in (door_lo + 0.10, (door_lo + door_hi) / 2.0, door_hi - 0.10):
        cylinder(mesh, hinge, (-HALF_WIDTH + 0.035, y, -HALF_DEPTH - 0.008), 'y', 0.016, 0.055,
                 sides=FITTING, uv_scale=0.2, caps=hinge)

    # ---- what an operator touches, all of it on the door: the panel, the lamps, the stop, the handle
    bezel = mesh.faces('door', 'plate')
    hmi = mesh.faces('door', 'display')
    # a 10 inch HMI, let into the door behind a bezel
    box(mesh, bezel, (-0.20, door_hi - 0.44, face - 0.014), (0.20, door_hi - 0.14, face), uv_scale=0.8)
    box(mesh, hmi, (-0.175, door_hi - 0.415, face - 0.020), (0.175, door_hi - 0.165, face - 0.014),
        uv_scale=1.0)
    # RUN, REMOTE, ALARM: three 22 mm lamps in a row under the panel, each its own lens
    for x, lens in ((0.10, 'lamp_run'), (0.0, 'lamp_remote'), (-0.10, 'lamp_alarm')):
        lamp = mesh.faces('door', lens)
        cylinder(mesh, lamp, (x, door_hi - 0.52, face), 'z', 0.020, 0.008, sides=FITTING,
                 uv_scale=1.0, caps=lamp)
    # the mushroom stop, which is bigger than the lamps and stands further out because it is hit by hand
    stop = mesh.faces('door', 'lamp_alarm')
    cylinder(mesh, stop, (-0.235, door_hi - 0.52, face), 'z', 0.032, 0.018, sides=FITTING,
             uv_scale=1.0, caps=stop, taper=1.25)
    # the three-point swing handle, down the closing edge
    box(mesh, mesh.faces('door', 'steel'), (HALF_WIDTH - 0.115, door_hi - 0.66, face - 0.030),
        (HALF_WIDTH - 0.055, door_hi - 0.50, face), uv_scale=0.3)

    # ---- the louvre: the lower third of the door is the air path over the DIN rail behind it
    vent = mesh.faces('door', 'vent')
    box(mesh, vent, (-0.26, door_lo + 0.06, face - 0.006), (0.26, door_lo + 0.34, face), uv_scale=1.0)

    # ---- the DIN rail, seen through the louvre: the switch, two gateways, the controller and its supply
    rail = mesh.faces('rail', 'steel')
    box(mesh, rail, (-0.30, door_lo + 0.19, -HALF_DEPTH + 0.06),
        (0.30, door_lo + 0.21, -HALF_DEPTH + 0.075), uv_scale=0.2)
    kit = mesh.faces('rail', 'instrument')
    # widths as the real modules are: a managed Ethernet switch is wider than a two-port serial gateway
    cursor = -0.28
    for width in (0.11, 0.055, 0.055, 0.13, 0.075):
        box(mesh, kit, (cursor, door_lo + 0.13, -HALF_DEPTH + 0.055),
            (cursor + width, door_lo + 0.27, -HALF_DEPTH + 0.115), uv_scale=0.5)
        cursor += width + 0.008

    # ---- the gland plate under the cabinet: where the fibre and the metering leads come in
    glands = mesh.faces('glands', 'gland')
    box(mesh, mesh.faces('glands', 'plate'), (-0.24, PLINTH - 0.014, -0.09), (0.24, PLINTH, 0.09),
        uv_scale=0.5)
    for x in (-0.15, -0.05, 0.05, 0.15):
        cylinder(mesh, glands, (x, PLINTH - 0.030, 0.0), 'y', 0.026, 0.016, sides=HEX, uv_scale=0.3,
                 caps=glands)

    # ---- the antenna: an L bracket on the roof and a whip on a moulded base
    mast = mesh.faces('antenna', 'steel')
    box(mesh, mast, (HALF_WIDTH - 0.13, TOP + 0.02, -0.03), (HALF_WIDTH - 0.05, TOP + 0.05, 0.03),
        uv_scale=0.2)
    box(mesh, mast, (HALF_WIDTH - 0.11, TOP + 0.05, -0.025), (HALF_WIDTH - 0.07, TOP + 0.15, 0.025),
        uv_scale=0.2)
    base = mesh.faces('antenna', 'instrument')
    cylinder(mesh, base, (HALF_WIDTH - 0.09, TOP + 0.17, 0.0), 'y', 0.022, 0.028, sides=FITTING,
             uv_scale=0.3, caps=base, taper=0.7)
    cylinder(mesh, mast, (HALF_WIDTH - 0.09, WHIP_TOP - 0.20, 0.0), 'y', 0.008, 0.22, sides=ROUND,
             uv_scale=0.4, uv_along=3.0, taper=0.55, caps=mast, cap_ends=(1,))

    return mesh


MODELS = [('plant_controller', cabinet)]


def main():
    for _ in emit(OUT, MODELS, MATERIALS, 'gen_control_models.py'):
        pass


if __name__ == '__main__':
    main()
