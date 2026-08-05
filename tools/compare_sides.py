#!/usr/bin/env python3
"""How a lower subdivision actually looks, side by side, at the distance a player is at.

    python3 tools/compare_sides.py

Writes build/render/sides_<scene>.png: the same scene generated at several settings of ROUND and FITTING,
tiled left to right with the figure and the file cost under each.  CLAUDE.md section 1 fixes ROUND at 80
and FITTING at 32 and says never to lower one to save faces; this is the tool for arguing with that with
a picture rather than an opinion.  It restores the tree with git when it is done.
"""

import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)

import render_blocks as rb                                   # noqa: E402
from texlib import Canvas                                    # noqa: E402

ASSETS = 'src/main/resources/assets/electricity'

# One variable per sheet, or the picture answers two questions at once and neither clearly.  A cable's own
# tubes are FITTING, never ROUND, so lowering ROUND does nothing for a cable; a pole shaft is the reverse.
FITTINGS = (('32 (adesso)', 80, 32), ('24', 80, 24), ('16', 80, 16), ('12', 80, 12), ('8', 80, 8))
ROUNDS = (('80 (adesso)', 80, 32), ('40', 40, 32), ('24', 24, 32), ('16', 16, 32), ('10', 10, 32))

# scene -> the generators it needs, the scene to draw, and which subdivision the sheet varies
SCENES = {
    'cable': (('gen_cable_models.py', 'gen_trunk_models.py'), 'cable_junction', FITTINGS),
    'trunk': (('gen_cable_models.py', 'gen_trunk_models.py'), 'trunk', FITTINGS),
    'pole': (('gen_grid_models.py',), 'pole', ROUNDS),
}
TILE = 700


def generate(scripts, env):
    for script in scripts:
        run = subprocess.run([sys.executable, os.path.join(HERE, script)],
                             cwd=ROOT, capture_output=True, text=True, env=env)
        # A lower subdivision moves every box, so the Java tables stop agreeing - expected, and not what
        # this tool is asking about.  Anything else is a real failure.
        if run.returncode and 'is not what DcCableBlock declares' not in run.stdout:
            print(run.stdout[-1500:], run.stderr[-800:])
            raise SystemExit('%s failed at %s' % (script, env.get('ELECTRICITY_ROUND')))


def obj_lines(scripts):
    directories = {'gen_cable_models.py': ['dc_string_cable'], 'gen_trunk_models.py': ['dc_trunk_cable'],
                   'gen_grid_models.py': ['utility_pole', 'power_box']}
    total = 0
    for script in scripts:
        for name in directories.get(script, []):
            path = os.path.join(ROOT, ASSETS, 'models', name)
            for entry in os.listdir(path):
                if entry.endswith('.obj'):
                    total += sum(1 for _ in open(os.path.join(path, entry)))
    return total


