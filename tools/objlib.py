#!/usr/bin/env python3
"""Reading an OBJ, once.

Seven tools here read the models and every one of them had its own parser.  CLAUDE.md's loudest lesson is
that **a tool that reads a model differently from the game proves nothing** - the renderer said the junction
box had walls for four rounds, because it appended faces to a flat list where the game replaced a whole
group.  Seven parsers is seven chances to make that mistake again in a different file.

``read`` gives back the faces as the file states them and nothing more:

* ``normal`` is None where the file states none.  check_winding compares a face's winding against its
  *stated* normal, so a reader that made one up from the winding would make that check pass on everything.
* nothing is triangulated.  A polygon is a polygon: check_hitboxes bounds it whole.  ``triangles`` is the
  separate step, for the tools that rasterise.
* ``occurrence`` is which ``o`` block a face came from, which is all ``forge_only`` needs.

**The two pipelines group differently and it matters.**  Forge's ObjModel does ``parts.put(name, ...)`` into
a map, so a second ``o`` of the same name *replaces* the first and every face it held is never baked - that
is true of a block model, and only of one.  A machine's OBJ is read by the mod's own RenderixSplitter, which
splits by object and then by material and *merges*.  So ``read`` merges, and ``forge_only`` is the filter the
block-model path puts on top of it.
"""

import collections
import os

MODELS = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')

# One face, as the file states it.  ``points`` and ``uvs`` are in file order, so the winding is the file's.
Face = collections.namedtuple('Face', 'group material points uvs normal occurrence')


def read(path):
    """Every face of an OBJ, in file order."""
    verts, uvs, normals, faces = [], [], [], []
    group, material, occurrence, seen = 'none', 'none', -1, {}
    for line in open(path):
        parts = line.split()
        if not parts:
            continue

        head = parts[0]
        if head == 'v':
            verts.append(tuple(float(value) for value in parts[1:4]))
        elif head == 'vt':
            uvs.append(tuple(float(value) for value in parts[1:3]))
        elif head == 'vn':
            normals.append(tuple(float(value) for value in parts[1:4]))
        elif head == 'o':
            group = parts[1]
            # a repeated name is a *later* occurrence of the same part, which is the whole of Forge's rule
            occurrence = seen[group] = seen.get(group, -1) + 1
        elif head == 'usemtl':
            material = parts[1]
        elif head == 'f':
            faces.append(_face(parts[1:], verts, uvs, normals, group, material, occurrence))

    return faces


def _face(fields, verts, uvs, normals, group, material, occurrence):
    points, texture, normal = [], [], None
    for field in fields:
        bits = field.split('/')
        points.append(verts[int(bits[0]) - 1])
        texture.append(uvs[int(bits[1]) - 1] if len(bits) > 1 and bits[1] else None)
        if len(bits) > 2 and bits[2]:
            normal = normals[int(bits[2]) - 1]

    return Face(group, material, points, texture, normal, occurrence)


def grouped(faces, key=None):
    """Faces bucketed by a key, in the order the file first used it - the group name by default."""
    key = key or (lambda face: face.group)
    out = collections.OrderedDict()
    for face in faces:
        out.setdefault(key(face), []).append(face)

    return out


def polygons(faces, key=None):
    """The same, as bare point lists: what anything that only wants the shape needs."""
    return collections.OrderedDict((name, [face.points for face in group])
                                   for name, group in grouped(faces, key).items())


def triangles(faces):
    """Every face fanned into triangles of points, for the tools that rasterise."""
    out = []
    for face in faces:
        for index in range(1, len(face.points) - 1):
            out.append((face.points[0], face.points[index], face.points[index + 1]))

    return out


def bounds(points):
    """The box round a run of points, as (low, high), or None if there are none."""
    points = list(points)
    if not points:
        return None

    return (tuple(min(p[axis] for p in points) for axis in range(3)),
            tuple(max(p[axis] for p in points) for axis in range(3)))


def forge_only(faces):
    """The faces Forge's own loader would bake: the last ``o`` block of each name wins, the rest are dropped.

    Only a block model is read that way.  See the module docstring.
    """
    last = {}
    for face in faces:
        last[face.group] = max(last.get(face.group, -1), face.occurrence)

    return [face for face in faces if face.occurrence == last[face.group]]


def material_library(path):
    """The MTL an OBJ names, or the file's own name with the extension swapped if it names none."""
    for line in open(path):
        if line.startswith('mtllib '):
            return line.split()[1]

    return os.path.splitext(os.path.basename(path))[0] + '.mtl'


def centre(faces, group):
    """The mean of a group's points: where a ``pivot_*`` marker is, which is what a hinge is measured from."""
    points = [point for face in faces if face.group == group for point in face.points]
    if not points:
        return None

    return tuple(sum(p[axis] for p in points) / len(points) for axis in range(3))
