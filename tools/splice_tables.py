#!/usr/bin/env python3
"""Pastes the generators' --java tables back into the blocks that declare them.

    python3 tools/splice_tables.py

Every one of these tables is printed by a generator and pasted into Java by hand, and each generator then
proves the paste is still current - so a figure that moves fails the build until the table is refreshed.
Doing that by hand across three classes and 1400 boxes is where a stale table comes from, so this does it:
it runs each generator with --java, finds each `private static final ... NAME =` declaration in the output,
and replaces the same declaration in the class.  Nothing else in the file is touched.
"""

import glob
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


def machines():
    """The per-machine cell tables check_hitboxes cuts from the models, spliced into their own blocks.

    Its --java prints `new Cell(...)` rows under each model's heading rather than whole declarations, so the
    target constant comes from its own MODEL_TABLE and the class from its BLOCK_CLASS - one table, read
    rather than restated, or this would be the second copy of the mapping.
    """
    sys.path.insert(0, os.path.join(ROOT, 'tools'))
    import check_hitboxes as check

    run = subprocess.run([sys.executable, os.path.join(ROOT, 'tools', 'check_hitboxes.py'), '--java'],
                         cwd=ROOT, capture_output=True, text=True)
    # check_hitboxes prints the file's name, and electric_cab/cab.obj is not named after its directory -
    # every table here is keyed by directory, so map back through the models tree rather than guess.
    directory_of = {os.path.basename(p): os.path.basename(os.path.dirname(p))
                    for p in glob.glob(os.path.join(ROOT, check.MODELS, '*', '*.obj'))}

    rows, model = {}, None
    for line in run.stdout.split('\n'):
        found = re.match(r'(\S+\.obj)\s', line)
        if found:
            model = directory_of.get(found.group(1))
            rows[model] = []
        elif line.startswith('\t') and model:
            rows[model].append(line)
        elif model and not line.startswith('\t'):
            model = None

    done = 0
    for model, cells in rows.items():
        # A swept shape is deliberately not the drawn one, and check_hitboxes says so per model.
        if not cells or model in check.SWEPT:
            continue
        # MODEL_TABLE only names the constant where a class holds several; the rest call it CELLS.
        constant = check.MODEL_TABLE.get(model, 'CELLS')
        block = check.BLOCK_CLASS.get(check.MODEL_BLOCK.get(model, model))
        if not block:
            continue

        path = os.path.join(BLOCKS, block + '.java')
        lines = open(path).read().split('\n')
        found = blocks(lines)
        if constant not in found:
            raise SystemExit('%s declares no %s' % (block, constant))
        start, end, text = found[constant]
        head = lines[start]
        body = '\n'.join(cells).rstrip().rstrip(',')
        lines[start:end + 1] = (head + '\n' + body + ');').split('\n')
        open(path, 'w').write('\n'.join(lines))
        print('%-26s %-18s %d cella/e' % (block + '.java', constant, len(cells)))
        done += 1
    return done


def main():
    total = machines()
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
