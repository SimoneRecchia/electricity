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
DATAPACK = os.path.join('build', 'plant', 'datapack')
# The pack format 1.20.1 reads.  A pack that states the wrong one is refused with no explanation in game.
PACK_FORMAT = 15
PACK = os.path.join('build', 'plant', 'datapack')
# The namespace and name the function is called by: /function electricity:plant
FUNCTION = ('electricity', 'plant')
REGISTRY = os.path.join('src', 'main', 'java', 'com', 'dooji', 'electricity', 'main', 'registry')
BLOCKS = os.path.join('src', 'main', 'java', 'com', 'dooji', 'electricity', 'block')

STRING = 'electricity:dc_string_cable'
TRUNK = 'electricity:dc_trunk_cable'

# Which machines a gauge may end on.  A cable side pointing at one of these is drawn connected and is what
# DcNetwork's walk steps through; a laid conductor has no such rule, because it meets a machine by span.
TERMINATES = {
    STRING: ('array', 'combiner', 'inverter'),
    TRUNK: ('combiner', 'inverter'),
}

# The products that turn: they meet a cable on the north or south face whatever they are facing.
TRACKED = ('pv_track_700', 'pv_dual_440')


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
        self.terminals = {}     # (x, y, z) -> which kind of machine stands there
        self.notes = []

    def machine(self, kind, block, pos, **state):
        self.machines.append((kind, block, pos, state))
        self.terminals[pos] = kind
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

        # A row meets a cable on one axis only, and which axis is not the same for the two kinds: a fixed
        # row takes it on the face it *faces* (and, once its leads are fitted, the opposite one), a tracked
        # row always on north or south.  A spine on the wrong axis leaves a field that looks wired and
        # carries nothing - which is exactly what the first draft of this layout did.
        for kind, block, (x, y, z), state in self.machines:
            if kind != 'array':
                continue

            tracked = block.split(':')[1] in TRACKED
            faces = ({(x, y, z - 1), (x, y, z + 1)} if tracked
                     else {(x + 1, y, z), (x - 1, y, z)} if state.get('facing') in ('east', 'west')
                     else {(x, y, z - 1), (x, y, z + 1)})
            if not any(self.runs.get(p) == STRING for p in faces):
                out.append('%s at %s has no string cable on a face it takes one (%s)'
                           % (block, (x, y, z), 'north or south' if tracked else 'its facing axis'))

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
        """Every side of every cable, computed here, because nothing in the world will compute it.

        setblock does not run a block's own placement rule *and does not send a shape update either* - it
        sets the state with UPDATE_CLIENTS only.  So a cable put down by command connects to nothing: not to
        the cable beside it, and not to the machine beside it when the machine lands afterwards.  Both have
        to be written into the state, and a run that misses the second looks perfect and carries nothing -
        the walk starts at the machine's own neighbours, so it leaves the box happily and then never reaches
        an array, because reaching one means a cable side that points at it.
        """
        out = []
        for (x, y, z), gauge in sorted(self.runs.items()):
            sides = []
            for name, step in (('north', (0, 0, -1)), ('south', (0, 0, 1)),
                               ('east', (1, 0, 0)), ('west', (-1, 0, 0))):
                beside = (x + step[0], y, z + step[2])
                joined = self.runs.get(beside) == gauge or TERMINATES.get(gauge, ()).__contains__(
                        self.terminals.get(beside))
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
    """A 596 kW plant on four sub-fields, out to a 400 kV line, with everything a real one has.

    Two constraints shape it, and both are the mod's own:

    A row meets a cable on one axis only.  A **fixed** row takes it on the face it faces - and it has to face
    east or west, because that is where the sun is - so a block of fixed rows is a north-south spine with
    rows down either side.  A **tracked** row takes it on north or south whatever it faces, so a block of
    trackers is an east-west spine with a row each side.  Get that backwards and the field looks wired and
    carries nothing.

    And an inverter has a **string count** as well as a rating.  A VX-350K takes 32 strings; three boxes of
    sixteen is 48, and it simply refuses the third - the box goes on saying it is full and nobody collects
    it.  Each sub-field here is sized to one inverter's inputs, which is also why the flat tables and the
    thin-film row go straight in with no box at all: twelve strings and three.
    """
    ox, oy, oz = origin
    p = Plant()

    def at(x, z):
        return (ox + x, oy, oz + z)

    def fixed(product, spine_x, rows, first_z, both=True):
        """A block of fixed rows: facing east to the west of the spine, facing west to the east of it."""
        for i in range(rows):
            p.machine('array', 'electricity:' + ARRAYS[product]['block'], at(spine_x - 1, first_z + i),
                      facing='east', harnessed='true')
            if both:
                p.machine('array', 'electricity:' + ARRAYS[product]['block'], at(spine_x + 1, first_z + i),
                          facing='west', harnessed='true')

    # ---- sub-field 1: thirty-two fixed-tilt rows in two blocks, one string each, sixteen to a box.  Both
    # boxes go on one trunk to the big inverter: 32 strings, which is exactly what it takes.
    for spine_x, box in ((1, 'CB_16'), (6, 'CB_32')):
        fixed('TR_580', spine_x, 8, 0)
        p.run(STRING, (at(spine_x, 0), at(spine_x, 7)), 'stringhe TR-580 x%d' % spine_x)
        p.machine('combiner', 'electricity:' + COMBINERS[box]['block'], at(spine_x, -1), facing='north',
                  isolated='false')

    p.run(TRUNK, (at(1, -2), at(16, -2)), 'dorsale')
    inverter_a = p.machine('inverter', 'electricity:' + INVERTERS['VX_350']['block'], at(17, -2), facing='south')

    # ---- sub-field 2: the tracked rows, which take their cable on the other axis.  Fourteen single-axis
    # and two dual-axis: sixteen strings, one box, one string inverter.
    for i in range(6):
        for z in (11, 13):
            p.machine('array', 'electricity:' + ARRAYS['HX_700']['block'], at(i, z), facing='north',
                      harnessed='true')
    for z in (11, 13):
        p.machine('array', 'electricity:' + ARRAYS['AE_440']['block'], at(6, z), facing='north',
                  harnessed='true')
    # Straight in, with no box: a *string* inverter has no trunk terminals at all - only the VX-350K and the
    # central VC-2500K do - so a combiner in front of one would sit there full and uncollected.  Sixteen
    # strings against eighteen inputs is what a string inverter is for.
    p.run(STRING, (at(0, 12), at(8, 12)), 'stringhe HX-700 e AE-440')
    inverter_b = p.machine('inverter', 'electricity:' + INVERTERS['VX_110']['block'], at(9, 12), facing='west')

    # ---- sub-field 3: six flat tables straight into a string inverter, no box between them, which is what
    # a roof or a small ground mount is.  Twelve strings against eighteen inputs.
    # No thin film here, and that is a decision the cabinet makes: three TR-530 strings sit at 999 V, which
    # is inside this window by half a volt and outside the small cabinet's altogether, and one string out of
    # window stops the whole inverter.  The product wants the 500-1500 V cabinet and three ways a row.
    fixed('FT_430', 1, 3, 16)
    p.run(STRING, (at(1, 16), at(1, 19)), 'stringhe FT-430')
    inverter_c = p.machine('inverter', 'electricity:' + INVERTERS['VX_110']['block'], at(1, 20), facing='south')

    # ---- sub-field 4: one row on the smallest cabinet there is - 10.4 kW against 10, which is the ratio a
    # shop roof actually has.  A fixed-tilt row and not the thin film: eighteen modules in series sit at
    # 783 V and this cabinet's tracker stops at 980, while three thin-film strings sit at 999 and it
    # refuses them - out of window, and the panel says so.
    fixed('TR_580', 6, 1, 17, both=False)
    p.run(STRING, (at(6, 17), at(6, 18)), 'stringhe TR-580 tetto')
    inverter_d = p.machine('inverter', 'electricity:' + INVERTERS['VX_10']['block'], at(6, 19), facing='south')

    # ---- the two machine transformers: the big cabinet on its own, the three small ones on the other
    tx_a = p.machine('tx_machine', 'electricity:tx_machine', at(20, -2), facing='south')
    tx_b = p.machine('tx_machine', 'electricity:tx_machine', at(16, 20), facing='south')

    # ---- the medium-voltage bay, north of the field: breaker, then disconnector, then the cabin
    breaker = p.machine('breaker', 'electricity:mv_breaker', at(24, -2), facing='south', open='false')
    disconnector = p.machine('disconnector', 'electricity:mv_disconnector', at(27, -2), facing='south', open='false')
    cabin = p.machine('cabin', 'electricity:electric_cabin', at(31, -2), facing='south')

    # ---- the substation, three cells across, and the line that leaves it
    substation = p.machine('tx_substation', 'electricity:tx_substation', at(36, -2), facing='south')
    towers = [('terminale partenza', p.machine('tower', 'electricity:lattice_terminal', at(44, -2), facing='south'))]
    for i, x in enumerate((64, 84)):
        towers.append(('sospensione %d' % (i + 1),
                       p.machine('tower', 'electricity:lattice_suspension', at(x, -2), facing='south')))
    towers.append(('terminale arrivo', p.machine('tower', 'electricity:lattice_terminal', at(104, -2), facing='south')))

    # ---- and the way down.  A 400 kV tower takes nothing but transmission conductor and a pole refuses
    # it, so there is no span that joins the two: coming off the line means a second substation transformer,
    # the line in on its upper bushings and medium voltage out of its lower ones - a receiving substation,
    # which is what a real one is.  From there a laid run, because that is the only thing its own feeds list
    # will send medium voltage to, and a laid run reaches a pole.
    arrival = p.machine('tx_substation', 'electricity:tx_substation', at(112, -2), facing='south')
    p.laid('electricity:mv_conductor_run', (at(116, -2), at(120, -2)), 'uscita MT della sottostazione di arrivo')
    poles = [p.machine('pole', 'electricity:utility_pole', at(124, -2), facing='south'),
             p.machine('pole', 'electricity:utility_pole', at(138, -2), facing='south')]
    kiosk = p.machine('kiosk', 'electricity:power_box', at(146, -2), facing='south', mounted='false')

    # ---- what measures and what commands.  The mast has to be within twelve blocks of a row to take it as
    # its reference; the cabinet within sixty-four of every machine it dispatches.  Both stand clear of any
    # row's east-west line, because that is the line the sun comes down.
    mast = p.machine('mast', 'electricity:met_station', at(3, -4), facing='north')
    controller = p.machine('controller', 'electricity:plant_controller', at(20, -5), facing='south')

    # ---- the spans, which a player strings by hand: a conductor apart is the point of them
    mv = 'AAAC_228'
    p.span(mv, 'inverter VX-350K (tetto)', inverter_a, 'TX macchina A (isolatore basso)', tx_a)
    for name, machine in (('VX-110K inseguitori', inverter_b), ('VX-110K tavoli piani', inverter_c),
                          ('VX-10K film sottile', inverter_d)):
        p.span(mv, 'inverter %s (tetto)' % name, machine, 'TX macchina B (isolatore basso)', tx_b)
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
    p.notes.append(('inverters', (inverter_a, inverter_b, inverter_c, inverter_d)))
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


