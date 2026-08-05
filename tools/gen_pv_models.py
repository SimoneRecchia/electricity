#!/usr/bin/env python3
"""The photovoltaic models: four mountings, the inverter, the combiner, the met mast.

    python3 tools/gen_pv_models.py

Authored about the block's centre, facing north, because the mod's own renderer places them that way.

CORE_RADIUS / CORE_OFFSET / CORE_Y / MC4 are gen_cable_models' figures repeated here: every length of
cable a machine shows has to be the same product as the run a player lays next to it.  DOOR_LOW,
DOOR_HIGH and DISPLAY_* are the inverter's front, and pv_cabinet_door is drawn round them.

Group names are a contract - see CLAUDE.md section 5.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from modellib import (FITTING, ROUND, Mesh, angle, arc, bolt, box, channel, clad_box, cylinder,
                      eyebolt, hemisphere, ibeam, pin_insulator, pivot, rotate, strut, tube,
                      write_mtl)

OUT = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')

# Materials, and the texture each one resolves to.
# map_Kd names, so these are bare file names.
MATERIALS = {
    'module': 'pv_module.png',
    'module_back': 'pv_module_back.png',
    'module_edge': 'pv_module_edge.png',
    'frame': 'pv_frame.png',
    'steel': 'pv_steel.png',
    'steel_end': 'pv_steel_end.png',
    'plate': 'pv_plate.png',
    'cabinet': 'pv_cabinet.png',
    'cabinet_door': 'pv_cabinet_door.png',
    'cabinet_leaf': 'pv_cabinet_leaf.png',
    'cabinet_top': 'pv_cabinet_top.png',
    'vent': 'pv_vent.png',
    'display': 'pv_display.png',
    'instrument': 'pv_instrument.png',
    'dome': 'pv_dome.png',
    'core': 'dc_core.png',
    'plug_plus': 'dc_connector_plus.png',
    'plug_minus': 'dc_connector_minus.png',
    'combiner_door': 'pv_combiner_door.png',
    'dc_section': 'pv_dc_section.png',
    'blank': 'pv_blank.png',
    'switch': 'pv_switch.png',
    'concrete': 'box_plinth.png',
    'porcelain': 'porcelain_brown.png',
    'warning': 'warning.png',
}

# Which material each face of a laminate carries.
LAMINATE = {'up': 'module', 'down': 'module_back', '*': 'module_edge'}

# One module, in blocks.
MODULE_WIDE = 0.42
MODULE_LONG = 0.94
MODULE_THICK = 0.030

# The tilt a fixed rack is built at, in degrees.
FIXED_TILT_DEG = 25.0


def laminate(mesh, name, lo, hi, across='x', rot=None):
    """One module: cells up, backsheet down, frame on all four edges."""
    clad_box(mesh, name, lo, hi, LAMINATE, uv_rot=0 if across == 'x' else 90, rot=rot)


# ------------------------------------------------------------------ cable, and the contract

# The pair's own figure: two pixels across, one tall, one conductor to a pixel.
# draws cable uses it, so nothing swells at a join.
LEAD = 0.0625
# What a run laid across the ground is, from gen_cable_models.py.
LAID = 2 * LEAD
# Where a row's own lead runs on a fixed mounting: hard against the block's edge, so its outer face
# *is* the boundary.
EDGE = 0.50 - LEAD


# The string cable's own figures, from gen_cable_models.py, in blocks rather than sixteenths.
CORE_RADIUS = 0.65 / 16.0
CORE_OFFSET = 1.05 / 16.0
CORE_Y = CORE_RADIUS
# The four segments of an MC4, as (length, radius, v0, v1) - the same table, and the same texture bands.
MC4 = ((0.80 / 16.0, 0.80 / 16.0, 0.00, 0.12),
       (1.05 / 16.0, 0.85 / 16.0, 0.12, 0.44),
       (2.25 / 16.0, 0.95 / 16.0, 0.44, 0.90),
       (0.70 / 16.0, 0.62 / 16.0, 0.90, 1.00))
MC4_Y = max(radius for _, radius, _, _ in MC4)
# How far short of the middle a machine's stub stops
UNDER = 0.20


def spin_y(point, degrees):
    """A point turned about the block's own vertical axis"""
    angle = math.radians(degrees)
    c, s = math.cos(angle), math.sin(angle)
    return (point[0] * c + point[2] * s, point[1], -point[0] * s + point[2] * c)


def cable_pair(mesh, name, points, turn=0.0, lanes=CORE_OFFSET, radius=CORE_RADIUS):
    """The pair swept along a path, one tube a core - the string cable's own geometry."""
    faces = mesh.faces(name, 'core')
    for lane in (-lanes, lanes):
        path = [spin_y((x + lane, y, z), turn) for x, y, z in points]
        tube(mesh, faces, path, radius, sides=FITTING, uv_scale=1.0, uv_along=1.0, caps=faces)


def tail_rise(z_from, z_to):
    """A pair's last stretch before a plug, lifting from the cable's axis onto the plug's."""
    step = 0.9 / 16.0 * (1.0 if z_to > z_from else -1.0)
    return [(0.0, CORE_Y, z_from), (0.0, MC4_Y, z_from + step), (0.0, MC4_Y, z_to)]


def mc4_pair(mesh, name, at_z, y=None, turn=0.0, into=1.0, lanes=CORE_OFFSET, at_x=0.0):
    """Two MC4 plugs on the end of a pair, red collar on the positive pole."""
    y = MC4_Y if y is None else y
    axis = 'z' if turn % 180 == 0 else 'x'
    for index, lane in enumerate((at_x - lanes, at_x + lanes)):
        faces = mesh.faces(name, 'plug_plus' if index == 0 else 'plug_minus')
        cursor = at_z
        for step, (length, radius, v0, v1) in enumerate(MC4):
            low, high = sorted((cursor, cursor + into * length))
            cylinder(mesh, faces, spin_y((lane, y, (low + high) / 2.0), turn), axis, radius,
                     (high - low) / 2.0, sides=FITTING, uv_scale=1.0, uv=(0.0, v0, 1.0, v1),
                     caps=faces, cap_ends=(1,) if step == len(MC4) - 1 else ())
            cursor += into * length


