#!/usr/bin/env python3
"""Generates the OBJ geometry for the photovoltaic blocks.

Why a generator rather than a modelling package: this geometry is almost entirely
boxes at right angles, and the few that are not are a plate at a fixed tilt and a
handful of cups on a hub.  Written out from a script it is reviewable in a diff,
reproducible, and it cannot drift away from the figures it is built from - the
tilted rack really is at the mounting's 25 degrees because the number comes from
the same place the physics reads it.

    python3 tools/gen_pv_models.py

Writes into src/main/resources/assets/electricity/models/.

What the renderer needs from the output
---------------------------------------
The OBJ pipeline splits by object and then by material, and keys each group
``<object>_<material>``.  So every part that has to move independently is its own
``o`` block, and the renderer matches on the object prefix.  Anything named
``rotate_*`` is a moving part and the renderer is expected to know its pivot.

Everything is authored in block units with x and z running -0.5 to 0.5 and y from
0 upwards, because ObjRenderUtil puts the frame at the block's centre in x and z
and at its floor in y.  Tilted and tracked planes are authored *flat*: their tilt
is a matrix at draw time, never baked into the vertices, since the buffer cache is
keyed by block position and would otherwise have to be rebuilt every frame.
"""

import math
import os

OUT = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')

# Materials, and the texture each one resolves to.  ObjModel prefixes
# textures/block/ to whatever map_Kd names, so these are bare file names.
MATERIALS = {
    'module': 'pv_module.png',
    'module_back': 'pv_module_back.png',
    'module_edge': 'pv_module_edge.png',
    'frame': 'pv_frame.png',
    'steel': 'pv_steel.png',
    'steel_end': 'pv_steel_end.png',
    'cabinet': 'pv_cabinet.png',
    'cabinet_door': 'pv_cabinet_door.png',
    'cabinet_top': 'pv_cabinet_top.png',
    'vent': 'pv_vent.png',
    'display': 'pv_display.png',
    'instrument': 'pv_instrument.png',
    'dome': 'pv_dome.png',
    'dc_cable': 'dc_harness.png',
    'combiner_door': 'pv_combiner_door.png',
    'switch': 'pv_switch.png',
}

# Which material each face of a laminate carries.  A module is not one material: the sun
# side is cells, the back is a backsheet with a junction box on it, and the four edges are
# the aluminium frame that clamps the glass.  Giving all six the cell texture is what made
# a panel look like it was cells all the way through when you stood beside it.
LAMINATE = {'up': 'module', 'down': 'module_back', '*': 'module_edge'}

# The tilt a fixed rack is built at, in degrees.  The same figure PvMounting
# declares, restated here rather than imported because a Python script cannot read
# a Java enum - so if one changes the other has to, and this comment is the note
# saying so.
FIXED_TILT_DEG = 25.0

# Half-width of the drive bay at the centre of a tracker, in blocks.
#
# A plane rotating about an axis sweeps a disc of radius equal to its own semi-width, so
# any fixed part inside that disc gets swept through.  Rather than trying to thread the
# piers and the drive between the module edges - which cannot be done, because the plane
# passes through every angle - the fixed parts live in a bay at the centre of the row and
# the modules stop short of it.  Nothing can then collide at any angle.
#
# It is also what a real independent-row tracker looks like: one bay with no module in it,
# because that is where the slew drive is.
DRIVE_BAY = 0.11


class Mesh:
    """Accumulates vertices, normals, texture coordinates and faces for one OBJ file."""

    def __init__(self):
        self.v = []
        self.vn = []
        self.vt = []
        self.objects = []  # (name, material, [faces]) with faces as index triples

    def _index(self, table, value):
        # OBJ indices are 1-based and shared across the whole file, so identical
        # vertices are pooled rather than repeated: it keeps the files a third of
        # the size and costs nothing to read back
        key = tuple(round(c, 6) for c in value)
        for i, existing in enumerate(table):
            if existing == key:
                return i + 1
        table.append(key)
        return len(table)

    def add_object(self, name, material):
        self.objects.append((name, material, []))
        return self.objects[-1][2]

    def faces(self, name, material):
        """The face list for one object under one material, created once and reused.

        A part whose faces are not all the same material has to be written as one section
        per material.  That is safe because the renderer keys groups by object *and*
        material and poses them by the object's name prefix, so every material of
        ``rotate_modules`` gets the same rotation - and it is why the same object name may
        appear more than once in the file.
        """
        for existing_name, existing_material, faces in self.objects:
            if existing_name == name and existing_material == material:
                return faces

        return self.add_object(name, material)

    def quad(self, faces, corners, normal, uvs):
        indices = []
        n = self._index(self.vn, normal)
        for corner, uv in zip(corners, uvs):
            indices.append((self._index(self.v, corner), self._index(self.vt, uv), n))
        faces.append(indices)

    def write(self, path, mtl_name):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w') as f:
            f.write('# generated by tools/gen_pv_models.py - do not edit by hand\n')
            f.write('mtllib %s\n' % mtl_name)
            for x, y, z in self.v:
                f.write('v %.6f %.6f %.6f\n' % (x, y, z))
            for x, y, z in self.vn:
                f.write('vn %.4f %.4f %.4f\n' % (x, y, z))
            for u, v in self.vt:
                f.write('vt %.6f %.6f\n' % (u, v))
            for name, material, faces in self.objects:
                if not faces:
                    continue
                f.write('o %s\n' % name)
                f.write('usemtl %s\n' % material)
                for face in faces:
                    f.write('f ' + ' '.join('%d/%d/%d' % idx for idx in face) + '\n')


