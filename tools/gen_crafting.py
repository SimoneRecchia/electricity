#!/usr/bin/env python3
"""Writes every recipe, part item model and language entry for the crafting tree.

    python3 tools/gen_crafting.py

Writes into src/main/resources/data/electricity/recipes/, assets/electricity/models/item/ and merges
assets/electricity/lang/en_us.json.

Why a generator: there are seventy-odd recipes and thirty-two parts, each of which needs a recipe, an
item model, a name and a tooltip. Written by hand that is four files per part to keep in step; written
here the whole tree is one table that can be read top to bottom, and the parts come out of
PartCatalog.java rather than being restated - so a part cannot exist in the game without a recipe, a name
and a tooltip, and cannot have them without existing.

The shape of the tree
---------------------
Raw stock is made at a vanilla bench or in a furnace, because a player has to be able to start.
Everything else is made at the mod's own workbench, which is what makes it the gate between having iron
and building a power station.

And the products of one kind share a spine. Every array is three laminates over a mounting with
connectors under it; every inverter is bridges over an enclosure with a control board; every turbine is
three blades over a drivetrain. What differs between two models is what differs between the real
machines and nothing else - which is why a C112 and a C90 take the same generator pair and different
blades, why the 350 kW inverter has DC sections where the 2500 has busbars, and why the small-wind
machine has neither gearbox nor yaw drive.
"""

import json
import os
import re

DATA = os.path.join('src', 'main', 'resources', 'data', 'electricity', 'recipes')
ITEM_MODELS = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models', 'item')
LANG = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'lang', 'en_us.json')
CATALOGUE = os.path.join('src', 'main', 'java', 'com', 'dooji', 'electricity', 'main', 'registry',
                         'PartCatalog.java')

WORKBENCH = 'electricity:workbench'
SHAPED = 'minecraft:crafting_shaped'

# Every ingredient letter used below, resolved once. A letter means the same thing in every recipe in this
# file, which is what makes the tree readable: X is always a gearbox, G always a generator set.
ITEMS = {
    # vanilla
    'i': 'minecraft:iron_ingot',
    'n': 'minecraft:iron_nugget',
    'c': 'minecraft:copper_ingot',
    'o': 'minecraft:coal',
    'r': 'minecraft:redstone',
    'g': 'minecraft:gold_nugget',
    'q': 'minecraft:quartz',
    'a': 'minecraft:sand',
    'y': 'minecraft:glass_pane',
    'h': 'minecraft:honeycomb',
    'm': 'minecraft:slime_ball',
    'l': 'minecraft:iron_block',
    # the mod's own older parts
    'W': 'electricity:wire',
    'C': 'electricity:circuit_board',
    'U': 'electricity:cpu',
    'I': 'electricity:insulator',
    'M': 'electricity:metal_casing',
    'K': 'electricity:motor_core',
    'R': 'electricity:screen',
    # raw stock
    'P': 'electricity:steel_plate',
    'S': 'electricity:steel_section',
    'A': 'electricity:tempered_glass',
    'F': 'electricity:resin',
    'O': 'electricity:copper_coil',
    'N': 'electricity:silicon_ingot',
    'Z': 'electricity:silicon_wafer',
    'B': 'electricity:busbar',
    # components
    'V': 'electricity:solar_cell',
    'd': 'electricity:bypass_diode',
    'j': 'electricity:mc4_connector',
    'J': 'electricity:junction_box',
    'w': 'electricity:power_module',
    'k': 'electricity:capacitor_bank',
    'e': 'electricity:magnetic_core',
    'b': 'electricity:control_board',
    'Y': 'electricity:bearing',
    'H': 'electricity:gear_set',
    'f': 'electricity:gpv_fuse',
    'Q': 'electricity:load_break_switch',
    'p': 'electricity:sensor_head',
    # assemblies
    'L': 'electricity:pv_laminate',
    'E': 'electricity:enclosure',
    'D': 'electricity:dc_section',
    'G': 'electricity:inverter_bridge',
    'T': 'electricity:mounting_rack',
    't': 'electricity:torque_tube',
    'u': 'electricity:slew_drive',
    'x': 'electricity:generator_set',
    'X': 'electricity:gearbox',
    's': 'electricity:turbine_blade',
    'z': 'electricity:long_blade',
}

