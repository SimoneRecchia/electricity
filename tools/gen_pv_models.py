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

from modellib import (FITTING, HEX, MC4_RADIUS, MC4_SPREAD, ROUND, Mesh, angle, arc, bolt, box,
                      channel, clad_box, cylinder, eyebolt, hemisphere, ibeam, mc4, pin_insulator,
                      pivot, rotate, square_uv, strut, tube, write_mtl)

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
    # the laid run's own fittings: a row's joints are the same products as a run's, so they are the same
    # textures, and a moulded box under a table is the box a player laid outside it
    'jbox': 'dc_jbox.png',
    'jbox_side': 'dc_jbox_side.png',
    'gland': 'dc_gland.png',
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
# The plug's own profile is modellib.MC4, shared with gen_cable_models: a plug lies on its fattest part,
# and a pair of them splays to MC4_SPREAD because two coupling rings will not sit at the cable's spacing.
MC4_Y = MC4_RADIUS / 16.0
MC4_LANE = MC4_SPREAD / 16.0
# How far back from the edge the pair leaves its own lane to climb onto the plugs.  It has to stay inside
# JOINT, or the climb starts further in than the cable feeding it does and the two run through each other.
SOCKET_RISE = 0.16
# How far short of the middle a machine's stub stops
UNDER = 0.20


def spin_y(point, degrees):
    """A point turned about the block's own vertical axis"""
    angle = math.radians(degrees)
    c, s = math.cos(angle), math.sin(angle)
    return (point[0] * c + point[2] * s, point[1], -point[0] * s + point[2] * c)


def cable_pair(mesh, name, points, turn=0.0, lanes=CORE_OFFSET, radius=CORE_RADIUS,
               ends=(True, True)):
    """The pair swept along a path, one tube a core - the string cable's own geometry.

    ``ends`` is which ends are capped, and only a *free* end is: an end at the block's own boundary is
    carried on by the laid run's arm, and two caps in one plane fight each other.
    """
    faces = mesh.faces(name, 'core')
    cap_ends = tuple(end for end, wanted in ((-1, ends[0]), (1, ends[1])) if wanted)
    for lane in (-lanes, lanes):
        path = [spin_y((x + lane, y, z), turn) for x, y, z in points]
        tube(mesh, faces, path, radius, sides=FITTING, uv_scale=1.0, uv_along=1.0,
             caps=faces if cap_ends else None, cap_ends=cap_ends)


def tail_rise(z_from, z_to, base=0.0):
    """A core's last stretch before its plug: out onto the plug's lane and up onto its axis.

    One path a lane, so it returns a pair.  It runs 0.35 px into the plug, where its own end cap is
    buried - level with the plug's first face the two discs are coplanar and flicker.
    """
    step = 1.6 / 16.0 * (1.0 if z_to > z_from else -1.0)
    bury = 0.35 / 16.0 * (1.0 if z_to > z_from else -1.0)
    return [[(lane * CORE_OFFSET, base + CORE_Y, z_from),
             (lane * CORE_OFFSET, base + CORE_Y, z_to - step),
             (lane * MC4_LANE, base + MC4_Y, z_to - step * 0.28),
             (lane * MC4_LANE, base + MC4_Y, z_to + bury)] for lane in (-1.0, 1.0)]


def mc4_pair(mesh, name, at_z, y=None, turn=0.0, into=1.0, at_x=0.0):
    """Two MC4 plugs on the end of a pair, the positive one carrying the red ring and the pin."""
    y = MC4_Y if y is None else y
    axis = 'z' if turn % 180 == 0 else 'x'
    for index, lane in enumerate((at_x - MC4_LANE, at_x + MC4_LANE)):
        faces = mesh.faces(name, 'plug_plus' if index == 0 else 'plug_minus')
        # flip_v because the mod's own renderer does 1 - v, so the bands arrive tail first without it
        mc4(mesh, faces, spin_y((lane, y, at_z), turn), axis, unit=1.0 / 16.0,
            into=1 if into > 0 else -1, flip_v=True, pin=index == 0)


