#!/usr/bin/env python3
"""The two switches: a 24 kV air-break disconnector and a 24 kV vacuum circuit breaker.

    python3 tools/gen_switch_models.py

They are two objects because they do two jobs that cannot be done by one device, and the models say which
is which.  A **disconnector** exists to make a gap you can see: two bare contacts in air, a blade between
them, and an operating handle a linesman throws by hand.  It cannot break load - an arc across bare blades
in air would not go out - so its blade is the whole of it, and when it is open you can see that it is.  A
**circuit breaker** exists to break load and fault current, so its contacts part inside a sealed vacuum
bottle: nothing about it moves where you can see it, and all it can show you is a mechanical flag.  That
pair is why a substation has both, and in that order.

Both are one block: a 24 kV three-pole unit is about a metre and a half across, and a block is 2.5 m at
this mod's scale.  The line runs along z, the three poles are spread along x - the same convention the
towers use, so a switch drops into a line without turning it.

Group names are a contract: insulator_1..3 are the line side and insulator_4..6 the load side, in the order
ObjDefinitions names them, and a wire is stored against the index.  rotate_* is what SwitchgearRenderer
turns and pivot_* is where it measures the hinge from.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from modellib import (FITTING, Mesh, angle, bolt, box, channel, clad_box, cylinder, emit,   # noqa: E402
                      pivot, sheds, square_uv)

OUT = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')

MATERIALS = {
    'steel': 'tower_steel.png',
    'plate': 'tower_plate.png',
    'porcelain': 'porcelain_brown.png',
    'concrete': 'box_plinth.png',
    'blade': 'switch_blade.png',
    'epoxy': 'switch_epoxy.png',
    'cabinet': 'pv_cabinet.png',
    'cabinet_top': 'pv_cabinet_top.png',
    'flag': 'switch_flag.png',
}

# ---------------------------------------------------------------- shared figures

# The pad both units stand on.
PAD_HALF_X = 0.45
PAD_HALF_Z = 0.24
PAD_TOP = 0.030

# Where the three poles are, and how far apart the two sides of each are.  A 24 kV unit needs 320 mm of
# clearance between phases and 250 mm across the open gap; at 2.5 m to the block those are 0.128 and 0.10,
# and this is comfortably over both.
POLES = (-0.27, 0.0, 0.27)
REACH = 0.155

# The two bands of switch_flag.png - red closed over green open - and the rects are the other way round
# because the mod's own renderer does 1 - v, so the *lower* rect samples the file's *upper* band.
FLAG_SHUT, FLAG_OPEN = (0.0, 0.5, 1.0, 1.0), (0.0, 0.0, 1.0, 0.5)


def pad(mesh):
    """The concrete the frame is bolted down to, so neither unit stands on grass."""
    concrete = mesh.faces('pad', 'concrete')
    box(mesh, concrete, (-PAD_HALF_X, 0.0, -PAD_HALF_Z), (PAD_HALF_X, PAD_TOP, PAD_HALF_Z),
        uv_scale=0.7)


def palm(mesh, group, centre):
    """The palm a conductor is bolted to, on top of a post: a flat pad and the two bolts through it.

    In the insulator's own group on purpose - a wire is anchored on the top of the fitting it hangs from
    (ObjBoundingBoxRegistry.getTopSafe), so the palm has to be part of what that box measures.
    """
    x, y, z = centre
    faces = mesh.faces(group, 'blade')
    box(mesh, faces, (x - 0.030, y, z - 0.024), (x + 0.030, y + 0.012, z + 0.024), uv_scale=0.25)
    for side in (-0.014, 0.014):
        bolt(mesh, faces, (x + side, y + 0.012, z), 'y', 0.006, 0.014, uv_scale=0.12)

    return y + 0.012


def fixed_contact(mesh, group, x, y, z):
    """The upstanding contact a blade closes onto, on the line-side post: what the gap is measured across."""
    box(mesh, mesh.faces(group, 'blade'), (x - 0.013, y, z - 0.010), (x + 0.013, y + 0.042, z + 0.010),
        uv_scale=0.2)


def hinge_boss(mesh, group, x, y, z):
    """The hinge the blade turns on, on the load-side post: the pivot and the connection both."""
    faces = mesh.faces(group, 'blade')
    cylinder(mesh, faces, (x, y, z), 'x', 0.020, 0.026, sides=FITTING, uv_scale=0.3, caps=faces)


# ---------------------------------------------------------------- the disconnector

FRAME_HALF_X = 0.41
FRAME_HALF_Z = 0.18
FRAME_TOP = PAD_TOP + 0.050
POST_HEIGHT = 0.20
POST_RADIUS = 0.044
# Where the blade hinges: on the load-side post's head, which is also where the torque shaft runs.
HINGE_Y = FRAME_TOP + POST_HEIGHT + 0.056
BLADE_HALF = 0.013


def disconnector_frame(mesh):
    """A galvanised base frame: two channels along the line and a cross member under each pole."""
    steel = mesh.faces('frame', 'steel')
    plate = mesh.faces('frame', 'plate')
    for z in (-FRAME_HALF_Z, FRAME_HALF_Z - 0.055):
        channel(mesh, steel, (-FRAME_HALF_X, PAD_TOP, z), (FRAME_HALF_X, FRAME_TOP, z + 0.055),
                along='x', opening='down', uv_scale=0.35)
    for x in POLES:
        channel(mesh, steel, (x - 0.026, PAD_TOP, -FRAME_HALF_Z + 0.055),
                (x + 0.026, FRAME_TOP, FRAME_HALF_Z - 0.055), along='z', opening='down', uv_scale=0.3)
    # the holding-down bolts, at the four corners, and the earth stud
    for x in (-FRAME_HALF_X + 0.030, FRAME_HALF_X - 0.030):
        for z in (-FRAME_HALF_Z + 0.026, FRAME_HALF_Z - 0.026):
            bolt(mesh, plate, (x, FRAME_TOP, z), 'y', 0.008, 0.016, uv_scale=0.12)

    # the earth stud, off the frame's end rather than under a pole: on the centreline its underside sat on
    # the same plane as the middle post's own bottom shed
    earth = mesh.faces('earth', 'blade')
    box(mesh, earth, (-0.370, FRAME_TOP, -FRAME_HALF_Z - 0.014),
        (-0.330, FRAME_TOP + 0.030, -FRAME_HALF_Z), uv_scale=0.2)
    bolt(mesh, earth, (-0.350, FRAME_TOP + 0.030, -FRAME_HALF_Z - 0.007), 'y', 0.007, 0.012,
         uv_scale=0.12)


def disconnector_poles(mesh):
    """Three poles, each a post insulator either side of the gap.

    The two sides are not the same fitting: the line side carries the fixed contact the blade closes onto,
    the load side carries the hinge it turns on.  That is what makes the gap a gap on one side only.
    """
    for index, x in enumerate(POLES):
        for side, z in ((0, -REACH), (3, REACH)):
            group = 'insulator_%d' % (index + 1 + side)
            sheds(mesh, mesh.faces(group, 'porcelain'), (x, FRAME_TOP, z), POST_RADIUS, POST_HEIGHT,
                  count=4, uv_scale=1.0)
            # the cap the post is cemented into, and the palm on top of it
            cylinder(mesh, mesh.faces(group, 'steel'), (x, FRAME_TOP + POST_HEIGHT + 0.010, z), 'y',
                     POST_RADIUS * 0.72, 0.014, sides=FITTING, uv_scale=0.4,
                     caps=mesh.faces(group, 'steel'))
            top = palm(mesh, group, (x, FRAME_TOP + POST_HEIGHT + 0.024, z))
            if side == 0:
                fixed_contact(mesh, group, x, top, z)
            else:
                hinge_boss(mesh, group, x, HINGE_Y, z)


def disconnector_blades(mesh):
    """The three blades and the handle that swings them, all on the one hinge axis.

    One group and one pivot for the three: they hinge on the same line, and a rotation about x does not care
    where along x a part is - so three markers would be three copies of one figure.  The blade is twin, a bar
    either side of the fixed contact, which is how a real one grips: two surfaces and a spring between them.
    """
    pivot(mesh, 'blade', (0.0, HINGE_Y, REACH))
    blade = mesh.faces('rotate_blade', 'blade')
    for x in POLES:
        for side in (-0.028, 0.016):
            box(mesh, blade, (x + side, HINGE_Y - BLADE_HALF, -REACH - 0.008),
                (x + side + 0.012, HINGE_Y + BLADE_HALF, REACH - 0.010), uv_scale=0.4)
        # the web that ties the two bars together over the hinge, clear of the boss they turn on
        box(mesh, blade, (x - 0.028, HINGE_Y + 0.020, REACH - 0.026),
            (x + 0.028, HINGE_Y + 0.030, REACH + 0.026), uv_scale=0.3)
        # the contact spring's cap on the far end, which is the fattest part of a blade
        box(mesh, blade, (x - 0.032, HINGE_Y - 0.017, -REACH - 0.008),
            (x + 0.032, HINGE_Y + 0.017, -REACH + 0.014), uv_scale=0.3)

    # The operating handle, on the same axis and turning with it: pulled down, the blades stand up.
    steel = mesh.faces('rotate_blade', 'steel')
    box(mesh, steel, (FRAME_HALF_X - 0.004, HINGE_Y - 0.155, REACH - 0.011),
        (FRAME_HALF_X + 0.024, HINGE_Y - 0.022, REACH + 0.011), uv_scale=0.3)
    cylinder(mesh, steel, (FRAME_HALF_X + 0.010, HINGE_Y - 0.165, REACH), 'x', 0.016, 0.028,
             sides=FITTING, uv_scale=0.25, caps=steel)


def disconnector_shaft(mesh):
    """The torque shaft that gangs the three poles, and the bracket that carries its outboard end.

    Along the hinge axis and inside the hinge bosses, which is what a ganged disconnector really is: one
    shaft through three hinges, so the three blades cannot be in different positions.
    """
    steel = mesh.faces('shaft', 'steel')
    cylinder(mesh, steel, (0.0, HINGE_Y, REACH), 'x', 0.013, FRAME_HALF_X + 0.010, sides=FITTING,
             uv_scale=0.6, uv_along=5.0, caps=steel)
    # the outboard bearing, on a bracket off the frame's end
    angle(mesh, steel, (FRAME_HALF_X - 0.014, FRAME_TOP, REACH - 0.030),
          (FRAME_HALF_X + 0.014, HINGE_Y - 0.024, REACH + 0.030), along='y', web=0.42, uv_scale=0.25)
    cylinder(mesh, steel, (FRAME_HALF_X, HINGE_Y, REACH), 'x', 0.022, 0.014, sides=FITTING,
             uv_scale=0.25, caps=steel)
    # the locking hasp the handle is padlocked to, which is how a disconnector is proved open
    hasp = mesh.faces('shaft', 'blade')
    box(mesh, hasp, (FRAME_HALF_X - 0.010, FRAME_TOP, REACH + 0.032),
        (FRAME_HALF_X + 0.018, FRAME_TOP + 0.044, REACH + 0.042), uv_scale=0.2)


def disconnector():
    """A 24 kV three-pole vertical-break air disconnector."""
    mesh = Mesh()
    pad(mesh)
    disconnector_frame(mesh)
    disconnector_poles(mesh)
    disconnector_shaft(mesh)
    disconnector_blades(mesh)
    return mesh


# ---------------------------------------------------------------- the breaker

CABINET_HALF_X = 0.35
CABINET_HALF_Z = 0.165
CABINET_TOP = PAD_TOP + 0.235
# The moulded pole: a vacuum bottle in cast epoxy, with sheds down its flanks.
POLE_RADIUS = 0.052
POLE_HEIGHT = 0.200


def breaker_cabinet(mesh):
    """The operating mechanism: a sheet-steel box with the spring charge, the trip coils and the counter."""
    lo, hi = (-CABINET_HALF_X, PAD_TOP, -CABINET_HALF_Z), (CABINET_HALF_X, CABINET_TOP, CABINET_HALF_Z)
    clad_box(mesh, 'cabinet', lo, hi, {'up': 'cabinet_top', '*': 'cabinet'}, uv=square_uv(lo, hi, 2.0))
    # the door, its two hinges and the handle: the front is the face a player reads the unit off
    door_lo = (-CABINET_HALF_X + 0.045, PAD_TOP + 0.030, -CABINET_HALF_Z - 0.012)
    door_hi = (CABINET_HALF_X - 0.045, CABINET_TOP - 0.030, -CABINET_HALF_Z)
    # clad_box rather than box: only clad_box takes a uv rect per face, and square_uv is a rect per face
    clad_box(mesh, 'door', door_lo, door_hi, {'*': 'cabinet'}, uv=square_uv(door_lo, door_hi, 2.0))
    steel = mesh.faces('door', 'steel')
    for y in (PAD_TOP + 0.060, CABINET_TOP - 0.070):
        box(mesh, steel, (-CABINET_HALF_X + 0.038, y, -CABINET_HALF_Z - 0.016),
            (-CABINET_HALF_X + 0.058, y + 0.020, -CABINET_HALF_Z - 0.006), uv_scale=0.15)
    cylinder(mesh, steel, (CABINET_HALF_X - 0.070, (PAD_TOP + CABINET_TOP) * 0.5, -CABINET_HALF_Z - 0.024),
             'z', 0.010, 0.012, sides=FITTING, uv_scale=0.15, caps=steel)
    # the earth stud, low on the flank where every cabinet carries one
    earth = mesh.faces('earth', 'blade')
    box(mesh, earth, (-CABINET_HALF_X - 0.014, PAD_TOP + 0.030, -0.020),
        (-CABINET_HALF_X, PAD_TOP + 0.070, 0.020), uv_scale=0.2)


def breaker_flags(mesh):
    """The mechanical indicator: the one thing a vacuum breaker can show you.

    Two plates in the same place, one drawn per state.  There is no visible gap to look at inside a sealed
    bottle, which is exactly why a disconnector is a separate object.
    """
    y = CABINET_TOP - 0.075
    for group, band in (('flag_shut', FLAG_SHUT), ('flag_open', FLAG_OPEN)):
        box(mesh, mesh.faces(group, 'flag'),
            (CABINET_HALF_X - 0.140, y, -CABINET_HALF_Z - 0.019),
            (CABINET_HALF_X - 0.086, y + 0.054, -CABINET_HALF_Z - 0.014), uv=band)


def breaker_poles(mesh):
    """Three moulded poles on the mechanism box, each with a terminal bushing either side of its head.

    Upright, because that is where a vacuum bottle goes: the interrupter is inside the epoxy and the two
    terminals come out of the head, one to the line and one to the load.  Nothing about it moves where a
    player can see it, which is the whole reason the disconnector next to it exists.
    """
    for index, x in enumerate(POLES):
        group = 'pole_%d' % (index + 1)
        epoxy = mesh.faces(group, 'epoxy')
        base = CABINET_TOP
        head = base + POLE_HEIGHT
        # the pole: a cast column with sheds down it, and the bottle inside where it cannot be seen
        cylinder(mesh, epoxy, (x, (base + head) * 0.5, 0.0), 'y', POLE_RADIUS, POLE_HEIGHT * 0.5,
                 sides=FITTING, uv_scale=1.0, uv_along=2.0)
        for i in range(3):
            y = base + POLE_HEIGHT * (0.22 + i * 0.26)
            cylinder(mesh, epoxy, (x, y, 0.0), 'y', POLE_RADIUS * 1.26, 0.009, sides=FITTING,
                     uv_scale=0.6, taper=0.80)
        # the head the two terminals come out of, and the skirt where it meets the box
        cylinder(mesh, epoxy, (x, head + 0.020, 0.0), 'y', POLE_RADIUS * 1.10, 0.020, sides=FITTING,
                 uv_scale=0.8, caps=epoxy)
        cylinder(mesh, epoxy, (x, base + 0.010, 0.0), 'y', POLE_RADIUS * 1.30, 0.010, sides=FITTING,
                 uv_scale=0.6, caps=epoxy)

        for side, z in ((0, -REACH), (3, REACH)):
            fitting = 'insulator_%d' % (index + 1 + side)
            porcelain = mesh.faces(fitting, 'porcelain')
            # the bushing out of the head to the terminal: a cast stalk with two sheds on it
            cylinder(mesh, porcelain, (x, head + 0.020, z * 0.5), 'z', POLE_RADIUS * 0.56, REACH * 0.5,
                     sides=FITTING, uv_scale=0.8, uv_along=2.0)
            # three sheds on the stalk, as wide as the pole itself: a bushing that is not obviously an
            # insulator reads as a copper pipe, which is what this looked like when they were half the size
            for at in (0.30, 0.58, 0.86):
                cylinder(mesh, porcelain, (x, head + 0.020, z * at), 'z', POLE_RADIUS * 1.04, 0.010,
                         sides=FITTING, uv_scale=0.6)
            cylinder(mesh, porcelain, (x, head + 0.020, z), 'z', POLE_RADIUS * 0.62, 0.012,
                     sides=FITTING, uv_scale=0.5, caps=porcelain)
            palm(mesh, fitting, (x, head + 0.020 + POLE_RADIUS * 0.62, z))


def breaker():
    """A 24 kV three-pole outdoor vacuum circuit breaker."""
    mesh = Mesh()
    pad(mesh)
    breaker_cabinet(mesh)
    breaker_poles(mesh)
    breaker_flags(mesh)
    return mesh


MODELS = [
    ('mv_disconnector', disconnector),
    ('mv_breaker', breaker),
]


def main():
    for _ in emit(OUT, MODELS, MATERIALS, 'gen_switch_models.py'):
        pass


if __name__ == '__main__':
    main()