def stubs(mesh, name='stub'):
    """A run of cable from each of the four block edges in under the machine, one group apiece."""
    for side, turn in (('north', 0.0), ('west', 90.0), ('south', 180.0), ('east', 270.0)):
        group = '%s_%s' % (name, side)
        cable_pair(mesh, group, [(0.0, CORE_Y, -0.5), (0.0, CORE_Y, -UNDER)], turn=turn)
        # the cleat that holds it down where it crosses open ground: the same fitting a laid run has
        faces = mesh.faces(group, 'steel')
        strap, top = CORE_OFFSET + CORE_RADIUS, CORE_Y + CORE_RADIUS
        spin = ((0.0, 0.0, 0.0), 'y', turn)
        for lo, hi in ((-0.115, -strap), (strap, 0.115)):
            box(mesh, faces, (lo, 0.0, -0.425), (hi, top, -0.395), uv_scale=0.2, rot=spin)
        box(mesh, faces, (-0.115, top, -0.425), (0.115, top + 0.026, -0.395), uv_scale=0.3, rot=spin)


def row_lead(mesh, x0, height, start=-0.44):
    """The lead out of one end of a row"""
    y = height + CORE_Y
    cable_pair(mesh, 'harness', [(x0 - CORE_OFFSET, y, start), (x0 - CORE_OFFSET, y, 0.5)])


def row_socket(mesh, x0, height, name='harness_input', end=-1):
    """Where the row behind plugs in: a pair of MC4s on the edge a row is wired on."""
    # The plugs and nothing else: the lead is already there
    # second tube end on the same plane as the lead's own.
    mc4_pair(mesh, name, -0.44 if end < 0 else 0.44, y=height + CORE_Y,
             into=-1.0 if end < 0 else 1.0, lanes=CORE_OFFSET, at_x=x0 - CORE_OFFSET)


def row_entry(mesh, x0, height, end=-1):
    """The corner between a laid run and the edge a row is wired on, and the riser up onto the rack."""
    name = 'harness_entry_north' if end < 0 else 'harness_entry_south'
    edge = -0.5 if end < 0 else 0.5
    corner = -EDGE if end < 0 else EDGE
    faces = mesh.faces(name, 'core')
    for lane in (-CORE_OFFSET, CORE_OFFSET):
        turn_z = corner + (lane if end < 0 else -lane)
        path = [(lane, CORE_Y, edge), (lane, CORE_Y, turn_z), (x0 - CORE_OFFSET + lane, CORE_Y, turn_z)]
        if height > 0.0:
            path.append((x0 - CORE_OFFSET + lane, height + CORE_Y, turn_z))
        tube(mesh, faces, path, CORE_RADIUS, sides=FITTING, uv_scale=1.0, uv_along=1.0, caps=faces)


def row_harness(mesh):
    """A tracked row's harness: a run along the ground down the middle, and nothing else."""
    cable_pair(mesh, 'harness', [(0.0, CORE_Y, -0.5), (0.0, CORE_Y, 0.5)])
    for end, side in ((-1, 'north'), (1, 'south')):
        name = 'harness_plug_%s' % side
        edge = -0.44 if end < 0 else 0.44
        cable_pair(mesh, name, tail_rise(0.0, edge))
        mc4_pair(mesh, name, edge, into=-1.0 if end < 0 else 1.0)


# ------------------------------------------------------------------ the flat table

def flat_table():
    """A ballasted table lying flat: two modules on an aluminium frame on concrete ballast."""
    mesh = Mesh()

    # the ballast: four precast blocks, one under each corner of the frame
    ballast = mesh.faces('ballast', 'concrete')
    for x in (-0.44, 0.30):
        for z in (-0.44, 0.30):
            box(mesh, ballast, (x, 0.0, z), (x + 0.14, 0.045, z + 0.14), uv_scale=0.6)

    frame = mesh.faces('frame', 'frame')
    # the perimeter frame: channel
    for z0, z1 in ((-0.47, -0.41), (0.41, 0.47)):
        channel(mesh, frame, (-0.47, 0.045, z0), (0.47, 0.085, z1), along='x', opening='down',
                uv_scale=0.35)
    for x0, x1 in ((-0.47, -0.41), (0.41, 0.47)):
        channel(mesh, frame, (x0, 0.045, -0.41), (x1, 0.085, 0.41), along='z', opening='down',
                uv_scale=0.35)
    # the two rails the modules are clamped to, across the frame
    for z in (-0.28, 0.22):
        box(mesh, frame, (-0.44, 0.085, z), (0.44, 0.105, z + 0.06), uv_scale=0.3)

    # the modules: two whole tiles, portrait, with the rail gap between them
    top = 0.105 + MODULE_THICK
    for x0 in (-0.44, 0.02):
        laminate(mesh, 'modules', (x0, 0.105, -MODULE_LONG / 2), (x0 + MODULE_WIDE, top, MODULE_LONG / 2))

    # the clamps: a mid clamp between the two modules and an end clamp outside each
    # actually holds a laminate onto a rail
    clamps = mesh.faces('modules', 'frame')
    # inside the rail's own span rather than flush with its edge, so no two faces share a plane
    for z in (-0.265, 0.235):
        box(mesh, clamps, (-0.025, 0.101, z), (0.025, top + 0.004, z + 0.03), uv_scale=0.12)
        bolt(mesh, clamps, (0.0, top + 0.004, z + 0.015), 'y', 0.007, 0.014, uv_scale=0.1)
        for x in (-0.455, 0.435):
            box(mesh, clamps, (x, 0.101, z), (x + 0.02, top + 0.004, z + 0.03), uv_scale=0.12)

    # The harness: a short tail out of the corner and a socket on the far edge, and that is all.
    row_lead(mesh, EDGE, 0.0, start=0.38)
    row_socket(mesh, EDGE, 0.0)
    for end in (-1, 1):
        row_entry(mesh, EDGE, 0.0, end=end)

    return mesh


# ------------------------------------------------------------------ the fixed rack