def stubs(mesh, name='stub', reach=UNDER):
    """A run of cable from each of the four block edges in under the machine, one group apiece.

    ``reach`` is how far in the pair goes before it stops.  A cabinet sits over its own stubs, so the
    default buries the capped ends under it; a combiner stands on a post and has to bury them in something,
    which is what its own conduit body is for.
    """
    for side, turn in (('north', 0.0), ('west', 90.0), ('south', 180.0), ('east', 270.0)):
        group = '%s_%s' % (name, side)
        cable_pair(mesh, group, [(0.0, CORE_Y, -0.5), (0.0, CORE_Y, -reach)], turn=turn,
                   ends=(False, True))
        # the cleat that holds it down where it crosses open ground: the same fitting a laid run has
        faces = mesh.faces(group, 'steel')
        strap, top = CORE_OFFSET + CORE_RADIUS, CORE_Y + CORE_RADIUS
        spin = ((0.0, 0.0, 0.0), 'y', turn)
        for lo, hi in ((-0.115, -strap), (strap, 0.115)):
            box(mesh, faces, (lo, 0.0, -0.425), (hi, top, -0.395), uv_scale=0.2, rot=spin)
        box(mesh, faces, (-0.115, top, -0.425), (0.115, top + 0.026, -0.395), uv_scale=0.3, rot=spin)


# How far one piece of the run reaches back inside the piece it continues.  Butted end to end their two
# caps are coplanar and fight; this buries them, and it is one cable either side so the overlap is invisible.
SOCKET_LAP = 0.02

# ------------------------------------------------------------------ how a fixed row is cabled
#
# One route, and it is the route a real row takes: the pair arrives at the middle of the edge a laid run
# meets, S-bends onto the **left-hand lane**, crosses the block under the modules, and S-bends back out to
# the middle of the far edge.  Where the far edge is another row instead, it stays on the lane and the two
# plug together - which is the whole reason the lane is at the side and not down the middle.
#
# Round on the ground, flat clipped to the structure.  That is the division between the two forms, and it is
# what puts a ribbon under a ballasted table - 2.5 px of table has no room for a 3.4 px pair - and leaves a
# rack's pair round, because a rack stands on piers with a quarter of a block of headroom under it.
#
# Five groups a row, and exactly one of the three at each end is ever drawn: see PvArrayRenderer.drawn.

# Where the lane runs.  A table's ballast blocks and a rack's piers both start at 0.30; the widest thing on
# the lane is the MC4 pair, which splays to 0.147 either side of it, so 0.15 clears them both.
LANE = 0.15
# The S-bend's radius.  Nothing physical fixes it: the cable is drawn sixteen times oversize, so its real
# 20 mm bend radius comes to a fifth of a pixel here.  Chosen to read as a bend rather than a kink, and the
# pair of arcs is then as short as that allows.
BEND = 0.17


def sbend(offset, radius=BEND, steps=7):
    """Two arcs that carry a path across ``offset`` and leave it pointing the way it came in.

    A cable does not turn the square corner a polyline gives it.  Both arcs turn through the same angle, and
    the offset fixes it: 2r(1 - cos t) = offset.  The figure is point-symmetric about its middle, so the
    second arc is the first one reflected through its own end.
    """
    turn = math.acos(max(-1.0, 1.0 - abs(offset) / (2.0 * radius)))
    side = 1.0 if offset >= 0.0 else -1.0
    first = [(side * radius * (1.0 - math.cos(turn * i / steps)), radius * math.sin(turn * i / steps))
             for i in range(steps + 1)]
    end = first[-1]
    return first + [(2.0 * end[0] - x, 2.0 * end[1] - z) for x, z in reversed(first[:-1])]


# Where a row's own cable starts and stops, measured from the middle: as far in as one S-bend from the block
# edge reaches.  The spine is what is left between the two, and every end piece begins inside it.
JOINT = 0.5 - sbend(LANE)[-1][1]

# A flat twin cable is two cores in one moulded web, which in section is two circles touching - so the
# ribbon is the same pair at the only spacing that makes it flat, and nothing swells at the joint.
RIBBON_LANE = CORE_RADIUS
# How much air the clips leave between the ribbon and what they hold it against.
RIBBON_GAP = 0.012