def write_mtl(path, materials):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write('# generated by tools/gen_pv_models.py - do not edit by hand\n')
        for name in materials:
            f.write('\nnewmtl %s\n' % name)
            f.write('Ns 250.000000\nKa 1.000000 1.000000 1.000000\nKs 0.500000 0.500000 0.500000\n')
            f.write('Ke 0.000000 0.000000 0.000000\nNi 1.500000\nd 1.000000\nillum 2\n')
            f.write('map_Kd %s\n' % MATERIALS[name])


def rotate(point, pivot, axis, degrees):
    """Rotates a point about an axis-aligned line through pivot."""
    if degrees == 0.0:
        return point
    a = math.radians(degrees)
    c, s = math.cos(a), math.sin(a)
    x, y, z = point[0] - pivot[0], point[1] - pivot[1], point[2] - pivot[2]
    if axis == 'x':
        y, z = y * c - z * s, y * s + z * c
    elif axis == 'y':
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + pivot[0], y + pivot[1], z + pivot[2])


def pivot(mesh, name, point):
    """Marks where a moving part turns, as a group of its own.

    The renderer used to find a pivot by taking the centre of the rotating group's
    bounding box, which is exact for anything symmetric about its own axis - a torque
    tube, an elevation frame - and wrong for everything else.  Three anemometer cups at
    120 degrees have a bounding box whose centre is nowhere near the mast, so they turned
    about a point beside it and wobbled; a wind vane's box centre sits out by its tail.

    So the pivot is emitted here, where the design decides it, as a zero-size object.  Six
    degenerate quads draw nothing, the bounding box is the point exactly, and the renderer
    reads it instead of inferring it.
    """
    faces = mesh.add_object('pivot_' + name, 'steel')
    box(mesh, faces, point, point)


def stubs(mesh, name='stub'):
    """A run of cable from the middle of the block out to each of the four edges, one group apiece.

    Four groups rather than one rotated four ways, because a renderer draws a group once: the pose map
    is keyed by group name, so the way to draw a stub towards two different sides is to have two.  The
    renderer includes only the sides a run of cable has actually been laid against, which is what makes
    the cable appear to run *into* the machine instead of stopping a pixel short of it over open sand.

    The cross-section is a laid run's own - two pixels across and one tall, one pixel to a conductor,
    centred on the block's axis, so where the two meet there is no seam to see.  Deliberately the
    narrowest thing that still reads as a pair: at a block to ten metres a real cable would not be a
    pixel wide, so everything drawn here is symbolic, and a symbol that shouts is worse than one that
    has to be looked for.  It is the same figure on a run laid across the ground, on the stub into a
    machine, and on the riser up a combiner's post - one number, so nothing swells at a join.

    All four are the same box turned about the block's middle rather than four boxes written out, and
    that is not brevity: this file maps a face's texture along its own x and z, so a stub *written* along
    x would come out with its conductors running across it instead of along it.  Turning the geometry
    turns the mapping with it, and the same quarter turns the renderer uses for a facing are the ones
    used here - north, then ninety degrees a side round to east.
    """
    for side, turn in (('north', 0.0), ('west', 90.0), ('south', 180.0), ('east', 270.0)):
        spin = ((0.0, 0.0, 0.0), 'y', turn)
        box(mesh, mesh.faces('%s_%s' % (name, side), 'dc_cable'),
            (-0.0625, 0.0, -0.5), (0.0625, 0.0625, 0.0), rot=spin)
        # the saddle clipping it down a hand's width out from the machine, which is where a real one is
        box(mesh, mesh.faces('%s_%s' % (name, side), 'steel'),
            (-0.09, 0.0, -0.425), (0.09, 0.085, -0.40), uv_scale=0.3, rot=spin)


def harness(mesh, enclosure, run):
    """The junction box an array grows when a reel of cable is worked into it, and the leads out of it.

    Two things have to be true at once, and the first version of this got one of them at the cost of the
    other.  The *leads* have to be centred on the block's axis, because that is where a laid run of cable
    is and a stub anywhere else can never meet one.  The *box* has to be where a junction box belongs on
    that particular mounting, which is bolted to the racking - and putting it in the middle to match the
    leads stood it up through the glass in the centre of the panel, which looked like damage.

    So they are separate: ``run`` is the length of pair from the middle of the block to the box, at a laid
    run's own cross-section, and ``enclosure`` is where the box sits.  Both are given per mounting,
    because the answer is different for each one - a table has a front rail to bolt it to, a tilted rack
    has room under its high edge, and a tracked row has only the bay at the centre where no module goes.
    """
    stubs(mesh, 'harness')
    box(mesh, mesh.faces('harness', 'dc_cable'), run[0], run[1])
    clad_box(mesh, 'harness', enclosure[0], enclosure[1],
             {'up': 'cabinet_top', '*': 'cabinet'}, uv_scale=0.5)


def clad_box(mesh, name, lo, hi, sides, uv_scale=1.0, rot=None):
    """A box whose six faces are not all the same material.

    ``sides`` maps face names - down, up, north, south, west, east - to materials, with
    ``'*'`` standing for the rest.  Each face is written into its own material's section,
    which is how a module can be cells on top, a backsheet underneath and frame all round
    while still being one part as far as the renderer is concerned.
    """
    for face in ('down', 'up', 'north', 'south', 'west', 'east'):
        material = sides.get(face, sides['*'])
        box(mesh, mesh.faces(name, material), lo, hi, uv_scale=uv_scale, rot=rot, only=face)


