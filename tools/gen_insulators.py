#!/usr/bin/env python3
"""Patches the one pin insulator into the machines that already carry one, in place.

    python3 tools/gen_insulators.py

Rewrites the named objects in cab.obj and wind_turbine.obj and compacts the shared tables, so a second
run is byte-identical.  Placements are constants here for the same reason.
"""

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from modellib import Mesh, pin_insulator                                          # noqa: E402

MODELS = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models')

# The materials the replacement needs
NEEDED = {
    'insulator_porcelain': 'porcelain_brown.png',
    'insulator_steel': 'pv_steel.png',
}

# Where each one goes, measured off the object it replaces: the centre of its footprint
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
    """Rewrites the shared tables to hold only what the faces still reference."""
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