# The moulded joint where the two forms meet.  Wide enough to take the round pair's own spacing on one face,
# long enough to bury both sets of end caps, and tall enough to take the step in height between them.
JOINT_X = CORE_OFFSET + CORE_RADIUS + 0.010
JOINT_Z = 0.038
# How far inside the spine's own end the piece continuing it starts: inside the moulded joint where there is
# one, and inside the spine's last hundredth where there is not.  At 2 x SOCKET_LAP it fell 0.002 short of
# the joint's inner face, and two capped cable ends stood in the open under the table.
HANDOVER = 0.030


def joint_body(mesh, group, x0, z, base, top):
    """The moulded joint that turns the ground run's round pair into the ribbon clipped up under the panel.

    The same polycarbonate the laid run's own junction boxes are, and all six faces take the wall tile: a
    moulded joint is resin-filled and has no screwed lid, so dc_jbox's bolt heads have no business on it.
    The round pair comes in one face on the ground and the ribbon leaves the other at module height, and the
    step between the two is inside the box, which is what a moulded joint is for.
    """
    lo = (x0 - JOINT_X, base, z - JOINT_Z)
    hi = (x0 + JOINT_X, top, z + JOINT_Z)
    clad_box(mesh, group, lo, hi, {'*': 'jbox_side'}, uv=square_uv(lo, hi, 3.0))


def ribbon_clip(mesh, group, x0, z, axis_y, under):
    """A saddle clip holding the ribbon up against the member above it.

    ``under`` is the height of what it is bolted to - the laminate's own frame on a table - so the cheeks are
    as deep as the gap they bridge, which is what makes the cable read as attached rather than as laid.
    """
    faces = mesh.faces(group, 'steel')
    wide = RIBBON_LANE + CORE_RADIUS + 0.012
    low, high = axis_y - CORE_RADIUS, axis_y + CORE_RADIUS
    box(mesh, faces, (x0 - wide, low - 0.010, z - 0.015), (x0 + wide, low, z + 0.015), uv_scale=0.2)
    for side in (-1.0, 1.0):
        box(mesh, faces, (x0 + side * wide - 0.011, low - 0.010, z - 0.015),
            (x0 + side * wide, under, z + 0.015), uv_scale=0.2)
    box(mesh, faces, (x0 - wide, high + 0.002, z - 0.015), (x0 + wide, under, z + 0.015), uv_scale=0.2)


def pair_cleat(mesh, group, x0, z, height):
    """The cleat that holds a pair down where it crosses open ground: the laid run's own fitting.

    A rack's pair is on the ground with nothing over it to clip to, and cable pulled along a row is pegged
    down - the same two feet and strap a run laid across the sand gets.
    """
    faces = mesh.faces(group, 'steel')
    strap = CORE_OFFSET + CORE_RADIUS
    top = height + CORE_Y + CORE_RADIUS
    for lo, hi in ((-0.115, -strap), (strap, 0.115)):
        box(mesh, faces, (x0 + lo, height, z - 0.015), (x0 + hi, top, z + 0.015), uv_scale=0.2)
    box(mesh, faces, (x0 - 0.115, top, z - 0.015), (x0 + 0.115, top + 0.026, z + 0.015), uv_scale=0.3)


def row_spine(mesh, height, ribbon_y=None, under=None, clips=(), ties=()):
    """The stretch of a row's own cable that crosses the block, on the lane.

    ``ribbon_y`` is where the flat cable's own axis runs when it is clipped to the structure; None leaves the
    pair round on the ground, which is what a rack with headroom under it gets.
    """
    flat = ribbon_y is not None
    y = ribbon_y if flat else height + CORE_Y
    reach = JOINT - SOCKET_LAP
    cable_pair(mesh, 'harness', [(LANE, y, -reach), (LANE, y, reach)],
               lanes=RIBBON_LANE if flat else CORE_OFFSET)
    for z in clips:
        ribbon_clip(mesh, 'harness', LANE, z, y, under)
    for z in ties:
        pair_cleat(mesh, 'harness', LANE, z, height)


def row_joint(mesh, group, height, ribbon_y, end):
    """One end's moulded joint, if this mounting has one at all."""
    if ribbon_y is None:
        return

    joint_body(mesh, group, LANE, JOINT * end, height + 0.002, ribbon_y + CORE_RADIUS + 0.006)