def tilted_rack():
    """A fixed rack at the mounting's own tilt, tipping towards -z."""
    mesh = Mesh()
    tilt = FIXED_TILT_DEG
    sin_tilt = math.sin(math.radians(tilt))
    cos_tilt = math.cos(math.radians(tilt))

    depth = MODULE_LONG / 2
    purlin_depth = 0.046
    pivot_y = 0.045 + purlin_depth * cos_tilt + depth * sin_tilt
    pivot_point = (0.0, pivot_y, 0.0)
    spin = (pivot_point, 'x', -tilt)
    # the two purlin lines, in the plane's own frame
    purlin_z = (-0.415, 0.415)

    def on_plane(x, y, z):
        return rotate((x, y, z), pivot_point, 'x', -tilt)

    piers = mesh.faces('piers', 'steel')
    plate = mesh.faces('piers', 'plate')
    # two rows of piers, the front pair short and the back pair tall
    # the ground and cut to where its own purlin actually is
    heads = {}
    for x in (-0.38, 0.30):
        for z in purlin_z:
            head = on_plane(x + 0.04, pivot_y - purlin_depth, z)
            heads[(x, z)] = head
            ibeam(mesh, piers, (x, 0.0, head[2] - 0.045), (x + 0.08, head[1] + 0.004, head[2] + 0.045),
                  uv_scale=0.3)
            # the cleat: an angle bolted through the pier
            angle(mesh, piers, (x - 0.006, head[1] - 0.030, head[2] - 0.048),
                  (x + 0.086, head[1] + 0.004, head[2] + 0.048), along='x', web=0.40, uv_scale=0.2)
            bolt(mesh, plate, (x + 0.04, head[1] - 0.014, head[2] - 0.050), 'z', 0.007, 0.016,
                 uv_scale=0.15)

    # the diagonal brace between the two rows
    # back one, which is what stops a rack racking
    for x in (-0.38, 0.30):
        front = heads[(x, purlin_z[0])]
        back = heads[(x, purlin_z[1])]
        strut(mesh, piers, (x + 0.04, 0.055, front[2] + 0.045),
              (x + 0.04, back[1] - 0.050, back[2] - 0.040), 0.013, 0.010, uv_scale=0.25)

    # The two purlins across the piers, in the plane's frame
    # tools/check_hitboxes.py cuts a shape from each object it finds, and two purlins at different
    for index, z in enumerate(purlin_z):
        channel(mesh, mesh.faces('purlin_%d' % index, 'steel'),
                (-0.47, pivot_y - purlin_depth, z - 0.030), (0.47, pivot_y, z + 0.030),
                along='x', opening='down', uv_scale=0.3, web=0.26, rot=spin)

    rails = mesh.faces('rails', 'frame')
    # the rails along the slope, one under each module edge and one either side of the middle joint
    rail_y = pivot_y
    for x in (-0.455, -0.045, 0.015, 0.425):
        box(mesh, rails, (x, rail_y, -depth), (x + 0.030, rail_y + 0.022, depth), uv_scale=0.25,
            rot=spin)

    glass = rail_y + 0.022
    top = glass + MODULE_THICK
    for x0 in (-0.44, 0.02):
        laminate(mesh, 'modules', (x0, glass, -depth), (x0 + MODULE_WIDE, top, depth), rot=spin)

    # the clamps along the rails, three a side
    clamps = mesh.faces('modules', 'frame')
    for z in (-0.36, 0.0, 0.36):
        box(mesh, clamps, (-0.030, glass - 0.004, z - 0.018), (0.030, top + 0.004, z + 0.018),
            uv_scale=0.1, rot=spin)
        for x in (-0.462, 0.442):
            box(mesh, clamps, (x, glass - 0.004, z - 0.018), (x + 0.020, top + 0.004, z + 0.018),
                uv_scale=0.1, rot=spin)

    # The harness: the same pair as a table's, on the ground rather than on the modules.
    # stands off the ground on piers
    row_lead(mesh, EDGE, 0.0)
    row_socket(mesh, EDGE, 0.0)
    for end in (-1, 1):
        row_entry(mesh, EDGE, 0.0, end=end)

    return mesh


# ------------------------------------------------------------------ the single-axis tracker

# Half-length of the drive bay at the centre of a tracked row, in blocks.
DRIVE_BAY = 0.055


def single_axis():
    """A 1P horizontal tracker: one module in portrait across a torque tube, on driven piers."""
    mesh = Mesh()
    axis_y = 0.62
    bay = DRIVE_BAY

    pier = mesh.faces('pier', 'steel')
    plate = mesh.faces('pier', 'plate')
    # one central pier rather than a pair
    # swept disc: within one block a real row has a pier every six metres
    ibeam(mesh, pier, (-0.075, 0.0, -bay + 0.008), (0.075, axis_y - 0.105, bay - 0.008),
          uv_scale=0.3)
    box(mesh, plate, (-0.13, 0.0, -bay), (0.13, 0.028, bay), uv_scale=0.5)
    for x in (-0.105, 0.075):
        bolt(mesh, plate, (x + 0.015, 0.028, 0.0), 'y', 0.009, 0.020, uv_scale=0.15)

    # The slew drive: a housing round the tube, wholly inside the bay, with the motor off its side.
    clad_box(mesh, 'motor', (-0.105, axis_y - 0.105, -bay + 0.002), (0.105, axis_y + 0.105, bay - 0.002),
             {'up': 'cabinet_top', '*': 'cabinet'}, uv_scale=1.0)
    motor = mesh.faces('motor', 'steel')
    cylinder(mesh, motor, (0.155, axis_y, 0.0), 'x', 0.042, 0.052, sides=FITTING, uv_scale=0.4,
             caps=mesh.faces('motor', 'steel_end'))
    # the fins on the motor, which is how a geared motor sheds its heat
    for i in range(5):
        box(mesh, motor, (0.108 + i * 0.020, axis_y - 0.048, -0.048),
            (0.114 + i * 0.020, axis_y + 0.048, 0.048), uv_scale=0.12)

    pivot(mesh, 'tube', (0.0, axis_y, 0.0))

    tube = mesh.faces('rotate_tube', 'steel')
    # eighty sides: the tube is the one part of a tracker a player walks up to
    # block radius it is nearly a metre across in this mod's scale
    cylinder(mesh, tube, (0.0, axis_y, 0.0), 'z', 0.045, 0.49, sides=ROUND, uv_scale=1.0,
             uv_along=6.0, caps=mesh.faces('rotate_tube', 'steel_end'))

    # The module rails, across the tube, which is what a 1P row is clamped to - and the modules
    glass = axis_y + 0.062
    for index, (z0, rails_z) in enumerate(((-0.48, (-0.44, -0.10)), (bay, (0.10, 0.44)))):
        rails = mesh.faces('rotate_rails_%d' % index, 'frame')
        for z in rails_z:
            box(mesh, rails, (-0.46, axis_y + 0.040, z - 0.022), (0.46, glass, z + 0.022),
                uv_scale=0.25)
        # the saddle that clamps this bay's rails to the tube
        clad_box(mesh, 'rotate_rails_%d' % index, (-0.070, axis_y + 0.030, sum(rails_z) / 2 - 0.028),
                 (0.070, axis_y + 0.055, sum(rails_z) / 2 + 0.028), {'*': 'steel'}, uv_scale=0.2)

        laminate(mesh, 'rotate_modules_%d' % index, (-MODULE_LONG / 2, glass, z0),
                 (MODULE_LONG / 2, glass + MODULE_THICK, z0 + MODULE_WIDE), across='z')
        # the clamps, dropped a whisker below the glass so they are not coplanar with its underside
        clamps = mesh.faces('rotate_modules_%d' % index, 'frame')
        for z in (z0 + 0.004, z0 + MODULE_WIDE - 0.028):
            for x in (-0.44, 0.41):
                box(mesh, clamps, (x, glass - 0.004, z), (x + 0.03, glass + MODULE_THICK + 0.004,
                                                          z + 0.024), uv_scale=0.1)

    row_harness(mesh)
    return mesh


