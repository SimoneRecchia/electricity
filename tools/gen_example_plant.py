#!/usr/bin/env python3
"""A whole photovoltaic plant, as commands a dev server can be handed.

    python3 tools/gen_example_plant.py               # checks the layout and writes both outputs

Two ways to build what it writes, because there are two ways to have a world:

    python3 tools/rcon.py -f build/plant/plant.txt   # a dev server (./gradlew runServer), absolute coordinates
    /function electricity:plant                      # any world at all, from the datapack, where you stand

The datapack is written to build/plant/datapack/ with *relative* coordinates, so it builds from the block
the player is standing on.  Copy it into a world's datapacks folder, /reload, and run the function.

Either way it prints the spans a player has to string afterwards, because a span is a player action by
design - it is saved against two fittings a conductor apart, not against a block.

The layout is checked before it is written: no two machines' cells may overlap, every array has to touch
a run of the gauge its inverter looks for, and every span has to be inside its conductor's own limit.

Two figures about the mod's sky decide the whole field, and neither is guessable:

  * the sun is only ever due **east** (all morning) or due **west** (all afternoon), so a fixed row's own
    facing is the whole of its aim.  East or west; a row facing north or south is 90 degrees off the sun all
    day and its tilt earns nothing.  Half the rows face each way, which is what flattens the day's curve.
  * a tracked row ignores its facing altogether and turns to face the sun itself.
  * row-to-row shading is a property of the product's ground cover ratio, not of how far apart the blocks
    are - but *anything standing east or west of a row does shade it*, because that is where the sun is.
    So every cabinet on this plant is north of the field it serves.
"""

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import check_hitboxes                                                            # noqa: E402

OUT = os.path.join('build', 'plant', 'plant.txt')
PACK = os.path.join('build', 'plant', 'datapack')
# The namespace and name the function is called by: /function electricity:plant
FUNCTION = ('electricity', 'plant')
REGISTRY = os.path.join('src', 'main', 'java', 'com', 'dooji', 'electricity', 'main', 'registry')
BLOCKS = os.path.join('src', 'main', 'java', 'com', 'dooji', 'electricity', 'block')

STRING = 'electricity:dc_string_cable'
TRUNK = 'electricity:dc_trunk_cable'


# ------------------------------------------------------------------ what the catalogues say

def module_watts():
    """Watts a module, from PvModuleCatalog.

    The rated power opens the third line of the record - after the cell count and the four dimensions -
    and the designation is no help: HN-144-580 is 144 cells and 580 W, in that order.
    """
    text = open(os.path.join(REGISTRY, 'PvModuleCatalog.java')).read()
    out = {}
    for name, body in re.findall(r'PvModuleSpec (\w+) = new PvModuleSpec\((.*?)\);', text, re.S):
        lines = [line.strip() for line in body.strip().split('\n')]
        out[name] = float(lines[2].split(',')[0])
    return out


def arrays():
    """Each array product: its block id, its module, and how its strings are made up."""
    text = open(os.path.join(REGISTRY, 'PvCatalog.java')).read()
    watts = module_watts()
    out = {}
    for name, body in re.findall(r'PvArraySpec (\w+) = ARRAYS\.register\(new PvArraySpec\((.*?)\)\);', text, re.S):
        block = re.search(r'id\("(\w+)"\)', body).group(1)
        module = re.search(r'PvModuleCatalog\.(\w+)', body).group(1)
        per_string, strings = (int(v) for v in re.findall(r'\n\s+(\d+), (\d+)\)?\s*$', body)[0])
        out[name] = dict(block=block, strings=strings,
                         kw=per_string * strings * watts[module] / 1000.0)
    return out


def combiners():
    """Each combiner: its block id and how many fused ways it has."""
    text = open(os.path.join(REGISTRY, 'CombinerCatalog.java')).read()
    out = {}
    for name, body in re.findall(r'CombinerSpec (\w+) = COMBINERS\.register\(new CombinerSpec\((.*?)\)\);', text, re.S):
        block = re.search(r'id\("(\w+)"\)', body).group(1)
        out[name] = dict(block=block, ways=int(re.search(r'(\d+),', body.split('id(')[1].split(')', 1)[1]).group(1)))
    return out


