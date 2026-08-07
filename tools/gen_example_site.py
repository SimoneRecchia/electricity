#!/usr/bin/env python3
"""A whole site: two solar parks, a wind farm, and the substation that takes their power away.

    python3 tools/gen_example_site.py                # checks the layout and writes both outputs
    python3 tools/rcon.py -f build/site/site.txt     # a dev server, absolute coordinates
    /function electricity:sito                       # any world, from the datapack, where you stand

Built the way a real one is, not as a diagram of one: gravel yards under the machines, a road round the
fields, a fenced compound with a gate, a control building, and a sign at every bay saying what it is and at
what voltage - which is the first thing you see walking into a substation and the last thing a mod usually
has.

What decides the shape of it, all of it the mod's own and none of it guessable - see gen_example_plant.py
for the long version:

  * a fixed row faces east or west and takes its cable on that axis, so a block of them is a north-south
    spine with rows down either side; a tracked row takes it north or south, so a block of them is an
    east-west spine with a row each side
  * an inverter has a string count and a direct-current ceiling as well as a rating, and past either it
    takes the nearest rows and drops the rest without saying so
  * a string box needs an inverter with trunk terminals, which a string inverter has not
  * a 400 kV tower joins only to another tower or to a substation transformer's upper bushings: coming off
    a line means a second transformer, and its medium-voltage side reaches a pole only through a laid run
  * an auxiliary supply is that same chain in miniature: the yard's own transformer, a laid run, a pole,
    and low-voltage bundle into the board - which is how a real substation feeds its own fans and lights
"""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_example_plant as plant                                                # noqa: E402
from gen_example_plant import ARRAYS, COMBINERS, CONDUCTORS, INVERTERS, Plant     # noqa: E402

OUT = os.path.join('build', 'site', 'site.txt')
DATAPACK = os.path.join('build', 'site', 'datapack')
FUNCTION = 'sito'
# What a control cabinet's radio reaches, from PlantControllerBlockEntity.RANGE.
RANGE = 64

STRING = plant.STRING
TRUNK = plant.TRUNK

# A turbine and a tower segment are one cell each: the tower is a stack of blocks with their own collision,
# and the machine on top carries the rotor, which nothing stands in.
plant.FOOTPRINTS['turbine'] = [(0, 0, 0)]
plant.FOOTPRINTS['tower_segment'] = [(0, 0, 0)]

# What the ground is made of.  A substation yard is crushed stone over a membrane, a park's roads are
# graded gravel, and a machine stands on a poured plinth - so that is what they are here.
YARD = 'minecraft:gravel'
ROAD = 'minecraft:dirt_path'
PLINTH = 'minecraft:smooth_stone'
FENCE = 'minecraft:iron_bars'
POST = 'minecraft:stone_bricks'
WALL = 'minecraft:light_gray_concrete'
ROOF = 'minecraft:deepslate_tiles'
WINDOW = 'minecraft:glass_pane'