# ------------------------------------------------------------------ the dual-axis pedestal

def dual_axis():
    """A pedestal, an azimuth collar, and an elevation frame carrying two modules."""
    mesh = Mesh()
    top = 0.55
    bay = 0.066
    # the elevation axis, and the height the renderer measures back off the rotating groups' own
    # bounding box - so the two cannot disagree about where the pivot is
    pivot_y = top + 0.1975

    pedestal = mesh.faces('pedestal', 'steel')
    plate = mesh.faces('pedestal', 'plate')
    # the base plate at 0.05, and it is the harness that decides that: the run crosses the plate at
    # ground level, and lower than the cable's own top read as a run broken where it crossed
    box(mesh, plate, (-0.19, 0.0, -0.19), (0.19, 0.05, 0.19), uv_scale=0.7)
    for x in (-0.155, 0.115):
        for z in (-0.155, 0.115):
            bolt(mesh, plate, (x + 0.02, 0.05, z + 0.02), 'y', 0.010, 0.022, uv_scale=0.15)
    # the column, tapering the way a spun steel pedestal does, and its gusset ribs
    cylinder(mesh, pedestal, (0.0, top / 2.0 + 0.025, 0.0), 'y', 0.050, top / 2.0 - 0.025,
             sides=ROUND, uv_scale=1.0, uv_along=3.0, taper=0.80,
             caps=mesh.faces('pedestal', 'steel_end'), cap_ends=(1,))
    # the gusset ribs, their feet a whisker inside the base plate rather than exactly on its face
    for turn in (0.0, 90.0, 180.0, 270.0):
        box(mesh, pedestal, (0.044, 0.046, -0.012), (0.115, 0.20, 0.012), uv_scale=0.2,
            rot=((0.0, 0.0, 0.0), 'y', turn))

    pivot(mesh, 'azimuth', (0.0, top + 0.05, 0.0))
    pivot(mesh, 'elevation', (0.0, pivot_y, 0.0))

    azimuth = mesh.faces('rotate_azimuth', 'cabinet')
    # the slew ring: a collar on the column, with the drive worm on its flank
    cylinder(mesh, azimuth, (0.0, top + 0.05, 0.0), 'y', 0.058, 0.05, sides=ROUND, uv_scale=1.0,
             caps=mesh.faces('rotate_azimuth', 'cabinet_top'), cap_ends=(1,))
    cylinder(mesh, mesh.faces('rotate_azimuth', 'steel'), (0.0, top + 0.02, 0.0), 'y', 0.062, 0.018,
             sides=ROUND, uv_scale=0.3)
    box(mesh, azimuth, (-0.030, top + 0.052, -0.062), (0.030, top + 0.094, -0.030), uv_scale=0.2)
    # the elevation bearing housing, in the gap the modules leave down the middle
    clad_box(mesh, 'rotate_azimuth', (-0.058, top + 0.082, -bay + 0.006),
             (0.058, pivot_y + 0.016, bay - 0.006), {'up': 'cabinet_top', '*': 'cabinet'},
             uv_scale=0.5)
    # and the worm box that tips the frame, off the bearing's side and wholly inside the bay
    clad_box(mesh, 'rotate_azimuth', (0.058, pivot_y - 0.034, -0.052), (0.125, pivot_y + 0.030, 0.052),
             {'up': 'cabinet_top', '*': 'cabinet'}, uv_scale=0.4)

    elevation = mesh.faces('rotate_elevation', 'steel')
    # the frame's torque tube, through the bearing and out to both module bays
    cylinder(mesh, elevation, (0.0, pivot_y, 0.0), 'z', 0.030, 0.44, sides=ROUND, uv_scale=0.4,
             uv_along=5.0, caps=mesh.faces('rotate_elevation', 'steel_end'))
    glass = pivot_y + 0.032
    # a group per bay, for the same reason the tracked row has one: everything named rotate_elevation
    for index, (z0, rails_z) in enumerate(((-0.486, (-0.45, -0.10)), (bay, (0.10, 0.45)))):
        frame = mesh.faces('rotate_elevation_%d' % index, 'frame')
        for z in rails_z:
            box(mesh, frame, (-0.46, pivot_y + 0.008, z - 0.020), (0.46, glass, z + 0.020),
                uv_scale=0.25)
        box(mesh, mesh.faces('rotate_elevation_%d' % index, 'steel'),
            (-0.055, pivot_y - 0.004, sum(rails_z) / 2 - 0.024),
            (0.055, pivot_y + 0.024, sum(rails_z) / 2 + 0.024), uv_scale=0.2)

        laminate(mesh, 'rotate_elevation_modules_%d' % index, (-MODULE_LONG / 2, glass, z0),
                 (MODULE_LONG / 2, glass + MODULE_THICK, z0 + MODULE_WIDE), across='z')
        clamps = mesh.faces('rotate_elevation_modules_%d' % index, 'frame')
        for z in (z0 + 0.004, z0 + MODULE_WIDE - 0.028):
            for x in (-0.44, 0.41):
                box(mesh, clamps, (x, glass - 0.004, z), (x + 0.03, glass + MODULE_THICK + 0.004,
                                                          z + 0.024), uv_scale=0.1)

    # The harness: the run along the ground and nothing else, the same as a single-axis row.
    row_harness(mesh)
    return mesh