def row_lead(mesh, height, end=-1, ribbon_y=None):
    """The straight round pair from the joint out to the block edge, on the lane.

    Drawn at whichever end has another row against it or nothing at all: it is the cable a row is chained by,
    and it stays on the lane because that is where the row in front presents its own.
    """
    name = 'harness_lead_north' if end < 0 else 'harness_lead_south'
    row_joint(mesh, name, height, ribbon_y, end)
    y = height + CORE_Y
    cable_pair(mesh, name, [(LANE, y, (JOINT - HANDOVER) * end), (LANE, y, 0.5 * end)],
               ends=(True, False))


def row_socket(mesh, height, end=-1, ribbon_y=None):
    """The same lead, ending in the pair of MC4s the row in front plugs into.

    It carries its own cable rather than continuing row_lead's, because exactly one of the two is ever
    drawn - two pieces of cable in one place is not a state this can be in.
    """
    name = 'harness_input'
    row_joint(mesh, name, height, ribbon_y, end)
    y = height + CORE_Y
    plugs = 0.44 * end
    faces = mesh.faces(name, 'core')
    for path in tail_rise(plugs - SOCKET_RISE * end, plugs, base=height):
        tube(mesh, faces, [(LANE + x, py, pz) for x, py, pz in path], CORE_RADIUS, sides=FITTING,
             uv_scale=1.0, uv_along=1.0, caps=faces)
    cable_pair(mesh, name, [(LANE, y, (JOINT - HANDOVER) * end),
                            (LANE, y, (0.44 - SOCKET_RISE + SOCKET_LAP) * end)])
    mc4_pair(mesh, name, plugs, y=height + MC4_Y, into=float(end), at_x=LANE)


def row_entry(mesh, height, end=-1, ribbon_y=None):
    """Where a laid run meets the middle of an edge: the S-bend from there onto the lane.

    Both cores turn together, a lane apart the whole way, so neither has to cross the other.  The one square
    crossing the old axis-aligned route could not avoid has gone with the corner that forced it.
    """
    name = 'harness_entry_north' if end < 0 else 'harness_entry_south'
    row_joint(mesh, name, height, ribbon_y, end)
    y = height + CORE_Y
    points = [(x, y, (0.5 - z) * end) for x, z in sbend(LANE)]
    points.append((LANE, y, (JOINT - HANDOVER) * end))
    # the block edge is carried on by the laid run's own arm, so that end takes no cap
    cable_pair(mesh, name, points, ends=(False, True))


def row_cabling(mesh, height=0.0, ribbon_y=None, under=None, clips=(), ties=()):
    """Every piece of a fixed row's cabling, in the five groups the renderer chooses between."""
    row_spine(mesh, height, ribbon_y=ribbon_y, under=under, clips=clips, ties=ties)
    row_socket(mesh, height, end=-1, ribbon_y=ribbon_y)
    for end in (-1, 1):
        row_lead(mesh, height, end=end, ribbon_y=ribbon_y)
        row_entry(mesh, height, end=end, ribbon_y=ribbon_y)


# ------------------------------------------------------------------ how a tracked row is cabled
#
# Down the middle, because a tracked row's own axis is where its harness runs and both its faces take a
# connection.  Straight through the pier it stood on, until the pull box: the pair used to be drawn inside
# the pier's own I-beam and inside its base plate for the whole length of the block.

# The pull box at the foot of the pier, and the height the pair rises to enter it.
BOX_RISE = 0.088


# A gland is 1.4x the cable it seals, so its tip has to stay wider than the core it is threaded over or the
# cone comes out inside the cable.  Same figure as the combiner's conduit.
GLAND_RADIUS = 0.057


