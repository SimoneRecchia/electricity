#!/usr/bin/env python3
"""Proves the generators do not tread on each other, and that nothing writes a file nobody reads.

    python3 tools/check_generated_assets.py

Two faults live here and neither was visible from the game or from any other check.

Twenty block textures were claimed by *both* gen_block_textures.py and gen_pv_textures.py.  Whichever
ran last won, and gen_pv_textures ran last, so every regeneration quietly put the old low-resolution
drawing back: pv_module went from a 1024-pixel laminate to a 256-pixel one and nobody could tell from
the output which tool had written it.

And a texture nothing references still gets written, still ships, and still looks like part of the mod.
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
     'utility_pole', 'weather_tablet')}


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

    print('%d generated assets, %d referenced' % (len(measured), len(used & set(measured))))
    for problem in problems:
        print('  %s' % problem)
    if problems:
        print('\n%d problem(s)' % len(problems))
        return 1

    print('one generator a file, every file read, nothing under resolution')
    return 0


if __name__ == '__main__':
    sys.exit(main())