# ------------------------------------------------------------------ the inverter

# The inverter's front, as one set of figures: the doors, the window in the left leaf, and the band
DOOR_LOW, DOOR_HIGH = 0.440, 0.940
DISPLAY_LOW, DISPLAY_HIGH = 0.585, 0.735


def inverter():
    """A station inverter: a cabinet on a plinth, double doors, a rain hood, roof fans."""
    mesh = Mesh()
    body_y = 0.98
    face = -0.30
    back = 0.28

    # the plinth: a channel base, wider than the cabinet
    clad_box(mesh, 'plinth', (-0.465, 0.0, -0.325), (0.465, 0.055, 0.305),
             {'up': 'plate', '*': 'concrete'}, uv_scale=0.7)

    clad_box(mesh, 'cabinet', (-0.44, 0.055, face), (0.44, body_y, back),
             {'up': 'cabinet_top', '*': 'cabinet'})
    # the corner posts, which is what a sheet-steel cabinet is actually built round
    for x in (-0.45, 0.44):
        for z in (face - 0.01, back):
            box(mesh, mesh.faces('cabinet', 'frame'), (x, 0.055, z), (x + 0.01, body_y, z + 0.01),
                uv_scale=0.1)

    # the rain hood: crowned, overhanging on all four sides
    clad_box(mesh, 'hood', (-0.47, body_y, face - 0.03), (0.47, body_y + 0.035, back + 0.03),
             {'up': 'cabinet_top', '*': 'cabinet'}, uv_scale=0.8)
    box(mesh, mesh.faces('hood', 'cabinet'), (-0.47, body_y - 0.018, face - 0.03),
        (0.47, body_y + 0.006, face - 0.018), uv_scale=0.3)
    # the lifting eyebolts, at the roof's four corners - clear of both fan guards and of the insulator
    for x in (-0.435, 0.435):
        for z in (face + 0.045, back + 0.005):
            eyebolt(mesh, mesh.faces('hood', 'steel'), (x, body_y + 0.035, z), 0.018)

    # doors: upper half only, so the DC compartment below them is not a box bolted over them
    for side, x0, x1 in ((-1, -0.405, -0.010), (1, 0.010, 0.405)):
        clad_box(mesh, 'door', (x0, DOOR_LOW, face - 0.035), (x1, DOOR_HIGH, face),
                 {'north': 'cabinet_door' if side < 0 else 'cabinet_leaf', '*': 'cabinet'})
        handle_x = x1 - 0.055 if side < 0 else x0 + 0.030
        box(mesh, mesh.faces('door', 'frame'), (handle_x, 0.60, face - 0.062),
            (handle_x + 0.025, 0.76, face - 0.035), uv_scale=0.12)
        # hinges on the outer edge
        hinge_x = x0 if side < 0 else x1 - 0.012
        for y in (DOOR_LOW + 0.05, DOOR_HIGH - 0.10):
            box(mesh, mesh.faces('door', 'steel'), (hinge_x, y, face - 0.048),
                (hinge_x + 0.012, y + 0.055, face - 0.035), uv_scale=0.1)
    box(mesh, mesh.faces('cabinet', 'frame'), (-0.012, DOOR_LOW, face - 0.036),
        (0.012, DOOR_HIGH, face - 0.030), uv_scale=0.1)

    # the screen, on the left leaf between its plate and its louvre bank - the door texture is drawn
    # round this window, so DISPLAY_* and pv_cabinet_door have to move together
    clad_box(mesh, 'display', (-0.330, DISPLAY_LOW, face - 0.048), (-0.060, DISPLAY_HIGH, face - 0.036),
             {'north': 'display', '*': 'cabinet'})

    # The roof fans: two guarded impellers on an upstand
    roof = body_y + 0.035
    for x in (-0.22, 0.22):
        pivot(mesh, 'fan_%s' % ('west' if x < 0 else 'east'), (x, roof + 0.010, -0.01))
        guard = mesh.faces('fan_guard', 'steel')
        # the upstand the assembly is bolted into
        cylinder(mesh, guard, (x, roof + 0.005, -0.01), 'y', 0.112, 0.005, sides=ROUND, uv_scale=0.4)
        cylinder(mesh, guard, (x, roof + 0.013, -0.01), 'y', 0.104, 0.006, sides=ROUND, uv_scale=0.4)
        # the finger guard: three concentric rings on eight spokes, in wire rather than in bar
        for radius in (0.036, 0.064, 0.092):
            ring = arc((x, roof + 0.026, -0.01), radius, (0, 2), 0.0, 360.0, FITTING)
            tube(mesh, guard, ring + [ring[0]], 0.0035, sides=8, uv_scale=1.0)
        for i in range(8):
            angle = math.radians(i * 45.0)
            tube(mesh, guard, [(x + math.cos(angle) * 0.016, roof + 0.026,
                                -0.01 + math.sin(angle) * 0.016),
                               (x + math.cos(angle) * 0.100, roof + 0.026,
                                -0.01 + math.sin(angle) * 0.100)], 0.0035, sides=8, uv_scale=1.0)
        # the four bolts that hold the upstand down
        for i in range(4):
            angle = math.radians(45.0 + i * 90.0)
            bolt(mesh, guard, (x + math.cos(angle) * 0.106, roof + 0.010,
                               -0.01 + math.sin(angle) * 0.106), 'y', 0.006, 0.010, uv_scale=0.08)

    for x, name in ((-0.22, 'rotate_fan_west'), (0.22, 'rotate_fan_east')):
        fan = mesh.faces(name, 'steel')
        hub = (x, roof + 0.012, -0.01)
        # the hub: the motor's own can
        cylinder(mesh, fan, (x, roof + 0.008, -0.01), 'y', 0.026, 0.008, sides=FITTING,
                 uv_scale=0.2, caps=fan)
        cylinder(mesh, fan, (x, roof + 0.018, -0.01), 'y', 0.020, 0.004, sides=FITTING,
                 uv_scale=0.2, taper=0.7, caps=fan, cap_ends=(1,))
        # seven blades, each pitched thirty degrees about its own radius and then carried round the hub
        for i in range(7):
            box(mesh, fan, (x + 0.022, roof + 0.008, -0.038), (x + 0.098, roof + 0.014, 0.018),
                uv_scale=0.2,
                rot=(((x + 0.060, roof + 0.011, -0.01), 'x', 30.0),
                     (hub, 'y', i * 360.0 / 7.0)))

    # the side louvre banks, on the outside of the cabinet's own face rather than inside it
    for x, side in ((0.44, 'east'), (-0.4425, 'west')):
        clad_box(mesh, 'grille', (x, 0.30, -0.24), (x + 0.0025, 0.86, 0.22),
                 {side: 'vent', '*': 'cabinet'})

    # the wire fitting: on the roof at the east end, clear of both fans and both eyebolts.
    # clicks this, so ObjDefinitions names the group and WireManager stores wires by its index.
    steel = mesh.faces('hardware', 'steel')
    box(mesh, steel, (0.356, body_y + 0.002, 0.096), (0.444, body_y + 0.018, 0.184), uv_scale=0.2)
    pin_insulator(mesh, mesh.faces('insulator', 'porcelain'), steel,
                  (0.40, body_y + 0.050, 0.14), 0.108)

    # The lower front, in two mutually exclusive groups the renderer picks between: 'section' is the DC
    clad_box(mesh, 'section', (-0.360, 0.100, face - 0.072), (0.360, 0.415, face - 0.035),
             {'north': 'dc_section', 'up': 'frame', '*': 'cabinet'})
    glands = mesh.faces('section', 'steel')
    for i in range(5):
        cylinder(mesh, glands, (-0.22 + i * 0.11, 0.076, face - 0.054), 'y', 0.016, 0.024,
                 sides=FITTING, uv_scale=0.2, caps=glands)

    clad_box(mesh, 'blank', (-0.405, 0.098, face - 0.035), (0.405, 0.425, face),
             {'north': 'blank', '*': 'cabinet'})

    # where the direct current comes in, one run per side
    stubs(mesh, 'entry')
    return mesh


