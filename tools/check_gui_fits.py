#!/usr/bin/env python3
"""Whether every string in a GUI panel fits the space the layout gives it.

    python3 tools/check_gui_fits.py

Measured at the widest each field can get, not at the value it happens to hold - and a line that fits
today but overruns at the extremes of its own fields is a failure, not a warning.  SPANS is what bounds
"the extremes": a reading with a unit this file knows is measured at what that unit can really reach, and
one it does not know is measured at an absurd figure, because asking for too much room is the safe error.
"""

import json
import os
import re
import struct
import sys
import zlib

FONT = os.path.join('build', 'gui-check', 'ascii.png')
LANG = os.path.join('src', 'main', 'resources', 'assets', 'electricity', 'lang', 'en_us.json')
SCREENS = os.path.join('src', 'main', 'java', 'com', 'dooji', 'electricity', 'client', 'screen')

# Glyphs that are not in ascii.png come from unifont, which is a fixed grid.
UNIFONT_ADVANCE = 6
SPACE_ADVANCE = 4


def read_png(path):
    """Width, height and a row-major list of RGBA tuples. Only what ascii.png needs."""
    data = open(path, 'rb').read()
    pos, width, height, idat = 8, 0, 0, b''
    while pos < len(data):
        length = struct.unpack('>I', data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + length]
        if kind == b'IHDR':
            width, height, bits, colour = struct.unpack('>IIBB', payload[:10])
            if bits != 8 or colour != 6:
                raise SystemExit('%s: expected 8-bit RGBA, got bits=%d colour=%d' % (path, bits, colour))
        elif kind == b'IDAT':
            idat += payload
        pos += 12 + length

    raw = zlib.decompress(idat)
    stride = width * 4
    rows, previous, offset = [], bytearray(stride), 0
    for _ in range(height):
        filter_type = raw[offset]
        offset += 1
        line = bytearray(raw[offset:offset + stride])
        offset += stride
        for i in range(stride):
            left = line[i - 4] if i >= 4 else 0
            up = previous[i]
            upleft = previous[i - 4] if i >= 4 else 0
            if filter_type == 1:
                line[i] = (line[i] + left) & 255
            elif filter_type == 2:
                line[i] = (line[i] + up) & 255
            elif filter_type == 3:
                line[i] = (line[i] + ((left + up) >> 1)) & 255
            elif filter_type == 4:
                predictor = left + up - upleft
                a, b, c = abs(predictor - left), abs(predictor - up), abs(predictor - upleft)
                line[i] = (line[i] + (left if a <= b and a <= c else up if b <= c else upleft)) & 255
        rows.append(bytes(line))
        previous = line

    return width, height, rows


def advances(path):
    """The advance of every ASCII codepoint"""
    width, height, rows = read_png(path)
    cell = width // 16
    table = {}
    for code in range(256):
        column, row = (code & 15) * cell, (code >> 4) * cell
        rightmost = -1
        for y in range(row, row + cell):
            for x in range(column, column + cell):
                if rows[y][x * 4 + 3] != 0:
                    rightmost = max(rightmost, x - column)
        table[code] = 0 if rightmost < 0 else rightmost + 2

    table[ord(' ')] = SPACE_ADVANCE
    return table


def width_of(text, table):
    total = 0
    for character in text:
        code = ord(character)
        total += table.get(code, UNIFONT_ADVANCE) if code < 256 else UNIFONT_ADVANCE

    return total


def constants(source):
    """Every {@code private static final int NAME = value;} in a screen, as a dict."""
    return {name: int(value) for name, value in
            re.findall(r'private static final int (\w+) = (-?\d+);', source)}


def resolved(text, key, source, typical=False):
    """The text with its %s placeholders replaced by what the screen actually formats into them."""
    at = source.find('"%s"' % key)
    if at < 0:
        return text

    depth, i = 1, source.index('(', source.rindex('translatable', 0, at))
    i += 1
    while i < len(source) and depth > 0:
        if source[i] == '(':
            depth += 1
        elif source[i] == ')':
            depth -= 1
        i += 1

    formats = re.findall(r'fmt\("([^"]+)"', source[at:i])
    slots = [match.start() for match in re.finditer('%s', text)]
    units = [unit_of(fmt, inside_format=True) or unit_of(text[slot + 2:])
             for fmt, slot in zip(formats, slots)]
    # A bare reading takes the unit of the next one that states it: "Cut-in %s · rated %s · cut-out %s m/s"
    # is three wind speeds, and the line says so once at the end, the way the panel is read.
    for index in range(len(units) - 2, -1, -1):
        if units[index] is None:
            units[index] = units[index + 1]

    out = text
    for fmt, unit in zip(formats, units):
        slot = out.index('%s')
        out = out[:slot] + (typical_value(fmt) if typical else widest_value(fmt, unit)) + out[slot + 2:]

    return out