def inverters():
    """Each inverter: its block id and its AC rating."""
    text = open(os.path.join(REGISTRY, 'InverterCatalog.java')).read()
    out = {}
    for name, body in re.findall(r'InverterSpec (\w+) = INVERTERS\.register\(new InverterSpec\((.*?)\)\);', text, re.S):
        block = re.search(r'id\("(\w+)"\)', body).group(1)
        out[name] = dict(block=block, kw=float(re.search(r'\n\s+(\d+\.\d+),', body).group(1)))
    return out


def conductors():
    """Each conductor: its item id and the longest span it will make."""
    text = open(os.path.join(REGISTRY, 'ConductorCatalog.java')).read()
    out = {}
    for name, body in re.findall(r'ConductorSpec (\w+) = CONDUCTORS\.register\(new ConductorSpec\((.*?)\)\);', text, re.S):
        item = re.search(r'id\("(\w+)"\)', body).group(1)
        # the last line of the record is sag, the longest span, and blocks a reel
        out[name] = dict(item=item, klass=re.search(r'VoltageClass\.(\w+)', body).group(1),
                         max_span=float(body.rstrip().rsplit('\n', 1)[1].split(',')[1]))
    return out


ARRAYS, COMBINERS, INVERTERS, CONDUCTORS = arrays(), combiners(), inverters(), conductors()


def cells(model, java_class, constant=None):
    """The cell offsets a machine claims, read out of the block's own table."""
    text = open(os.path.join(BLOCKS, java_class + '.java')).read()
    table = check_hitboxes.declared(check_hitboxes.scoped(text, constant))
    return sorted(table) or [(0, 0, 0)]


# Which table each machine on this plant reads its footprint from.
FOOTPRINTS = {
    'array': cells('pv_array', 'PvArrayBlock'),
    'combiner': cells('pv_combiner', 'PvCombinerBlock'),
    'inverter': cells('pv_inverter', 'PvInverterBlock', 'CELLS'),
    'tx_machine': cells('tx_machine', 'TransformerBlock', 'MACHINE_CELLS'),
    'tx_substation': cells('tx_substation', 'TransformerBlock', 'SUBSTATION_CELLS'),
    'breaker': cells('mv_breaker', 'SwitchgearBlock', 'BREAKER_CELLS'),
    'disconnector': cells('mv_disconnector', 'SwitchgearBlock', 'DISCONNECTOR_CELLS'),
    'cabin': cells('electric_cabin', 'ElectricCabinBlock', 'CELLS'),
    'pole': cells('utility_pole', 'UtilityPoleBlock'),
    'kiosk': cells('power_box', 'PowerBoxBlock', 'CELLS'),
    'tower': cells('lattice_suspension', 'LatticeTowerBlock', 'BODY_CELLS'),
    'mast': cells('met_station', 'MetStationBlock'),
    'controller': cells('plant_controller', 'PlantControllerBlock'),
}


# ------------------------------------------------------------------ the plant