def box(mesh, faces, lo, hi, uv_scale=1.0, rot=None, only=None):
    """Six quads, outward normals, each face mapped across the whole texture.

    uv_scale under one insets the mapping, which is how a long rail gets a strip of
    its texture rather than the whole thing stretched along it.  ``only`` emits a single
    named face, which is what lets clad_box split a box across materials.
    """
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    u1 = uv_scale

    corners = {
        'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
        'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        'north': [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)],
        'south': [(x1, y0, z1), (x0, y0, z1), (x0, y1, z1), (x1, y1, z1)],
        'west': [(x0, y0, z1), (x0, y0, z0), (x0, y1, z0), (x0, y1, z1)],
        'east': [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)],
    }
    normals = {
        'down': (0, -1, 0), 'up': (0, 1, 0), 'north': (0, 0, -1),
        'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0),
    }

    # Which corner of the texture goes on which corner of the face, and it is not the same for all six.
    #
    # The corner lists above are wound for outward normals, which is what the renderer needs, and a
    # texture laid on them in the obvious order comes out *mirrored* on all four sides: the first corner
    # of a side face is its low-x (or low-z) end, and viewed from outside the block that end is on the
    # right rather than the left.  Nobody noticed while every side texture was either noise or a pattern
    # of stripes, and then the combiner box got a door with a fuse window on one side of it and a switch
    # escutcheon on the other, and they came out swapped.
    #
    # The lid has the opposite problem: its first corner is the one nearest north, which is the *top* of
    # the picture on screen, while the loader reads texture v from the bottom.  So the sides get their u
    # reversed and the lid gets its v reversed, and the two other faces are left as they were - nothing
    # in this mod has a picture on its underside.
    plain = [(0.0, 0.0), (u1, 0.0), (u1, u1), (0.0, u1)]
    flipped_u = [(u1, 0.0), (0.0, 0.0), (0.0, u1), (u1, u1)]
    flipped_v = [(0.0, u1), (u1, u1), (u1, 0.0), (0.0, 0.0)]
    mapping = {
        'down': plain, 'up': flipped_v,
        'north': flipped_u, 'south': flipped_u, 'west': flipped_u, 'east': flipped_u,
    }

    for face, pts in corners.items():
        if only is not None and face != only:
            continue

        uvs = mapping[face]
        normal = normals[face]
        if rot is not None:
            pivot, axis, degrees = rot
            pts = [rotate(p, pivot, axis, degrees) for p in pts]
            normal = rotate(normal, (0, 0, 0), axis, degrees)
        mesh.quad(faces, pts, normal, uvs)


def cylinder(mesh, faces, centre, axis, radius, half_length, sides=8, uv_scale=1.0, caps=None):
    """A prism standing in for a tube: eight sides reads as round at this scale.

    ``caps`` closes both ends into that face list, which is not decoration: without them a
    mast is a hole when you look down it, and a radiometer has no dome to catch the light.
    Fanned into quads rather than left as one polygon, because the render type consumes
    quads and a stray triangle would shear the whole buffer after it.
    """
    cx, cy, cz = centre
    ring = []
    for i in range(sides):
        a = 2.0 * math.pi * i / sides
        if axis == 'y':
            ring.append((cx + radius * math.cos(a), cz + radius * math.sin(a)))
        elif axis == 'z':
            ring.append((cx + radius * math.cos(a), cy + radius * math.sin(a)))
        else:
            ring.append((cy + radius * math.cos(a), cz + radius * math.sin(a)))

    def point(i, end):
        p, q = ring[i]
        if axis == 'y':
            return (p, cy + end * half_length, q)
        if axis == 'z':
            return (p, q, cz + end * half_length)
        return (cx + end * half_length, p, q)

    for i in range(sides):
        j = (i + 1) % sides
        a, b = point(i, -1), point(j, -1)
        c, d = point(j, 1), point(i, 1)
        nx = (a[0] + c[0]) / 2 - cx
        ny = (a[1] + c[1]) / 2 - cy
        nz = (a[2] + c[2]) / 2 - cz
        length = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
        u = i / sides * uv_scale
        u2 = (i + 1) / sides * uv_scale
        mesh.quad(faces, [a, b, c, d], (nx / length, ny / length, nz / length),
                  [(u, 0.0), (u2, 0.0), (u2, uv_scale), (u, uv_scale)])

    if caps is None:
        return

    normal = {'x': (1.0, 0.0, 0.0), 'y': (0.0, 1.0, 0.0), 'z': (0.0, 0.0, 1.0)}[axis]
    for end in (-1, 1):
        outward = tuple(component * end for component in normal)
        # the ring's own coordinates carried into the texture, so a drawn rim lands on the rim
        def uv(i):
            p, q = ring[i]
            centre_p, centre_q = (cx, cz) if axis == 'y' else (cx, cy) if axis == 'z' else (cy, cz)
            return (0.5 + (p - centre_p) / (2.0 * radius), 0.5 + (q - centre_q) / (2.0 * radius))

        order = range(sides) if end > 0 else range(sides - 1, -1, -1)
        indices = list(order)
        for i in range(1, sides - 2, 2):
            corners = [indices[0], indices[i], indices[i + 1], indices[i + 2]]
            mesh.quad(caps, [point(k, end) for k in corners], outward, [uv(k) for k in corners])


# ---------------------------------------------------------------- flat table