class Site(Plant):
    """A plant with the ground it stands on, the fence round it and the signs on it."""

    def __init__(self):
        super().__init__()
        self.blocks = {}     # (x, y, z) -> block, for the scenery
        self.signs = []      # (pos, rotation, lines)

    def slab(self, block, corner_a, corner_b):
        """A rectangular slab of one block, inclusive of both corners."""
        (x0, y0, z0), (x1, y1, z1) = corner_a, corner_b
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.blocks[(x, y, z)] = block

    def outline(self, block, corner_a, corner_b):
        """The four sides of a rectangle and nothing inside it: a fence line."""
        (x0, y, z0), (x1, _, z1) = corner_a, corner_b
        for x in range(min(x0, x1), max(x0, x1) + 1):
            self.blocks[(x, y, z0)] = block
            self.blocks[(x, y, z1)] = block
        for z in range(min(z0, z1), max(z0, z1) + 1):
            self.blocks[(x0, y, z)] = block
            self.blocks[(x1, y, z)] = block

    def sign(self, pos, rotation, lines):
        """A site sign on its own little plinth: what this bay is, and at what voltage."""
        self.blocks[pos] = POST
        self.signs.append(((pos[0], pos[1] + 1, pos[2]), rotation, lines))

    # ---- what a server is handed ----

    def commands(self):
        out = []
        # the ground first, then the machines, then the cables, then the signs: a cable wants the machine
        # beside it to exist already only for the model, and a sign wants its post under it
        for pos, block in sorted(self.blocks.items()):
            out.append('setblock %d %d %d %s' % (pos[0], pos[1], pos[2], block))

        out += super().commands()

        for pos, rotation, lines in self.signs:
            # two quotings, one inside the other: the line is JSON, and the JSON sits in a single-quoted
            # NBT string - so an apostrophe in the Italian ends the NBT string unless it is escaped too
            messages = ','.join("'" + json.dumps({'text': text}).replace("'", "\\'") + "'"
                                for text in (list(lines) + ['', '', '', ''])[:4])
            out.append('setblock %d %d %d minecraft:oak_sign[rotation=%d]'
                       '{is_waxed:1b,front_text:{messages:[%s]}}'
                       % (pos[0], pos[1], pos[2], rotation, messages))
        return out

    def problems(self):
        out = super().problems()
        cabinets = [pos for kind, _, pos, _ in self.machines if kind == 'controller']
        for kind, block, pos, _ in self.machines:
            if kind not in ('inverter', 'turbine'):
                continue

            reaching = [c for c in cabinets
                        if sum((c[i] - pos[i]) ** 2 for i in range(3)) <= RANGE * RANGE]
            if len(reaching) > 1:
                out.append('%s at %s is inside the reach of %d control cabinets, and they will fight over it'
                           % (block, pos, len(reaching)))
            if not reaching:
                out.append('%s at %s is out of reach of every control cabinet' % (block, pos))

        taken, _ = self.claimed()
        for pos in self.blocks:
            if pos in taken and self.blocks[pos] != PLINTH:
                out.append('scenery at %s is inside %s' % (pos, taken[pos][0]))
            if pos in self.runs:
                out.append('scenery at %s is under a cable' % (pos,))
        for pos, _, _ in self.signs:
            if pos in taken:
                out.append('a sign at %s is inside %s' % (pos, taken[pos][0]))
        return out


# ------------------------------------------------------------------ the site