def write_datapack(p, origin):
    """The same commands at ~ ~ ~, so the plant is built from wherever the player is standing.

    RCON is a server protocol and a single-player world has no server to talk to, so this is the only way
    into a world somebody is actually playing.  Relative coordinates rather than the origin the command
    file uses: a datapack cannot know where it will be run.
    """
    functions = os.path.join(DATAPACK, 'data', 'electricity', 'functions')
    os.makedirs(functions, exist_ok=True)
    write(os.path.join(DATAPACK, 'pack.mcmeta'),
          '{\n  "pack": {\n    "pack_format": %d,\n'
          '    "description": "Electricity - impianto solare da 533 kW: /function electricity:plant"\n'
          '  }\n}\n' % PACK_FORMAT)

    lines = ['# generated by tools/gen_example_plant.py - do not edit by hand',
             '# built from the block the command is run at: %d east, %d south, and 3 west of you'
             % (144, 16)]
    for command in p.commands():
        head, x, y, z, rest = command.split(' ', 4)
        lines.append('%s ~%d ~%d ~%d %s' % (head, int(x) - origin[0], int(y) - origin[1],
                                            int(z) - origin[2], rest))
    write(os.path.join(functions, 'plant.mcfunction'), '\n'.join(lines) + '\n')


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(text)


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

    write_datapack(p, origin)
    print('%s: the same plant, at ~ ~ ~, for /function electricity:plant' % DATAPACK)

    print('\nthe spans, which a player strings by hand with the reel in hand:')
    for conductor, name_a, a, name_b, b in p.spans:
        length = sum((a[i] - b[i]) ** 2 for i in range(3)) ** 0.5
        print('    %-14s %-34s %-16s -> %-34s %-16s %3.0f blocks'
              % (CONDUCTORS[conductor]['item'], name_a, a, name_b, b, length))
    return 0


if __name__ == '__main__':
    sys.exit(main())