def flat_table():
    """A ballasted table lying flat: two modules in portrait on a low frame.

    Three pixels tall, which is the collision box, so it reads as a panel from
    across a field and is walked over rather than round.
    """
    mesh = Mesh()

    legs = mesh.add_object('legs', 'steel')
    for x in (-0.42, 0.34):
        for z in (-0.42, 0.34):
            box(mesh, legs, (x, 0.0, z), (x + 0.08, 0.035, z + 0.08), uv_scale=0.25)

    frame = mesh.add_object('frame', 'frame')
    box(mesh, frame, (-0.46, 0.035, -0.46), (0.46, 0.075, -0.40), uv_scale=0.4)
    box(mesh, frame, (-0.46, 0.035, 0.40), (0.46, 0.075, 0.46), uv_scale=0.4)
    box(mesh, frame, (-0.46, 0.035, -0.40), (-0.40, 0.075, 0.40), uv_scale=0.4)
    box(mesh, frame, (0.40, 0.035, -0.40), (0.46, 0.075, 0.40), uv_scale=0.4)

    # two modules with a gap between them, which is where a real table's rails run
    clad_box(mesh, 'modules', (-0.46, 0.075, -0.46), (-0.02, 0.11, 0.46), LAMINATE)
    clad_box(mesh, 'modules', (0.02, 0.075, -0.46), (0.46, 0.11, 0.46), LAMINATE)

    # hung on the very front edge rather than out on the glass, which is where it was when it looked
    # right - and the pair runs back to the middle underneath the modules, out of sight
    harness(mesh, ((-0.10, 0.055, 0.435), (0.10, 0.205, 0.50)),
            ((-0.0625, 0.0, 0.0), (0.0625, 0.0625, 0.46)))
    return mesh


# ------------------------------------------------------------- tilted rack

def tilted_rack():
    """A fixed rack at the mounting's own tilt, tipping towards -z.

    Towards -z because the block's default facing is north and PvArrayBlock reads
    the plane's bearing off that facing, so the geometry and the physics have to
    agree about which way is downhill.

    Where the pivot goes
    --------------------
    Not where it is convenient - where it has to be for the tilted plane to fit inside
    the block.  Tipping a plane 0.94 deep by 25 degrees moves its two edges 0.199 up and
    down, so a pivot chosen by eye put the low edge below the block's floor, where it sank
    into the ground, and left the rear legs standing 0.07 taller than the plane they were
    holding - which is the grey posts poking through the panel.

    So the pivot is solved for instead: high enough that the low edge clears the floor,
    low enough that the modules stay under the collision box, and the legs are cut to
    where the frame's underside actually is at each end rather than to a guessed height.
    """
    mesh = Mesh()
    tilt = FIXED_TILT_DEG
    sin_tilt = math.sin(math.radians(tilt))
    cos_tilt = math.cos(math.radians(tilt))

    depth = 0.47
    frame_half = 0.02
    module_thickness = 0.035
    # the lowest corner is the frame's far underside, so the pivot is that clearance plus
    # however far the tilt drops it
    pivot_y = 0.03 + frame_half * cos_tilt + depth * sin_tilt
    pivot = (0.0, pivot_y, 0.0)

    def underside_at(world_z):
        """Height of the frame's underside where it crosses a given z, once tilted.

        The tilt moves z as well as y, so the local coordinate has to be recovered before
        the height can be read off: taking the height at the *local* z instead is what left
        the legs standing a couple of centimetres proud of the frame they hold.
        """
        local_z = (world_z - frame_half * sin_tilt) / cos_tilt
        return pivot_y - frame_half * cos_tilt + local_z * sin_tilt

    legs = mesh.add_object('legs', 'steel')
    # the low pair at the front, the tall pair behind: what makes the plane tilt, each cut
    # to meet the frame rather than pass through it
    for x in (-0.40, 0.32):
        box(mesh, legs, (x, 0.0, -0.44), (x + 0.08, underside_at(-0.40) + 0.005, -0.36), uv_scale=0.3)
        box(mesh, legs, (x, 0.0, 0.36), (x + 0.08, underside_at(0.40) + 0.005, 0.44), uv_scale=0.3)

    frame = mesh.add_object('frame', 'frame')
    box(mesh, frame, (-0.46, pivot_y - frame_half, -depth), (0.46, pivot_y + frame_half, depth),
        uv_scale=0.5, rot=(pivot, 'x', -tilt))

    top = pivot_y + frame_half + module_thickness
    clad_box(mesh, 'modules', (-0.46, pivot_y + frame_half, -depth), (-0.02, top, depth), LAMINATE, rot=(pivot, 'x', -tilt))
    clad_box(mesh, 'modules', (0.02, pivot_y + frame_half, -depth), (0.46, top, depth), LAMINATE, rot=(pivot, 'x', -tilt))

    # under the high edge, where a tilted rack has the most room and a technician can reach it
    harness(mesh, ((-0.10, 0.0, 0.375), (0.10, 0.17, 0.465)),
            ((-0.0625, 0.0, 0.0), (0.0625, 0.0625, 0.40)))
    return mesh


# ---------------------------------------------------- single-axis tracker