def build(origin):
    """Two solar parks, a wind farm and a 400/33 kV substation, on one site."""
    ox, oy, oz = origin
    s = Site()

    def at(x, z, y=0):
        return (ox + x, oy + y, oz + z)

    def ground(x0, z0, x1, z1, block=YARD):
        s.slab(block, at(x0, z0, -1), at(x1, z1, -1))

    # ================================================== the road that ties the site together
    ground(-6, -1, 200, 1, ROAD)
    ground(2, 2, 4, 44, ROAD)

    # ================================================== PARCO SOLARE A - inseguitori biassiali
    # A tracked row takes its cable north or south, so each block is an east-west spine with a row above
    # and a row below.  Eighteen rows to a string inverter: 18 strings against 18 inputs, 103 kW on 110.
    a_inverters = []
    for block, spine_z in enumerate((8, 12), start=1):
        for i in range(9):
            for dz in (-1, 1):
                s.machine('array', 'electricity:' + ARRAYS['AE_440']['block'], at(6 + i, spine_z + dz),
                          facing='north', harnessed='true')
        s.run(STRING, (at(6, spine_z), at(15, spine_z)), 'stringhe biassiali %d' % block)
        a_inverters.append(s.machine('inverter', 'electricity:' + INVERTERS['VX_110']['block'],
                                     at(16, spine_z), facing='west'))
        ground(15, spine_z - 1, 17, spine_z + 1, PLINTH)
        s.sign(at(16, spine_z - 3), 8, ['CAMPO A%d' % block, '18 x AE-440', 'biassiale', 'VX-110K 110 kW'])

    s.sign(at(5, 6), 8, ['PARCO SOLARE A', 'INSEGUITORI', 'BIASSIALI', '36 x AE-440'])

    # ================================================== PARCO SOLARE B - fissi obliqui
    # A fixed row faces east or west - the only two places the sun ever is - and takes its cable on that
    # axis, so each block is a north-south spine with rows down either side, half facing each way.
    for block, spine_x in enumerate((8, 13), start=1):
        for i in range(8):
            s.machine('array', 'electricity:' + ARRAYS['TR_580']['block'], at(spine_x - 1, 20 + i),
                      facing='east', harnessed='true')
            s.machine('array', 'electricity:' + ARRAYS['TR_580']['block'], at(spine_x + 1, 20 + i),
                      facing='west', harnessed='true')
        s.run(STRING, (at(spine_x, 20), at(spine_x, 27)), 'stringhe fisse %d' % block)
        box = 'CB_16' if block == 1 else 'CB_32'
        s.machine('combiner', 'electricity:' + COMBINERS[box]['block'], at(spine_x, 19), facing='north',
                  isolated='false')
        ground(spine_x - 1, 19, spine_x + 1, 19, PLINTH)
        s.sign(at(spine_x + 2, 16), 8, ['QUADRO STRINGA', COMBINERS[box]['block'].upper().replace('PV_COMBINER_', 'CB-'),
                                        '16 stringhe', 'sezione DC'])

    s.run(TRUNK, (at(8, 18), at(20, 18)), 'dorsale solare B')
    s.run(TRUNK, (at(13, 18), at(13, 18)), 'dorsale solare B')
    b_inverter = s.machine('inverter', 'electricity:' + INVERTERS['VX_350']['block'], at(21, 18), facing='west')
    ground(20, 17, 22, 19, PLINTH)
    s.sign(at(10, 30), 8, ['PARCO SOLARE B', 'FISSI OBLIQUI', '32 x TR-580', '334 kW'])
    s.sign(at(21, 21), 8, ['INVERTER', 'VX-350K', '352 kW', '32 stringhe'])

    # the machine transformers that lift the parks to the collector voltage
    tx_solar_a = s.machine('tx_machine', 'electricity:tx_machine', at(20, 10), facing='west')
    tx_solar_b = s.machine('tx_machine', 'electricity:tx_machine', at(24, 18), facing='west')
    for pos in (tx_solar_a, tx_solar_b):
        ground(pos[0] - ox - 1, pos[2] - oz - 1, pos[0] - ox + 1, pos[2] - oz + 1, PLINTH)
    s.sign(at(20, 13), 8, ['TRASF. MACCHINA', 'BT/MT', '0,8 / 33 kV', 'parco solare A'])
    s.sign(at(24, 21), 8, ['TRASF. MACCHINA', 'BT/MT', '0,8 / 33 kV', 'parco solare B'])

    # the met mast, inside its twelve blocks of a row and clear of any row's east-west line
    s.machine('mast', 'electricity:met_station', at(3, 10), facing='north')
    s.sign(at(3, 12), 8, ['PALO METEO', 'irraggiamento', 'temperatura', 'vento'])

    # ================================================== PARCO EOLICO
    # Four machines at fifty blocks, which is five rotor diameters: any closer and the one downwind stands
    # in the wake of the one upwind.  Each on its own transformer, chained into one collector feeder.
    wind = []
    for name, (wx, wz) in (('WTG-01', (54, -60)), ('WTG-02', (54, -110)),
                           ('WTG-03', (104, -60)), ('WTG-04', (104, -110))):
        ground(wx - 3, wz - 3, wx + 3, wz + 3, YARD)
        ground(wx - 1, wz - 1, wx + 1, wz + 1, PLINTH)
        for segment in range(9):
            s.machine('tower_segment', 'electricity:turbine_tower', at(wx, wz, segment))
        nacelle = s.machine('turbine', 'electricity:c90_30', at(wx, wz, 9), facing='north')
        transformer = s.machine('tx_machine', 'electricity:tx_machine', at(wx + 3, wz + 3), facing='west')
        ground(wx + 2, wz + 2, wx + 4, wz + 4, PLINTH)
        s.sign(at(wx - 3, wz + 4), 8, [name, 'Cube C90-3.0', '3,0 MW', 'mozzo 9 blocchi'])
        s.sign(at(wx + 3, wz + 5), 8, ['TRASF. MACCHINA', 'BT/MT', '0,8 / 33 kV', name])
        wind.append((name, nacelle, transformer))

    s.sign(at(50, -56), 8, ['PARCO EOLICO', '4 x C90-3.0', '12,0 MW', 'sezione MT 33 kV'])

    # The wind farm gets a control cabinet of its own, because one does not reach both: the radio is 64
    # blocks and the four machines are 50 apart.  A real farm has its own SCADA for the same reason.
    ground(78, -86, 80, -84, PLINTH)
    s.machine('controller', 'electricity:plant_controller', at(79, -85), facing='south')
    s.sign(at(79, -82), 8, ['CONTROLLO', 'PARCO EOLICO', '4 unita', '12,0 MW'])

    # ================================================== SOTTOSTAZIONE 400/33 kV
    # The compound: crushed stone inside a fence, a gate on the road, and the bays laid out the way a real
    # yard is - medium voltage at the near end, the transformer in the middle, the line bay at the far end.
    ground(40, -12, 78, 12, YARD)
    s.outline(FENCE, at(40, -12), at(78, 12))
    for z in (-1, 0, 1):
        s.blocks.pop(at(40, z), None)                       # the gate, on the road
    s.sign(at(39, 3), 12, ['SOTTOSTAZIONE', 'ELETTRICA', '400 / 33 kV', 'VIETATO ACCESSO'])
    s.sign(at(39, -3), 12, ['PERICOLO', 'ALTA TENSIONE', 'IMPIANTO', 'IN SERVIZIO'])

    # ---- the control building, first thing inside the gate
    s.slab(WALL, at(43, 5, 0), at(48, 8, 0))
    s.slab(WALL, at(43, 5, 3), at(48, 8, 3))
    s.slab(WALL, at(43, 5, 1), at(43, 8, 2))
    s.slab(WALL, at(48, 5, 1), at(48, 8, 2))
    s.slab('minecraft:air', at(44, 6, 1), at(47, 7, 2))
    s.slab(WINDOW, at(44, 6, 1), at(45, 6, 1))
    s.slab(WINDOW, at(47, 6, 1), at(47, 6, 1))
    s.slab('minecraft:oak_door[facing=south,half=lower]', at(46, 6, 0), at(46, 6, 0))
    s.slab('minecraft:oak_door[facing=south,half=upper]', at(46, 7, 0), at(46, 7, 0))
    s.slab(ROOF, at(42, 5, 4), at(49, 8, 4))
    controller = s.machine('controller', 'electricity:plant_controller', at(50, 6), facing='west')
    ground(49, 5, 51, 7, PLINTH)
    s.sign(at(50, 9), 8, ['EDIFICIO', 'CONTROLLO', 'e regolatore', "d'impianto"])

    # ---- the medium-voltage bay: the collector busbar, and the two switches that section it
    cabin = s.machine('cabin', 'electricity:electric_cabin', at(46, -4), facing='west')
    ground(45, -6, 47, -2, PLINTH)
    breaker = s.machine('breaker', 'electricity:mv_breaker', at(52, -4), facing='west', open='false')
    disconnector = s.machine('disconnector', 'electricity:mv_disconnector', at(56, -4), facing='west', open='false')
    ground(51, -5, 57, -3, PLINTH)
    s.sign(at(46, -8), 8, ['SBARRA MT', '33 kV', 'quadro di', 'raccolta'])
    s.sign(at(52, -7), 8, ['INTERRUTTORE', 'DI SBARRA', '33 kV', 'apre in carico'])
    s.sign(at(56, -7), 8, ['SEZIONATORE', '33 kV', 'non apre', 'in carico'])

    # ---- the transformer bay
    substation = s.machine('tx_substation', 'electricity:tx_substation', at(62, 0), facing='west')
    ground(60, -2, 64, 2, PLINTH)
    s.sign(at(62, 4), 8, ['TRASFORMATORE', 'AT / MT', '400 / 33 kV', 'stallo TR1'])

    # ---- the line bay: the terminal tower is the gantry a 400 kV line is dead-ended on
    gantry = s.machine('tower', 'electricity:lattice_terminal', at(72, 0), facing='west')
    ground(69, -3, 75, 3, YARD)
    s.sign(at(72, 5), 8, ['STALLO LINEA', '400 kV', 'LINEA 1', 'partenza'])

    # ---- the auxiliary supply.  A substation feeds its own fans, lights and controls, and it does it from
    # its own little transformer off the collector busbar: 33 kV in, 400 V out, and then a board.
    aux = s.machine('tx_machine', 'electricity:tx_machine', at(50, -9), facing='west')
    ground(49, -10, 51, -8, PLINTH)
    s.laid('electricity:mv_conductor_run', (at(53, -9), at(56, -9)), 'servizi ausiliari MT')
    aux_pole = s.machine('pole', 'electricity:utility_pole', at(60, -9), facing='west')
    aux_board = s.machine('kiosk', 'electricity:power_box', at(66, -9), facing='west', mounted='false')
    ground(65, -10, 67, -8, PLINTH)
    s.sign(at(50, -11), 8, ['SERVIZI', 'AUSILIARI', 'MT / BT', '33 / 0,4 kV'])
    s.sign(at(66, -11), 8, ['QUADRO BT', 'AUSILIARI', '400 V', 'ventilazione e luci'])

    # ================================================== LA LINEA 400 kV
    towers = [('sostegno 1', gantry)]
    for number, x in enumerate((92, 112, 132), start=2):
        kind = 'lattice_suspension' if number < 4 else 'lattice_tension'
        towers.append(('sostegno %d' % number, s.machine('tower', 'electricity:' + kind, at(x, 0), facing='west')))
        s.sign(at(x, 4), 8, ['LINEA 400 kV', 'LINEA 1', 'SOSTEGNO N.%d' % number,
                             'sospensione' if number < 4 else 'amarro'])
    towers.append(('sostegno 5', s.machine('tower', 'electricity:lattice_terminal', at(152, 0), facing='west')))
    s.sign(at(152, 4), 8, ['LINEA 400 kV', 'LINEA 1', 'SOSTEGNO N.5', 'arrivo'])

    # ---- the receiving end, which is a second substation in miniature: a tower joins nothing but another
    # tower and a transformer's upper bushings, so this is the only way off a line.
    arrival = s.machine('tx_substation', 'electricity:tx_substation', at(160, 0), facing='west')
    ground(156, -4, 176, 4, YARD)
    ground(158, -2, 162, 2, PLINTH)
    s.laid('electricity:mv_conductor_run', (at(164, 0), at(167, 0)), 'uscita MT arrivo')
    pole = s.machine('pole', 'electricity:utility_pole', at(171, 0), facing='west')
    kiosk = s.machine('kiosk', 'electricity:power_box', at(178, 0), facing='west', mounted='false')
    ground(177, -1, 179, 1, PLINTH)
    s.sign(at(160, 4), 8, ['SOTTOSTAZIONE', 'DI ARRIVO', 'AT / MT', '400 / 33 kV'])
    s.sign(at(178, 3), 8, ['CABINA', 'DI CONSEGNA', 'BT 400 V', 'utenza'])

    # ================================================== the spans
    mv, hv, lv = 'AAAC_228', 'ACSR_592_QUAD', 'ABC_70'
    for i, inverter in enumerate(a_inverters, start=1):
        s.span(mv, 'inverter campo A%d' % i, inverter, 'TX macchina solare A', tx_solar_a)
    s.span(mv, 'inverter VX-350K', b_inverter, 'TX macchina solare B', tx_solar_b)
    s.span(mv, 'TX macchina solare A', tx_solar_a, 'sbarra MT (ingresso)', cabin)
    s.span(mv, 'TX macchina solare B', tx_solar_b, 'sbarra MT (ingresso)', cabin)

    # the wind collector, daisy-chained the way a real feeder is
    for name, nacelle, transformer in wind:
        s.span(mv, '%s (navicella)' % name, nacelle, '%s TX macchina' % name, transformer)
    chain = {name: transformer for name, _, transformer in wind}
    s.span(mv, 'WTG-02 TX', chain['WTG-02'], 'WTG-01 TX', chain['WTG-01'])
    s.span(mv, 'WTG-04 TX', chain['WTG-04'], 'WTG-03 TX', chain['WTG-03'])
    s.span(mv, 'WTG-03 TX', chain['WTG-03'], 'WTG-01 TX', chain['WTG-01'])
    s.span(mv, 'WTG-01 TX', chain['WTG-01'], 'sbarra MT (ingresso)', cabin)

    # the yard, in the order the current goes through it
    s.span(mv, 'sbarra MT (uscita)', cabin, 'interruttore di sbarra (linea)', breaker)
    s.span(mv, 'interruttore di sbarra (carico)', breaker, 'sezionatore (linea)', disconnector)
    s.span(mv, 'sezionatore (carico)', disconnector, 'TR1 (isolatore basso)', substation)
    s.span(hv, 'TR1 (isolatore alto)', substation, 'stallo linea 400 kV', gantry)

    # the auxiliaries, off the busbar
    s.span(mv, 'sbarra MT (uscita)', cabin, 'TX servizi ausiliari', aux)
    s.span(mv, 'TX servizi ausiliari', aux, 'tratto MT ausiliari', (aux[0] + 3, aux[1], aux[2]))
    s.span(mv, 'tratto MT ausiliari', (aux[0] + 6, aux[1], aux[2]), 'palo BT ausiliari', aux_pole)
    s.span(lv, 'palo BT ausiliari', aux_pole, 'quadro BT ausiliari', aux_board)

    # the line, and the way down at the far end
    for (name_a, a), (name_b, b) in zip(towers, towers[1:]):
        s.span(hv, 'traliccio %s' % name_a, a, 'traliccio %s' % name_b, b)
    s.span(hv, 'traliccio %s' % towers[-1][0], towers[-1][1], 'TX arrivo (isolatore alto)', arrival)
    s.span(mv, 'TX arrivo (isolatore basso)', arrival, 'tratto MT arrivo', (arrival[0] + 4, arrival[1], arrival[2]))
    s.span(mv, 'tratto MT arrivo', (arrival[0] + 7, arrival[1], arrival[2]), 'palo di consegna', pole)
    s.span(lv, 'palo di consegna', pole, 'cabina di consegna', kiosk)

    s.notes.append(('controller', controller))
    s.notes.append(('inverters', tuple(a_inverters) + (b_inverter,)))
    s.notes.append(('mast', at(3, 10)))
    return s


