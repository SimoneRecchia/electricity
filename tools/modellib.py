#!/usr/bin/env python3
"""The primitives every model in this mod is built from, and the subdivisions they use.

ROUND / FITTING / HEX are the standard - see CLAUDE.md section 1.  Never lower one to save faces.

Worth knowing before using these: uv is mapped off world coordinates so a circle stays a circle (see
box's ``uv=``); ``tube`` carries one reference vector along the path so the mesh cannot twist; ``lathe``
closes by construction, which is why the insulator is drawn with it rather than stacked cones.
"""

import math
import os

# The turbine's own subdivisions, restated here because they are now the whole mod's.  Overridable so a
# setting can be generated and *looked at* side by side - tools/compare_sides.py writes the sheet, and a
# render is the only way to argue with a figure like this.  CLAUDE.md section 1 says what each is for.
ROUND = int(os.environ.get('ELECTRICITY_ROUND', 80))
FITTING = int(os.environ.get('ELECTRICITY_FITTING', 16))
# A hexagon, for the things that are hexagons.
HEX = 6


# ------------------------------------------------------------------ collision

# How much air a merged collision box may gain, as a fraction of the boxes it replaces.  A hitbox is what a
# player points at and stands on, not a drawing: a box per fitting was 621 across the two cable gauges, and
# nobody can feel the difference between a tube's eight segments and the one box that contains them.  Only
# ever merges, so a coarse box still contains everything the model draws.  0.0 turns it off.
COLLISION_SLACK = float(os.environ.get('ELECTRICITY_SLACK', 0.5))


def _volume(box):
    lo, hi = box
    return max(0.0, hi[0] - lo[0]) * max(0.0, hi[1] - lo[1]) * max(0.0, hi[2] - lo[2])


def coarse(boxes, slack=None):
    """Merges boxes greedily while the merge gains little air, to a fixpoint.

    A tube's segments share a cross-section, so merging along the run is exact; a swept bend's staircase is
    not, and that is where the slack goes.  Shared by the cables, the ground conductors and the towers, so
    one figure decides how coarse every run in the mod is.
    """
    slack = COLLISION_SLACK if slack is None else slack
    if slack <= 0.0:
        return list(boxes)

    kept = [(tuple(lo), tuple(hi)) for lo, hi in boxes]
    merged = True
    while merged and len(kept) > 1:
        merged = False
        for i in range(len(kept)):
            for j in range(i + 1, len(kept)):
                lo = tuple(min(kept[i][0][k], kept[j][0][k]) for k in range(3))
                hi = tuple(max(kept[i][1][k], kept[j][1][k]) for k in range(3))
                own = _volume(kept[i]) + _volume(kept[j])
                if _volume((lo, hi)) <= own * (1.0 + slack):
                    kept = [b for n, b in enumerate(kept) if n not in (i, j)] + [(lo, hi)]
                    merged = True
                    break
            if merged:
                break
    return kept


