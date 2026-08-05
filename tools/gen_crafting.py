#!/usr/bin/env python3
"""Recipes, part item models and the language file.

    python3 tools/gen_crafting.py

Everything is a shaped crafting-table recipe.  check() reads every recipe on disk, mirrors included, and
fails on an ambiguous pair - two recipes the game cannot tell apart resolve arbitrarily.
"""

import json
import os
import re

DATA = os.path.join('src', 'main', 'resources', 'data', 'electricity', 'recipes')
ITEM_MODELS = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'models', 'item')
BLOCKSTATES = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'blockstates')
LOOT = os.path.join('src', 'main', 'resources', 'data', 'electricity', 'loot_tables', 'blocks')
LANG = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'lang', 'en_us.json')
CATALOGUE = os.path.join('src', 'main', 'java', 'com', 'dooji', 'electricity', 'main', 'registry',
                         'PartCatalog.java')

SHAPED = 'minecraft:crafting_shaped'

# Every ingredient letter used below, resolved once.
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
    # ------------------------------------------------------------------------- raw stock
    ('steel_plate', 3, SHAPED, ['iii', 'ooo']),
    ('steel_section', 2, SHAPED, ['P', 'P']),
    ('busbar', 2, SHAPED, ['ccc']),
    ('copper_coil', 1, SHAPED, ['BBB', ' n ']),
    # a film of polymer and a wax: what a laminate is glued together with and what a blade's fibre is
    # bound in
    ('resin', 2, SHAPED, ['mh']),
    # sawn from the ingot, which is where every cell and every switching die starts
    ('silicon_wafer', 4, SHAPED, ['N']),

    # ------------------------------------------------------------------------ components
    ('solar_cell', 3, SHAPED, ['ZZZ', ' r ', 'BBB']),
    ('bypass_diode', 2, SHAPED, ['q', 'r', 'g']),
    ('mc4_connector', 4, SHAPED, ['II', 'BB']),
    # three diodes, because a sixty-cell module has three substrings and each one gets its own
    ('junction_box', 1, SHAPED, [' d ', 'dMd', ' j ']),
    # switching dies on a substrate over a baseplate
    ('power_module', 1, SHAPED, [' Z ', 'BCB', ' P ']),
    # a film capacitor bank in a steel can: polymer film, charge
    ('capacitor_bank', 1, SHAPED, ['FrF', 'FrF', 'PPP']),
    # laminated iron with a winding through it
    ('magnetic_core', 1, SHAPED, ['PPP', 'OOO', 'PPP']),
    ('control_board', 1, SHAPED, [' U ', 'CBC']),
    ('bearing', 2, SHAPED, [' P ', 'nPn', ' P ']),
    ('gear_set', 2, SHAPED, [' S ', 'SPS', ' S ']),
    # a glass barrel, silver element and sand filler
    ('gpv_fuse', 4, SHAPED, ['yay', ' B ']),
    # contacts, an arc chamber and the snap mechanism that makes it break rather than draw
    ('load_break_switch', 1, SHAPED, [' I ', 'BSB', ' H ']),
    ('sensor_head', 2, SHAPED, [' A ', 'CBC']),

    # ------------------------------------------------------------------------ assemblies
    ('pv_laminate', 1, SHAPED, ['AFA', 'VVV', 'SJS']),
    # eight plates and a gasket: an enclosure is mostly steel, which is why a cabinet costs what it does
    ('enclosure', 1, SHAPED, ['PPP', 'PIP', 'PPP']),
    ('dc_section', 1, SHAPED, ['fff', 'BQB', 'PPP']),
    ('inverter_bridge', 1, SHAPED, ['www', 'kbk', 'BeB']),
    ('mounting_rack', 1, SHAPED, ['S S', 'SSS', 'P P']),
    ('torque_tube', 1, SHAPED, ['SSS', 'YPY']),
    ('slew_drive', 1, SHAPED, [' H ', 'YKY', ' b ']),
    # stator windings round a rotor, in a frame
    ('generator_set', 1, SHAPED, ['OOO', 'ele', 'SSS']),
    ('gearbox', 1, SHAPED, ['HHH', 'YPY', 'PPP']),
    # glass fibre in resin over a steel spar, which is how a blade is actually built
    ('turbine_blade', 1, SHAPED, ['AAA', 'FSF', 'AAA']),
    # the same blade with more fibre on a stronger spar: a longer rotor for a quieter site
    ('long_blade', 1, SHAPED, ['AAA', 'FsF', 'AAA']),

    # ------------------------------------------------------------------- the line conductors
    ('abc_conductor', 8, SHAPED, ['BBB', 'FFF', 'SSS']),
    ('mv_conductor', 6, SHAPED, ['BBB', 'PPP']),
    ('hv_conductor', 4, SHAPED, ['BBB', 'SSS', 'BBB']),

    # --------------------------------------------------------------------------- the cables
    ('dc_string_cable', 6, SHAPED, ['BBB', 'FFF']),
    ('dc_trunk_cable', 4, SHAPED, ['BBB', 'FFF', 'PPP']),

    # --------------------------------------------------------------------------- the arrays
    ('solar_panel', 1, SHAPED, ['LLL', ' T ', 'jSj']),
    ('pv_flat_430', 1, SHAPED, ['LLL', 'PTP', 'jSj']),
    ('pv_tilt_530', 1, SHAPED, ['LLL', 'STS', 'jSj']),
    # the 580 W module is the large-format one, so it is the same rack with a fourth laminate of silicon
    ('pv_tilt_580', 1, SHAPED, ['LLL', 'STS', 'jLj']),
    ('pv_track_700', 1, SHAPED, ['LLL', 'tut', 'jbj']),
    # two slew drives, because a dual-axis frame has two axes to turn
    ('pv_dual_440', 1, SHAPED, ['LLL', 'uSu', 'jbj']),

    # ------------------------------------------------------------------------- the inverters
    # Bridges over an enclosure with a control board under it.
    ('inverter_10', 1, SHAPED, [' G ', ' E ', ' b ']),
    ('inverter_110', 1, SHAPED, ['GGG', ' E ', 'KbK']),
    ('inverter_350', 1, SHAPED, ['GGG', 'DED', 'KbK']),
    ('inverter_2500', 1, SHAPED, ['GGG', 'BEB', 'KbK']),

    # ------------------------------------------------------------------------- the combiners
    # the output, and the thirty-two way box is the one built to IP66.
    ('pv_combiner_6', 1, SHAPED, ['fff', ' E ', ' Q ']),
    ('pv_combiner_16', 1, SHAPED, ['fff', 'BEB', ' Q ']),
    ('pv_combiner_32', 1, SHAPED, ['fff', 'BEB', 'PQP']),

    # ------------------------------------------------------------------- the met station
    # A logger with a display on it, which is what the model draws and what put the screen back in the tree:
    # nothing else consumed one, so it was an item a player could craft and never use.
    ('met_station', 1, SHAPED, [' p ', 'pbp', 'SRS']),

    # ------------------------------------------------------------------- the transformers
    # A tank, a core with windings on it, and the bushings the windings leave through.  The substation
    # unit is the same machine with radiators, a conservator and a tap changer on it.
    ('tx_machine', 1, SHAPED, ['III', 'OeO', 'PPP']),
    ('tx_substation', 1, SHAPED, ['III', 'ete', 'PQP']),

    # ------------------------------------------------------------------------ the switches
    # A disconnector is three post insulators a side, a copper blade and a frame: no arc-breaking parts at
    # all, which is exactly why it cannot be opened under load.  A breaker is the same posts over three
    # load-break interrupters and the mechanism that throws them.
    ('mv_disconnector', 1, SHAPED, ['III', 'BSB', 'SSS']),
    ('mv_breaker', 1, SHAPED, ['III', 'QQQ', 'PbP']),

    # ---------------------------------------------------------------- the transmission towers
    # Steel sections, plate gussets and the insulator strings the phases hang from.  The duty is what
    # costs: a suspension tower is only holding weight, a tension tower is braced for the difference
    # between two pulls, and a terminal tower takes all of it and is stayed back.
    ('lattice_suspension', 1, SHAPED, ['SIS', 'SPS', 'S S']),
    ('lattice_tension', 1, SHAPED, ['SIS', 'SPS', 'SPS']),
    ('lattice_terminal', 1, SHAPED, ['SIS', 'PPP', 'SPS']),

    # ------------------------------------------------------------------- towers and turbines
    ('turbine_tower', 2, SHAPED, ['PPP', 'n n', 'PPP']),
    # Three blades over a drivetrain, and the drivetrain is where the machines differ.
    ('sw_10', 1, SHAPED, ['sss', ' x ', 'SbS']),
    ('c52_085', 1, SHAPED, ['sss', 'XxY', 'ubE']),
    # long blades: an eighty metre rotor on the same drivetrain
    ('c80_20', 1, SHAPED, ['zzz', 'XxY', 'ubE']),
    # three megawatts: the generator doubles
    ('c90_30', 1, SHAPED, ['zzz', 'Xxx', 'ubE']),
    # the same generator pair as the C90 and no gearbox - a second main bearing instead
    # direct drive is, and the reason these two share a nameplate on different rotors
    ('c112_30', 1, SHAPED, ['zzz', 'Yxx', 'ubE']),
    # four megawatts, direct drive, and all generator
    ('wind_turbine', 1, SHAPED, ['zzz', 'xxx', 'ubE']),
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