# ------------------------------------------------------------------ the combiner box

def combiner():
    """A string combiner on a post: an enclosure, a hood, a window"""
    mesh = Mesh()
    box_y0, box_y1 = 0.38, 0.78

    post = mesh.faces('post', 'steel')
    plate = mesh.faces('post', 'plate')
    # the footing and the post, because a field combiner stands on one rather than lying on the ground
    box(mesh, plate, (-0.095, 0.0, -0.075), (0.095, 0.030, 0.075), uv_scale=0.5)
    for x in (-0.070, 0.048):
        bolt(mesh, plate, (x + 0.011, 0.030, 0.0), 'y', 0.007, 0.015, uv_scale=0.12)
    cylinder(mesh, post, (0.0, 0.215, 0.0), 'y', 0.034, 0.185, sides=ROUND, uv_scale=0.4,
             uv_along=3.0, caps=mesh.faces('post', 'steel_end'), cap_ends=(1,))
    # the two brackets between the post and the box, which is how an enclosure is actually hung
    for y in (box_y0 + 0.06, box_y1 - 0.10):
        angle(mesh, post, (-0.030, y, 0.030), (0.030, y + 0.030, 0.105), along='x', web=0.35,
              uv_scale=0.15)

    # the enclosure: a lid that is a lid, and side sheet everywhere else
    clad_box(mesh, 'enclosure', (-0.215, box_y0, -0.095), (0.215, box_y1, 0.095),
             {'up': 'cabinet_top', '*': 'cabinet'})
    # the rain hood over it, overhanging the door so water does not run down the window
    clad_box(mesh, 'enclosure', (-0.235, box_y1, -0.125), (0.235, box_y1 + 0.020, 0.100),
             {'up': 'cabinet_top', '*': 'cabinet'}, uv_scale=0.5)

    # the door, with the fuse window and the rating label on the one face anybody stands at
    clad_box(mesh, 'door', (-0.185, box_y0 + 0.025, -0.112), (0.185, box_y1 - 0.025, -0.095),
             {'north': 'combiner_door', '*': 'frame'})
    for y in (box_y0 + 0.06, box_y1 - 0.09):
        box(mesh, mesh.faces('door', 'steel'), (0.185, y, -0.110), (0.198, y + 0.040, -0.096),
            uv_scale=0.1)

    # the gland plate underneath, where every string arrives: one row of them
    # underside of a real box looks like and the only view that says how many ways it has
    clad_box(mesh, 'glands', (-0.195, box_y0 - 0.025, -0.070), (0.195, box_y0, 0.070),
             {'down': 'plate', '*': 'steel'}, uv_scale=0.4)
    glands = mesh.faces('glands', 'steel')
    for i in range(6):
        x = -0.155 + i * 0.062
        cylinder(mesh, glands, (x, box_y0 - 0.050, 0.0), 'y', 0.014, 0.026, sides=FITTING,
                 uv_scale=0.2, caps=glands, cap_ends=(-1,))

    # and the one heavy gland the trunk leaves through, on the end
    trunk = mesh.faces('glands', 'steel_end')
    cylinder(mesh, glands, (0.235, box_y0 + 0.075, 0.0), 'x', 0.030, 0.024, sides=FITTING,
             uv_scale=0.3, caps=trunk)

    pivot(mesh, 'handle', (0.145, box_y0 + 0.115, -0.112))

    # where the strings come in and the trunk leaves, one run per side of the block, and the riser
    # that carries them up the post into the glands
    stubs(mesh, 'entry')
    # the riser up the post, at exactly the cross-section of the run it continues
    riser = mesh.faces('post', 'core')
    for lane in (-CORE_OFFSET, CORE_OFFSET):
        tube(mesh, riser, [(lane, CORE_Y, UNDER - 0.02), (lane, CORE_Y, 0.04),
                           (lane, box_y0 + 0.015, 0.04)], CORE_RADIUS, sides=FITTING,
             uv_scale=1.0, uv_along=1.0, caps=riser)
    for y in (0.22, 0.34):
        box(mesh, post, (-0.075, y, 0.055), (0.075, y + 0.016, 0.068), uv_scale=0.12)

    # the handle: a boss off the door with a bar on it, drawn once and turned by the renderer
    handle = mesh.faces('rotate_handle', 'switch')
    cylinder(mesh, handle, (0.145, box_y0 + 0.115, -0.126), 'z', 0.018, 0.014, sides=FITTING,
             uv_scale=1.0, caps=handle)
    box(mesh, handle, (0.130, box_y0 + 0.115, -0.142), (0.160, box_y0 + 0.195, -0.126), uv_scale=0.3)
    return mesh