class Mesh:
    """Accumulates vertices, normals, texture coordinates and faces for one OBJ file."""

    def __init__(self):
        self.v = []
        self.vn = []
        self.vt = []
        self.objects = []  # (name, material, [faces]) with faces as index triples
        self._pool = {}

    def _index(self, table, value, pool_key):
        # OBJ indices are 1-based and shared across the whole file
        key = tuple(round(c, 6) for c in value)
        pool = self._pool.setdefault(pool_key, {})
        found = pool.get(key)
        if found is not None:
            return found
        table.append(key)
        pool[key] = len(table)
        return len(table)

    def add_object(self, name, material):
        faces = []
        self.objects.append((name, material, faces))
        return faces

    def faces(self, name, material):
        """The face list for one object-material pair, made if it is not there yet."""
        for existing_name, existing_material, faces in self.objects:
            if existing_name == name and existing_material == material:
                return faces
        return self.add_object(name, material)

    def quad(self, faces, corners, normal, uvs):
        """One quad.  Wound to agree with its own normal, whatever order the caller passed.

        Minecraft culls by winding, not by the stated normal, so a quad wound the wrong way is a hole in
        the world - and half of every box in this mod was wound inside out.  That is what a player saw as
        a junction box with no lid, a cleat that was an open channel and a cable with a gap in it.
        check_winding.py fails the build on it now; this is where it cannot happen in the first place.
        """
        corners, uvs = list(corners), list(uvs)
        if len(corners) >= 3:
            a = tuple(corners[1][i] - corners[0][i] for i in range(3))
            b = tuple(corners[2][i] - corners[0][i] for i in range(3))
            wound = (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
            if sum(c * c for c in wound) < 1e-18:
                return          # no area: nothing to draw, and no winding to get right either

            if sum(wound[i] * normal[i] for i in range(3)) < 0.0:
                corners.reverse()
                uvs.reverse()

        indices = []
        n = self._index(self.vn, normal, 'vn')
        for corner, uv in zip(corners, uvs):
            indices.append((self._index(self.v, corner, 'v'), self._index(self.vt, uv, 'vt'), n))
        faces.append(indices)

    def marker(self, faces, point):
        """A zero-size face at one point: a ``pivot_*`` hinge, which quad() would drop for having no area.

        The renderers and check_pv_clearance measure a hinge off this group - CLAUDE.md section 5.
        """
        v = self._index(self.v, point, 'v')
        n = self._index(self.vn, (0.0, 1.0, 0.0), 'vn')
        t = self._index(self.vt, (0.0, 0.0), 'vt')
        faces.append([(v, t, n)] * 3)

    def write(self, path, mtl_name, source):
        """One ``o`` block per group name, with a ``usemtl`` section inside it per material.

        Forge does ``parts.put(name, new ModelGroup(name))`` into a map, so a second ``o`` of the same name
        *replaces* the first and every face it held is never baked.  A part in two materials was written as
        two same-named blocks, so the game drew the junction box's lid and dropped its five walls, and drew
        one connector of a pair.  check_obj_loading.py fails the build on a repeated name.
        """
        os.makedirs(os.path.dirname(path), exist_ok=True)
        order, sections = [], {}
        for name, material, faces in self.objects:
            if not faces:
                continue
            if name not in sections:
                order.append(name)
                sections[name] = []
            sections[name].append((material, faces))

        with open(path, 'w') as f:
            f.write('# generated by tools/%s - do not edit by hand\n' % source)
            f.write('mtllib %s\n' % mtl_name)
            for x, y, z in self.v:
                f.write('v %.6f %.6f %.6f\n' % (x, y, z))
            for x, y, z in self.vn:
                f.write('vn %.4f %.4f %.4f\n' % (x, y, z))
            for u, v in self.vt:
                f.write('vt %.6f %.6f\n' % (u, v))
            for name in order:
                f.write('o %s\n' % name)
                for material, faces in sections[name]:
                    f.write('usemtl %s\n' % material)
                    for face in faces:
                        f.write('f ' + ' '.join('%d/%d/%d' % idx for idx in face) + '\n')

    def stats(self):
        return len(self.v), sum(len(faces) for _, _, faces in self.objects)


def write_mtl(path, materials, textures, source):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write('# generated by tools/%s - do not edit by hand\n' % source)
        for name in materials:
            f.write('\nnewmtl %s\n' % name)
            f.write('Ns 250.000000\nKa 1.000000 1.000000 1.000000\nKs 0.500000 0.500000 0.500000\n')
            f.write('Ke 0.000000 0.000000 0.000000\nNi 1.500000\nd 1.000000\nillum 2\n')
            f.write('map_Kd %s\n' % textures[name])


# ------------------------------------------------------------------ transforms

def rotate(point, pivot_point, axis, degrees):
    """Rotates a point about an axis-aligned line through a pivot."""
    if degrees == 0.0:
        return point
    a = math.radians(degrees)
    c, s = math.cos(a), math.sin(a)
    x, y, z = point[0] - pivot_point[0], point[1] - pivot_point[1], point[2] - pivot_point[2]
    if axis == 'x':
        y, z = y * c - z * s, y * s + z * c
    elif axis == 'y':
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + pivot_point[0], y + pivot_point[1], z + pivot_point[2])


def pivot(mesh, name, point, material='steel'):
    """Marks where a moving part turns"""
    mesh.marker(mesh.faces('pivot_' + name, material), point)


# ------------------------------------------------------------------ boxes

FACES = ('down', 'up', 'north', 'south', 'west', 'east')