# (result, count, type, pattern) - the tree, top to bottom.
RECIPES = [
    # ------------------------------------------------------------------ raw stock, at a vanilla bench
    #
    # Steel is iron with carbon in it, which is the one bit of metallurgy worth putting in a recipe: a
    # plate is rolled from a slab and a section is folded from plate.
    ('steel_plate', 3, SHAPED, ['iii', 'ooo']),
    ('steel_section', 2, SHAPED, ['P', 'P']),
    ('busbar', 2, SHAPED, ['ccc']),
    ('copper_coil', 1, SHAPED, ['WWW', ' n ']),
    # a film of polymer and a wax: what a laminate is glued together with and what a blade's fibre is
    # bound in
    ('resin', 2, SHAPED, ['mh']),
    # sawn from the ingot, which is where every cell and every switching die starts
    ('silicon_wafer', 4, SHAPED, ['N']),

    # ---------------------------------------------------------------- components, at the workbench
    #
    # A cell is a wafer with a junction diffused into it and its fingers printed on: silicon, a dopant,
    # and silver paste, which here is the mod's own wire.
    ('solar_cell', 3, WORKBENCH, ['ZZZ', ' r ', 'WWW']),
    ('bypass_diode', 2, WORKBENCH, ['q', 'r', 'g']),
    ('mc4_connector', 4, WORKBENCH, ['II', 'WW']),
    # three diodes, because a sixty-cell module has three substrings and each one gets its own
    ('junction_box', 1, WORKBENCH, [' d ', 'dMd', ' j ']),
    # switching dies on a substrate over a baseplate, which is what a power module is
    ('power_module', 1, WORKBENCH, [' Z ', 'BCB', ' P ']),
    # a film capacitor bank in a steel can: polymer film, charge, and something to hold it
    ('capacitor_bank', 1, WORKBENCH, ['FrF', 'FrF', 'PPP']),
    # laminated iron with a winding through it
    ('magnetic_core', 1, WORKBENCH, ['PPP', 'OOO', 'PPP']),
    ('control_board', 1, WORKBENCH, [' U ', 'CWC']),
    ('bearing', 2, WORKBENCH, [' P ', 'nPn', ' P ']),
    ('gear_set', 2, WORKBENCH, [' S ', 'SPS', ' S ']),
    # a glass barrel, silver element and sand filler, which is exactly what a photovoltaic fuse is
    ('gpv_fuse', 4, WORKBENCH, ['yay', ' B ']),
    # contacts, an arc chamber and the snap mechanism that makes it break rather than draw
    ('load_break_switch', 1, WORKBENCH, [' I ', 'BSB', ' H ']),
    ('sensor_head', 2, WORKBENCH, [' A ', 'CWC']),

    # ----------------------------------------------------------------- assemblies, at the workbench
    #
    # The module: front glass, encapsulant, the cells, the frame rails and the junction box on the back.
    ('pv_laminate', 1, WORKBENCH, ['AFA', 'VVV', 'SJS']),
    # eight plates and a gasket: an enclosure is mostly steel, which is why a cabinet costs what it does
    ('enclosure', 1, WORKBENCH, ['PPP', 'PIP', 'PPP']),
    ('dc_section', 1, WORKBENCH, ['fff', 'BQB', 'PPP']),
    ('inverter_bridge', 1, WORKBENCH, ['www', 'kbk', 'BeB']),
    ('mounting_rack', 1, WORKBENCH, ['S S', 'SSS', 'P P']),
    ('torque_tube', 1, WORKBENCH, ['SSS', 'YPY']),
    ('slew_drive', 1, WORKBENCH, [' H ', 'YKY', ' b ']),
    # stator windings round a rotor, in a frame
    ('generator_set', 1, WORKBENCH, ['OOO', 'ele', 'SSS']),
    ('gearbox', 1, WORKBENCH, ['HHH', 'YPY', 'PPP']),
    # glass fibre in resin over a steel spar, which is how a blade is actually built
    ('turbine_blade', 1, WORKBENCH, ['AAA', 'FSF', 'AAA']),
    # the same blade with more fibre on a stronger spar: a longer rotor for a quieter site
    ('long_blade', 1, WORKBENCH, ['AAA', 'FsF', 'AAA']),

    # --------------------------------------------------------------------------- the cables
    #
    # Tinned copper in a polymer jacket. The trunk is bar rather than wire and carries armour, which is
    # the difference between a string cable and a home run.
    ('dc_string_cable', 6, WORKBENCH, ['WWW', 'FFF']),
    ('dc_trunk_cable', 4, WORKBENCH, ['BBB', 'FFF', 'PPP']),

    # --------------------------------------------------------------------------- the arrays
    #
    # Three laminates over a mounting with connectors under it, every time. What changes between them is
    # the mounting and nothing else - which is exactly what changes between the real ones.
    ('pv_flat_430', 1, WORKBENCH, ['LLL', 'PTP', 'jSj']),
    ('pv_tilt_530', 1, WORKBENCH, ['LLL', 'STS', 'jSj']),
    # the 580 W module is the large-format one, so it is the same rack with a fourth laminate of silicon
    ('pv_tilt_580', 1, WORKBENCH, ['LLL', 'STS', 'jLj']),
    ('pv_track_700', 1, WORKBENCH, ['LLL', 'tut', 'jbj']),
    # two slew drives, because a dual-axis frame has two axes to turn
    ('pv_dual_440', 1, WORKBENCH, ['LLL', 'uSu', 'jbj']),

    # ------------------------------------------------------------------------- the inverters
    #
    # Bridges over an enclosure with a control board under it. One bridge per MPPT channel's worth of
    # nameplate, fans where the machine has them, and the middle row is its DC terminals: nothing on the
    # small string machine, a DC section where it takes trunks as well as strings, and busbars on the
    # central machine, which takes nothing but.
    ('inverter_10', 1, WORKBENCH, [' G ', ' E ', ' b ']),
    ('inverter_110', 1, WORKBENCH, ['GGG', ' E ', 'KbK']),
    ('inverter_350', 1, WORKBENCH, ['GGG', 'DED', 'KbK']),
    ('inverter_2500', 1, WORKBENCH, ['GGG', 'BEB', 'KbK']),

    # ------------------------------------------------------------------------- the combiners
    #
    # Fuse ways over an enclosure with the load-break switch under it. More ways means more bar to carry
    # the output, and the thirty-two way box is the one built to IP66.
    ('pv_combiner_6', 1, WORKBENCH, ['fff', ' E ', ' Q ']),
    ('pv_combiner_16', 1, WORKBENCH, ['fff', 'BEB', ' Q ']),
    ('pv_combiner_32', 1, WORKBENCH, ['fff', 'BEB', 'PQP']),

    # ------------------------------------------------------------------- the met station
    #
    # Three sensor heads on a mast with a logger in the middle, which is what one is.
    ('met_station', 1, WORKBENCH, [' p ', 'pbp', 'SSS']),

    # ------------------------------------------------------------------- towers and turbines
    #
    # A tower section is rolled plate with a flange bolted at each end.
    ('turbine_tower', 2, WORKBENCH, ['PPP', 'n n', 'PPP']),
    # Three blades over a drivetrain, and the drivetrain is where the machines differ. The small-wind
    # machine has neither gearbox nor yaw drive - it turns on a tail vane and drives its generator
    # directly, which is what small wind is.
    ('sw_10', 1, WORKBENCH, ['sss', ' x ', 'SbS']),
    ('c52_085', 1, WORKBENCH, ['sss', 'XxY', 'ubE']),
    # long blades: an eighty metre rotor on the same drivetrain
    ('c80_20', 1, WORKBENCH, ['zzz', 'XxY', 'ubE']),
    # three megawatts: the generator doubles
    ('c90_30', 1, WORKBENCH, ['zzz', 'Xxx', 'ubE']),
    # the same generator pair as the C90 and no gearbox - a second main bearing instead, which is what
    # direct drive is, and the reason these two share a nameplate on different rotors
    ('c112_30', 1, WORKBENCH, ['zzz', 'Yxx', 'ubE']),
    # four megawatts, direct drive, and all generator
    ('wind_turbine', 1, WORKBENCH, ['zzz', 'xxx', 'ubE']),
]


