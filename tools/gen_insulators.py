#!/usr/bin/env python3
"""Puts the mod's one insulator into the two models that were not generated.

    python3 tools/gen_insulators.py

Rewrites the named insulator objects inside src/main/resources/assets/electricity/models/{electric_cab,
wind_turbine}/ and adds the materials they need to those models' .mtl files.

Why a patcher rather than a generator
-------------------------------------
The turbine and the cabin are inherited art - a modelling package's output against a 1024-pixel atlas -
and they are the two models a player already liked, so they are not being redrawn.  But their wire
fittings were the two worst things in the mod: the turbine's was a stack of five flat green plastic
discs and the cabin's a tall brown bushing, neither of them the brown porcelain pin insulator the pole
and the kiosk carry.  Five objects, four different fittings, one of which was white because it had been
given the wrong material.

A distribution insulator is a catalogue part.  Whoever built the line bought the same ANSI 55-4 for
every structure on it, so the mod should draw one insulator and put it everywhere - which for these two
means replacing the faces of one object inside a file and leaving every other object in it alone.

How the replacement is safe
---------------------------
The wire hangs from the *centre of the insulator group's bounding box*, read off the model at runtime by
``calculateOrientedInsulatorCenter`` - so the new geometry carries its own anchor and a wire in an
existing world follows it.  What must not change is the *order* of the names in ``ObjDefinitions``,
because a wire is stored against its index in that list.  Nothing here touches the names.

The placements below are measured off the models they replace and then stated as constants, so running
this twice gives the same file: reading them back out of a file this script has already rewritten would
make the second run depend on the first.
"""

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from modellib import Mesh, pin_insulator                                          # noqa: E402

MODELS = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')

# The materials the replacement needs, and the textures they resolve to.  Added to each model's own .mtl
# rather than reusing whatever it had: the cabin's insulator wore seven materials off the inherited
# atlas and the turbine's wore one called Plastic, and none of them is porcelain.
NEEDED = {
    'insulator_porcelain': 'porcelain_brown.png',
    'insulator_steel': 'pv_steel.png',
}

# Where each one goes, measured off the object it replaces: the centre of its footprint, the height its
# porcelain sits down at, and the diameter of its widest shed.
#
# The two on the cabin sat where the old bushings did, at their own width.  The turbine's stood under
# the nacelle and was half a block across and flat - a fitting that wide would dwarf the real thing, so
# it takes the same diameter the pole's do and keeps the old one's top, which is where the wire was.
PLACEMENTS = {
    'electric_cab/cab.obj': (
        ('insulator_input', (-0.0255, 2.6062, -0.67165), 0.2146),
        ('insulatoroutput', (-0.1554, 2.6062, 0.80785), 0.2146),
    ),
    'wind_turbine/wind_turbine.obj': (
        ('insulator', (0.0, -0.1515, 0.0), 0.1660),
    ),
}


def read(path):
    """An OBJ split into its header, its shared tables and one section per object."""
    header, verts, normals, uvs, sections = [], [], [], [], []
    current = None
    for line in open(path):
        if line.startswith('v '):
            verts.append(line)
        elif line.startswith('vn '):
            normals.append(line)
        elif line.startswith('vt '):
            uvs.append(line)
        elif line.startswith('o '):
            current = [line.split(None, 1)[1].strip(), []]
            sections.append(current)
        elif current is None:
            header.append(line)
        else:
            current[1].append(line)

    return header, verts, normals, uvs, sections


def build(placement):
    """The replacement geometry for one fitting, as its own little mesh."""
    _, centre, diameter = placement
    mesh = Mesh()
    pin_insulator(mesh, mesh.faces('insulator', 'insulator_porcelain'),
                  mesh.faces('insulator', 'insulator_steel'), centre, diameter)
    return mesh


def emit(mesh, name):
    """The mesh's own tables and faces, still numbered from one: ``rewrite`` renumbers them."""
    faces = []
    for _, material, corners_list in mesh.objects:
        if not corners_list:
            continue
        faces.append((material, corners_list))

    return {'v': list(mesh.v), 'vt': list(mesh.vt), 'vn': list(mesh.vn), 'faces': faces}


