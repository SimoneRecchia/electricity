#!/usr/bin/env python3
"""Proves the generators do not tread on each other, and that nothing writes a file nobody reads.

    python3 tools/check_generated_assets.py

Three faults live here and none was visible from the game or from any other check.

Twenty block textures were claimed by *both* gen_block_textures.py and gen_pv_textures.py.  Whichever
ran last won, and gen_pv_textures ran last, so every regeneration quietly put the old low-resolution
drawing back: pv_module went from a 1024-pixel laminate to a 256-pixel one and nobody could tell from
the output which tool had written it.

And a texture nothing references still gets written, still ships, and still looks like part of the mod.

And a block *model* nothing names still ships: 487 lines of the trunk cable's pre-OBJ drawing sat on disk
naming a texture that had been deleted under it.

And a *blockstate* no generator writes drifts, because nothing regenerates it.  Twenty-two of them were
hand-written, and one had gone wrong: the kiosk has a `mounted` as well as a facing, and its four `facing=`
variant keys named half its states - Minecraft resolved the other half to no model at all.  A machine's
blockstate is only the particle it breaks into, so every one of them is now one unconditional multipart from
gen_crafting.MACHINES, and this file is what keeps a hand-written one from coming back.
"""

import importlib
import os
import re
import struct
import sys

TOOLS = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, TOOLS)

ASSETS = os.path.join('src', 'main', 'resources', 'assets', 'electricity')
TEXTURES = os.path.join(ASSETS, 'textures')

# Resolution follows how close a player gets to the face - CLAUDE.md section 2.  This is the floor: a
# block texture below it is one that was left behind by an older pass.
MIN_BLOCK = 128

# What is not drawn by a script.  block/plastic2.png is the turbine's nacelle plastic, on
# wind_turbine.mtl; the item sprites are the original ones, kept at the user's word over a generated set.
# Everything else in the mod is generated - see CLAUDE.md section 6.
INHERITED = {'block/plastic2.png'} | {
    'item/%s.png' % name for name in
    ('cab', 'circuit_board', 'cpu', 'insulator', 'metal_casing', 'motor_core', 'power_box', 'screen',
     'utility_pole')}


def outputs():
    """Which generator writes which asset, read out of the generators' own tables.

    The modules are imported rather than parsed: the tables are assembled with ``update`` from several
    families, so reading the source would see a third of the sprites.  Importing draws nothing - every
    generator keeps its work in ``main``.
    """
    claimed = {}
    for name in sorted(os.listdir(TOOLS)):
        if not name.startswith('gen_') or not name.endswith('.py'):
            continue

        module = importlib.import_module(name[:-3])
        for attribute in dir(module):
            if not attribute.endswith('TEXTURES'):
                continue

            table = getattr(module, attribute)
            if not isinstance(table, dict) or not all(isinstance(k, str) for k in table):
                continue

            # a table of textures a *model* names resolves to paths; a table of drawings is keyed by name
            if any(isinstance(v, str) for v in table.values()):
                continue

            folder = 'item' if 'ITEM' in attribute else 'block'
            for key in table:
                claimed.setdefault('%s/%s.png' % (folder, key), []).append(name)
    return claimed


def orphan_models():
    """Block models nothing names - not a blockstate, not another model, not the Java.

    The trunk cable was drawn by vanilla JSON before it became an OBJ, and 487 lines of it stayed on disk
    naming a texture that is not there. A model nobody reads still ships and still looks like part of the mod.
    """
    folder = os.path.join(ASSETS, 'models', 'block')
    text = ''
    for root, _, names in os.walk('src'):
        if os.path.abspath(root) == os.path.abspath(folder):
            continue
        for name in names:
            if name.endswith(('.json', '.java', '.mtl')):
                text += open(os.path.join(root, name)).read()

    return sorted(name[:-5] for name in os.listdir(folder)
                  if name.endswith('.json') and 'electricity:block/%s"' % name[:-5] not in text)


def blockstates():
    """Which generator claims which blockstate, and what is actually on disk.

    Three tools write them: gen_crafting for the machines, and the two run generators for the cable gauges
    and the ground conductors, which name their products in NAME and JACKETS.
    """
    claimed = {}
    for module_name, names in (('gen_crafting', None), ('gen_cable_models', None),
                               ('gen_trunk_models', None), ('gen_conductor_models', None)):
        module = importlib.import_module(module_name)
        if hasattr(module, 'MACHINES'):
            names = list(module.MACHINES)
        elif hasattr(module, 'JACKETS'):
            names = ['%s_run' % name for name in module.JACKETS]
        elif hasattr(module, 'NAME'):
            names = [module.NAME]
        else:
            names = []

        for name in names:
            claimed.setdefault(name + '.json', []).append(module_name + '.py')

    on_disk = sorted(name for name in os.listdir(os.path.join(ASSETS, 'blockstates'))
                     if name.endswith('.json'))
    return claimed, on_disk