def parts():
    """The parts, out of PartCatalog.java, so this file cannot disagree with the registry."""
    source = open(CATALOGUE).read()
    found = []
    for match in re.finditer(r'new Part\("([a-z0-9_]+)", Tier\.(\w+), "([^"]+)",\s*"([^"]+)"\)', source):
        found.append(match.groups())

    return found


def key_for(pattern):
    """The key map for a pattern: every letter in it, resolved through ITEMS."""
    letters = {c for row in pattern for c in row if c != ' '}
    unknown = sorted(c for c in letters if c not in ITEMS)
    if unknown:
        raise SystemExit('gen_crafting: no ingredient for %s' % ', '.join(unknown))

    return {c: {'item': ITEMS[c]} for c in sorted(letters)}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def check(catalogue):
    """Three things that have to hold, and would be invisible in the game if they did not.

    Two recipes with the same grid are ambiguous - the game picks whichever it indexed first, so one of
    the two products becomes uncraftable with no error anywhere. A part with no recipe is an item that
    exists and cannot be got. And a part nothing consumes is a dead end: it would be craftable, useless,
    and a player would waste an evening working out why.
    """
    problems = []

    grids = {}
    for result, _, kind, pattern in RECIPES:
        grid = (kind, tuple(''.join(ITEMS.get(c, ' ') for c in row) for row in pattern))
        if grid in grids:
            problems.append('%s and %s have the same grid, so one of them is uncraftable'
                            % (grids[grid], result))
        grids[grid] = result

    made = {result for result, _, _, _ in RECIPES} | {'tempered_glass', 'silicon_ingot'}
    used = {ITEMS[c].split(':')[1] for _, _, _, pattern in RECIPES for row in pattern
            for c in row if c != ' ' and ITEMS[c].startswith('electricity:')}
    for part_id, _, name, _ in catalogue:
        if part_id not in made:
            problems.append('%s has no recipe' % part_id)
        if part_id not in used:
            problems.append('%s is made and never used' % part_id)

    for line in problems:
        print('    PROBLEM %s' % line)

    return len(problems)