# ------------------------------------------------------------------ the met station

def met_mast():
    """A ten-metre mast with nine instruments"""
    mesh = Mesh()

    wind_y = 1.0
    radiometer_y = 0.35
    screen_y = 0.20
    snow_y = 0.20

    mast = mesh.faces('mast', 'steel')
    ends = mesh.faces('mast', 'steel_end')
    plate = mesh.faces('mast', 'plate')
    # Two sections with a coupling, tapering: the lower one heavier than the upper.
    cylinder(mesh, mast, (0.0, 0.28, 0.0), 'y', 0.038, 0.24, sides=ROUND, uv_scale=1.0,
             uv_along=3.0, taper=0.86)
    cylinder(mesh, mast, (0.0, 0.30, 0.0), 'y', 0.043, 0.022, sides=ROUND, uv_scale=0.3)
    cylinder(mesh, mast, (0.0, 0.66, 0.0), 'y', 0.032, 0.34, sides=ROUND, uv_scale=1.0,
             uv_along=4.0, taper=0.84, caps=ends, cap_ends=(1,))
    # the base: a plate with anchor bolts and the hinge a mast is lowered on
    box(mesh, plate, (-0.115, 0.0, -0.115), (0.115, 0.032, 0.115), uv_scale=0.6)
    for x in (-0.092, 0.062):
        for z in (-0.092, 0.062):
            bolt(mesh, plate, (x + 0.015, 0.032, z + 0.015), 'y', 0.008, 0.020, uv_scale=0.12)
    for z in (-0.055, 0.040):
        box(mesh, mast, (-0.016, 0.032, z), (0.016, 0.075, z + 0.015), uv_scale=0.12)
    # the finial: a mast this exposed carries a lightning rod above every instrument on it
    cylinder(mesh, mast, (0.0, 1.055, 0.0), 'y', 0.008, 0.045, sides=FITTING, uv_scale=0.2,
             taper=0.35, caps=ends, cap_ends=(1,))

    # the logger: a small enclosure strapped to the mast at chest height
    clad_box(mesh, 'logger', (-0.062, 0.10, -0.105), (0.062, 0.20, -0.038),
             {'up': 'cabinet_top', '*': 'cabinet'}, uv_scale=1.0)
    for y in (0.115, 0.185):
        box(mesh, mesh.faces('logger', 'steel'), (-0.048, y, -0.040), (0.048, y + 0.012, 0.040),
            uv_scale=0.12)

    boom = mesh.faces('boom', 'steel')
    # the radiometer boom, with the clamp that holds it and a stay under its outer end
    box(mesh, boom, (-0.018, radiometer_y - 0.018, 0.030), (0.018, radiometer_y + 0.018, 0.42),
        uv_scale=0.3)
    cylinder(mesh, boom, (0.0, radiometer_y, 0.0), 'y', 0.048, 0.030, sides=FITTING, uv_scale=0.2,
             caps=boom)
    box(mesh, boom, (-0.010, radiometer_y - 0.115, 0.155), (0.010, radiometer_y - 0.014, 0.175),
        uv_scale=0.2, rot=((0.0, radiometer_y - 0.014, 0.165), 'x', 34.0))
    # the screen boom
    box(mesh, boom, (-0.016, screen_y - 0.016, 0.030), (0.016, screen_y + 0.016, 0.30), uv_scale=0.3)
    cylinder(mesh, boom, (0.0, screen_y, 0.0), 'y', 0.044, 0.026, sides=FITTING, uv_scale=0.2,
             caps=boom)

    def radiometer(name, z, radius, dome_radius, ring=False, downward=False):
        """One radiometer: a machined body on three levelling feet, under a glass dome."""
        body = mesh.faces(name, 'instrument')
        glass = mesh.faces(name, 'dome')
        cylinder(mesh, body, (0.0, radiometer_y + 0.030, z), 'y', radius, 0.018, sides=FITTING,
                 uv_scale=0.4, caps=body)
        # The sun shield: the white disc every pyranometer wears round its dome.
        cylinder(mesh, body, (0.0, radiometer_y + 0.046, z), 'y', radius * 1.15, 0.004,
                 sides=FITTING, uv_scale=0.5, caps=body)
        hemisphere(mesh, glass, (0.0, radiometer_y + 0.050, z), dome_radius, sides=FITTING, rings=5,
                   squash=0.85, uv_scale=1.0)
        # the three levelling feet, at *this* instrument's own z: written at zero and turned about the
        # instrument's centre, all three radiometers' feet landed in a heap at the root of the boom
        for turn in (0.0, 120.0, 240.0):
            box(mesh, body, (radius * 0.55, radiometer_y + 0.010, z - 0.008),
                (radius * 0.85, radiometer_y + 0.012, z + 0.008), uv_scale=0.1,
                rot=((0.0, 0.0, z), 'y', turn))
        if ring:
            # the shadow ring, which is what makes it a diffuse instrument at all
            cylinder(mesh, mesh.faces(name, 'steel'), (0.0, radiometer_y + 0.058, z), 'x', 0.070,
                     0.006, sides=FITTING, uv_scale=0.2)
        if downward:
            # an albedometer is two pyranometers
            hemisphere(mesh, glass, (0.0, radiometer_y + 0.010, z), dome_radius, sides=FITTING,
                       rings=5, up=-1, squash=0.85, uv_scale=1.0)

    radiometer('pyranometer', 0.155, 0.050, 0.030)
    radiometer('diffuse', 0.295, 0.050, 0.028, ring=True)
    radiometer('albedometer', 0.425, 0.044, 0.024, downward=True)

    shield = mesh.faces('shield', 'instrument')
    # a naturally aspirated radiation shield: a stack of plates on three tie rods
    # shallow cone so rain runs off it
    for i in range(6):
        y = screen_y + i * 0.018
        cylinder(mesh, shield, (0.0, y, 0.300), 'y', 0.058 - i * 0.001, 0.005, sides=FITTING,
                 uv_scale=0.4, taper=0.86, caps=shield)
    for turn in (30.0, 150.0, 270.0):
        box(mesh, shield, (0.040, screen_y - 0.004, 0.296), (0.048, screen_y + 0.096, 0.304),
            uv_scale=0.1, rot=((0.0, 0.0, 0.300), 'y', turn))
    # the sensor itself, hanging under the stack in the dark
    cylinder(mesh, mesh.faces('shield', 'steel'), (0.0, screen_y - 0.014, 0.300), 'y', 0.008, 0.014,
             sides=FITTING, uv_scale=0.2, caps=mesh.faces('shield', 'steel'))

    # the snow gauge looks straight down, so its underside is the transducer and not paint
    clad_box(mesh, 'snow', (-0.030, snow_y, -0.340), (0.030, snow_y + 0.055, -0.280),
             {'down': 'dome', 'up': 'cabinet_top', '*': 'cabinet'}, uv_scale=0.3)
    snow = mesh.faces('snow', 'steel')
    box(mesh, snow, (-0.014, snow_y + 0.018, -0.285), (0.014, snow_y + 0.036, -0.030), uv_scale=0.3)
    cylinder(mesh, snow, (0.0, snow_y + 0.027, -0.030), 'y', 0.040, 0.022, sides=FITTING,
             uv_scale=0.2, caps=snow)

    pivot(mesh, 'cups', (0.0, wind_y, 0.0))
    pivot(mesh, 'vane', (0.0, 0.885, 0.0))

    # the anemometer: a body on the mast top, three cups on arms, and the cups are open upwards
    body = mesh.faces('anemometer', 'instrument')
    cylinder(mesh, body, (0.0, 0.955, 0.0), 'y', 0.026, 0.030, sides=FITTING, uv_scale=0.3, caps=body)

    cups = mesh.faces('rotate_cups', 'instrument')
    cylinder(mesh, cups, (0.0, wind_y - 0.012, 0.0), 'y', 0.016, 0.026, sides=FITTING,
             uv_scale=0.2, caps=cups)
    for i in range(3):
        turn = i * 120.0
        # A cup is a hemisphere open at the top, which is the one face of the whole mast a player
        hub = (0.0, wind_y, 0.0)
        centre = rotate((0.135, wind_y + 0.008, 0.0), hub, 'y', turn)
        hemisphere(mesh, cups, centre, 0.034, sides=FITTING, rings=4, up=-1, squash=0.9,
                   uv_scale=1.0)
        cylinder(mesh, mesh.faces('rotate_cups', 'dome'), centre, 'y', 0.034, 0.002, sides=FITTING,
                 uv_scale=0.3, caps=mesh.faces('rotate_cups', 'dome'))
        box(mesh, cups, (0.020, wind_y + 0.002, -0.007), (0.140, wind_y + 0.014, 0.007),
            uv_scale=0.2, rot=(hub, 'y', turn))

    vane = mesh.faces('rotate_vane', 'instrument')
    # the wind vane: a counterweighted nose, a boom, and a tail fin
    cylinder(mesh, vane, (0.0, 0.885, 0.0), 'y', 0.020, 0.022, sides=FITTING, uv_scale=0.2, caps=vane)
    box(mesh, vane, (-0.008, 0.878, -0.020), (0.008, 0.892, 0.230), uv_scale=0.3)
    box(mesh, mesh.faces('rotate_vane', 'frame'), (-0.004, 0.855, 0.140), (0.004, 0.930, 0.245),
        uv_scale=0.3)
    cylinder(mesh, vane, (0.0, 0.885, -0.055), 'z', 0.018, 0.020, sides=FITTING, uv_scale=0.2,
             caps=vane, taper=0.7)
    return mesh