# What a reading can actually hold, by the unit written beside it.  A percentage cannot pass 100 and an
# angle of incidence cannot pass 90, so measuring either at 1400 makes this file demand room for a value
# the mod cannot produce - and the only way to answer that is to shorten a label that was never too long.
# The bound is the widest *text*, not the number: a signed field pays for its minus sign.
SPANS = {
    '%': '100.0',      # soiling, shading, buried fraction, efficiency, availability
    '°C': '-60.0',     # colder than any biome, hotter than any module gets
    '°': '-180.0',     # an azimuth, or a tracker angle, which is signed
    'm/s': '60.0',     # over the cut-out speed of every turbine in the catalogue
    'cm': '300.0',
    'm': '40960',      # DcNetwork.MAX_RUNS blocks at WorldConditions.METRES_PER_BLOCK
    'V': '1500',       # the DC ceiling of the string catalogue
    'A': '1500',
    'kW': '-9999',
    'kWh': '999999',
    'Hz': '-60.0',
    'kohm': '99999',
    'rpm': '-60.0',
}

# The order matters: the longest unit has to be tried first, or 'm/s' is read as 'm'.
_UNITS = sorted(SPANS, key=len, reverse=True)


def unit_of(text, inside_format=False):
    """The unit a reading is written in, taken from what immediately follows it.

    Two places carry it and both count: the format the screen passes - {@code fmt("%.1f%%")} - and the
    translation the value lands in - {@code "Cell %s°C"}.  Only up to the next separator or the next
    placeholder, or the wind in "wind %s m/s · AOI %s°" reads its unit off the angle behind it.
    """
    tail = text
    if inside_format:
        for mark in ('%+.0f', '%.0f', '%.1f', '%.2f', '%.3f', '%d', '%s'):
            if mark in tail:
                tail = tail[tail.index(mark) + len(mark):]
                break
    tail = tail.split('·')[0].split('%s')[0].replace('%%', '%').strip()
    for unit in _UNITS:
        if tail.startswith(unit):
            return unit
    return None


def widest_value(fmt, unit=None):
    """The widest string a format could produce, bounded by its unit where SPANS knows one.

    A format with no unit this file recognises is measured at an absurd figure on purpose: a checker that
    guesses low is worse than one that asks for too much room.
    """
    ceiling = SPANS.get(unit)
    out = fmt
    if ceiling:
        whole, point, decimals = ceiling.partition('.')
        out = out.replace('%.0f', whole).replace('%+.0f', whole)
        out = out.replace('%.1f', whole + '.' + (decimals or '0')[:1].ljust(1, '0'))
        out = out.replace('%.2f', whole + '.' + (decimals or '0').ljust(2, '0')[:2])
        out = out.replace('%.3f', whole + '.' + (decimals or '0').ljust(3, '0')[:3])
        return out.replace('%d', whole).replace('%s', whole).replace('%%', '%')

    out = out.replace('%.0f', '1400').replace('%+.0f', '-60').replace('%.1f', '-40.0')
    out = out.replace('%.2f', '1.00').replace('%.3f', '1.000')
    out = out.replace('%d', '1500').replace('%s', '1500')
    return out.replace('%%', '%')


def typical_value(fmt):
    """What the slot holds on an ordinary afternoon."""
    out = fmt
    out = out.replace('%.0f', '35').replace('%+.0f', '-45').replace('%.1f', '34.8')
    out = out.replace('%.2f', '0.98').replace('%.3f', '0.985')
    out = out.replace('%d', '12').replace('%s', '12')
    return out.replace('%%', '%')


# Which drawing helpers share a line legitimately.
DRAW_GROUPS = (('label', 'value'), ('faint', 'faintValue'), ('state',), ('drawString',))

# Strings the screen hands to font.split itself
# file's.  The mast's footnote is one sentence over two lines on purpose.
WRAPPED = {'met_station.no_reference'}


def rows(source, where):
    """Every text row a screen draws, as {y: set of helper names}."""
    found = {}
    for match in re.finditer(r'\b(label|value|faint|faintValue|state)\(graphics,', source):
        helper = match.group(1)
        depth, i = 1, match.end()
        last = i
        while i < len(source) and depth > 0:
            if source[i] in '([':
                depth += 1
            elif source[i] in ')]':
                depth -= 1
                if depth == 0:
                    break
            elif source[i] == ',' and depth == 1:
                last = i + 1
            i += 1

        argument = source[last:i].strip()
        if argument.isdigit():
            y = int(argument)
        elif argument in where:
            y = where[argument]
        else:
            continue

        found.setdefault(y, set()).add(helper)

    return found


def widgets(source):
    """Where the buttons and sliders sit, as (top, bottom) bands."""
    return [(int(match.group(1)), int(match.group(1)) + 20)
            for match in re.finditer(r'topPos \+ (\d+),\s*\w+[^;]*?,\s*20\)', source)]