def pull_box(mesh, group, half_x, half_z, base, top, rise):
    """A cast pull box at the foot of a tracker's pier, with a gland a core each side.

    The pier comes up through it, which is what a trough at a pier's foot really looks like: the pair goes
    in one face and out the other, and where it runs inside is the box's business.
    """
    lo, hi = (-half_x, base, -half_z), (half_x, top, half_z)
    clad_box(mesh, group, lo, hi, {'up': 'jbox', '*': 'jbox_side'}, uv=square_uv(lo, hi, 3.0))
    faces = mesh.faces(group, 'gland')
    for end in (-1.0, 1.0):
        for lane in (-CORE_OFFSET, CORE_OFFSET):
            cursor = half_z
            # a hex body against the wall and a plain sleeve outboard of it: no taper, because cylinder()
            # always narrows towards +z and one of these two glands faces the other way
            for length, radius, sides in ((0.016, GLAND_RADIUS, HEX), (0.022, GLAND_RADIUS * 0.80, FITTING)):
                cylinder(mesh, faces, (lane, rise, end * (cursor + length * 0.5)), 'z', radius,
                         length * 0.5, sides=sides, uv_scale=0.5)
                cursor += length


def row_harness(mesh, half_x=0.128, half_z=0.105, base=0.030, rise=BOX_RISE):
    """A tracked row's harness: down the middle, in one side of the pull box and out the other."""
    top = rise + GLAND_RADIUS + 0.008
    pull_box(mesh, 'harness', half_x, half_z, base, top, rise)
    for end in (-1.0, 1.0):
        # along the ground to the pier, then an S-rise onto the gland's own axis and in through it
        climb = [(0.0, CORE_Y + up, end * (0.32 - along))
                 for up, along in sbend(rise - CORE_Y, radius=0.10)]
        cable_pair(mesh, 'harness', [(0.0, CORE_Y, end * 0.5)] + climb
                   + [(0.0, rise, end * (half_z - 0.02))], ends=(False, True))

    for end, side in ((-1, 'north'), (1, 'south')):
        name = 'harness_plug_%s' % side
        edge = -0.44 if end < 0 else 0.44
        faces = mesh.faces(name, 'core')
        # Each rise starts its own side of the middle, not on it: both starting at zero put the two ends in
        # the same place, and a player fed from both ends saw the two sets of plugs share a tube.
        for path in tail_rise(edge - SOCKET_RISE * end, edge):
            tube(mesh, faces, path, CORE_RADIUS, sides=FITTING, uv_scale=1.0, uv_along=1.0,
                 caps=faces)
        mc4_pair(mesh, name, edge, into=-1.0 if end < 0 else 1.0)


# ------------------------------------------------------------------ the flat table

# A precast ballast block, in blocks.  It used to be 0.045, which left 0.7 px of ground clearance - nothing
# could pass under the table at all, which is why the cable ran on the ground beside it and through the
# ballast rather than under the panel.  What sets the figure is the mated pair of MC4s that has to get under
# the perimeter frame: 0.138 tall, because the cable is drawn sixteen times oversize and its connectors with
# it.  375 mm of precast at this model's scale, which is a tall block or two stacked, and both are built.
BALLAST = 0.15
# and the courses above it: the perimeter frame, the rails across it, the laminate.
FLAT_FRAME_TOP = BALLAST + 0.040
FLAT_GLASS = FLAT_FRAME_TOP + 0.020
# Where the two rails sit.  Far enough out that the moulded joint at each end of the ribbon passes under
# the modules and not through a rail: the joint stands 0.037 taller than the frame does.
FLAT_RAILS = (-0.31, 0.25)