LAMINATE_MATERIALS = ('module', 'module_back', 'module_edge')
HARNESS_MATERIALS = ('cabinet', 'cabinet_top', 'core', 'plug_plus', 'plug_minus')
STRUCTURE = ('steel', 'steel_end', 'plate', 'frame')

MODELS = [
    ('pv_flat', flat_table, STRUCTURE + ('concrete',) + HARNESS_MATERIALS + LAMINATE_MATERIALS),
    ('pv_tilt', tilted_rack, STRUCTURE + HARNESS_MATERIALS + LAMINATE_MATERIALS),
    ('pv_track', single_axis, STRUCTURE + HARNESS_MATERIALS + LAMINATE_MATERIALS),
    ('pv_dual', dual_axis, STRUCTURE + HARNESS_MATERIALS + LAMINATE_MATERIALS),
    ('pv_inverter', inverter, STRUCTURE + ('cabinet', 'cabinet_door', 'cabinet_leaf', 'cabinet_top', 'vent',
                                           'display', 'instrument', 'porcelain', 'core', 'plug_plus',
                                           'plug_minus', 'dc_section', 'blank', 'concrete')),
    ('pv_combiner', combiner, STRUCTURE + ('cabinet', 'cabinet_top', 'combiner_door', 'switch',
                                           'core', 'plug_plus', 'plug_minus')),
    ('met_mast', met_mast, STRUCTURE + ('instrument', 'dome', 'cabinet', 'cabinet_top',
                                        'cabinet_door')),
]


def main():
    for name, builder, materials in MODELS:
        mesh = builder()
        directory = os.path.join(OUT, name)
        mesh.write(os.path.join(directory, name + '.obj'), name + '.mtl', 'gen_pv_models.py')
        write_mtl(os.path.join(directory, name + '.mtl'), materials, MATERIALS, 'gen_pv_models.py')
        vertices, faces = mesh.stats()
        groups = ['%s_%s' % (obj, material) for obj, material, faces_list in mesh.objects if faces_list]
        print('%-12s %4d vertices, %4d faces, %2d groups' % (name, vertices, faces, len(groups)))
        print('             %s' % ', '.join(groups))


if __name__ == '__main__':
    main()