def collisions(prefix, source, where):
    """Rows where two things are writing over each other: two helpers, or a helper and a widget."""
    out = []
    bands = widgets(source)
    for y, helpers in sorted(rows(source, where).items()):
        for top, bottom in bands:
            # a line of text is nine pixels tall, so it clashes with anything starting under its baseline
            if top < y + 9 and y < bottom:
                out.append('%s: %s at y=%d is under a widget at y=%d' % (prefix, ', '.join(sorted(helpers)), y, top))

        if any(helpers <= set(group) for group in DRAW_GROUPS):
            continue

        out.append('%s: %s all draw at y=%d' % (prefix, ', '.join(sorted(helpers)), y))

    return out


def main():
    if not os.path.exists(FONT):
        raise SystemExit('run the extraction step first: %s is missing' % FONT)

    table = advances(FONT)
    lang = json.load(open(LANG))
    problems = []

    source = open(os.path.join(SCREENS, 'MetStationScreen.java')).read()
    where = constants(source)
    columns = [('left', where['LEFT_LABEL_X'], where['LEFT_VALUE_X']),
               ('right', where['RIGHT_LABEL_X'], where['RIGHT_VALUE_X'])]

    print('=== met mast, %d wide ===' % where['WIDTH'])
    for match in re.finditer(r'pair\(graphics, row\+\+, "(\w+)", fmt\("([^"]+)"[^;]*?, "(\w+)",\s*\n?\s*fmt\("([^"]+)"', source):
        pairs = [(match.group(1), match.group(2)), (match.group(3), match.group(4))]
        for (key, fmt), (side, label_x, value_x) in zip(pairs, columns):
            label = lang['screen.electricity.met_station.' + key]
            value = widest_value(fmt)
            need = width_of(label, table) + width_of(value, table)
            room = value_x - label_x
            flag = 'ok' if need + 4 <= room else 'COLLIDES'
            print('  %-5s %-18s %-10s label %3d + value %3d = %3d  of %3d  %s'
                  % (side, label, value, width_of(label, table), width_of(value, table), need, room, flag))
            if need + 4 > room:
                problems.append('met mast %s column: "%s" and "%s" need %d of %d' % (side, label, value, need + 4, room))

    # the two full-width lines under the readings
    inner = where['WIDTH'] - 2 * 8
    for key in ('fitted', 'no_reference'):
        text = widest_value(lang['screen.electricity.met_station.' + key])
        got = width_of(text, table)
        wrapped = 'wraps' if key == 'no_reference' else 'must fit on one line'
        print('  full  %-58s %3d of %3d  %s' % (text[:56], got, inner, 'ok' if got <= inner else wrapped))
        if got > inner and key != 'no_reference':
            problems.append('met mast: "%s" needs %d of %d' % (text, got, inner))

    for name, prefix in (('PvArrayScreen.java', 'pv_array'), ('PvInverterScreen.java', 'pv_inverter'),
                         ('PvCombinerScreen.java', 'pv_combiner'), ('WindTurbineScreen.java', 'wind_turbine'),
                         ('MetStationScreen.java', 'met_station')):
        source = open(os.path.join(SCREENS, name)).read()
        where = constants(source)
        problems.extend(collisions(prefix, source, where))
        inner = where.get('WIDTH', where.get('IMAGE_WIDTH', 0)) - 2 * 8
        print('=== %s, %d wide ===' % (prefix, inner + 16))
        for key, text in sorted(lang.items()):
            if not key.startswith('screen.electricity.%s.' % prefix):
                continue

            short = key.split('screen.electricity.')[1]
            # a tooltip is wrapped by the game, so its length is not this file's business
            if re.search(r'Tooltip\.create\(Component\.translatable\("screen\.electricity\.%s"'
                         % re.escape(short), source) or '.tip' in short:
                continue

            if short in WRAPPED:
                continue

            worst = width_of(widest_value(resolved(text, key, source)), table)
            usual = width_of(typical_value(resolved(text, key, source, typical=True)), table)
            if usual > inner:
                problems.append('%s: "%s" needs %d of %d at ordinary values' % (prefix, text, usual, inner))
                print('  OVERFLOWS %-52s %3d of %3d' % (text[:50], usual, inner))
            elif worst > inner:
                problems.append('%s: "%s" fits at %d but reaches %d of %d at the extremes of every field'
                                % (prefix, text, usual, worst, inner))
                print('  OVERRUNS  %-52s %3d usual, %3d worst, of %3d' % (text[:50], usual, worst, inner))
        print('  inner width %d' % inner)

    print()
    for problem in problems:
        print('  PROBLEM  %s' % problem)

    print('%d problems' % len(problems))
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
