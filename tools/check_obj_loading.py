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

**A fitting named after nothing.**  ObjDefinitions names each machine's fittings by group, and a wire is hung
on one by pointing at it - so a name no model draws is a terminal that has no hover box and no anchor.  The
wind turbine's said ``insulator_Plastic`` for as long as it has existed: the inherited model's object is
``insulator`` and its material ``insulator_porcelain``, and the mod's loader keys a group as object + "_" +
material, so the real name is ``insulator_insulator_porcelain``.  No wire could ever be hung on a turbine, and
the only sign of it was a warning in the log at every resource reload.

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
import re
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


def fitting_names(java):
    """Every group name ObjDefinitions names a fitting by, as a literal.

    The names built in a loop - "bushing_" + i + "_porcelain" - are covered by their own family's literals
    elsewhere in the same file, so the literals are what is worth checking and what went wrong.
    """
    source = open(os.path.join(java, 'com', 'dooji', 'electricity', 'main', 'registry',
                               'ObjDefinitions.java')).read()
    # followed by a comma or a close bracket, so the prefix of a "bushing_" + i + "_porcelain" is not one
    return sorted({name for name in re.findall(r'"([a-z][a-z0-9_]*)"\s*[,)]', source)
                   if name.startswith(('insulator', 'bushing'))})


def group_keys(objs):
    """Every group the mod's own loader would see: object + "_" + material, over every model."""
    keys = set()
    for path in objs:
        group = None
        for line in open(path):
            parts = line.split()
            if not parts:
                continue
            if parts[0] == 'o':
                group = parts[1]
            elif parts[0] == 'usemtl' and group is not None:
                keys.add(group + '_' + parts[1])
    return keys


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

    java = os.path.join(ASSETS.split(os.sep + 'src' + os.sep)[0], 'src', 'main', 'java')
    drawn = group_keys(objs)
    for name in fitting_names(java):
        if name not in drawn:
            print('NO GROUP  ObjDefinitions names the fitting "%s", which no model draws -> that terminal has '
                  'no hover box and no wire can be hung on it.' % name)
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