class Plant:
    """Everything on the site, in the block's own coordinates, with nothing placed twice."""

    def __init__(self):
        self.machines = []      # (kind, block, (x, y, z), state)
        self.runs = {}          # (x, y, z) -> gauge
        self.spans = []         # (conductor, from label, from pos, to label, to pos)
        self.groups = {}        # (x, y, z) -> which direct-current network the cable belongs to
        self.notes = []

    def machine(self, kind, block, pos, **state):
        self.machines.append((kind, block, pos, state))
        return pos

    def run(self, gauge, points, group):
        """A straight length of cable, inclusive of both ends, belonging to one named network.

        The group is the point: a plant has several direct-current networks that must *not* touch, one per
        string box and one per string inverter, and two that touch are silently one.
        """
        (x0, y, z0), (x1, _, z1) = points
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                self.runs[(x, y, z)] = gauge
                self.groups[(x, y, z)] = group

    def laid(self, block, points, group):
        """A length of conductor laid along the ground: the same idea as a cable, other properties."""
        (x0, y, z0), (x1, _, z1) = points
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                self.runs[(x, y, z)] = block
                self.groups[(x, y, z)] = group

    def span(self, conductor, name_a, a, name_b, b):
        self.spans.append((conductor, name_a, a, name_b, b))

    # ---- what the layout has to satisfy ----

    def claimed(self):
        """Every cell every machine occupies, and by which machine."""
        taken = {}
        clashes = []
        for kind, block, (x, y, z), _ in self.machines:
            for dx, dy, dz in FOOTPRINTS[kind]:
                cell = (x + dx, y + dy, z + dz)
                if cell in taken:
                    clashes.append('%s at %s and %s at %s both claim %s'
                                   % (block, (x, y, z), taken[cell][0], taken[cell][1], cell))
                taken[cell] = (block, (x, y, z))
        return taken, clashes

    def problems(self):
        out = []
        taken, clashes = self.claimed()
        out += clashes

        for cell, gauge in self.runs.items():
            if cell in taken:
                out.append('a %s run at %s is inside %s' % (gauge.split(':')[1], cell, taken[cell][0]))

        # every array has to touch a string run, or its inverter will never find it
        for kind, block, (x, y, z), _ in self.machines:
            if kind != 'array':
                continue
            beside = [(x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1)]
            if not any(self.runs.get(p) == STRING for p in beside):
                out.append('%s at %s touches no string cable' % (block, (x, y, z)))

        # each network has to be one piece - a gap is a plant that reads as two - and no two of them may
        # touch, because two that touch are one, and then a box sees strings that are not its own
        for group in sorted(set(self.groups.values())):
            cells_of = {p for p, name in self.groups.items() if name == group}
            if len(self.walk(cells_of)) != len(cells_of):
                out.append('the %s network is in more than one piece' % group)

            for x, y, z in cells_of:
                for step in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1)):
                    beside = (x + step[0], y, z + step[2])
                    other = self.groups.get(beside)
                    if other is not None and other != group and self.runs[beside] == self.runs[(x, y, z)]:
                        out.append('%s and %s touch at %s: two networks of one gauge are one network'
                                   % (group, other, beside))

        # a block id that is not registered is a command the server answers with an error and nothing else
        states = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'blockstates')
        known = {name[:-5] for name in os.listdir(states) if name.endswith('.json')}
        for block in sorted({b for _, b, _, _ in self.machines} | set(self.runs.values())):
            if block.split(':')[1] not in known:
                out.append('%s is not a block this mod registers' % block)

        for conductor, name_a, a, name_b, b in self.spans:
            reach = CONDUCTORS[conductor]['max_span']
            length = sum((a[i] - b[i]) ** 2 for i in range(3)) ** 0.5
            if length > reach:
                out.append('the span %s to %s is %.0f blocks, and %s reaches %.0f'
                           % (name_a, name_b, length, conductor, reach))
        return out

    @staticmethod
    def walk(cells_of):
        """One connected piece, from any starting cell: four-connected, which is how a run joins up."""
        start = next(iter(cells_of))
        seen, frontier = {start}, [start]
        while frontier:
            x, y, z = frontier.pop()
            for step in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1)):
                other = (x + step[0], y + step[1], z + step[2])
                if other in cells_of and other not in seen:
                    seen.add(other)
                    frontier.append(other)
        return seen

    # ---- what a server is handed ----

    def relative(self, origin):
        """The same commands with every coordinate written as an offset, for a datapack function.

        A function runs at whoever calls it, so `~ ~ ~` is the block the player is standing on - which is
        what makes one datapack build the plant anywhere rather than only at the coordinates it was written
        for.  Minecraft floors a relative coordinate, so standing anywhere in a block is the same block.
        """
        out = []
        for line in self.commands():
            head, x, y, z, tail = line.split(' ', 4)
            offsets = ' '.join('~%s' % (int(value) - origin[i] or '')
                               for i, value in enumerate((x, y, z)))
            out.append('%s %s %s' % (head, offsets, tail))
        return out

    def commands(self):
        """The runs first and the machines after, which is what makes the cables join up.

        setblock does not run a block's own placement rule, so a cable put down beside another cable does
        not connect to it: the neighbour is told, the new block is not.  The sides between two cables are
        computed here, and every side facing a machine is left to the machine - which fixes it when it
        lands, because that is a neighbour update the cable does get.
        """
        out = []
        for (x, y, z), gauge in sorted(self.runs.items()):
            sides = []
            for name, step in (('north', (0, 0, -1)), ('south', (0, 0, 1)),
                               ('east', (1, 0, 0)), ('west', (-1, 0, 0))):
                beside = (x + step[0], y, z + step[2])
                joined = self.runs.get(beside) == gauge
                # a cable's sides are a RedstoneSide; a laid conductor's are plain booleans
                sides.append('%s=%s' % (name, ('side' if joined else 'none') if 'cable' in gauge
                                        else ('true' if joined else 'false')))
            tail = ',buried=false' if 'cable' in gauge else ''
            out.append('setblock %d %d %d %s[%s%s]' % (x, y, z, gauge, ','.join(sides), tail))

        for kind, block, (x, y, z), state in self.machines:
            written = ''.join('[%s]' % ','.join('%s=%s' % item for item in sorted(state.items())) if state else '')
            out.append('setblock %d %d %d %s%s' % (x, y, z, block, written))
        return out