def loot():
    """Every block that must drop something, against the tables on disk.

    A block with requiresCorrectToolForDrops() and no loot table breaks into nothing, and nothing in the
    game says so.  Twenty-six of these sat on disk with no generator behind them while the table that wrote
    the rest was a hand-written list of ten - so the next machine added got none at all.
    """
    import gen_crafting
    claimed = set(gen_crafting.DROPS)
    folder = os.path.join('src', 'main', 'resources', 'data', 'electricity', 'loot_tables', 'blocks')
    on_disk = {name[:-5] for name in os.listdir(folder) if name.endswith('.json')}
    machines = {name for name in gen_crafting.MACHINES if name != 'machine_shell'}
    return claimed, on_disk, machines


def referenced():
    """Every texture named by a model, a material library, a blockstate or the Java."""
    seen = set()
    roots = [ASSETS, os.path.join('src', 'main', 'java')]
    for root in roots:
        for folder, _, files in os.walk(root):
            if os.path.join('textures') in folder:
                continue

            for name in files:
                if not name.endswith(('.json', '.mtl', '.java')):
                    continue

                text = open(os.path.join(folder, name)).read()
                for kind in ('block', 'item'):
                    seen |= {'%s/%s.png' % (kind, m) for m in
                             re.findall(r'electricity:%s/([a-z0-9_/]+)' % kind, text)}
                # an mtl names a bare file, and the folder it sits in says which kind it is
                seen |= {'block/%s' % m for m in re.findall(r'map_Kd\s+([a-z0-9_]+\.png)', text)}
    return seen


def sizes():
    """The pixel size of every texture on disk."""
    out = {}
    for kind in ('block', 'item'):
        folder = os.path.join(TEXTURES, kind)
        for name in sorted(os.listdir(folder)):
            if not name.endswith('.png'):
                continue

            with open(os.path.join(folder, name), 'rb') as handle:
                header = handle.read(24)
            width, height = struct.unpack('>II', header[16:24])
            out['%s/%s' % (kind, name)] = (width, height)
    return out


def main():
    problems = []
    claimed, used, measured = outputs(), referenced(), sizes()

    for path, tools in sorted(claimed.items()):
        if len(tools) > 1:
            problems.append('%s is written by %s - whichever runs last wins'
                            % (path, ' and '.join(tools)))

    for path in sorted(measured):
        if path not in claimed and path not in INHERITED:
            problems.append('%s is on disk but no generator writes it' % path)
        elif path not in used:
            problems.append('%s is generated but no model, mtl or class names it' % path)

    for path in sorted(claimed):
        if path not in measured:
            problems.append('%s is claimed by %s but is not on disk'
                            % (path, ' and '.join(claimed[path])))

    for path, (width, height) in sorted(measured.items()):
        if path.startswith('block/') and min(width, height) < MIN_BLOCK:
            problems.append('%s is %dx%d, under the %d-pixel floor a block face gets'
                            % (path, width, height, MIN_BLOCK))

    for name in orphan_models():
        problems.append('models/block/%s.json is named by no blockstate, model or class' % name)

    states, on_disk = blockstates()
    for name, tools in sorted(states.items()):
        if len(tools) > 1:
            problems.append('blockstates/%s is written by %s - whichever runs last wins'
                            % (name, ' and '.join(tools)))
        if name not in on_disk:
            problems.append('blockstates/%s is claimed by %s but is not on disk'
                            % (name, ' and '.join(tools)))
    for name in on_disk:
        if name not in states:
            problems.append('blockstates/%s is on disk but no generator writes it' % name)

    dropped, tables, machines = loot()
    for name in sorted(machines - dropped):
        problems.append('%s is a machine that drops nothing: no generator writes its loot table' % name)
    for name in sorted(dropped - tables):
        problems.append('loot_tables/blocks/%s.json is claimed but is not on disk' % name)
    for name in sorted(tables - dropped):
        problems.append('loot_tables/blocks/%s.json is on disk but no generator writes it' % name)

    print('%d generated assets, %d referenced, %d blockstates from %d generators, %d loot tables'
          % (len(measured), len(used & set(measured)), len(on_disk),
             len({tool for tools in states.values() for tool in tools}), len(tables)))
    for problem in problems:
        print('  %s' % problem)
    if problems:
        print('\n%d problem(s)' % len(problems))
        return 1

    print('one generator a file, every file read, nothing under resolution')
    return 0


if __name__ == '__main__':
    sys.exit(main())