def compact(sections, verts, uvs, normals):
    """Rewrites the shared tables to hold only what the faces still reference.

    Which is what makes running this twice give the same file.  Replacing an object by appending its
    geometry leaves the object it replaced still in the vertex table, unreferenced - so the file grows
    every run and the second run is not the first.  Rebuilding the tables from what is actually used
    also means the file shrinks by whatever the old fitting cost.
    """
    keep = {'v': {}, 'vt': {}, 'vn': {}}
    tables = {'v': [], 'vt': [], 'vn': []}
    source = {'v': verts, 'vt': uvs, 'vn': normals}

    def renumber(kind, index):
        found = keep[kind].get(index)
        if found is None:
            tables[kind].append(source[kind][index - 1])
            found = len(tables[kind])
            keep[kind][index] = found
        return found

    out = []
    for name, body in sections:
        lines = []
        for line in body:
            if not line.startswith('f '):
                lines.append(line)
                continue

            corners = []
            for token in line.split()[1:]:
                parts = (token.split('/') + ['', ''])[:3]
                v = renumber('v', int(parts[0]))
                t = renumber('vt', int(parts[1])) if parts[1] else None
                n = renumber('vn', int(parts[2])) if parts[2] else None
                corners.append('%d/%s/%s' % (v, t if t else '', n if n else ''))
            lines.append('f ' + ' '.join(corners) + '\n')
        out.append((name, lines))

    return out, tables


def patch_mtl(path, source):
    """Adds the materials the replacement needs, if the file has not got them already."""
    text = open(path).read()
    added = []
    for name, texture in NEEDED.items():
        if re.search(r'^newmtl %s$' % re.escape(name), text, re.M):
            continue

        text += ('\nnewmtl %s\n'
                 'Ns 250.000000\nKa 1.000000 1.000000 1.000000\nKs 0.500000 0.500000 0.500000\n'
                 'Ke 0.000000 0.000000 0.000000\nNi 1.500000\nd 1.000000\nillum 2\n'
                 'map_Kd %s\n' % (name, texture))
        added.append(name)

    if added:
        text = text.replace('\n', '\n', 1)
        open(path, 'w').write(text)

    return added


def patch(relative, placements):
    path = os.path.join(MODELS, relative)
    header, verts, normals, uvs, sections = read(path)
    wanted = {placement[0] for placement in placements}

    kept = [(name, body) for name, body in sections if name not in wanted]
    found = {name for name, _ in sections if name in wanted}
    missing = sorted(wanted - found)
    if missing:
        raise SystemExit('%s: no object called %s' % (relative, ', '.join(missing)))

    # the replacements, as sections of their own with their tables tacked onto the ends
    drawn = 0
    for placement in placements:
        piece = emit(build(placement), placement[0])
        v_base, vt_base, vn_base = len(verts), len(uvs), len(normals)
        verts += ['v %.6f %.6f %.6f\n' % point for point in piece['v']]
        uvs += ['vt %.6f %.6f\n' % point for point in piece['vt']]
        normals += ['vn %.4f %.4f %.4f\n' % point for point in piece['vn']]
        body = []
        for material, corners_list in piece['faces']:
            body.append('usemtl %s\n' % material)
            for corners in corners_list:
                body.append('f ' + ' '.join('%d/%d/%d' % (v + v_base, t + vt_base, n + vn_base)
                                            for v, t, n in corners) + '\n')
                drawn += 1
        kept.append((placement[0], body))

    sections, tables = compact(kept, verts, uvs, normals)

    with open(path, 'w') as f:
        f.writelines(header)
        f.writelines(tables['v'])
        f.writelines(tables['vn'])
        f.writelines(tables['vt'])
        for name, body in sections:
            f.write('o %s\n' % name)
            f.writelines(body)

    mtl = os.path.join(os.path.dirname(path),
                       [line.split()[1] for line in header if line.startswith('mtllib')][0])
    added = patch_mtl(mtl, relative)
    print('%-30s %-34s %4d faces, %d vertices, %d material(s) added'
          % (relative, ', '.join(sorted(wanted)), drawn, len(tables['v']), len(added)))


def main():
    for relative, placements in PLACEMENTS.items():
        patch(relative, placements)


if __name__ == '__main__':
    main()