def flat_table():
    """A ballasted table lying flat: two modules on an aluminium frame on concrete ballast."""
    mesh = Mesh()

    # The ballast: two precast beams, one under each long edge of the frame.  Four blocks under the corners
    # read as legs once the table stood tall enough for its own cable to pass under it, and a ballast beam is
    # the other thing a real one stands on - which also leaves the corridor between them clear the whole way,
    # so the pair crosses no concrete at all.
    ballast = mesh.faces('ballast', 'concrete')
    for x in (-0.47, 0.33):
        box(mesh, ballast, (x, 0.0, -0.47), (x + 0.14, BALLAST, 0.47), uv_scale=0.6)

    frame = mesh.faces('frame', 'frame')
    # the perimeter frame: channel
    for z0, z1 in ((-0.47, -0.41), (0.41, 0.47)):
        channel(mesh, frame, (-0.47, BALLAST, z0), (0.47, FLAT_FRAME_TOP, z1), along='x',
                opening='down', uv_scale=0.35)
    for x0, x1 in ((-0.47, -0.41), (0.41, 0.47)):
        channel(mesh, frame, (x0, BALLAST, -0.41), (x1, FLAT_FRAME_TOP, 0.41), along='z',
                opening='down', uv_scale=0.35)
    # the two rails the modules are clamped to, across the frame
    for z in FLAT_RAILS:
        box(mesh, frame, (-0.44, FLAT_FRAME_TOP, z), (0.44, FLAT_GLASS, z + 0.06), uv_scale=0.3)

    # the modules: two whole tiles, portrait, with the rail gap between them
    top = FLAT_GLASS + MODULE_THICK
    for x0 in (-0.44, 0.02):
        laminate(mesh, 'modules', (x0, FLAT_GLASS, -MODULE_LONG / 2),
                 (x0 + MODULE_WIDE, top, MODULE_LONG / 2))

    # the clamps: a mid clamp between the two modules and an end clamp outside each
    # actually holds a laminate onto a rail
    clamps = mesh.faces('modules', 'frame')
    # inside the rail's own span rather than flush with its edge, so no two faces share a plane
    for z in (FLAT_RAILS[0] + 0.015, FLAT_RAILS[1] + 0.015):
        box(mesh, clamps, (-0.025, FLAT_GLASS - 0.004, z), (0.025, top + 0.004, z + 0.03), uv_scale=0.12)
        bolt(mesh, clamps, (0.0, top + 0.004, z + 0.015), 'y', 0.007, 0.014, uv_scale=0.1)
        for x in (-0.455, 0.435):
            box(mesh, clamps, (x, FLAT_GLASS - 0.004, z), (x + 0.02, top + 0.004, z + 0.03),
                uv_scale=0.12)

    # The cabling: round on the ground from the middle of each edge, then a moulded joint and the flat
    # ribbon clipped up under the laminate for the crossing.  A table is 3 px tall in all, so the round
    # pair could never have gone under it - see row_cabling.
    row_cabling(mesh, ribbon_y=FLAT_GLASS - RIBBON_GAP - CORE_RADIUS, under=FLAT_GLASS - 0.003,
                clips=(-0.14, 0.0, 0.14))

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
    # How high the plane's low edge stands off the ground.  A real fixed-tilt rack keeps half a metre under
    # its front purlin - for shading, for snow, and because that is where the cable runs.  At 0.045 the low
    # corner sat 0.107 off the ground with the row's own pair 0.081 tall and its connectors 0.138: the cable
    # could not pass under its own rack, which is most of what "the cables collide" was.
    clearance = 0.18
    pivot_y = clearance + purlin_depth * cos_tilt + depth * sin_tilt
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

    # The cabling: the same route as a table's, and round the whole way.  A rack stands on piers with a
    # quarter of a block of headroom under it, so there is nothing a ribbon would solve - and cable is
    # pulled along a row on the ground and tied down, which is what it does here.
    row_cabling(mesh, ties=(-0.10, 0.10))

    return mesh


# ------------------------------------------------------------------ the single-axis tracker

# Half-length of the drive bay at the centre of a tracked row, in blocks.
DRIVE_BAY = 0.055


def single_axis():
    """A 1P horizontal tracker: one module in portrait across a torque tube, on driven piers."""
    mesh = Mesh()
    # 1.70 m at this model's own scale, where MODULE_LONG stands for 2.35 m.  At 0.62 the module's low
    # corner swept to 0.39 m off the ground - under the height of the harness lying beside it, which is
    # what check_pv_clearance caught.  Real 1P rows run 1.5 to 2.0 m and keep half a metre of clearance.
    axis_y = 0.68
    bay = DRIVE_BAY

    pier = mesh.faces('pier', 'steel')
    plate = mesh.faces('pier', 'plate')
    # one central pier rather than a pair
    # swept disc: within one block a real row has a pier every six metres
    ibeam(mesh, pier, (-0.075, 0.0, -bay + 0.008), (0.075, axis_y - 0.105, bay - 0.008),
          uv_scale=0.3)
    # 0.11 in z rather than the bay's own 0.055: the pull box that carries the pair through the pier sits
    # on this plate, and a box overhanging the plate it stands on floats at its corners
    box(mesh, plate, (-0.13, 0.0, -0.11), (0.13, 0.028, 0.11), uv_scale=0.5)
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

    # The harness: down the middle, the same as a single-axis row, but the box sits on the pedestal's own
    # base plate at 0.05 - so it and the glands in its walls start that much higher.
    row_harness(mesh, half_x=0.145, half_z=0.125, base=0.052, rise=0.115)
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