def main():
    catalogue = parts()

    # one item model per part: a flat sprite, like every other ingredient in the game
    for part_id, _, _, _ in catalogue:
        write(os.path.join(ITEM_MODELS, part_id + '.json'),
              {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'electricity:item/' + part_id}})

    # the names and the tooltips, merged rather than rewritten: the file has two hundred keys already
    lang = json.load(open(LANG))
    for part_id, _, name, tooltip in catalogue:
        lang['item.electricity.' + part_id] = name
        lang['tooltip.electricity.' + part_id] = tooltip
    write(LANG, lang)

    # and the recipes
    ingots = 0
    for result, count, kind, pattern in RECIPES:
        recipe = {'type': kind, 'pattern': pattern, 'key': key_for(pattern),
                  'result': {'item': 'electricity:' + result}}
        if count > 1:
            recipe['result']['count'] = count

        write(os.path.join(DATA, result + '.json'), recipe)
        ingots += 1

    # the two furnace steps, which are the only ones that are not a bench
    for result, source, name in (('tempered_glass', 'minecraft:glass', 'glass'),
                                 ('silicon_ingot', 'minecraft:quartz', 'quartz')):
        write(os.path.join(DATA, result + '.json'),
              {'type': 'minecraft:smelting', 'ingredient': {'item': source},
               'result': 'electricity:' + result, 'experience': 0.1, 'cookingtime': 200})

    print('%d parts, %d bench recipes, 2 furnace recipes' % (len(catalogue), ingots))
    print('lang now has %d keys' % len(lang))
    problems = check(catalogue)
    print('the tree is consistent' if problems == 0 else '%d problem(s) in the tree' % problems)


if __name__ == '__main__':
    main()