# ------------------------------------------------------------------ the site

def build(origin):
    """A 533 kW plant on four sub-fields, out to a 400 kV line, with everything a real one has."""
    ox, oy, oz = origin
    p = Plant()

    def at(x, z):
        return (ox + x, oy, oz + z)

    # ---- sub-field 1: sixteen fixed-tilt rows, one string each, east and west of the spine
    for i in range(8):
        p.machine('array', 'electricity:' + ARRAYS['TR_580']['block'], at(i, 0), facing='east')
        p.machine('array', 'electricity:' + ARRAYS['TR_580']['block'], at(i, 2), facing='west')
    p.run(STRING, (at(0, 1), at(8, 1)), 'stringhe TR-580')

    # ---- sub-field 2: ten thin-film rows, three strings each
    for i in range(5):
        p.machine('array', 'electricity:' + ARRAYS['TR_530']['block'], at(i, 4), facing='east')
        p.machine('array', 'electricity:' + ARRAYS['TR_530']['block'], at(i, 6), facing='west')
    p.run(STRING, (at(0, 5), at(8, 5)), 'stringhe TR-530')

    # ---- sub-field 3: sixteen single-axis trackers.  Facing is cosmetic: a tracked row turns itself
    for i in range(8):
        p.machine('array', 'electricity:' + ARRAYS['HX_700']['block'], at(i, 8), facing='north')
        p.machine('array', 'electricity:' + ARRAYS['HX_700']['block'], at(i, 10), facing='north')
    p.run(STRING, (at(0, 9), at(8, 9)), 'stringhe HX-700')

    # ---- the three string boxes, north of their own field, and the trunk that joins them
    boxes = [('CB_16', 1, 'CB-16 nord'), ('CB_32', 5, 'CB-32 centro'), ('CB_16', 9, 'CB-16 sud')]
    for spec, z, _ in boxes:
        p.machine('combiner', 'electricity:' + COMBINERS[spec]['block'], at(9, z), facing='west', isolated='false')
        p.run(TRUNK, (at(10, z), at(12, z)), 'dorsale')
    p.run(TRUNK, (at(12, 1), at(12, 9)), 'dorsale')
    p.run(TRUNK, (at(12, 5), at(14, 5)), 'dorsale')

    # ---- the central inverter, and its transformer beside it
    inverter_a = p.machine('inverter', 'electricity:' + INVERTERS['VX_350']['block'], at(15, 5), facing='west')
    tx_a = p.machine('tx_machine', 'electricity:tx_machine', at(18, 5), facing='west')

    # ---- sub-field 4: the other topology - eight flat tables and two dual-axis rows straight into a
    # string inverter, no box between them, which is what a roof or a small ground mount is
    for i in range(3):
        p.machine('array', 'electricity:' + ARRAYS['FT_430']['block'], at(i, 14), facing='east')
        p.machine('array', 'electricity:' + ARRAYS['FT_430']['block'], at(i, 16), facing='west')
    p.machine('array', 'electricity:' + ARRAYS['AE_440']['block'], at(4, 14), facing='north')
    p.machine('array', 'electricity:' + ARRAYS['AE_440']['block'], at(4, 16), facing='north')
    p.run(STRING, (at(0, 15), at(14, 15)), 'stringhe FT-430 e AE-440')
    inverter_b = p.machine('inverter', 'electricity:' + INVERTERS['VX_110']['block'], at(15, 15), facing='west')
    tx_b = p.machine('tx_machine', 'electricity:tx_machine', at(18, 15), facing='west')

    # ---- the medium-voltage bay: breaker, then disconnector, then the cabin that collects both inverters
    breaker = p.machine('breaker', 'electricity:mv_breaker', at(22, 5), facing='west', open='false')
    disconnector = p.machine('disconnector', 'electricity:mv_disconnector', at(25, 5), facing='west', open='false')
    cabin = p.machine('cabin', 'electricity:electric_cabin', at(29, 10), facing='west')

    # ---- the substation, three cells across, and the line that leaves it
    substation = p.machine('tx_substation', 'electricity:tx_substation', at(34, 10), facing='west')
    towers = [('terminale partenza', p.machine('tower', 'electricity:lattice_terminal', at(42, 10), facing='west'))]
    for i, x in enumerate((62, 82)):
        towers.append(('sospensione %d' % (i + 1),
                       p.machine('tower', 'electricity:lattice_suspension', at(x, 10), facing='west')))
    towers.append(('terminale arrivo', p.machine('tower', 'electricity:lattice_terminal', at(102, 10), facing='west')))

    # ---- and the way down.  A 400 kV tower takes nothing but transmission conductor and a pole refuses
    # it, so there is no span that joins the two: coming off the line means a second substation transformer,
    # the line in on its upper bushings and medium voltage out of its lower ones - a receiving substation,
    # which is what a real one is.  From there a laid run, because that is the only thing its own feeds list
    # will send medium voltage to, and a laid run reaches a pole.
    arrival = p.machine('tx_substation', 'electricity:tx_substation', at(110, 10), facing='west')
    p.laid('electricity:mv_conductor_run', (at(114, 10), at(118, 10)), 'uscita MT della sottostazione di arrivo')
    poles = [p.machine('pole', 'electricity:utility_pole', at(122, 10), facing='west'),
             p.machine('pole', 'electricity:utility_pole', at(136, 10), facing='west')]
    kiosk = p.machine('kiosk', 'electricity:power_box', at(144, 10), facing='west', mounted='false')

    # ---- what measures and what commands.  The mast has to be within twelve blocks of a row to take it
    # as its reference; the cabinet within sixty-four of every machine it dispatches.
    mast = p.machine('mast', 'electricity:met_station', at(-3, 5), facing='north')
    controller = p.machine('controller', 'electricity:plant_controller', at(18, 1), facing='west')

    # ---- the spans, which a player strings by hand: a conductor apart is the point of them
    mv = 'AAAC_228'
    p.span(mv, 'inverter VX-350K (tetto)', inverter_a, 'TX macchina A (isolatore basso)', tx_a)
    p.span(mv, 'inverter VX-110K (tetto)', inverter_b, 'TX macchina B (isolatore basso)', tx_b)
    p.span(mv, 'TX macchina A (isolatore alto)', tx_a, 'interruttore (lato linea)', breaker)
    p.span(mv, 'TX macchina B (isolatore alto)', tx_b, 'interruttore (lato linea)', breaker)
    p.span(mv, 'interruttore (lato carico)', breaker, 'sezionatore (lato linea)', disconnector)
    p.span(mv, 'sezionatore (lato carico)', disconnector, 'cabina (ingresso)', cabin)
    p.span(mv, 'cabina (uscita)', cabin, 'TX sottostazione (isolatore basso)', substation)
    hv = 'ACSR_592_QUAD'
    p.span(hv, 'TX sottostazione (isolatore alto)', substation, 'traliccio %s' % towers[0][0], towers[0][1])
    for (name_a, a), (name_b, b) in zip(towers, towers[1:]):
        p.span(hv, 'traliccio %s' % name_a, a, 'traliccio %s' % name_b, b)
    p.span(hv, 'traliccio %s' % towers[-1][0], towers[-1][1], 'TX arrivo (isolatore alto)', arrival)
    p.span(mv, 'TX arrivo (isolatore basso)', arrival, 'tratto MT posato (primo blocco)', (arrival[0] + 4, arrival[1], arrival[2]))
    p.span(mv, 'tratto MT posato (ultimo blocco)', (arrival[0] + 8, arrival[1], arrival[2]), 'palo 1', poles[0])
    p.span(mv, 'palo 1', poles[0], 'palo 2', poles[1])
    p.span('ABC_70', 'palo 2', poles[1], 'kiosk', kiosk)

    p.notes.append(('mast', mast))
    p.notes.append(('controller', controller))
    p.notes.append(('inverters', (inverter_a, inverter_b)))
    return p