def box(mesh, faces, lo, hi, uv_scale=1.0, rot=None, only=None, uv=None, uv_rot=0):
    """Six quads, outward normals, each face mapped across the whole texture."""
    x0, y0, z0 = lo
    x1, y1, z1 = hi

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

    # Which corner of the texture goes on which corner of the face, and it is not the same for all
    u0, v0, u1, v1 = uv if uv else (0.0, 0.0, uv_scale, uv_scale)
    plain = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
    flipped_u = [(u1, v0), (u0, v0), (u0, v1), (u1, v1)]
    flipped_v = [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]
    mapping = {
        'down': plain, 'up': flipped_v,
        'north': flipped_u, 'south': flipped_u, 'west': flipped_u, 'east': flipped_u,
    }

    for face, pts in corners.items():
        if only is not None and face != only:
            continue

        uvs = mapping[face]
        if uv_rot:
            shift = (uv_rot // 90) % 4
            uvs = uvs[shift:] + uvs[:shift]
        normal = normals[face]
        if rot is not None:
            # One rotation or a list of them, applied in order.
            for pivot_point, axis, degrees in ([rot] if isinstance(rot[1], str) else rot):
                pts = [rotate(p, pivot_point, axis, degrees) for p in pts]
                normal = rotate(normal, (0, 0, 0), axis, degrees)
        mesh.quad(faces, pts, normal, uvs)


def square_uv(lo, hi, scale=1.0):
    """A uv rect per face taken off the part's own size, so u and v get the same scale on all six of them.

    One rect on every face of a box that is not a cube stretches the texture, and a circle drawn on it comes
    out an oval - which is the most common fault in this mod and what check_model_textures calls OVAL.
    ``scale`` is tiles per block. See CLAUDE.md section 3.
    """
    span = tuple((hi[i] - lo[i]) * scale for i in range(3))
    across = {'up': (0, 2), 'down': (0, 2), 'north': (0, 1), 'south': (0, 1), 'east': (2, 1),
              'west': (2, 1)}
    return {face: (0.0, 0.0, span[u], span[v]) for face, (u, v) in across.items()}


def clad_box(mesh, name, lo, hi, sides, uv_scale=1.0, rot=None, uv=None, uv_rot=0):
    """A box whose six faces are not all the same material."""
    for face in FACES:
        material = sides.get(face, sides['*'])
        box(mesh, mesh.faces(name, material), lo, hi, uv_scale=uv_scale, rot=rot, only=face,
            uv=uv if not isinstance(uv, dict) else uv.get(face, uv.get('*')), uv_rot=uv_rot)


# ------------------------------------------------------------------ prisms

def ring_points(radius, sides, phase=0.0):
    return [(radius * math.cos(phase + 2.0 * math.pi * i / sides),
             radius * math.sin(phase + 2.0 * math.pi * i / sides)) for i in range(sides)]


def cylinder(mesh, faces, centre, axis, radius, half_length, sides=FITTING, uv_scale=1.0,
             caps=None, taper=1.0, phase=0.0, uv_along=1.0, cap_ends=(-1, 1), uv=None):
    """A prism standing in for a tube."""
    cx, cy, cz = centre
    near = ring_points(radius, sides, phase)
    far = ring_points(radius * taper, sides, phase)
    # kept under its own name so the cap's own uv function can see it without shadowing itself
    window = uv

    def point(i, end):
        p, q = (far if end > 0 else near)[i]
        if axis == 'y':
            return (cx + p, cy + end * half_length, cz + q)
        if axis == 'z':
            return (cx + p, cy + q, cz + end * half_length)
        return (cx + end * half_length, cy + p, cz + q)

    for i in range(sides):
        j = (i + 1) % sides
        a, b = point(i, -1), point(j, -1)
        c, d = point(j, 1), point(i, 1)
        nx = (a[0] + c[0]) / 2 - cx
        ny = (a[1] + c[1]) / 2 - cy
        nz = (a[2] + c[2]) / 2 - cz
        if axis == 'y':
            ny = 0.0
        elif axis == 'z':
            nz = 0.0
        else:
            nx = 0.0
        length = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
        if uv is None:
            u = i / sides * uv_scale
            u2 = (i + 1) / sides * uv_scale
            v0, v1 = 0.0, uv_along * uv_scale
        else:
            # An explicit window, for a fitting whose texture is a strip along its own length rather
            u = uv[0] + (uv[2] - uv[0]) * i / sides
            u2 = uv[0] + (uv[2] - uv[0]) * (i + 1) / sides
            v0, v1 = uv[1], uv[3]
        mesh.quad(faces, [a, b, c, d], (nx / length, ny / length, nz / length),
                  [(u, v0), (u2, v0), (u2, v1), (u, v1)])

    if caps is None:
        return

    normal = {'x': (1.0, 0.0, 0.0), 'y': (0.0, 1.0, 0.0), 'z': (0.0, 0.0, 1.0)}[axis]
    # ``cap_ends`` is which ends are closed
    for end in cap_ends:
        outward = tuple(component * end for component in normal)
        ring = far if end > 0 else near
        span = radius * (taper if end > 0 else 1.0) * 2.0

        def uv(i, ring=ring, span=span):
            p, q = ring[i]
            u, v = 0.5 + p / span, 0.5 + q / span
            if window is None:
                return (u, v)

            # A cap on a segment whose texture is a *strip along its own length* has to stay inside that
            # instead put a shrunk copy of the entire drawing on the end face: an MC4's nose came out
            return (window[0] + (window[2] - window[0]) * (0.25 + u * 0.5),
                    window[1] + (window[3] - window[1]) * (0.25 + v * 0.5))

        order = list(range(sides)) if end > 0 else list(range(sides - 1, -1, -1))
        for i in range(1, sides - 2, 2):
            corners = [order[0], order[i], order[i + 1], order[i + 2]]
            mesh.quad(caps, [point(k, end) for k in corners], outward, [uv(k) for k in corners])


def hemisphere(mesh, faces, centre, radius, sides=FITTING, rings=6, up=1, squash=1.0, uv_scale=1.0):
    """A dome: a pole cap, a lantern top, a bollard head."""
    cx, cy, cz = centre
    for r in range(rings):
        a0 = (math.pi / 2) * r / rings
        a1 = (math.pi / 2) * (r + 1) / rings
        r0, r1 = math.cos(a0) * radius, math.cos(a1) * radius
        y0 = cy + up * math.sin(a0) * radius * squash
        y1 = cy + up * math.sin(a1) * radius * squash
        lower = ring_points(r0, sides)
        upper = ring_points(r1, sides)
        for i in range(sides):
            j = (i + 1) % sides
            a = (cx + lower[i][0], y0, cz + lower[i][1])
            b = (cx + lower[j][0], y0, cz + lower[j][1])
            c = (cx + upper[j][0], y1, cz + upper[j][1])
            d = (cx + upper[i][0], y1, cz + upper[i][1])
            nx = (a[0] + c[0]) / 2 - cx
            ny = up * radius * (1.0 - math.cos((a0 + a1) / 2)) * 0.6 + 0.35 * up * radius
            nz = (a[2] + c[2]) / 2 - cz
            length = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
            v0 = r / rings * uv_scale
            v1 = (r + 1) / rings * uv_scale
            u = i / sides * uv_scale
            u2 = (i + 1) / sides * uv_scale
            mesh.quad(faces, [a, b, c, d], (nx / length, ny / length, nz / length),
                      [(u, v0), (u2, v0), (u2, v1), (u, v1)])


# ------------------------------------------------------------------ structural profiles

def ibeam(mesh, faces, lo, hi, axis='y', web=0.28, flange=0.22, uv_scale=0.3, rot=None):
    """An I-section: two flanges and a web between them."""
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    if axis == 'y':
        width, depth = x1 - x0, z1 - z0
        mid_x = (x0 + x1) / 2
        box(mesh, faces, (x0, y0, z0), (x1, y1, z0 + depth * flange), uv_scale=uv_scale, rot=rot)
        box(mesh, faces, (x0, y0, z1 - depth * flange), (x1, y1, z1), uv_scale=uv_scale, rot=rot)
        box(mesh, faces, (mid_x - width * web / 2, y0, z0 + depth * flange),
            (mid_x + width * web / 2, y1, z1 - depth * flange), uv_scale=uv_scale, rot=rot)
    else:
        # a horizontal I, used for a purlin: flanges top and bottom, web between
        height = y1 - y0
        box(mesh, faces, (x0, y0, z0), (x1, y0 + height * flange, z1), uv_scale=uv_scale, rot=rot)
        box(mesh, faces, (x0, y1 - height * flange, z0), (x1, y1, z1), uv_scale=uv_scale, rot=rot)
        mid_z = (z0 + z1) / 2
        span = (z1 - z0) * web / 2
        box(mesh, faces, (x0, y0 + height * flange, mid_z - span),
            (x1, y1 - height * flange, mid_z + span), uv_scale=uv_scale, rot=rot)


def channel(mesh, faces, lo, hi, along='z', web=0.26, uv_scale=0.3, opening='up', rot=None):
    """A C-section: a back with two legs turned off it."""
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    height = y1 - y0
    thick_y = height * web
    if along == 'z':
        width = x1 - x0
        thick_x = width * web
        if opening in ('up', 'down'):
            back_y = (y0, y0 + thick_y) if opening == 'up' else (y1 - thick_y, y1)
            box(mesh, faces, (x0, back_y[0], z0), (x1, back_y[1], z1), uv_scale=uv_scale, rot=rot)
            box(mesh, faces, (x0, y0, z0), (x0 + thick_x, y1, z1), uv_scale=uv_scale, rot=rot)
            box(mesh, faces, (x1 - thick_x, y0, z0), (x1, y1, z1), uv_scale=uv_scale, rot=rot)
        else:
            back_x = (x0, x0 + thick_x) if opening == 'east' else (x1 - thick_x, x1)
            box(mesh, faces, (back_x[0], y0, z0), (back_x[1], y1, z1), uv_scale=uv_scale, rot=rot)
            box(mesh, faces, (x0, y0, z0), (x1, y0 + thick_y, z1), uv_scale=uv_scale, rot=rot)
            box(mesh, faces, (x0, y1 - thick_y, z0), (x1, y1, z1), uv_scale=uv_scale, rot=rot)
    else:
        depth = z1 - z0
        thick_z = depth * web
        back_y = (y0, y0 + thick_y) if opening == 'up' else (y1 - thick_y, y1)
        box(mesh, faces, (x0, back_y[0], z0), (x1, back_y[1], z1), uv_scale=uv_scale, rot=rot)
        box(mesh, faces, (x0, y0, z0), (x1, y1, z0 + thick_z), uv_scale=uv_scale, rot=rot)
        box(mesh, faces, (x0, y0, z1 - thick_z), (x1, y1, z1), uv_scale=uv_scale, rot=rot)


def angle(mesh, faces, lo, hi, along='z', web=0.30, uv_scale=0.3, rot=None):
    """An L-section: a brace, a bracket, a mounting cleat."""
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    if along == 'z':
        box(mesh, faces, (x0, y0, z0), (x1, y0 + (y1 - y0) * web, z1), uv_scale=uv_scale, rot=rot)
        box(mesh, faces, (x0, y0, z0), (x0 + (x1 - x0) * web, y1, z1), uv_scale=uv_scale, rot=rot)
    elif along == 'x':
        box(mesh, faces, (x0, y0, z0), (x1, y0 + (y1 - y0) * web, z1), uv_scale=uv_scale, rot=rot)
        box(mesh, faces, (x0, y0, z0), (x1, y1, z0 + (z1 - z0) * web), uv_scale=uv_scale, rot=rot)
    else:
        box(mesh, faces, (x0, y0, z0), (x0 + (x1 - x0) * web, y1, z1), uv_scale=uv_scale, rot=rot)
        box(mesh, faces, (x0, y0, z0), (x1, y1, z0 + (z1 - z0) * web), uv_scale=uv_scale, rot=rot)


def strut(mesh, faces, p0, p1, half, thick=None, uv_scale=0.2):
    """A beam between two arbitrary points: a brace, a stay, an actuator, a guy."""
    thick = half if thick is None else thick
    d = [p1[i] - p0[i] for i in range(3)]
    length = math.sqrt(sum(c * c for c in d)) or 1.0
    d = [c / length for c in d]
    # any axis not parallel to the beam will do for the first cross product
    other = (0.0, 1.0, 0.0) if abs(d[1]) < 0.9 else (1.0, 0.0, 0.0)
    right = [other[1] * d[2] - other[2] * d[1], other[2] * d[0] - other[0] * d[2],
             other[0] * d[1] - other[1] * d[0]]
    norm = math.sqrt(sum(c * c for c in right)) or 1.0
    right = [c / norm for c in right]
    up = [d[1] * right[2] - d[2] * right[1], d[2] * right[0] - d[0] * right[2],
          d[0] * right[1] - d[1] * right[0]]

    def corner(end, s_right, s_up):
        base = p1 if end else p0
        return tuple(base[i] + right[i] * s_right * half + up[i] * s_up * thick for i in range(3))

    faces_spec = (
        ((0, -1, -1), (0, 1, -1), (1, 1, -1), (1, -1, -1), [-u for u in up]),
        ((0, -1, 1), (1, -1, 1), (1, 1, 1), (0, 1, 1), up),
        ((0, -1, -1), (1, -1, -1), (1, -1, 1), (0, -1, 1), [-r for r in right]),
        ((0, 1, -1), (0, 1, 1), (1, 1, 1), (1, 1, -1), right),
        ((0, -1, -1), (0, -1, 1), (0, 1, 1), (0, 1, -1), [-c for c in d]),
        ((1, -1, -1), (1, 1, -1), (1, 1, 1), (1, -1, 1), d),
    )
    for a, b, c, e, normal in faces_spec:
        pts = [corner(*a), corner(*b), corner(*c), corner(*e)]
        mesh.quad(faces, pts, tuple(normal),
                  [(0.0, 0.0), (uv_scale, 0.0), (uv_scale, uv_scale), (0.0, uv_scale)])


def bolt(mesh, faces, centre, axis, radius, length, uv_scale=0.2, head=1.7, washer=True):
    """A bolt: a hexagon head on a shank"""
    cx, cy, cz = centre
    step = {'x': (length, 0, 0), 'y': (0, length, 0), 'z': (0, 0, length)}[axis]
    shank = (cx + step[0] / 2, cy + step[1] / 2, cz + step[2] / 2)
    cylinder(mesh, faces, shank, axis, radius, length / 2, sides=HEX, uv_scale=uv_scale)
    if washer:
        cylinder(mesh, faces, centre, axis, radius * head * 1.15, length * 0.06, sides=FITTING,
                 uv_scale=uv_scale, caps=faces)
    cylinder(mesh, faces, (cx + step[0] * 0.10, cy + step[1] * 0.10, cz + step[2] * 0.10), axis,
             radius * head, length * 0.10, sides=HEX, uv_scale=uv_scale, caps=faces,
             phase=math.pi / 6)


def eyebolt(mesh, faces, base, ring_radius, sides=FITTING, wire=0.30):
    """A DIN 580 lifting eye: a forged ring with a hole through it, on a shank with a collar."""
    cx, cy, cz = base
    section = ring_radius * wire
    collar = ring_radius * 0.62
    cylinder(mesh, faces, (cx, cy + collar * 0.30, cz), 'y', collar, collar * 0.30, sides=sides,
             uv_scale=0.1, caps=faces, cap_ends=(1,))

    centre_y = cy + collar * 0.60 + ring_radius
    # arc closes on itself at 270, so the path is already a loop: repeating the first point gave a
    # zero-length segment, and a ring swept along nothing came out as four faces with a junk normal.
    circle = arc((cx, centre_y, cz), ring_radius, (0, 1), -90.0, 270.0, sides)
    tube(mesh, faces, circle, section, sides=max(6, sides // 4), uv_scale=1.0)


# A Stäubli MC4, as (length, radius, taper, v0, v1) in sixteenths.  Fat, thin, fat is the signature:
# gland nut, body, coupling ring.  gen_cable_models and gen_pv_models both draw from this, and
# dc_connector_plus's bands are these v.
#
# The size is chosen by what reads, not by the datasheet's own ratio.  A real MC4 is 2.75 times its
# cable across the coupling ring - but this mod's cable is itself drawn nine times oversize, because a
# true 6.9 mm core is a fifteenth of a pixel and invisible, so taking the true ratio on top of that gave
# a connector 3.6 px across on a 1.3 px cable: a fitting the size of a bollard, wider than the run was
# long, hiding the cable it was moulded onto.  1.7 times the cable is the least that still shows three
# diameters and a knurl, and that is what this is.
MC4 = ((0.55, 0.68, 1.206, 0.0000, 0.1250),   # the strain relief out of the jacket
       (0.80, 0.95, 1.000, 0.1250, 0.3068),   # the cable gland nut, knurled
       (1.35, 0.80, 1.000, 0.3068, 0.6136),   # the body, carrying the legend
       (1.15, 1.10, 1.000, 0.6136, 0.8750),   # the coupling ring: the widest part, the one you grip
       (0.55, 1.10, 0.864, 0.8750, 1.0000))   # the nose, chamfered
MC4_LENGTH = sum(step[0] for step in MC4)
MC4_RADIUS = max(step[1] * max(1.0, step[2]) for step in MC4)
MC4_PIN = 0.22
# Two coupling rings 2.2 px across will not quite lie 2.1 px apart, so the pair splays a little at an
# end - a tenth of a pixel each way, where a bollard-sized plug needed a whole pixel.
MC4_SPREAD = 1.25
# And the two leads of a string are cut to different lengths, so their plugs do not sit level.  Which is
# also what makes two of them read as two rather than as one wide lump.
MC4_STAGGER = 2.00

# A mated pair, which is what joins two module leads and therefore what a laid run actually shows: a
# string is a chain of finite lengths plugged together, and every metre of one has a joint in it.  The
# silhouette is the pair's own - nut, coupling collar, nut - with the two smooth bodies cut back to what
# fits between a block's arms.  dc_joint_plus's bands are these v.
MC4_JOINT = ((0.40, 0.68, 1.235, 0.0000, 0.1111),   # the strain relief in
             (0.65, 0.95, 1.000, 0.1111, 0.2917),   # one gland nut
             (0.25, 0.80, 1.000, 0.2917, 0.3611),   # its body
             (1.00, 1.10, 1.000, 0.3611, 0.6389),   # the coupling collar, screwed home
             (0.25, 0.80, 1.000, 0.6389, 0.7083),   # the other body
             (0.65, 0.95, 1.000, 0.7083, 0.8889),   # the other gland nut
             (0.40, 0.84, 0.810, 0.8889, 1.0000))   # and the strain relief out
MC4_JOINT_LENGTH = sum(step[0] for step in MC4_JOINT)


# A heat-shrink straight joint, as (length, radius, taper, v0, v1) in sixteenths of a block, for the
# trunk: the sleeve shrunk down over a crimped joint, which is what joins two drums of 240 mm2 cable.
# Sized off the trunk's own 1.40 px core radius.  dc_trunk_shrink's bands are these v.
SHRINK = ((0.90, 1.40, 1.429, 0.0000, 0.1765),   # shrunk down onto the sheath
          (0.60, 2.05, 1.000, 0.1765, 0.2941),   # the step off it
          (2.10, 2.20, 1.000, 0.2941, 0.7059),   # the body, over the crimp
          (0.60, 2.05, 1.000, 0.7059, 0.8235),
          (0.90, 2.00, 0.700, 0.8235, 1.0000))
SHRINK_LENGTH = sum(step[0] for step in SHRINK)
SHRINK_RADIUS = max(step[1] * max(1.0, step[2]) for step in SHRINK)


def mc4(mesh, faces, start, axis, unit=1.0, into=1, flip_v=False, pin=False, profile=MC4):
    """An MC4 moulding grown along one axis from ``start``, which is a point on its own centre line.

    ``profile`` is MC4 for a plug on a free end or MC4_JOINT for a mated pair mid-run.
    ``unit`` is a sixteenth in the caller's units: 1.0 in a block-frame OBJ, 1/16 in a machine's.
    ``flip_v`` for the mod's own renderer, which does 1 - v, so the bands arrive tail first without it.
    ``pin`` is the male contact, and is the only thing that tells the two poles apart in silhouette.
    """
    # u zero is the top, which is where tube() puts its own reference vector: a plug lit down one side and
    # a cable lit down another read as two objects.
    phase = math.pi / 2 if axis == 'z' else 0.0
    steps = list(profile)
    if pin:
        nose = profile[-1]
        steps.append((MC4_PIN, 0.22, 1.0, nose[3], nose[4]))

    # A shoulder between two steps is an annulus, and an open one is a hole straight into the plug - which
    # is what a profile that steps in as well as out gets if only the last segment is capped.  The wider
    # side of each shoulder carries the disc; where the two are equal neither does, because two coplanar
    # discs flicker against each other.
    radii = [(r, r * t) for _, r, t, _, _ in steps]
    axis_index = 'xyz'.index(axis)
    cursor = start[axis_index]
    centre = list(start)
    for index, (length, radius, taper, v0, v1) in enumerate(steps):
        low, high = sorted((cursor, cursor + into * length * unit))
        # cylinder() tapers its +axis end, so a plug growing the other way takes the profile reversed
        near, ratio = (radius, taper) if into > 0 else (radius * taper, 1.0 / taper)
        behind = radii[index - 1][1] if index else 0.0
        ahead = radii[index + 1][0] if index + 1 < len(steps) else 0.0
        ends = ([-into] if radii[index][0] > behind + 1e-9 else []) + \
               ([into] if radii[index][1] > ahead + 1e-9 else [])
        centre[axis_index] = (low + high) / 2.0
        cylinder(mesh, faces, tuple(centre), axis, near * unit, (high - low) / 2.0, sides=FITTING,
                 uv_scale=1.0, taper=ratio, phase=phase, caps=faces if ends else None,
                 cap_ends=tuple(ends),
                 uv=(0.0, 1.0 - v1, 1.0, 1.0 - v0) if flip_v else (0.0, v0, 1.0, v1))
        cursor += into * length * unit


def sheds(mesh, faces, centre, radius, height, count=3, sides=FITTING, uv_scale=0.5, taper=0.72):
    """An insulator: a stack of skirts on a core, each wider at its lower rim."""
    cx, cy, cz = centre
    step = height / count
    for i in range(count):
        y = cy + i * step
        wide = radius * (1.0 - i * (1.0 - taper) / max(1, count))
        # the shed: a cone, widest at the bottom rim
        # skirt has an edge rather than a knife edge
        cylinder(mesh, faces, (cx, y + step * 0.30, cz), 'y', wide, step * 0.30, sides=sides,
                 uv_scale=uv_scale, taper=0.55)
        cylinder(mesh, faces, (cx, y + step * 0.04, cz), 'y', wide, step * 0.04, sides=sides,
                 uv_scale=uv_scale, caps=faces)
        # the core between one shed and the next
        cylinder(mesh, faces, (cx, y + step * 0.75, cz), 'y', wide * 0.52, step * 0.20,
                 sides=sides, uv_scale=uv_scale)
    cylinder(mesh, faces, (cx, cy + height, cz), 'y', radius * taper * 0.62, step * 0.10,
             sides=sides, uv_scale=uv_scale, caps=faces)


def pin_insulator(mesh, porcelain, steel, centre, diameter, sides=FITTING, spindle=True):
    """The mod's one insulator: an ANSI 55-4 pin insulator in brown glazed porcelain."""
    cx, cy, cz = centre
    d, h = diameter, diameter * 0.78

    # (radius, height) as fractions of the diameter and the height, bottom centre to top centre.
    contour = [
        (0.000, 0.000),
        (0.500, 0.000),   # the lower petticoat's rim, seen from underneath
        (0.300, 0.290),   # the cone up to the neck
        (0.255, 0.290),   # in to the neck
        (0.255, 0.340),
        (0.400, 0.340),   # out to the upper shed's rim
        (0.245, 0.565),   # its cone
        (0.235, 0.565),   # in to the upper neck
        (0.235, 0.660),
        (0.290, 0.660),   # out to the head
        (0.290, 0.712),
        (0.238, 0.712),   # the side groove, for a conductor running past
        (0.238, 0.768),
        (0.290, 0.768),
        (0.290, 0.812),
        (0.188, 0.812),   # the top groove, for one that terminates here
        (0.188, 0.898),
        (0.266, 0.898),   # out to the crown that holds the tie wire in
        (0.224, 1.000),
        (0.000, 1.000),
    ]
    lathe(mesh, porcelain, centre, [(r * d, y * h) for r, y in contour], sides=sides,
          uv_scale=1.0, uv_along=1.0)

    # The spindle: hot-dip galvanised steel, with the lead thimble the porcelain is cemented onto.
    if spindle:
        # half the sides: it is a spindle 8 mm across, mostly inside the porcelain
        # on one pole is the heaviest geometry in the mod
        lathe(mesh, steel, (cx, cy, cz), [
            (0.000, -0.34 * h), (0.085 * d, -0.34 * h),
            (0.085 * d, -0.10 * h), (0.118 * d, -0.08 * h),
            (0.118 * d, -0.005 * h), (0.000, -0.005 * h),
        ], sides=max(8, sides // 2), uv_scale=1.0, uv_along=1.0)


def tube(mesh, faces, points, radius, sides=FITTING, uv_scale=1.0, uv_along=1.0, caps=None,
         cap_ends=(-1, 1)):
    """A round tube swept along a polyline: the only way to draw a cable that turns."""
    path = [tuple(float(c) for c in point) for point in points]
    if len(path) < 2:
        return

    def norm(v):
        length = math.sqrt(sum(c * c for c in v)) or 1.0
        return (v[0] / length, v[1] / length, v[2] / length)

    def cross(a, b):
        return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

    def sub(a, b):
        return (a[0] - b[0], a[1] - b[1], a[2] - b[2])

    # the direction at each point: the segment's own at the ends, the average of the two at a corner
    segments = [norm(sub(path[i + 1], path[i])) for i in range(len(path) - 1)]
    tangents = [segments[0]]
    for i in range(1, len(path) - 1):
        tangents.append(norm((segments[i - 1][0] + segments[i][0], segments[i - 1][1] + segments[i][1],
                              segments[i - 1][2] + segments[i][2])))
    tangents.append(segments[-1])

    # one reference vector, carried along: the first is whichever axis the path starts least aligned
    # with, and every ring after it takes the previous ring's own, re-squared against the new tangent
    start = tangents[0]
    seed = (0.0, 1.0, 0.0) if abs(start[1]) < 0.9 else (1.0, 0.0, 0.0)
    up = norm(cross(cross(start, seed), start))

    rings, lengths, travelled = [], [0.0], 0.0
    for index, (point, tangent) in enumerate(zip(path, tangents)):
        if index:
            up = norm(cross(cross(tangent, up), tangent))
            travelled += math.dist(path[index], path[index - 1])
            lengths.append(travelled)
        side = norm(cross(tangent, up))
        ring = []
        for i in range(sides):
            a = 2.0 * math.pi * i / sides
            c, s = math.cos(a), math.sin(a)
            offset = (up[0] * c + side[0] * s, up[1] * c + side[1] * s, up[2] * c + side[2] * s)
            ring.append(((point[0] + offset[0] * radius, point[1] + offset[1] * radius,
                          point[2] + offset[2] * radius), offset))
        rings.append(ring)

    span = uv_along
    for index in range(len(rings) - 1):
        v0 = lengths[index] / max(travelled, 1e-6) * span
        v1 = lengths[index + 1] / max(travelled, 1e-6) * span
        for i in range(sides):
            j = (i + 1) % sides
            a, b = rings[index][i], rings[index][j]
            c, d = rings[index + 1][j], rings[index + 1][i]
            u0 = i / sides * uv_scale
            u1 = (i + 1) / sides * uv_scale
            normal = ((a[1][0] + b[1][0]) * 0.5, (a[1][1] + b[1][1]) * 0.5, (a[1][2] + b[1][2]) * 0.5)
            mesh.quad(faces, (a[0], b[0], c[0], d[0]), normal,
                      ((u0, v0), (u1, v0), (u1, v1), (u0, v1)))

    if caps is not None:
        for ring, tangent, sign in ((rings[0], tangents[0], -1), (rings[-1], tangents[-1], 1)):
            # ``cap_ends`` the same way ``cylinder`` takes it, because a swept run usually wants one end
            if sign not in cap_ends:
                continue

            normal = (tangent[0] * sign, tangent[1] * sign, tangent[2] * sign)
            corners = [point for point, _ in (ring if sign > 0 else list(reversed(ring)))]
            uvs = [(0.5 + 0.5 * math.cos(2.0 * math.pi * i / sides),
                    0.5 + 0.5 * math.sin(2.0 * math.pi * i / sides)) for i in range(sides)]
            mesh.quad(caps, corners, normal, uvs if sign > 0 else list(reversed(uvs)))


def arc(centre, radius, plane, start, end, steps):
    """Points round a quarter circle, for sweeping a bend along."""
    a, b = plane
    fixed = 3 - a - b
    out = []
    for i in range(steps + 1):
        angle = math.radians(start + (end - start) * i / steps)
        point = [0.0, 0.0, 0.0]
        point[a] = centre[a] + math.cos(angle) * radius
        point[b] = centre[b] + math.sin(angle) * radius
        point[fixed] = centre[fixed]
        out.append(tuple(point))
    return out


def lathe(mesh, faces, centre, profile, sides=FITTING, uv_scale=1.0, uv_along=1.0):
    """A surface of revolution through a profile: the honest way to draw anything turned."""
    cx, cy, cz = centre
    if len(profile) < 2:
        return

    lengths, travelled = [0.0], 0.0
    for i in range(1, len(profile)):
        travelled += math.dist(profile[i], profile[i - 1])
        lengths.append(travelled)

    def point(index, i):
        radius, height = profile[index]
        a = 2.0 * math.pi * i / sides
        return (cx + math.cos(a) * radius, cy + height, cz + math.sin(a) * radius)

    for index in range(len(profile) - 1):
        r0, h0 = profile[index]
        r1, h1 = profile[index + 1]
        if r0 <= 1e-9 and r1 <= 1e-9:
            continue

        v0 = lengths[index] / max(travelled, 1e-9) * uv_along
        v1 = lengths[index + 1] / max(travelled, 1e-9) * uv_along
        # the outward normal of the band, which is the profile segment turned a quarter turn
        dr, dh = r1 - r0, h1 - h0
        span = math.hypot(dr, dh) or 1.0
        for i in range(sides):
            j = (i + 1) % sides
            a, b = point(index, i), point(index, j)
            c, d = point(index + 1, j), point(index + 1, i)
            mid = 2.0 * math.pi * (i + 0.5) / sides
            normal = (math.cos(mid) * dh / span, -dr / span, math.sin(mid) * dh / span)
            u0, u1 = i / sides * uv_scale, (i + 1) / sides * uv_scale
            # a band that closes onto the axis is a triangle, and the quad degenerates cleanly
            mesh.quad(faces, (a, b, c, d), normal, ((u0, v0), (u1, v0), (u1, v1), (u0, v1)))
