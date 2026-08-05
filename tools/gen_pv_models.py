#!/usr/bin/env python3
"""Generates the OBJ geometry for the photovoltaic blocks and the meteorological mast.

    python3 tools/gen_pv_models.py

Writes into src/main/resources/assets/electricity/models/.

The primitives are in ``modellib.py``; the group-name contracts with the renderers are documented
there.  What is here is the design of each machine: which parts it is made of and where they go.

How much of a real machine is drawn
-----------------------------------
Every part a photograph of the real thing shows from ten metres away, and nothing smaller.  So a
rack has driven I-section piers with a bolted cleat at each purlin, a diagonal brace between the
front and back rows, channel purlins with their open side down, module rails on top of those, and a
clamp at every module edge - because all of that is what you see - and no earth bonding jumpers,
because at a block to ten metres they would be one pixel and their only effect would be noise.

One module everywhere
---------------------
``pv_module.png`` is one whole laminate, frame and all, so every piece of glass in the mod is
exactly one texture tile: 0.42 of a block across by 0.94 along, which is the 1134 by 2278 of the
product every one of these mountings carries.  That is a constraint on the layouts rather than on
the texture - a bay has to come out a whole module - and it is what makes a mixed field read as one
plant, because a module is the same size on a table, on a rack and on a tracker.  ``uv_rot`` turns
the mapping for the two mountings whose modules lie across the row instead of along it.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from modellib import (FITTING, ROUND, Mesh, angle, arc, bolt, box, channel, clad_box, cylinder,
                      hemisphere, ibeam, pin_insulator, pivot, rotate, strut, tube, write_mtl)

OUT = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')

# Materials, and the texture each one resolves to.  ObjModel prefixes textures/block/ to whatever
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
    'dc_cable': 'dc_harness.png',
    'dc_jacket': 'dc_jacket.png',
    'combiner_door': 'pv_combiner_door.png',
    'dc_section': 'pv_dc_section.png',
    'switch': 'pv_switch.png',
    'concrete': 'box_plinth.png',
    'porcelain': 'porcelain_brown.png',
    'warning': 'warning.png',
}

# Which material each face of a laminate carries.  A module is not one material: the sun side is
# cells, the back is a backsheet with a junction box on it, and the four edges are the aluminium
# frame that clamps the glass.
LAMINATE = {'up': 'module', 'down': 'module_back', '*': 'module_edge'}

# One module, in blocks.  Every laminate in the mod is exactly this, and a mounting's bays are laid
# out to suit rather than the other way round.
MODULE_WIDE = 0.42
MODULE_LONG = 0.94
MODULE_THICK = 0.030

# The tilt a fixed rack is built at, in degrees.  The same figure PvMounting declares, restated
# here rather than imported because a Python script cannot read a Java enum - so if one changes the
# other has to, and this comment is the note saying so.
FIXED_TILT_DEG = 25.0


def laminate(mesh, name, lo, hi, across='x', rot=None):
    """One module: cells up, backsheet down, frame on all four edges.

    ``across`` is the axis the module's *width* runs along.  A module whose length runs east-west
    needs its picture turned a quarter, or the cell rows come out across the panel instead of along
    it - which is the difference between a laminate and a barcode.

    ``rot`` is for the one mounting whose tilt is baked rather than posed at draw time.  A tracked
    row is authored flat and turned by a matrix; a fixed rack cannot be, because nothing about it
    moves and it would then be the only model in the mod that needs a renderer to look right.
    """
    clad_box(mesh, name, lo, hi, LAMINATE, uv_rot=0 if across == 'x' else 90, rot=rot)


# ------------------------------------------------------------------ cable, and the contract

# The pair's own figure: two pixels across, one tall, one conductor to a pixel.  Everything that
# draws cable uses it, so nothing swells at a join.
LEAD = 0.0625
# What a run laid across the ground is, from gen_cable_models.py.
LAID = 2 * LEAD
# Where a row's own lead runs on a fixed mounting: hard against the block's edge, so its outer face
# *is* the boundary.
EDGE = 0.50 - LEAD


def lead(mesh, name, lo, hi, face='up', rot=None):
    """A length of the pair, with the conductors on one face and jacket on the rest."""
    clad_box(mesh, name, lo, hi, {face: 'dc_cable', '*': 'dc_jacket'}, rot=rot)


def stubs(mesh, name='stub'):
    """A run of cable from the middle of the block out to each of the four edges, one group apiece.

    Four groups rather than one rotated four ways, because a renderer draws a group once: the pose
    map is keyed by group name, so the way to draw a stub towards two different sides is to have
    two.  The renderer includes only the sides a run has actually been laid against, which is what
    makes the cable appear to run *into* the machine instead of stopping a pixel short of it.

    All four are the same box turned about the block's middle rather than four boxes written out,
    and that is not brevity: this file maps a face's texture along its own x and z, so a stub
    *written* along x would come out with its conductors running across it instead of along it.
    """
    # the hub, drawn whatever is connected, so that two arms never have to meet in the middle: two
    # solids sharing a volume put two faces on the same plane, which flickers
    clad_box(mesh, '%s_hub' % name, (-LEAD, 0.0, -LEAD), (LEAD, LEAD, LEAD),
             {'up': 'dc_cable', '*': 'dc_jacket'})

    for side, turn in (('north', 0.0), ('west', 90.0), ('south', 180.0), ('east', 270.0)):
        spin = ((0.0, 0.0, 0.0), 'y', turn)
        clad_box(mesh, '%s_%s' % (name, side), (-LEAD, 0.0, -0.5), (LEAD, LEAD, -LEAD),
                 {'up': 'dc_cable', '*': 'dc_jacket'}, rot=spin)
        # the saddle: a bar over the pair on two legs beside it, rather than a block round it.  A
        # block round it shares the ground plane with the cable and the two flicker against each other
        faces = mesh.faces('%s_%s' % (name, side), 'steel')
        box(mesh, faces, (-0.09, LEAD, -0.425), (0.09, 0.095, -0.40), uv_scale=0.3, rot=spin)
        for x in (-0.0825, 0.0825):
            box(mesh, faces, (x - 0.0125, 0.0, -0.42), (x + 0.0125, LEAD, -0.405),
                uv_scale=0.2, rot=spin)


def row_lead(mesh, x0, height, start=-0.44):
    """The lead out of one end of a row, and nothing on the other sides.

    A string has two ends.  They are where the next row's string arrives and where this one's
    leaves, so everything a row shows is on one edge: the lead, and the socket at the far end that
    takes the row behind it.  Nothing on the flanks - a run round all four edges reads as a plant
    wrapped in wire, and it is not what a plant looks like either.
    """
    lead(mesh, 'harness', (x0, height, start), (x0 + LEAD, height + LEAD, 0.50))


def row_socket(mesh, x0, height, name='harness_input', end=-1, width=LEAD):
    """Where the row behind plugs in, at the other end of that same edge.

    Its own group, because it is drawn only when there is something to plug into it: a socket
    sitting on every panel whether or not anything feeds it is the difference between a plant that
    reads as wired through and one that reads as a warehouse of parts.
    """
    z0, z1 = (-0.50, -0.44) if end < 0 else (0.44, 0.50)
    steel = mesh.faces(name, 'steel')
    box(mesh, steel, (x0, height, z0), (x0 + width, height + LEAD, z1), uv_scale=0.25)
    nose = z0 - 0.015 if end < 0 else z1 + 0.015
    cylinder(mesh, steel, (x0 + width / 2, height + LEAD / 2, nose), 'z', 0.022, 0.018,
             sides=FITTING, uv_scale=0.3, caps=steel)


def row_entry(mesh, x0, height, end=-1):
    """The turn a laid run makes to reach the edge a row is wired on.

    A run arrives down the middle of the block, because that is where a laid run sits, and a fixed
    row's leads are out at its edge.  Left to themselves the two stop a third of a block apart and
    the plant reads as a cable pointing at a panel rather than plugged into one.
    """
    name = 'harness_entry_north' if end < 0 else 'harness_entry_south'
    z0, z1 = (-0.50, -EDGE) if end < 0 else (EDGE, 0.50)
    lead(mesh, name, (z0, 0.0, -x0), (z1, LEAD, LEAD), rot=((0.0, 0.0, 0.0), 'y', 270.0))
    if height > 0.0:
        lead(mesh, name, (x0, 0.0, z0), (x0 + LEAD, height, z1))


def row_harness(mesh):
    """A tracked row's harness: a run along the ground down the middle, and nothing else.

    Down the middle rather than along an edge because a tracked row has no edge to run along - the
    modules above it turn through sixty degrees and a cable on the flank would spend half the day
    underneath them.  Which puts it on the block's own axis, exactly where a laid run sits.
    """
    lead(mesh, 'harness', (-LAID / 2, 0.0, -0.50), (LAID / 2, LEAD, 0.50))
    for end, side in ((-1, 'north'), (1, 'south')):
        name = 'harness_plug_%s' % side
        row_socket(mesh, -LAID / 2, 0.0, name=name, end=end, width=LAID)
        tail = (-0.44, 0.0) if end < 0 else (0.0, 0.44)
        lead(mesh, name, (-LAID / 2, 0.0, tail[0]), (LAID / 2, LEAD, tail[1]))


# ------------------------------------------------------------------ the flat table

def flat_table():
    """A ballasted table lying flat: two modules on an aluminium frame on concrete ballast.

    What a flat-roof or a low-ground system actually is: nothing is driven into anything.  The
    frame stands on precast ballast blocks whose weight is the only thing holding it down, which is
    why the blocks are drawn at all - they are the largest single part of the machine and the one
    that says at a glance it is *ballasted* rather than piled.

    Low enough to walk over rather than round, which is what a table on a roof is.
    """
    mesh = Mesh()

    # the ballast: four precast blocks, one under each corner of the frame
    ballast = mesh.faces('ballast', 'concrete')
    for x in (-0.44, 0.30):
        for z in (-0.44, 0.30):
            box(mesh, ballast, (x, 0.0, z), (x + 0.14, 0.045, z + 0.14), uv_scale=0.6)

    frame = mesh.faces('frame', 'frame')
    # the perimeter frame: channel, open side down so water leaves it
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

    # the clamps: a mid clamp between the two modules and an end clamp outside each, which is what
    # actually holds a laminate onto a rail
    clamps = mesh.faces('modules', 'frame')
    # inside the rail's own span rather than flush with its edge, so no two faces share a plane
    for z in (-0.265, 0.235):
        box(mesh, clamps, (-0.025, 0.101, z), (0.025, top + 0.004, z + 0.03), uv_scale=0.12)
        bolt(mesh, clamps, (0.0, top + 0.004, z + 0.015), 'y', 0.007, 0.014, uv_scale=0.1)
        for x in (-0.455, 0.435):
            box(mesh, clamps, (x, 0.101, z), (x + 0.02, top + 0.004, z + 0.03), uv_scale=0.12)

    # The harness: a short tail out of the corner and a socket on the far edge, and that is all.  A
    # table's whole footprint is glass, so there is no edge to run a lead along - a cable across a
    # cell is a cell out of the string.
    row_lead(mesh, EDGE, 0.0, start=0.38)
    row_socket(mesh, EDGE, 0.0)
    for end in (-1, 1):
        row_entry(mesh, EDGE, 0.0, end=end)

    return mesh


# ------------------------------------------------------------------ the fixed rack

def tilted_rack():
    """A fixed rack at the mounting's own tilt, tipping towards -z.

    Towards -z because the block's default facing is north and PvArrayBlock reads the plane's
    bearing off that facing, so the geometry and the physics have to agree about which way is
    downhill.

    What a real fixed rack is made of, in the order it goes up: piers driven into the ground, a
    bolted cleat on each, a purlin across the cleats front and back, a diagonal brace tying the two
    rows together, rails along the slope, and the modules clamped to the rails.  Every one of those
    is drawn here, because every one of them is visible from across a field - a rack seen from the
    back is mostly structure.

    Everything above the ground is in the plane's own frame
    ------------------------------------------------------
    The purlins, the rails, the modules and the clamps are all written flat and turned by one
    rotation about the pivot, and the piers are cut to wherever that rotation leaves the purlin
    above them.  Nothing is placed at a height worked out by hand, which is the fault that had the
    purlins lying level under a plane at 25 degrees and the rear piers standing clear above it.

    Where the pivot goes
    --------------------
    Not where it is convenient - where it has to be for the tilted plane to fit inside the block.
    Tipping a plane 0.94 deep by 25 degrees moves its two edges 0.199 up and down, so a pivot
    chosen by eye put the low edge below the block's floor.
    """
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
    # two rows of piers, the front pair short and the back pair tall, each an I-section driven into
    # the ground and cut to where its own purlin actually is
    heads = {}
    for x in (-0.38, 0.30):
        for z in purlin_z:
            head = on_plane(x + 0.04, pivot_y - purlin_depth, z)
            heads[(x, z)] = head
            ibeam(mesh, piers, (x, 0.0, head[2] - 0.045), (x + 0.08, head[1] + 0.004, head[2] + 0.045),
                  uv_scale=0.3)
            # the cleat: an angle bolted through the pier, which is what the purlin sits on
            angle(mesh, piers, (x - 0.006, head[1] - 0.030, head[2] - 0.048),
                  (x + 0.086, head[1] + 0.004, head[2] + 0.048), along='x', web=0.40, uv_scale=0.2)
            bolt(mesh, plate, (x + 0.04, head[1] - 0.014, head[2] - 0.050), 'z', 0.007, 0.016,
                 uv_scale=0.15)

    # the diagonal brace between the two rows, from the foot of the front pier to the head of the
    # back one, which is what stops a rack racking
    for x in (-0.38, 0.30):
        front = heads[(x, purlin_z[0])]
        back = heads[(x, purlin_z[1])]
        strut(mesh, piers, (x + 0.04, 0.055, front[2] + 0.045),
              (x + 0.04, back[1] - 0.050, back[2] - 0.040), 0.013, 0.010, uv_scale=0.25)

    # The two purlins across the piers, in the plane's frame, channel with the open side down.
    #
    # One object each rather than both in one, and that is for the collision rather than the drawing:
    # tools/check_hitboxes.py cuts a shape from each object it finds, and two purlins at different
    # heights in one object have a bounding box that fills the air between them.  Apart they are two
    # flat boxes where the two purlins actually are.
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

    # the clamps along the rails, three a side, which is what a real rack shows most of
    clamps = mesh.faces('modules', 'frame')
    for z in (-0.36, 0.0, 0.36):
        box(mesh, clamps, (-0.030, glass - 0.004, z - 0.018), (0.030, top + 0.004, z + 0.018),
            uv_scale=0.1, rot=spin)
        for x in (-0.462, 0.442):
            box(mesh, clamps, (x, glass - 0.004, z - 0.018), (x + 0.020, top + 0.004, z + 0.018),
                uv_scale=0.1, rot=spin)

    # The harness: the same pair as a table's, on the ground rather than on the modules.  A rack
    # stands off the ground on piers, so the run crosses to the next row underneath it.
    row_lead(mesh, EDGE, 0.0)
    row_socket(mesh, EDGE, 0.0)
    for end in (-1, 1):
        row_entry(mesh, EDGE, 0.0, end=end)

    return mesh


# ------------------------------------------------------------------ the single-axis tracker

# Half-length of the drive bay at the centre of a tracked row, in blocks.
#
# A plane rotating about an axis sweeps a disc of radius equal to its own semi-width, so any fixed
# part inside that disc gets swept through.  Rather than trying to thread the pier and the drive
# between the module edges - which cannot be done, because the plane passes through every angle -
# the fixed parts live in a bay at the centre of the row and the modules stop short of it.
#
# It is also what a real independent-row tracker looks like: one bay with no module in it, because
# that is where the slew drive is.
DRIVE_BAY = 0.055


def single_axis():
    """A 1P horizontal tracker: one module in portrait across a torque tube, on driven piers.

    1P is what most of the world now builds - a single row of portrait modules whose long edge runs
    across the tube - and it is also the layout that lets every module here be one whole texture
    tile: two tiles laid across the row, 0.94 of a block along their length by 0.42 across.

    The plane is authored flat because its tilt is a matrix at draw time: the buffer cache is keyed
    by block position and rebuilt only when the light changes, so baking a rotation into the
    vertices would freeze the row wherever it happened to be.

    The tube runs north-south, which is the only axis from which a row can follow a sun that travels
    east to west - and it is why PvArrayBlock forces a tracker to that orientation.
    """
    mesh = Mesh()
    axis_y = 0.62
    bay = DRIVE_BAY

    pier = mesh.faces('pier', 'steel')
    plate = mesh.faces('pier', 'plate')
    # one central pier rather than a pair, because a pair either side of the bay would stand in the
    # swept disc: within one block a real row has a pier every six metres
    ibeam(mesh, pier, (-0.075, 0.0, -bay + 0.008), (0.075, axis_y - 0.105, bay - 0.008),
          uv_scale=0.3)
    box(mesh, plate, (-0.13, 0.0, -bay), (0.13, 0.028, bay), uv_scale=0.5)
    for x in (-0.105, 0.075):
        bolt(mesh, plate, (x + 0.015, 0.028, 0.0), 'y', 0.009, 0.020, uv_scale=0.15)

    # The slew drive: a housing round the tube, wholly inside the bay, with the motor off its side.
    #
    # One housing rather than a bearing block under a drive block: the two shared the bay and therefore
    # shared a volume and a plane, and on a real row the drive pier *is* the bearing at that pier -
    # the plain bearings are on the piers either side, which are in the next block along.
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
    # eighty sides: the tube is the one part of a tracker a player walks up to, and at 0.045 of a
    # block radius it is nearly a metre across in this mod's scale
    cylinder(mesh, tube, (0.0, axis_y, 0.0), 'z', 0.045, 0.49, sides=ROUND, uv_scale=1.0,
             uv_along=6.0, caps=mesh.faces('rotate_tube', 'steel_end'))

    # The module rails, across the tube, which is what a 1P row is clamped to - and the modules, one
    # bay each side of the drive.
    #
    # A group per bay rather than one for both, and that is not tidiness: a group's bounding box is what
    # the clearance checker sweeps and what the collision is cut from, and one group spanning both bays
    # has a box that covers the drive between them.  Everything named rotate_ turns with the tube, so
    # two groups turn exactly as one did.
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
    """A pedestal, an azimuth collar, and an elevation frame carrying two modules.

    Two moving groups, and the reason the azimuth one exists is worth stating: it turns to face the
    sun's bearing, which in this world is due east all morning and due west all afternoon.  It
    therefore swings a half turn at noon - and that is invisible, because at noon the elevation frame
    is lying flat and a flat plate turned about its own vertical axis looks identical.  Which is
    exactly what a real azimuth-elevation machine does as the sun crosses its zenith.

    Everything fixed is inside the drive bay, and that is what sets every dimension here
    -------------------------------------------------------------------------------------
    A plane turning about the elevation axis sweeps a *disc* of radius equal to its own semi-width -
    0.47 of a block - so it passes through anything standing at a z where the plane exists, whatever
    height that thing is.  The disc's lowest point is 0.28 up, which is below the top of the column,
    so the column, the slew ring, the bearing housing and the drive motor all have to be narrower
    than the gap the modules leave down the middle.  That gap is 0.066, so the column is 0.10 of a
    block across and the ring 0.124 - a metre and a bit at this mod's scale, which is what a real
    pedestal is - and the elevation drive is a worm box on the bearing rather than the linear
    actuator this had, because an actuator reaching out under the frame stands exactly where the
    frame goes.

    The elevation tube inside the bearing housing is the one place geometry is allowed to intersect,
    because that is a bearing: a cylinder turning about its own axis inside a housing sweeps nothing.
    """
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
    # turns together whether it is one group or two, and two have bounding boxes that leave the
    # bearing between them alone
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

    # The harness: the run along the ground and nothing else, the same as a single-axis row.  It had a
    # service coil round the column - a couple of turns left slack so a pedestal that spins can wind and
    # unwind - and that is what a real azimuth drive is given, but at this scale it read as a knot of
    # cable round the post rather than as slack.
    row_harness(mesh)
    return mesh


# ------------------------------------------------------------------ the inverter

def inverter():
    """A station inverter: a cabinet on a plinth, double doors, a rain hood, roof fans.

    Authored at the size of the commercial machine and scaled per product by the renderer, the same
    trick the turbines use: the catalogue spans a factor of two hundred and fifty in nameplate and
    the difference on screen is a transform rather than four assets.

    What makes it read as switchgear rather than as a grey box: the hood overhangs and has a drip
    edge, the doors are two leaves with a centre stile between them, the ventilation is where a real
    machine draws air - low on the doors, out through the roof - and there is a plinth under it,
    because a cabinet standing straight on the ground rusts from the bottom.
    """
    mesh = Mesh()
    body_y = 0.98
    face = -0.30
    back = 0.28

    # the plinth: a channel base, wider than the cabinet, which is what the cabinet is bolted to
    clad_box(mesh, 'plinth', (-0.465, 0.0, -0.325), (0.465, 0.055, 0.305),
             {'up': 'plate', '*': 'concrete'}, uv_scale=0.7)

    clad_box(mesh, 'cabinet', (-0.44, 0.055, face), (0.44, body_y, back),
             {'up': 'cabinet_top', '*': 'cabinet'})
    # the corner posts, which is what a sheet-steel cabinet is actually built round
    for x in (-0.45, 0.44):
        for z in (face - 0.01, back):
            box(mesh, mesh.faces('cabinet', 'frame'), (x, 0.055, z), (x + 0.01, body_y, z + 0.01),
                uv_scale=0.1)

    # the rain hood: crowned, overhanging on all four sides, with a drip edge turned down
    clad_box(mesh, 'hood', (-0.47, body_y, face - 0.03), (0.47, body_y + 0.035, back + 0.03),
             {'up': 'cabinet_top', '*': 'cabinet'}, uv_scale=0.8)
    box(mesh, mesh.faces('hood', 'cabinet'), (-0.47, body_y - 0.018, face - 0.03),
        (0.47, body_y + 0.006, face - 0.018), uv_scale=0.3)
    for x in (-0.30, 0.28):
        # the lifting eyes on the roof, which every cabinet this size is craned in by - back from the
        # fans rather than beside them, so nothing of theirs lands on a fan's plane
        eye = mesh.faces('hood', 'steel')
        cylinder(mesh, eye, (x, body_y + 0.058, 0.16), 'z', 0.026, 0.008, sides=FITTING,
                 uv_scale=0.2, caps=eye)
        box(mesh, eye, (x - 0.008, body_y + 0.033, 0.152), (x + 0.008, body_y + 0.048, 0.168),
            uv_scale=0.1)

    # The two door leaves, with the stile between them and a handle on each leading edge.
    #
    # Only the left leaf carries the interface.  A rating plate and a set of status lights on each
    # of the two doors reads as two machines bolted together, which is exactly how it looked when
    # both leaves took the same texture; a real two-door machine has one of each and ventilation on
    # both, which is what the plain leaf is.
    for side, x0, x1 in ((-1, -0.405, -0.010), (1, 0.010, 0.405)):
        clad_box(mesh, 'door', (x0, 0.095, face - 0.035), (x1, body_y - 0.04, face),
                 {'north': 'cabinet_door' if side < 0 else 'cabinet_leaf', '*': 'cabinet'})
        handle_x = x1 - 0.055 if side < 0 else x0 + 0.030
        box(mesh, mesh.faces('door', 'frame'), (handle_x, 0.44, face - 0.062),
            (handle_x + 0.025, 0.60, face - 0.035), uv_scale=0.12)
        # the hinges, on the outer edge
        hinge_x = x0 if side < 0 else x1 - 0.012
        for y in (0.16, body_y - 0.12):
            box(mesh, mesh.faces('door', 'steel'), (hinge_x, y, face - 0.048),
                (hinge_x + 0.012, y + 0.055, face - 0.035), uv_scale=0.1)
    box(mesh, mesh.faces('cabinet', 'frame'), (-0.012, 0.095, face - 0.036),
        (0.012, body_y - 0.04, face - 0.030), uv_scale=0.1)

    # The screen, on the *left leaf* and on the front of its bezel and nowhere else.
    #
    # Two faults it used to have. It was lit on all six faces, so the machine appeared to have four
    # displays and a lit underside. And it ran from x -0.28 to 0.02, which crosses the centre stile at
    # x -0.012 to 0.012 - so the interface sat astride the joint between the two doors and moved with
    # neither of them. It is inside the left leaf's own span now, which is -0.405 to -0.010.
    clad_box(mesh, 'display', (-0.325, 0.66, face - 0.048), (-0.045, 0.80, face - 0.036),
             {'north': 'display', '*': 'cabinet'})

    # The roof fans: two guarded impellers, which is how a station inverter exhausts.
    #
    # Rebuilt, because what was here was a flat disc with six bars laid across it and five flat plates
    # for an impeller - which is not what a fan looks like from any angle.  A real one has a spun rim, a
    # wire finger guard of concentric rings on radial spokes, a hub, and blades that are *pitched*: a
    # flat plate turning about its own axis moves no air, and a fan drawn with flat blades reads as a
    # paper windmill.
    for x in (-0.22, 0.22):
        pivot(mesh, 'fan_%s' % ('west' if x < 0 else 'east'), (x, body_y + 0.040, -0.01))
        guard = mesh.faces('fan_guard', 'steel')
        centre = (x, body_y + 0.030, -0.01)
        # the rim the whole assembly is bolted into: a spun ring, and the collar it stands in
        cylinder(mesh, guard, centre, 'y', 0.112, 0.007, sides=ROUND, uv_scale=0.4)
        cylinder(mesh, guard, (x, body_y + 0.037, -0.01), 'y', 0.104, 0.006, sides=ROUND,
                 uv_scale=0.4)
        # the finger guard: three concentric rings on eight spokes, in wire rather than in bar
        for radius in (0.036, 0.064, 0.092):
            ring = arc((x, body_y + 0.050, -0.01), radius, (0, 2), 0.0, 360.0, FITTING)
            tube(mesh, guard, ring + [ring[0]], 0.0035, sides=8, uv_scale=1.0)
        for i in range(8):
            angle = math.radians(i * 45.0)
            tube(mesh, guard, [(x + math.cos(angle) * 0.016, body_y + 0.050,
                                -0.01 + math.sin(angle) * 0.016),
                               (x + math.cos(angle) * 0.100, body_y + 0.050,
                                -0.01 + math.sin(angle) * 0.100)], 0.0035, sides=8, uv_scale=1.0)
        # the four bolts that hold the rim down
        for i in range(4):
            angle = math.radians(45.0 + i * 90.0)
            bolt(mesh, guard, (x + math.cos(angle) * 0.106, body_y + 0.037,
                               -0.01 + math.sin(angle) * 0.106), 'y', 0.006, 0.010, uv_scale=0.08)

    for x, name in ((-0.22, 'rotate_fan_west'), (0.22, 'rotate_fan_east')):
        fan = mesh.faces(name, 'steel')
        hub = (x, body_y + 0.032, -0.01)
        # the hub: the motor's own can, closed at the top where the spinner is
        cylinder(mesh, fan, (x, body_y + 0.030, -0.01), 'y', 0.026, 0.010, sides=FITTING,
                 uv_scale=0.2, caps=fan)
        cylinder(mesh, fan, (x, body_y + 0.041, -0.01), 'y', 0.020, 0.004, sides=FITTING,
                 uv_scale=0.2, taper=0.7, caps=fan, cap_ends=(1,))
        # seven blades, each pitched thirty degrees about its own radius and then carried round the hub
        for i in range(7):
            box(mesh, fan, (x + 0.022, body_y + 0.028, -0.038), (x + 0.098, body_y + 0.034, 0.018),
                uv_scale=0.2,
                rot=(((x + 0.060, body_y + 0.031, -0.01), 'x', 30.0),
                     (hub, 'y', i * 360.0 / 7.0)))

    # the side louvre banks, on the outside of the cabinet's own face rather than inside it
    for x, side in ((0.44, 'east'), (-0.4425, 'west')):
        clad_box(mesh, 'grille', (x, 0.30, -0.24), (x + 0.0025, 0.86, 0.22),
                 {side: 'vent', '*': 'cabinet'})

    # The insulator on the roof: this is where the plant joins the grid.  The mod's own
    # ``pin_insulator``, in brown glazed porcelain like every other one - it was wearing the
    # *instrument* material, which is a white painted enclosure, so the one fitting a player looks at
    # from a metre away was the one that did not match any of the others.
    steel = mesh.faces('hardware', 'steel')
    # two thousandths above the roof rather than exactly on it: two faces on one plane flicker
    #
    # At the right-hand end of the roof, which is where a station machine's terminal box is - and, more
    # to the point, clear of everything else up there. It used to stand at (0.30, 0.10), which is inside
    # the east fan's guard: the fitting a player clicks a wire onto was sitting on a spinning impeller.
    box(mesh, steel, (0.356, body_y + 0.002, 0.096), (0.444, body_y + 0.018, 0.184), uv_scale=0.2)
    pin_insulator(mesh, mesh.faces('insulator', 'porcelain'), steel,
                  (0.40, body_y + 0.050, 0.14), 0.108)

    # The direct-current section, drawn only when a combiner box has been fitted into the cabinet.
    #
    # A compartment across the bottom of the front, which is where a station inverter's own DC
    # section is: a row of fuse ways behind a window and a gland plate under them.  It is the whole
    # visible difference between a machine that can take a string and one that cannot.
    clad_box(mesh, 'section', (-0.30, 0.10, face - 0.075), (0.30, 0.42, face - 0.035),
             {'north': 'dc_section', 'up': 'frame', '*': 'cabinet'})
    glands = mesh.faces('section', 'steel')
    for i in range(5):
        cylinder(mesh, glands, (-0.22 + i * 0.11, 0.078, face - 0.055), 'y', 0.016, 0.025,
                 sides=FITTING, uv_scale=0.2, caps=glands)

    # where the direct current comes in, one run per side, drawn only for the sides it comes in from
    stubs(mesh, 'entry')
    return mesh


# ------------------------------------------------------------------ the combiner box

def combiner():
    """A string combiner on a post: an enclosure, a hood, a window, and the switch that isolates it.

    Authored at the size the collision box claims, which is a box about waist high on this mod's
    scale rather than the eight hundred millimetres a real one is.  Everything in this mod is drawn
    for legibility rather than to scale, and the collision shape agrees with the drawing, which is
    the part that matters.

    The handle is its own rotating group so the renderer can put it up or down off the block state.
    A load-break switch reads at a distance, which is the whole reason it is drawn: a player walking
    a field can see which group is isolated.
    """
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

    # the gland plate underneath, where every string arrives: one row of them, which is what the
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
    # the riser up the post, at exactly the cross-section of the run it continues - a join that
    # changes thickness halfway is the one thing a player's eye lands on.  From the top of the hub
    # rather than from the ground, so it does not share a volume with it.
    clad_box(mesh, 'post', (-LEAD, LEAD, 0.0), (LEAD, box_y0 + 0.015, LEAD),
             {'south': 'dc_cable', '*': 'dc_jacket'})
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
    """A ten-metre mast with nine instruments, each at the height its own standard puts it.

    A block is ten metres throughout this mod, so a one-block mast is a ten-metre one - which is
    exactly where the world's weather services measure wind, and where the anemometer goes.
    Everything else belongs much lower:

      * radiometers on a boom at 3.5 m, pointing away from the mast so its shadow cannot fall on them
      * the radiation shield at 2 m, which is the standard screen height for air temperature
      * the snow gauge on its own arm at 2 m looking down at clear ground - the same two metres
        MetStationBlockEntity works its depth out from
      * wind at the top, clear of all of it

    A real mast is a tube in two sections with a coupling, standing on a hinged base plate, with a
    logger enclosure at chest height and a lightning finial above everything.  All four are here:
    they are what a met mast looks like from twenty metres, which is the distance it is seen from.
    """
    mesh = Mesh()

    wind_y = 1.0
    radiometer_y = 0.35
    screen_y = 0.20
    snow_y = 0.20

    mast = mesh.faces('mast', 'steel')
    ends = mesh.faces('mast', 'steel_end')
    plate = mesh.faces('mast', 'plate')
    # Two sections with a coupling, tapering: the lower one heavier than the upper.
    #
    # Only the ends anything can see are capped.  The four internal ones - where a section meets the
    # coupling - would be four discs on two planes with the tube's own wall in front of them, which is
    # exactly the coplanar pair that flickers, and there were two hundred and sixty-six of them here.
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

    # the logger: a small enclosure strapped to the mast at chest height, where it is read from
    # plain sheet rather than the inverter's door: that texture is a picture with a frame drawn round
    # it, and six tenths of a framed picture is a frame with its edges cut off
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
    # the screen boom, lower down
    box(mesh, boom, (-0.016, screen_y - 0.016, 0.030), (0.016, screen_y + 0.016, 0.30), uv_scale=0.3)
    cylinder(mesh, boom, (0.0, screen_y, 0.0), 'y', 0.044, 0.026, sides=FITTING, uv_scale=0.2,
             caps=boom)

    def radiometer(name, z, radius, dome_radius, ring=False, downward=False):
        """One radiometer: a machined body on three levelling feet, under a glass dome.

        Thirty-two sides rather than eighty, and that is the rule this whole mod's geometry follows
        rather than an exception to it: the turbine's tower is eighty because it is twelve blocks of
        steel a player stands under, and its insulator is thirty-two because it is half a block.  A
        pyranometer dome is six hundredths of a block across.  Eighty sides on it would be sixty
        faces nobody can resolve, on the model that already carries the most of them.
        """
        body = mesh.faces(name, 'instrument')
        glass = mesh.faces(name, 'dome')
        cylinder(mesh, body, (0.0, radiometer_y + 0.030, z), 'y', radius, 0.018, sides=FITTING,
                 uv_scale=0.4, caps=body)
        # The sun shield: the white disc every pyranometer wears round its dome.
        #
        # Fifteen per cent proud of the body rather than thirty-five: at thirty-five the three
        # instruments' shields overlapped each other on the boom, which is a hundred and eight coplanar
        # faces and, on a real boom, three instruments that cannot be levelled.
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
            # an albedometer is two pyranometers, one of them upside down
            # an albedometer is two pyranometers, one of them upside down: the second dome hangs off
            # the underside of the same body, two thousandths inside it so the two are not coplanar
            hemisphere(mesh, glass, (0.0, radiometer_y + 0.010, z), dome_radius, sides=FITTING,
                       rings=5, up=-1, squash=0.85, uv_scale=1.0)

    radiometer('pyranometer', 0.155, 0.050, 0.030)
    radiometer('diffuse', 0.295, 0.050, 0.028, ring=True)
    radiometer('albedometer', 0.425, 0.044, 0.024, downward=True)

    shield = mesh.faces('shield', 'instrument')
    # a naturally aspirated radiation shield: a stack of plates on three tie rods, each plate a
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
        # looks straight down into.  A hemisphere is symmetric about its own axis, so it is placed by
        # turning its centre rather than its geometry - and its arm, which is not, is turned by the
        # box's own rotation.
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
HARNESS_MATERIALS = ('cabinet', 'cabinet_top', 'dc_cable', 'dc_jacket')
STRUCTURE = ('steel', 'steel_end', 'plate', 'frame')

MODELS = [
    ('pv_flat', flat_table, STRUCTURE + ('concrete',) + HARNESS_MATERIALS + LAMINATE_MATERIALS),
    ('pv_tilt', tilted_rack, STRUCTURE + HARNESS_MATERIALS + LAMINATE_MATERIALS),
    ('pv_track', single_axis, STRUCTURE + HARNESS_MATERIALS + LAMINATE_MATERIALS),
    ('pv_dual', dual_axis, STRUCTURE + HARNESS_MATERIALS + LAMINATE_MATERIALS),
    ('pv_inverter', inverter, STRUCTURE + ('cabinet', 'cabinet_door', 'cabinet_leaf', 'cabinet_top', 'vent',
                                           'display', 'instrument', 'porcelain', 'dc_cable', 'dc_jacket',
                                           'dc_section', 'concrete')),
    ('pv_combiner', combiner, STRUCTURE + ('cabinet', 'cabinet_top', 'combiner_door', 'switch',
                                           'dc_cable', 'dc_jacket')),
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