def single_axis():
    """A torque tube through a slew drive, with the module plane authored flat.

    The plane is flat because its tilt is a matrix at draw time: the buffer cache
    is keyed by block position and rebuilt only when the light changes, so baking a
    rotation into the vertices would freeze the row wherever it happened to be.

    The tube runs north-south, which is the only axis from which a row can follow a
    sun that travels east to west - and it is why PvArrayBlock forces a tracker to
    that orientation whatever direction the player was facing.

    The drive bay
    -------------
    A plane rotating about an axis sweeps a *disc* of radius equal to its own
    semi-width, so any fixed part inside that disc gets swept through - which is
    exactly what the first version of this model did, putting the drive housing at
    the tube's height where the modules pass over it twice a day.

    The fix is the one real trackers use.  The pier and the drive sit in a bay at the
    centre of the row and the modules stop short of it, so every fixed part lives in
    ``|z| <= DRIVE_BAY`` and every moving part in ``|z| >= DRIVE_BAY``.  Nothing can
    collide at any angle, and the row reads correctly: a real independent-row tracker
    has a bay with no module in it, because that is where the slew drive is.
    """
    mesh = Mesh()
    axis_y = 0.62
    bay = DRIVE_BAY

    pier = mesh.add_object('pier', 'steel')
    box(mesh, pier, (-0.16, 0.0, -0.16), (0.16, 0.05, 0.16), uv_scale=0.4)
    # one central pier rather than a pair, because a pair either side of the bay would
    # stand in the swept disc: within one block a real row has a pier every six metres
    box(mesh, pier, (-0.055, 0.05, -0.075), (0.055, axis_y, 0.075), uv_scale=0.3)

    # the slew drive: a housing round the tube, wholly inside the bay. Its lid is a lid and
    # its two ends are where the tube comes out, so neither is the painted side sheet
    clad_box(mesh, 'motor', (-0.13, axis_y - 0.13, -0.085), (0.13, axis_y + 0.13, 0.085),
             {'up': 'cabinet_top', 'north': 'steel_end', 'south': 'steel_end', '*': 'cabinet'}, uv_scale=0.6)

    pivot(mesh, 'tube', (0.0, axis_y, 0.0))

    tube = mesh.add_object('rotate_tube', 'steel')
    cylinder(mesh, tube, (0.0, axis_y, 0.0), 'z', 0.045, 0.48, uv_scale=0.5,
             caps=mesh.faces('rotate_tube', 'steel_end'))

    # two bays of modules, two rows deep, which is the 2-up portrait layout a real
    # horizontal single axis carries
    rails = mesh.add_object('rotate_rails', 'frame')
    for z0, z1 in ((-0.48, -bay), (bay, 0.48)):
        clad_box(mesh, 'rotate_modules', (-0.44, axis_y + 0.045, z0), (-0.02, axis_y + 0.075, z1), LAMINATE)
        clad_box(mesh, 'rotate_modules', (0.02, axis_y + 0.045, z0), (0.44, axis_y + 0.075, z1), LAMINATE)
        # purlins along the tube rather than across it: a rail spanning the full width
        # would pass through the tube it is supposed to be clamped to
        for x0, x1 in ((-0.42, -0.36), (-0.16, -0.10), (0.10, 0.16), (0.36, 0.42)):
            box(mesh, rails, (x0, axis_y + 0.02, z0), (x1, axis_y + 0.045, z1), uv_scale=0.3)

    # strapped to the pier inside the drive bay: the one span of a tracked row with no module over it,
    # which is where a real independent row keeps its controller for exactly the same reason
    harness(mesh, ((0.065, 0.05, -0.075), (0.235, 0.28, 0.075)),
            ((0.0, 0.0, -0.0625), (0.21, 0.0625, 0.0625)))
    return mesh


# ------------------------------------------------------ dual-axis pedestal

def dual_axis():
    """A pedestal, an azimuth collar, and an elevation frame carrying the modules.

    Two moving groups, and the reason the azimuth one exists is worth stating: it
    turns to face the sun's bearing, which in this world is due east all morning
    and due west all afternoon.  It therefore swings a half turn at noon - and that
    is invisible, because at noon the elevation frame is lying flat and a flat plate
    turned about its own vertical axis looks identical.  Which is exactly what a
    real azimuth-elevation machine does as the sun crosses its zenith.

    The same drive bay as the single axis, and for the same reason: the yoke arms that
    carry the elevation axis used to reach exactly as high as the modules started, so
    they clashed at zero degrees and got swept through completely by eighty.  Now the
    arms sit inside ``|z| <= DRIVE_BAY`` and the modules outside it, which is also what
    a real pedestal frame looks like - a gap down the middle where the yoke comes up.

    The elevation tube through the yoke is the one place geometry is allowed to
    intersect, because that is a bearing: a cylinder turning about its own axis inside
    a housing sweeps nothing.
    """
    mesh = Mesh()
    top = 0.55
    bay = DRIVE_BAY + 0.005
    # the elevation axis, and the height the renderer measures back off the rotating
    # groups' own bounding box - so the two cannot disagree about where the pivot is
    pivot_y = top + 0.1975

    pedestal = mesh.add_object('pedestal', 'steel')
    box(mesh, pedestal, (-0.20, 0.0, -0.20), (0.20, 0.06, 0.20), uv_scale=0.5)
    cylinder(mesh, pedestal, (0.0, top / 2.0 + 0.03, 0.0), 'y', 0.085, top / 2.0 - 0.03, uv_scale=0.4,
             caps=mesh.faces('pedestal', 'steel_end'))

    pivot(mesh, 'azimuth', (0.0, top + 0.05, 0.0))
    pivot(mesh, 'elevation', (0.0, pivot_y, 0.0))

    azimuth = mesh.add_object('rotate_azimuth', 'cabinet')
    cylinder(mesh, azimuth, (0.0, top + 0.05, 0.0), 'y', 0.10, 0.05, uv_scale=0.5,
             caps=mesh.faces('rotate_azimuth', 'cabinet_top'))
    # the yoke arms, separated along the elevation axis rather than across it, so the
    # frame's own torque tube runs between them and the modules clear them entirely. Kept
    # narrow so the bay - and therefore the gap down the middle of the plane - can be too
    for z0, z1 in ((-0.095, -0.04), (0.04, 0.095)):
        box(mesh, azimuth, (-0.04, top + 0.09, z0), (0.04, pivot_y + 0.012, z1), uv_scale=0.3)

    elevation = mesh.add_object('rotate_elevation', 'frame')
    # the frame's torque tube, through the yoke bearings and out to both module bays
    cylinder(mesh, elevation, (0.0, pivot_y, 0.0), 'z', 0.03, 0.42, uv_scale=0.4,
             caps=mesh.faces('rotate_elevation', 'steel_end'))
    for z0, z1 in ((-0.46, -bay), (bay, 0.46)):
        # a cross member at the bay's *inner* edge, tying the purlins back to the tube
        inner = (z1 - 0.06, z1) if z1 < 0.0 else (z0, z0 + 0.06)
        box(mesh, elevation, (-0.42, pivot_y - 0.0275, inner[0]), (0.42, pivot_y - 0.0025, inner[1]), uv_scale=0.4)
        for x0, x1 in ((-0.42, -0.36), (-0.16, -0.10), (0.10, 0.16), (0.36, 0.42)):
            box(mesh, elevation, (x0, pivot_y - 0.0275, z0), (x1, pivot_y - 0.0025, z1), uv_scale=0.3)

    for z0, z1 in ((-0.46, -bay), (bay, 0.46)):
        clad_box(mesh, 'rotate_elevation_modules', (-0.44, pivot_y - 0.0025, z0), (-0.02, pivot_y + 0.0275, z1), LAMINATE)
        clad_box(mesh, 'rotate_elevation_modules', (0.02, pivot_y - 0.0025, z0), (0.44, pivot_y + 0.0275, z1), LAMINATE)


    # on the pedestal, which is the only fixed thing a dual-axis frame has above ground
    harness(mesh, ((0.065, 0.05, -0.075), (0.235, 0.30, 0.075)),
            ((0.0, 0.0, -0.0625), (0.21, 0.0625, 0.0625)))
    return mesh