def main():
    origin = (0, 56, 0)
    if '--at' in sys.argv:
        index = sys.argv.index('--at')
        origin = tuple(int(v) for v in sys.argv[index + 1:index + 4])

    s = build(origin)
    problems = s.problems()
    print('the site, built from %s:' % (origin,))
    counted = {}
    for kind, block, _, _ in s.machines:
        counted[block] = counted.get(block, 0) + 1
    for block, n in sorted(counted.items()):
        print('    %3d x %s' % (n, block.split(':')[1]))
    print('    %d cable blocks, %d spans, %d signs, %d blocks of ground and fence'
          % (len(s.runs), len(s.spans), len(s.signs), len(s.blocks)))

    dc = sum(counted.get('electricity:' + spec['block'], 0) * spec['kw'] for spec in ARRAYS.values())
    ac = sum(counted.get('electricity:' + spec['block'], 0) * spec['kw'] for spec in INVERTERS.values())
    print('%.1f kW of modules, %.1f kW of inverters, and %d turbines of 3.0 MW'
          % (dc, ac, counted.get('electricity:c90_30', 0)))

    for line in problems:
        print('    PROBLEM %s' % line)
    if problems:
        print('%d problem(s) in the layout' % len(problems))
        return 1

    print('nothing overlaps anything, every row takes its cable, every span is inside its reach')
    commands = s.commands()
    plant.write(OUT, '\n'.join(commands) + '\n')
    print('%s: %d commands' % (OUT, len(commands)))

    lines = ['# generated by tools/gen_example_site.py - do not edit by hand']
    for command in commands:
        head, x, y, z, rest = command.split(' ', 4)
        lines.append('%s ~%d ~%d ~%d %s' % (head, int(x) - origin[0], int(y) - origin[1],
                                            int(z) - origin[2], rest))
    plant.write(os.path.join(DATAPACK, 'pack.mcmeta'),
                '{\n  "pack": {\n    "pack_format": %d,\n'
                '    "description": "Electricity - sito completo: /function electricity:%s"\n  }\n}\n'
                % (plant.PACK_FORMAT, FUNCTION))
    plant.write(os.path.join(DATAPACK, 'data', 'electricity', 'functions', FUNCTION + '.mcfunction'),
                '\n'.join(lines) + '\n')
    print('%s: /function electricity:%s' % (DATAPACK, FUNCTION))

    print('\nthe spans, which a player strings by hand with the reel in hand:')
    for conductor, name_a, a, name_b, b in s.spans:
        length = sum((a[i] - b[i]) ** 2 for i in range(3)) ** 0.5
        print('    %-14s %-32s %-18s -> %-32s %-18s %3.0f'
              % (CONDUCTORS[conductor]['item'], name_a, a, name_b, b, length))
    return 0


if __name__ == '__main__':
    sys.exit(main())