def label(canvas, x, y, text, scale=2):
    """Five-by-seven digits and a few letters, drawn by hand: no font, and none needed."""
    glyphs = {
        '0': ('01110', '10001', '10011', '10101', '11001', '10001', '01110'),
        '1': ('00100', '01100', '00100', '00100', '00100', '00100', '01110'),
        '2': ('01110', '10001', '00001', '00010', '00100', '01000', '11111'),
        '3': ('11110', '00001', '00001', '01110', '00001', '00001', '11110'),
        '4': ('00010', '00110', '01010', '10010', '11111', '00010', '00010'),
        '5': ('11111', '10000', '11110', '00001', '00001', '10001', '01110'),
        '6': ('00110', '01000', '10000', '11110', '10001', '10001', '01110'),
        '7': ('11111', '00001', '00010', '00100', '01000', '01000', '01000'),
        '8': ('01110', '10001', '10001', '01110', '10001', '10001', '01110'),
        '9': ('01110', '10001', '10001', '01111', '00001', '00010', '01100'),
        'k': ('10000', '10000', '10010', '10100', '11000', '10100', '10011'),
        '/': ('00001', '00010', '00010', '00100', '01000', '01000', '10000'),
        '(': ('00110', '01000', '10000', '10000', '10000', '01000', '00110'),
        ')': ('01100', '00010', '00001', '00001', '00001', '00010', '01100'),
        'a': ('00000', '00000', '01110', '00001', '01111', '10001', '01111'),
        'd': ('00001', '00001', '01101', '10011', '10001', '10011', '01101'),
        'e': ('00000', '00000', '01110', '10001', '11111', '10000', '01110'),
        'o': ('00000', '00000', '01110', '10001', '10001', '10001', '01110'),
        's': ('00000', '00000', '01111', '10000', '01110', '00001', '11110'),
        'r': ('00000', '00000', '10110', '11001', '10000', '10000', '10000'),
        'i': ('00100', '00000', '00100', '00100', '00100', '00100', '00100'),
        'g': ('00000', '00000', '01111', '10001', '01111', '00001', '01110'),
        'h': ('10000', '10000', '10110', '11001', '10001', '10001', '10001'),
        'n': ('00000', '00000', '10110', '11001', '10001', '10001', '10001'),
        't': ('01000', '01000', '11110', '01000', '01000', '01000', '00110'),
        'l': ('01100', '00100', '00100', '00100', '00100', '00100', '01110'),
        'c': ('00000', '00000', '01110', '10001', '10000', '10001', '01110'),
        'f': ('00110', '01001', '01000', '11100', '01000', '01000', '01000'),
        'm': ('00000', '00000', '11010', '10101', '10101', '10101', '10101'),
        'p': ('00000', '00000', '01110', '10001', '11110', '10000', '10000'),
        'u': ('00000', '00000', '10001', '10001', '10001', '10011', '01101'),
        'v': ('00000', '00000', '10001', '10001', '10001', '01010', '00100'),
        'w': ('00000', '00000', '10101', '10101', '10101', '10101', '01010'),
        'x': ('00000', '00000', '10001', '01010', '00100', '01010', '10001'),
        ' ': ('00000',) * 7, '.': ('00000',) * 6 + ('00100',), '+': ('00000', '00100', '00100',
                                                                    '11111', '00100', '00100', '00000'),
        '%': ('11001', '11010', '00010', '00100', '01000', '01011', '10011'),
        '-': ('00000', '00000', '00000', '11111', '00000', '00000', '00000'),
    }
    cursor = x
    for char in text:
        rows = glyphs.get(char.lower())
        if rows is None:
            cursor += 6 * scale
            continue
        for row, bits in enumerate(rows):
            for col, bit in enumerate(bits):
                if bit == '1':
                    canvas.rect(cursor + col * scale, y + row * scale,
                                cursor + (col + 1) * scale, y + (row + 1) * scale, (18, 18, 22, 255))
        cursor += (len(rows[0]) + 1) * scale
    return cursor


def main():
    os.chdir(ROOT)
    wanted = [s for s in sys.argv[1:] if s in SCENES] or ['cable', 'pole']
    strip = 34

    for scene in wanted:
        scripts, name, steps = SCENES[scene]
        sheet = Canvas(TILE * len(steps), TILE + strip, (246, 246, 248, 255))
        for index, (text, round_sides, fitting) in enumerate(steps):
            env = dict(os.environ, ELECTRICITY_ROUND=str(round_sides),
                       ELECTRICITY_FITTING=str(fitting))
            generate(scripts, env)
            lines = obj_lines(scripts)
            triangles, eye, target = rb.SCENES[name]()
            path = os.path.join('build', 'render', 'sides_%s_%d.png' % (scene, round_sides))
            rb.render(triangles, eye, target, path, size=TILE)
            _, _, rows = rb.read_png(path)
            for y in range(TILE):
                for x in range(TILE):
                    sheet.set(index * TILE + x, y, tuple(rows[y][x * 4:x * 4 + 4]))
            label(sheet, index * TILE + 12, TILE + 9,
                  '%s lati - %d righe obj' % (text, lines))
            os.remove(path)
            print('%-8s %-18s %6d righe obj' % (scene, text, lines))

        out = os.path.join('build', 'render', 'sides_%s.png' % scene)
        sheet.write(out)
        print('-> %s' % out)

    subprocess.run(['git', 'checkout', '--', ASSETS], cwd=ROOT, check=True)
    print('albero ripristinato')


if __name__ == '__main__':
    main()