# The cable entry box at the foot of a combiner's post, and how far into it the pair reaches.  A gland is
# 1.4x the cable it seals, so two of them a lane apart set the box's width, not the other way round: the
# box has to be wide enough for the outer gland to sit inside its face.
CONDUIT_FACE = 0.122
CONDUIT_END = 0.157


def conduit(mesh, group, turn):
    """The two glands one side's pair enters the post's entry box through.

    In the entry group, so a gland is only there when a cable is; the box itself belongs to the post, or
    four of them would meet at its corners and share planes.  The pair's capped ends stop inside the nuts,
    so a player sees the cable go into a gland and no further - which is where it goes, up inside the post.
    """
    faces = mesh.faces(group, 'steel')
    # spin_y turns the north outward direction (0, 0, -1) onto this side's, which is one axis and a sign
    out = spin_y((0.0, 0.0, -1.0), turn)
    axis = 'xyz'[max(range(3), key=lambda i: abs(out[i]))]
    step = 1.0 if out['xyz'.index(axis)] > 0 else -1.0
    # a hex body against the face and a compression nut in front of it - the string cable's own gland
    cursor = CONDUIT_FACE
    for length, radius, taper, sides in ((0.018, 0.052, 1.0, HEX), (0.024, 0.048, 0.80, FITTING)):
        for lane in (-CORE_OFFSET, CORE_OFFSET):
            middle = list(spin_y((lane, CORE_Y, 0.0), turn))
            middle['xyz'.index(axis)] = step * (cursor + length / 2.0)
            # cylinder tapers its +axis end, so a gland facing the other way wants the reciprocal
            cylinder(mesh, faces, tuple(middle), axis, radius if step > 0 else radius * taper,
                     length / 2.0, sides=sides, uv_scale=0.3,
                     taper=taper if step > 0 else 1.0 / taper,
                     caps=faces, cap_ends=(int(step),))
        cursor += length


def combiner():
    """A string combiner on a post: an enclosure, a hood, a window"""
    mesh = Mesh()
    box_y0, box_y1 = 0.38, 0.78

    post = mesh.faces('post', 'steel')
    # The footing and the cable entry box are one part - the base - and the column is another.  A group per
    # part, so the collision is the three rectangles the object has: base, post, enclosure.
    plate = mesh.faces('base', 'plate')
    box(mesh, plate, (-0.136, 0.0, -0.136), (0.136, 0.026, 0.136), uv_scale=0.5)
    for x in (-0.129, 0.129):
        for z in (-0.129, 0.129):
            bolt(mesh, plate, (x, 0.026, z), 'y', 0.007, 0.015, uv_scale=0.12)
    # the cable entry box every string arrives in, sunk into the footing so the two share no plane.  Not in
    # an entry group: one box takes all four sides, and four would meet at the post's corners.
    clad_box(mesh, 'base', (-CONDUIT_FACE, 0.020, -CONDUIT_FACE), (CONDUIT_FACE, 0.100, CONDUIT_FACE),
             {'up': 'plate', '*': 'steel'}, uv_scale=0.3)
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

    # where the strings come in, one run per side of the block, each into its own conduit body at the foot
    # of the post.  Two bare tubes used to climb the post into the glands, which is not how a field
    # combiner is wired anyway: the strings enter a conduit body and go up *inside* the post.
    stubs(mesh, 'entry', reach=CONDUIT_END)
    for side, turn in (('north', 0.0), ('west', 90.0), ('south', 180.0), ('east', 270.0)):
        conduit(mesh, 'entry_%s' % side, turn)

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
HARNESS_MATERIALS = ('cabinet', 'cabinet_top', 'core', 'plug_plus', 'plug_minus',
                     'jbox', 'jbox_side', 'gland')
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