# ------------------------------------------------------------- the inverter

def inverter():
    """A cabinet with a door, a display and a cooling fan.

    Authored at the size of the commercial machine and scaled per product by the
    renderer, the same trick the turbines use: the catalogue spans a factor of two
    hundred and fifty in nameplate and the difference on screen is a transform
    rather than four assets.
    """
    mesh = Mesh()

    # the lid is a rain hood and the rest is side sheet, which is the whole difference
    # between a cabinet and a box with the same picture on all six faces
    clad_box(mesh, 'cabinet', (-0.44, 0.0, -0.30), (0.44, 0.98, 0.28),
             {'up': 'cabinet_top', '*': 'cabinet'})
    # a plinth, because a cabinet standing straight on the ground rusts
    box(mesh, mesh.faces('cabinet', 'cabinet'), (-0.46, 0.0, -0.32), (0.46, 0.05, 0.30), uv_scale=0.5)

    # the door: louvres, handle and rating plate on the one face anybody stands in front of,
    # and plain aluminium on the five they do not
    clad_box(mesh, 'door', (-0.40, 0.10, -0.335), (0.40, 0.90, -0.30),
             {'north': 'cabinet_door', '*': 'frame'})
    # the hinge side and the handle, which is what makes it read as a door
    box(mesh, mesh.faces('door', 'frame'), (0.34, 0.42, -0.36), (0.40, 0.58, -0.335), uv_scale=0.2)

    # the screen on the front of its bezel and nowhere else: it used to be lit on all six
    # faces, so the machine appeared to have four displays and a lit underside
    clad_box(mesh, 'display', (-0.28, 0.62, -0.345), (0.06, 0.80, -0.335),
             {'north': 'display', '*': 'cabinet'})

    pivot(mesh, 'fan', (0.46, 0.68, 0.0))

    fan = mesh.add_object('rotate_fan', 'steel')
    # a five-bladed impeller behind a grille on the right-hand side
    for i in range(5):
        angle = i * 72.0
        box(mesh, fan, (0.455, 0.68 - 0.015, -0.015), (0.47, 0.68 + 0.015, 0.15),
            uv_scale=0.2, rot=((0.46, 0.68, 0.0), 'x', angle))

    # the grille's slats face outwards, so only the outward face carries them
    clad_box(mesh, 'grille', (0.44, 0.52, -0.16), (0.455, 0.84, 0.16),
             {'east': 'vent', '*': 'cabinet'}, uv_scale=0.4)

    insulator = mesh.add_object('insulator', 'instrument')
    cylinder(mesh, insulator, (0.30, 1.03, 0.0), 'y', 0.045, 0.05, uv_scale=0.3,
             caps=mesh.faces('insulator', 'instrument'))

    # The direct-current section, drawn only when a combiner box has been fitted into the cabinet.
    #
    # A compartment across the bottom of the front, which is where a central inverter's own DC section
    # is: a row of fuse ways behind a window and a gland plate under them.  It is the whole visible
    # difference between a machine that can take a string and one that cannot, and it needs to be
    # visible - fitting a box used to change nothing at all, so a player could not tell whether the
    # click had worked.
    clad_box(mesh, 'section', (-0.40, 0.08, -0.375), (0.40, 0.36, -0.335),
             {'north': 'combiner_door', 'up': 'cabinet_top', '*': 'cabinet'})
    glands = mesh.faces('section', 'steel')
    for i in range(6):
        cylinder(mesh, glands, (-0.30 + i * 0.12, 0.06, -0.355), 'y', 0.016, 0.025, sides=6,
                 uv_scale=0.2, caps=glands)

    # where the direct current comes in, one run per side, drawn only for the sides it comes in from
    stubs(mesh, 'entry')
    return mesh


# ------------------------------------------------------- the combiner box