def grid_of(pattern, key):
    """A recipe's grid as the game matches it: the items, laid out, with the letters resolved away."""
    rows = tuple(tuple('.' if c == ' ' else key[c].get('item', str(key[c])) for c in row)
                 for row in pattern)
    return min(rows, tuple(row[::-1] for row in rows))


def written_recipes():
    """Every recipe file, generated or hand-written, as (name, type, grid)."""
    found = []
    for name in sorted(os.listdir(DATA)):
        if not name.endswith('.json'):
            continue

        recipe = json.load(open(os.path.join(DATA, name)))
        if 'pattern' not in recipe:
            continue

        found.append((name[:-5], recipe['type'], grid_of(recipe['pattern'], recipe['key'])))

    return found


def check(catalogue):
    """Three things that have to hold"""
    problems = []

    grids = {}
    for result, kind, grid in written_recipes():
        if (kind, grid) in grids:
            problems.append('%s and %s have the same grid, so one of them is uncraftable'
                            % (grids[(kind, grid)], result))
        grids[(kind, grid)] = result

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


# A machine drawn by the mod's own OBJ renderer has an INVISIBLE render shape, so its blockstate only
# needs to name a particle texture - one variant a facing, all four the same.  Its item is a flat sprite.
# Every machine the mod draws itself, with the particle it breaks into and the sprite its item shows.
#
# RenderShape.INVISIBLE means the blockstate's model is *only* the break particle, so every one of these is
# one unconditional multipart.  Not a variant per facing: a variants block has to name every state the block
# has, and the kiosk has a `mounted` and a switch has an `open` that four `facing=` keys never named - so
# half of each one's states resolved to no model at all.
#
# The sprite is not always the block's own name, because a family shares one drawing: four inverters are one
# cabinet and six arrays are four mountings.  None means the block has no item - the shell cell is placed by
# its machine and never held.
MACHINES = {
    'utility_pole': ('stone_particle', 'utility_pole'),
    'electric_cabin': ('stone_particle', 'cab'),
    'power_box': ('stone_particle', 'power_box'),
    'turbine_tower': ('stone_particle', 'turbine_tower'),
    'met_station': ('metal_particle', 'met_station'),
    'machine_shell': ('metal_particle', None),
    'sw_10': ('stone_particle', 'sw_10'),
    'c52_085': ('stone_particle', 'c52_085'),
    'c80_20': ('stone_particle', 'c80_20'),
    'c90_30': ('stone_particle', 'c90_30'),
    'c112_30': ('stone_particle', 'c112_30'),
    'wind_turbine': ('stone_particle', 'wind_turbine'),
    'solar_panel': ('glass_particle', 'pv_flat'),
    'pv_flat_430': ('glass_particle', 'pv_flat'),
    'pv_tilt_530': ('glass_particle', 'pv_tilt'),
    'pv_tilt_580': ('glass_particle', 'pv_tilt'),
    'pv_track_700': ('glass_particle', 'pv_track'),
    'pv_dual_440': ('glass_particle', 'pv_dual'),
    'inverter_10': ('metal_particle', 'pv_inverter'),
    'inverter_110': ('metal_particle', 'pv_inverter'),
    'inverter_350': ('metal_particle', 'pv_inverter'),
    'inverter_2500': ('metal_particle', 'pv_inverter'),
    'pv_combiner_6': ('metal_particle', 'pv_combiner'),
    'pv_combiner_16': ('metal_particle', 'pv_combiner'),
    'pv_combiner_32': ('metal_particle', 'pv_combiner'),
    'tx_machine': ('metal_particle', 'tx_machine'),
    'tx_substation': ('metal_particle', 'tx_substation'),
    'lattice_suspension': ('stone_particle', 'lattice_suspension'),
    'lattice_tension': ('stone_particle', 'lattice_tension'),
    'lattice_terminal': ('stone_particle', 'lattice_terminal'),
    'mv_disconnector': ('metal_particle', 'mv_disconnector'),
    'mv_breaker': ('metal_particle', 'mv_breaker'),
}