# ------------------------------------------------------------------ the summary

def summary(p):
    """What the plant adds up to, counted off the layout rather than written down beside it."""
    counted = {}
    for kind, block, _, _ in p.machines:
        counted[block] = counted.get(block, 0) + 1

    dc, strings = 0.0, 0
    for name, spec in ARRAYS.items():
        n = counted.get('electricity:' + spec['block'], 0)
        dc += n * spec['kw']
        strings += n * spec['strings']

    ac = sum(counted.get('electricity:' + spec['block'], 0) * spec['kw'] for spec in INVERTERS.values())
    ways = sum(counted.get('electricity:' + spec['block'], 0) * spec['ways'] for spec in COMBINERS.values())
    return dc, ac, strings, ways, counted


def main():
    origin = (0, 0, 0)
    if '--at' in sys.argv:
        index = sys.argv.index('--at')
        origin = tuple(int(v) for v in sys.argv[index + 1:index + 4])

    p = build(origin)
    problems = p.problems()
    dc, ac, strings, ways, counted = summary(p)

    print('the plant, built from %s:' % (origin,))
    for block, n in sorted(counted.items()):
        print('    %3d x %s' % (n, block.split(':')[1]))
    print('    %d cable blocks, %d spans' % (len(p.runs), len(p.spans)))
    print('%.1f kW of modules in %d strings, %.1f kW of inverters, DC/AC %.2f'
          % (dc, strings, ac, dc / ac if ac else 0.0))
    print('%d fused ways in the string boxes' % ways)

    mast = dict(p.notes)['mast']
    nearest = min(((sum((mast[i] - pos[i]) ** 2 for i in range(3))) ** 0.5, pos)
                  for kind, _, pos, _ in p.machines if kind == 'array')
    print('the mast is %.1f blocks from its nearest row (it takes one within 12)' % nearest[0])
    controller = dict(p.notes)['controller']
    for pos in dict(p.notes)['inverters']:
        distance = (sum((controller[i] - pos[i]) ** 2 for i in range(3))) ** 0.5
        print('the control cabinet is %.1f blocks from the inverter at %s (it reaches 64)' % (distance, pos))

    print()
    for line in problems:
        print('    PROBLEM %s' % line)
    if problems:
        print('%d problem(s) in the layout' % len(problems))
        return 1

    print('nothing overlaps anything, every row touches its own gauge, every span is inside its reach')

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w') as f:
        f.write('\n'.join(p.commands()) + '\n')
    print('%s: %d commands, at %s' % (OUT, len(p.commands()), origin))

    namespace, name = FUNCTION
    functions = os.path.join(PACK, 'data', namespace, 'functions')
    os.makedirs(functions, exist_ok=True)
    with open(os.path.join(PACK, 'pack.mcmeta'), 'w') as f:
        f.write('{\n  "pack": {\n    "pack_format": 15,\n'
                '    "description": "Electricity: a 533 kW photovoltaic plant, where you stand"\n  }\n}\n')
    with open(os.path.join(functions, name + '.mcfunction'), 'w') as f:
        f.write('# written by tools/gen_example_plant.py - do not edit by hand\n')
        f.write('# /function %s:%s builds the plant from the block you are standing on\n' % (namespace, name))
        f.write('\n'.join(p.relative(origin)) + '\n')
    print('%s: /function %s:%s, relative to whoever runs it' % (PACK, namespace, name))

    print('\nthe spans, which a player strings by hand with the reel in hand:')
    for conductor, name_a, a, name_b, b in p.spans:
        length = sum((a[i] - b[i]) ** 2 for i in range(3)) ** 0.5
        print('    %-14s %-34s %-16s -> %-34s %-16s %3.0f blocks'
              % (CONDUCTORS[conductor]['item'], name_a, a, name_b, b, length))
    return 0


if __name__ == '__main__':
    sys.exit(main())