def combiner():
    """An enclosure on a post, with the switch that takes a group of strings off the cabinet.

    Authored at the size the collision box claims, which is a box about waist high on this mod's
    scale rather than the eight hundred millimetres a real one is.  Everything in this mod is
    drawn for legibility rather than to scale - a real combiner at a block to ten metres would be
    one pixel - and the collision shape agrees with the drawing, which is the part that matters.

    The handle is its own rotating group so the renderer can put it up or down off the block
    state.  A load-break switch reads at a distance, which is the whole reason it is drawn: a
    player walking a field can see which group is isolated.
    """
    mesh = Mesh()
    box_y0, box_y1 = 0.38, 0.78

    # the footing and the post, because a field combiner stands on one rather than lying on the ground
    plinth = mesh.add_object('post', 'steel')
    box(mesh, plinth, (-0.10, 0.0, -0.08), (0.10, 0.03, 0.08), uv_scale=0.4)
    cylinder(mesh, plinth, (0.0, 0.21, 0.0), 'y', 0.032, 0.18, uv_scale=0.3,
             caps=mesh.faces('post', 'steel_end'))

    # the enclosure: a lid that is a lid, and side sheet everywhere else
    # the whole texture rather than eight tenths of it: the lid's picture is a bolted frame, and a
    # fraction of a frame is a frame off centre, which is exactly how it looked
    clad_box(mesh, 'enclosure', (-0.22, box_y0, -0.10), (0.22, box_y1, 0.10),
             {'up': 'cabinet_top', '*': 'cabinet'})

    # the door, with the fuse window and the rating label on the one face anybody stands at
    clad_box(mesh, 'door', (-0.19, box_y0 + 0.03, -0.115), (0.19, box_y1 - 0.03, -0.10),
             {'north': 'combiner_door', '*': 'frame'})

    # the gland plate underneath, where every string arrives: one row of them, which is what the
    # underside of a real box looks like and the only view that says how many ways it has
    clad_box(mesh, 'glands', (-0.20, box_y0 - 0.025, -0.075), (0.20, box_y0, 0.075),
             {'down': 'steel_end', '*': 'steel'}, uv_scale=0.4)
    glands = mesh.faces('glands', 'steel')
    for i in range(6):
        x = -0.155 + i * 0.062
        cylinder(mesh, glands, (x, box_y0 - 0.05, 0.0), 'y', 0.014, 0.025, sides=6, uv_scale=0.2,
                 caps=glands)

    # and the one heavy gland the trunk leaves through, on the end
    trunk = mesh.faces('glands', 'steel_end')
    cylinder(mesh, trunk, (0.245, box_y0 + 0.08, 0.0), 'x', 0.03, 0.025, uv_scale=0.3, caps=trunk)

    pivot(mesh, 'handle', (0.15, box_y0 + 0.12, -0.115))

    # where the strings come in and the trunk leaves, one run per side of the block, and the riser that
    # carries them up the post into the glands - a box on a post with cable arriving at ground level has
    # to have something joining the two or the copper stops at the footing
    stubs(mesh, 'entry')
    # the riser up the post, at exactly the cross-section of the run it continues - a join that changes
    # thickness halfway is the one thing a player's eye lands on
    box(mesh, mesh.faces('post', 'dc_cable'), (-0.0625, 0.02, 0.032), (0.0625, box_y0 + 0.01, 0.0945))

    # the handle: a stub off the door with a bar on it, drawn once and turned by the renderer
    handle = mesh.add_object('rotate_handle', 'switch')
    cylinder(mesh, handle, (0.15, box_y0 + 0.12, -0.128), 'z', 0.018, 0.014, sides=6, uv_scale=0.3,
             caps=handle)
    box(mesh, handle, (0.135, box_y0 + 0.12, -0.145), (0.165, box_y0 + 0.20, -0.13), uv_scale=0.3)
    return mesh


# --------------------------------------------------------- the met station

