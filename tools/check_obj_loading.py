#!/usr/bin/env python3
"""Every part a model authors is a part the game loads.

    python3 tools/check_obj_loading.py

Two ways a part is on disk and not in the world, both of which shipped:

**A repeated ``o`` name.**  Forge's ObjModel does ``parts.put(name, new ModelGroup(name))`` into a
LinkedHashMap (ObjModel.java, case "o"), so a second ``o`` of the same name *replaces* the first and every
face it held is never baked.  A part drawn in two materials used to be written as two same-named blocks,
so the game drew the junction box's lid and dropped its five walls, and drew one connector of a mated
pair.  Nothing else could see it: check_hitboxes, check_winding and render_blocks all append faces to a
flat list, so every tool here read the twelve faces the game had reduced to one.

**A model whose OBJ is not there.**  Minecraft logs ``Failed to load model`` at ERROR and carries on with
a missing model, which in a multipart blockstate is a part of the run that silently does not draw.  A
stale dc_trunk_cable_climb.json outlived the climb it drew that way.

Legal OBJ for a part in several materials is one ``o`` block with a ``usemtl`` section per material -
Forge makes a ModelMesh per material inside the group and bakes them all.  modellib.Mesh.write does that.
"""

import collections
import glob
import json
import os
import sys

ASSETS = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                      'src', 'main', 'resources', 'assets', 'electricity')


def repeated_names(path):
    """The ``o`` names this OBJ declares more than once, in file order."""
    names = [line.split()[1] for line in open(path) if line.startswith('o ') and len(line.split()) > 1]
    counted = collections.Counter(names)
    return [(name, counted[name]) for name in dict.fromkeys(names) if counted[name] > 1]


def split_materials(path):
    """Group name -> the materials it is drawn in, so a legal multi-material part reads as one part."""
    out, group = collections.defaultdict(list), None
    for line in open(path):
        parts = line.split()
        if not parts:
            continue
        if parts[0] == 'o':
            group = parts[1]
        elif parts[0] == 'usemtl' and group is not None and parts[1] not in out[group]:
            out[group].append(parts[1])
    return out


def resolved(reference):
    """``electricity:models/x/y.obj`` as a path under the mod's assets."""
    return os.path.join(ASSETS, *reference.split(':', 1)[-1].split('/'))


def main():
    problems = 0

    objs = sorted(glob.glob(os.path.join(ASSETS, 'models', '*', '*.obj')))
    for path in objs:
        for name, count in repeated_names(path):
            materials = split_materials(path).get(name, [])
            print('REPEATED  %s: "o %s" declared %d times -> the game bakes only the last.  '
                  'Materials seen: %s' % (os.path.relpath(path, ASSETS), name, count,
                                          ', '.join(materials) or '(none)'))
            problems += 1

    models = sorted(glob.glob(os.path.join(ASSETS, 'models', 'block', '*.json')))
    referenced = set()
    for path in models:
        model = json.load(open(path))
        reference = model.get('model')
        if not reference:
            continue
        target = resolved(reference)
        referenced.add(os.path.normpath(target))
        if not os.path.exists(target):
            print('MISSING   %s references %s, which is not on disk -> Minecraft logs "Failed to load '
                  'model" and draws nothing.' % (os.path.relpath(path, ASSETS), reference))
            problems += 1

    # An OBJ nothing names is dead weight the next regeneration will not clean up.  A machine's OBJ is
    # named in Java by ObjDefinitions, either as a whole path (electric_cab/cab.obj, which is not named
    # after its directory) or built as "models/" + modelName() + "/" + modelName() + ".obj" - so accept
    # the directory name as a string literal too, which is all the Java ever holds for those.
    java = os.path.join(ASSETS.split(os.sep + 'src' + os.sep)[0], 'src', 'main', 'java')
    sources = ''.join(open(os.path.join(root, name)).read()
                      for root, _, names in os.walk(java) for name in names if name.endswith('.java'))
    for path in objs:
        if os.path.normpath(path) in referenced:
            continue
        directory = os.path.basename(os.path.dirname(path))
        if 'models/%s/%s' % (directory, os.path.basename(path)) in sources:
            continue
        if os.path.basename(path) == directory + '.obj' and '"%s"' % directory in sources:
            continue
        print('ORPHAN    %s is named by no block model and by no ObjDefinitions entry.'
              % os.path.relpath(path, ASSETS))
        problems += 1

    if problems:
        print('\n%d problems' % problems)
        return 1
    print('every part these %d models author is a part the game loads' % (len(objs) + len(models)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