# The names and tooltips for what is not a part: a block is named here rather than by hand.
BLOCK_NAMES = {
    # A run of conductor has a name even though nothing ever shows it: a block with no name key reads as
    # its own registry id in an advancement, in a death message, and in the F3 screen.
    'abc_conductor_run': ('Bundle Run', 'Aerial bundled cable, laid along the ground.'),
    'mv_conductor_run': ('Medium-Voltage Run', 'Bare all-aluminium-alloy conductor, laid along the ground.'),
    'hv_conductor_run': ('Transmission Run', 'A quad ACSR bundle on spacers, laid along the ground.'),
    'tx_machine': ('Machine Transformer',
                   'Steps a machine\u2019s 800 V up to 33 kV. Every generator on a plant needs one.'),
    'tx_substation': ('Substation Transformer',
                      'Steps 33 kV up to 400 kV. The transmission line starts here.'),
    'lattice_suspension': ('Suspension Tower', 'Holds a 400 kV line up. Nine towers in ten are one.'),
    'lattice_tension': ('Tension Tower', 'Takes the difference between the pulls either side of it.'),
    'lattice_terminal': ('Terminal Tower', 'Takes the whole pull of a line, and is stayed back for it.'),
    'mv_disconnector': ('Disconnector',
                        'Makes a gap in a 24 kV line that you can see. Will not open under load: open the '
                        'breaker first.'),
    'mv_breaker': ('Circuit Breaker',
                   'Breaks a 24 kV line under load, and trips itself above 26 MW. Its contacts are in a '
                   'vacuum, so all it shows is a flag.'),
}