def met_mast():
    """A mast with seven instruments, each at the height its own standard puts it.

    A block is ten metres throughout this mod, so a one-block mast is a ten-metre one -
    which is exactly where the world's weather services measure wind, and where the
    anemometer goes.  Everything else belongs much lower and used to be drawn near the
    top with it, which is why the mast read as an instrument tree rather than a station:

      * radiometers on a boom at 3.5 m, pointing away from the mast so its shadow cannot
        fall on them
      * the radiation shield at 2 m, which is the standard screen height for air temperature
      * the snow gauge on its own arm at 2 m looking down at clear ground - the same two
        metres MetStationBlockEntity works its depth out from, so the picture and the
        arithmetic now agree
      * wind at the top, clear of all of it
    """
    mesh = Mesh()

    # heights in blocks, which is metres over ten
    wind_y = 1.0
    radiometer_y = 0.35
    screen_y = 0.20
    snow_y = 0.20

    mast = mesh.add_object('mast', 'steel')
    cylinder(mesh, mast, (0.0, 0.48, 0.0), 'y', 0.035, 0.48, uv_scale=0.3,
             caps=mesh.faces('mast', 'steel_end'))
    # the base plate: seen from above far more than from any side, so it gets the lid
    clad_box(mesh, 'mast', (-0.12, 0.0, -0.12), (0.12, 0.04, 0.12),
             {'up': 'steel_end', '*': 'steel'}, uv_scale=0.4)

    boom = mesh.add_object('boom', 'steel')
    box(mesh, boom, (-0.02, radiometer_y - 0.02, 0.03), (0.02, radiometer_y + 0.02, 0.42), uv_scale=0.3)
    box(mesh, boom, (-0.02, screen_y - 0.02, 0.03), (0.02, screen_y + 0.02, 0.30), uv_scale=0.3)

    # the three radiometers: global, diffuse under its shadow ring, and the
    # albedometer, which is two of them back to back
    pyranometer = mesh.add_object('pyranometer', 'instrument')
    cylinder(mesh, pyranometer, (0.0, radiometer_y + 0.035, 0.16), 'y', 0.05, 0.02, uv_scale=0.4,
             caps=mesh.faces('pyranometer', 'instrument'))
    # the dome is glass and is looked down on, so it is glass and it is capped
    cylinder(mesh, mesh.faces('pyranometer', 'dome'), (0.0, radiometer_y + 0.065, 0.16), 'y', 0.028, 0.015,
             uv_scale=0.3, caps=mesh.faces('pyranometer', 'dome'))

    diffuse = mesh.add_object('diffuse', 'instrument')
    cylinder(mesh, diffuse, (0.0, radiometer_y + 0.035, 0.30), 'y', 0.05, 0.02, uv_scale=0.4,
             caps=mesh.faces('diffuse', 'instrument'))
    cylinder(mesh, mesh.faces('diffuse', 'dome'), (0.0, radiometer_y + 0.065, 0.30), 'y', 0.026, 0.014,
             uv_scale=0.3, caps=mesh.faces('diffuse', 'dome'))
    # the shadow ring, which is what makes it a diffuse instrument at all
    box(mesh, diffuse, (-0.075, radiometer_y + 0.06, 0.295), (0.075, radiometer_y + 0.075, 0.305), uv_scale=0.2)

    albedometer = mesh.add_object('albedometer', 'instrument')
    cylinder(mesh, albedometer, (0.0, radiometer_y + 0.035, 0.40), 'y', 0.045, 0.018, uv_scale=0.4,
             caps=mesh.faces('albedometer', 'instrument'))
    cylinder(mesh, mesh.faces('albedometer', 'dome'), (0.0, radiometer_y + 0.065, 0.40), 'y', 0.024, 0.013,
             uv_scale=0.3, caps=mesh.faces('albedometer', 'dome'))
    # the downward-looking half: an albedometer is two pyranometers, one of them upside down
    cylinder(mesh, mesh.faces('albedometer', 'dome'), (0.0, radiometer_y - 0.005, 0.40), 'y', 0.024, 0.013,
             uv_scale=0.3, caps=mesh.faces('albedometer', 'dome'))

    shield = mesh.add_object('shield', 'instrument')
    # a naturally aspirated radiation shield: a stack of plates with air between them
    for i in range(4):
        y = screen_y + i * 0.022
        box(mesh, shield, (-0.055, y, 0.245), (0.055, y + 0.012, 0.355), uv_scale=0.4)

    # the snow gauge looks straight down, so its underside is the transducer and not paint
    clad_box(mesh, 'snow', (-0.03, snow_y, -0.34), (0.03, snow_y + 0.06, -0.28),
             {'down': 'dome', 'up': 'cabinet_top', '*': 'cabinet'}, uv_scale=0.3)
    snow = mesh.faces('snow', 'cabinet')
    box(mesh, snow, (-0.02, snow_y + 0.02, -0.28), (0.02, snow_y + 0.04, 0.0), uv_scale=0.3)

    pivot(mesh, 'cups', (0.0, 1.0, 0.0))
    pivot(mesh, 'vane', (0.0, 0.885, 0.0))

    cups = mesh.add_object('rotate_cups', 'instrument')
    cylinder(mesh, cups, (0.0, 0.99, 0.0), 'y', 0.018, 0.03, uv_scale=0.2,
             caps=mesh.faces('rotate_cups', 'instrument'))
    for i in range(3):
        angle = i * 120.0
        # a cup is open at the top and shaded inside it, which is the one face of the whole
        # mast a player looks straight down into
        clad_box(mesh, 'rotate_cups', (0.10, 0.985, -0.035), (0.17, 1.03, 0.035),
                 {'up': 'dome', '*': 'instrument'}, uv_scale=0.3, rot=((0.0, 1.0, 0.0), 'y', angle))
        box(mesh, cups, (0.02, 0.995, -0.008), (0.11, 1.01, 0.008),
            uv_scale=0.2, rot=((0.0, 1.0, 0.0), 'y', angle))

    vane = mesh.add_object('rotate_vane', 'frame')
    box(mesh, vane, (-0.01, 0.86, -0.02), (0.01, 0.90, 0.22), uv_scale=0.3)
    box(mesh, vane, (-0.012, 0.845, 0.14), (0.012, 0.925, 0.24), uv_scale=0.3)
    box(mesh, vane, (-0.02, 0.855, -0.09), (0.02, 0.895, -0.02), uv_scale=0.2)
    return mesh


LAMINATE_MATERIALS = ('module', 'module_back', 'module_edge')
HARNESS_MATERIALS = ('cabinet', 'cabinet_top', 'dc_cable')

MODELS = [
    ('pv_flat', flat_table, ('steel', 'frame') + HARNESS_MATERIALS + LAMINATE_MATERIALS),
    ('pv_tilt', tilted_rack, ('steel', 'frame') + HARNESS_MATERIALS + LAMINATE_MATERIALS),
    ('pv_track', single_axis, ('steel', 'steel_end', 'frame') + HARNESS_MATERIALS + LAMINATE_MATERIALS),
    ('pv_dual', dual_axis, ('steel', 'steel_end', 'frame') + HARNESS_MATERIALS + LAMINATE_MATERIALS),
    ('pv_inverter', inverter, ('cabinet', 'cabinet_door', 'cabinet_top', 'vent', 'frame', 'display', 'steel', 'instrument', 'dc_cable', 'combiner_door')),
    ('pv_combiner', combiner, ('steel', 'steel_end', 'cabinet', 'cabinet_top', 'combiner_door', 'frame', 'switch', 'dc_cable')),
    ('met_mast', met_mast, ('steel', 'steel_end', 'instrument', 'dome', 'cabinet', 'cabinet_top', 'frame')),
]


def main():
    for name, builder, materials in MODELS:
        mesh = builder()
        directory = os.path.join(OUT, name)
        mesh.write(os.path.join(directory, name + '.obj'), name + '.mtl')
        write_mtl(os.path.join(directory, name + '.mtl'), materials)
        groups = ['%s_%s' % (obj, material) for obj, material, faces in mesh.objects if faces]
        print('%-12s %2d objects, %3d vertices, groups: %s'
              % (name, len(mesh.objects), len(mesh.v), ', '.join(groups)))


if __name__ == '__main__':
    main()
