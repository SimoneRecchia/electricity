#!/usr/bin/env python3
"""Proves the client is running the assets that are in the source tree.

    python3 tools/check_deployed.py

runClient does not read src/main/resources.  It reads build/resources/main, which Gradle's processResources
copies at launch - so regenerating a model or a texture and then pressing F3+T in a running client reloads
the *old* copy, and already-baked chunk sections keep their old geometry until they are rebuilt.  That is
indistinguishable, from a screenshot, from a modelling fault: half a run drawn one way and half the other.

This lists every asset that differs, is missing, or is left over.  If it says nothing, what is on screen is
what is in the tree, and any fault is mine.
"""

import hashlib
import os
import sys

SOURCE = os.path.join('src', 'main', 'resources')
DEPLOYED = os.path.join('build', 'resources', 'main')

# What is generated at build time rather than copied - Forge's own, and the datagen output.
IGNORED = ('META-INF/mods.toml', 'pack.mcmeta')


def digests(root):
    """Every file under a root, by its path relative to it, as a hash."""
    out = {}
    for folder, _, names in os.walk(root):
        for name in names:
            path = os.path.join(folder, name)
            key = os.path.relpath(path, root).replace(os.sep, '/')
            if key in IGNORED:
                continue

            with open(path, 'rb') as handle:
                out[key] = hashlib.sha1(handle.read()).hexdigest()
    return out


def main():
    if not os.path.isdir(DEPLOYED):
        print('%s does not exist: nothing has been built yet, so runClient will copy the tree fresh'
              % DEPLOYED)
        return 0

    source, deployed = digests(SOURCE), digests(DEPLOYED)
    stale = sorted(k for k in source if k in deployed and source[k] != deployed[k])
    missing = sorted(set(source) - set(deployed))
    orphan = sorted(set(deployed) - set(source))

    print('%d assets in the tree, %d deployed' % (len(source), len(deployed)))
    for key in stale:
        print('  STALE    %s differs from the copy the client reads' % key)
    for key in missing:
        print('  MISSING  %s has never been copied' % key)
    for key in orphan:
        print('  LEFTOVER %s is deployed but no longer in the tree' % key)

    if stale or missing or orphan:
        print('\n%d asset(s) out of date - run ./gradlew build, then restart the client rather than F3+T'
              % (len(stale) + len(missing) + len(orphan)))
        return 1

    print('the client reads exactly what is in the tree')
    return 0


if __name__ == '__main__':
    sys.exit(main())