# The lines a machine says to a player.  Here rather than by hand in the language file for the reason the
# block names are: a key with no entry reads as its own id in the corner of the screen.
MESSAGES = {
    'message.electricity.switch.under_load':
        'There is load on it. A disconnector cannot break current \u2014 open the breaker first.',
    'message.electricity.switch.opened': 'Open. The gap is visible from the outside.',
    'message.electricity.switch.closed': 'Closed.',
    'message.electricity.wire.wrong_class':
        'Neither of those fittings is built for %s. Match the conductor to the voltage.',
}


# What a broken block gives back: itself, unless it is a run of conductor, which gives back the reel.
DROPS = {
    'tx_machine': 'tx_machine',
    'tx_substation': 'tx_substation',
    'lattice_suspension': 'lattice_suspension',
    'lattice_tension': 'lattice_tension',
    'lattice_terminal': 'lattice_terminal',
    'mv_disconnector': 'mv_disconnector',
    'mv_breaker': 'mv_breaker',
    'abc_conductor_run': 'abc_conductor',
    'mv_conductor_run': 'mv_conductor',
    'hv_conductor_run': 'hv_conductor',
}


def loot_tables():
    """One drop per block this file owns.  Without one a block breaks into nothing, silently."""
    for block_id, item_id in DROPS.items():
        write(os.path.join(LOOT, block_id + '.json'), {
            'type': 'minecraft:block',
            'pools': [{
                'rolls': 1,
                'entries': [{'type': 'minecraft:item', 'name': 'electricity:' + item_id}],
                'conditions': [{'condition': 'minecraft:survives_explosion'}],
            }],
        })

    return len(DROPS)


def machine_assets():
    """The blockstate and item model for every machine the mod draws itself - see MACHINES."""
    for name, (particle, sprite) in MACHINES.items():
        write(os.path.join(BLOCKSTATES, name + '.json'),
              {'multipart': [{'apply': {'model': 'electricity:block/' + particle}}]})
        if sprite is not None:
            write(os.path.join(ITEM_MODELS, name + '.json'),
                  {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'electricity:item/' + sprite}})

    return len(MACHINES)


def main():
    catalogue = parts()
    machines = machine_assets()
    drops = loot_tables()

    # one item model per part: a flat sprite, like every other ingredient in the game
    for part_id, _, _, _ in catalogue:
        write(os.path.join(ITEM_MODELS, part_id + '.json'),
              {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'electricity:item/' + part_id}})

    # the names and the tooltips, merged rather than rewritten: the file has two hundred keys already
    lang = json.load(open(LANG))
    for part_id, _, name, tooltip in catalogue:
        lang['item.electricity.' + part_id] = name
        lang['tooltip.electricity.' + part_id] = tooltip
    for block_id, (name, tooltip) in BLOCK_NAMES.items():
        lang['block.electricity.' + block_id] = name
        lang['tooltip.electricity.' + block_id] = tooltip
    lang.update(MESSAGES)
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

    print('%d parts, %d bench recipes, 2 furnace recipes, %d machine blockstates, %d loot tables'
          % (len(catalogue), ingots, machines, drops))
    print('%d recipe files on disk, hand-written ones included' % len(written_recipes()))
    print('lang now has %d keys' % len(lang))
    problems = check(catalogue)
    print('the tree is consistent' if problems == 0 else '%d problem(s) in the tree' % problems)


if __name__ == '__main__':
    main()
