#!/usr/bin/env python3
"""Pastes the generators' --java tables back into the blocks that declare them.

    python3 tools/splice_tables.py

Every one of these tables is printed by a generator and pasted into Java by hand, and each generator then
proves the paste is still current - so a figure that moves fails the build until the table is refreshed.
Doing that by hand across three classes and 1400 boxes is where a stale table comes from, so this does it:
it runs each generator with --java, finds each `private static final ... NAME =` declaration in the output,
and replaces the same declaration in the class.  Nothing else in the file is touched.
"""

import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCKS = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'dooji', 'electricity', 'block')
DECL = re.compile(r'\t(?:private|public) static final (?:Map<[^>]+>|List<\w+>) (\w+) =')

# generator -> the class its tables live in
SOURCES = (
    (('gen_cable_models.py', 'gen_trunk_models.py'), 'DcCableBlock.java'),
    (('gen_conductor_models.py',), 'GroundConductorBlock.java'),
    (('gen_tower_models.py',), 'LatticeTowerBlock.java'),
)


def blocks(lines):
    """name -> (first line, last line, text) for each table declaration, ending at its closing `;`."""
    out, i = {}, 0
    while i < len(lines):
        found = DECL.match(lines[i])
        if found:
            j = i
            while j < len(lines) and not lines[j].rstrip().endswith(';'):
                j += 1
            out[found.group(1)] = (i, j, '\n'.join(lines[i:j + 1]))
        i += 1
    return out


def main():
    total = 0
    for scripts, java_name in SOURCES:
        fresh = {}
        for script in scripts:
            run = subprocess.run([sys.executable, os.path.join(ROOT, 'tools', script), '--java'],
                                 cwd=ROOT, capture_output=True, text=True)
            # The generator's own check fails while the table is stale, which is exactly why we are here.
            fresh.update({k: v[2] for k, v in blocks(run.stdout.split('\n')).items()})

        path = os.path.join(BLOCKS, java_name)
        lines = open(path).read().split('\n')
        found = blocks(lines)
        missing = sorted(set(fresh) - set(found))
        if missing:
            raise SystemExit('%s declares no %s' % (java_name, ', '.join(missing)))

        # bottom up, so the line numbers of the ones above stay valid
        for name in sorted(fresh, key=lambda n: -found[n][0]):
            start, end, _ = found[name]
            lines[start:end + 1] = fresh[name].split('\n')
        open(path, 'w').write('\n'.join(lines))
        print('%-26s %d tabelle: %s' % (java_name, len(fresh), ', '.join(sorted(fresh))))
        total += len(fresh)

    print('%d tabelle rigenerate' % total)


if __name__ == '__main__':
    main()
