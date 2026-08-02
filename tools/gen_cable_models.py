#!/usr/bin/env python3
"""Generates the blockstates and block models for the direct-current cables.

    python3 tools/gen_cable_models.py

Writes into src/main/resources/assets/electricity/{blockstates,models/block,models/item}/.

Vanilla JSON models rather than the OBJ pipeline the machines use, and deliberately: a cable is
laid by the hundred, it wants ambient occlusion and chunk batching, and its shape is decided by a
block state rather than by anything animated.  Which is also what redstone dust is, so this
generates the same kind of multipart definition dust has - a centre piece plus one arm per
connected side plus a climb where the run goes up a wall.

The one thing this has that dust does not is the trench, and it is the reason this file exists
rather than eight JSON files written by hand.  A buried run replaces a block of ground, so it has
to be a *complete* solid: the game culls the faces of the soil around it, and any part of the
block boundary the model does not cover would be a hole you could see through the world with.  So
the buried model is a bedding cube up to fifteen sixteenths with a one-pixel rim above it round
all four edges, which covers the boundary from nothing to sixteen with no two faces ever landing
on the same plane.  The arms stop at that rim, so the pair passes under a pixel of backfill at
each block edge - which is what a part-backfilled trench looks like anyway.
"""

import json
import os

ASSETS = os.path.join('src', 'main', 'resources', 'assets', 'electricity')
BLOCKSTATES = os.path.join(ASSETS, 'blockstates')
BLOCK_MODELS = os.path.join(ASSETS, 'models', 'block')
ITEM_MODELS = os.path.join(ASSETS, 'models', 'item')

# Height of the ground surface inside a buried block, and the rim that holds the boundary shut.
GROUND = 15.0
RIM = 1.0

# The two products, and the only two numbers that differ between them.  Half-width is measured
# from the middle of the block, so a string pair is six pixels across and a trunk ten - which is
# about the ratio of 6 mm² to 240 mm², allowing for the fact that a pair of anything narrower than
# four pixels is not readable at a block's size.
CABLES = {
    'dc_string_cable': dict(half=3.0, thick=1.5, texture='dc_string_line'),
    'dc_trunk_cable': dict(half=5.0, thick=2.5, texture='dc_trunk_line'),
}

TRENCH = 'electricity:block/dc_trench'


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def element(lo, hi, faces):
    return {'from': list(lo), 'to': list(hi), 'faces': faces}


def face(texture, uv, cull=None, tint=False):
    entry = {'uv': [round(v, 2) for v in uv], 'texture': texture}
    if cull is not None:
        entry['cullface'] = cull
    if tint:
        entry['tintindex'] = 0
    return entry


def cable_faces(texture, lo, hi, cull=None):
    """The faces of a length of cable, each mapped so the pair lines up across every seam.

    The top is mapped straight off the block coordinates, which is what makes the conductors in a
    corner piece meet the conductors in the arm leading into it: both take the same rectangle out
    of the same texture.  The sides take a slice through the pair, so a cable seen edge on shows
    its jacket rather than a smear of the top.

    No underside, because there is never anything to see it from: a surface run sits on a face
    sturdy enough to hold it and a buried one sits on its bedding.  ``cull`` names the one face
    that lands on a block boundary, where a solid neighbour should take it away.
    """
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    faces = {'up': face(texture, (x0, z0, x1, z1))}
    for name, uv in (('north', (x0, 16 - y1, x1, 16 - y0)), ('south', (x0, 16 - y1, x1, 16 - y0)),
                     ('west', (z0, 16 - y1, z1, 16 - y0)), ('east', (z0, 16 - y1, z1, 16 - y0))):
        faces[name] = face(texture, uv, cull=name if name == cull else None)

    return faces


def dot(cable, y0, y1):
    """The centre of a run: where a clip holds the pair down, and where the arms meet."""
    half, texture = cable['half'], 'electricity:block/' + cable['texture']
    lo = (8 - half, y0, 8 - half)
    hi = (8 + half, y1, 8 + half)
    return element(lo, hi, cable_faces(texture, lo, hi))


def arm(cable, y0, y1, near):
    """One arm, from the north edge in to the centre piece.

    Authored northwards and rotated by the blockstate, the same way dust is: north is no rotation,
    then ninety degrees a side round to west.  ``near`` is where it stops short of the edge, which
    is nothing on the surface and the width of the rim in a trench - so a buried pair passes under
    a pixel of backfill at each block edge and a surface one runs straight through.
    """
    half, texture = cable['half'], 'electricity:block/' + cable['texture']
    lo = (8 - half, y0, near)
    hi = (8 + half, y1, 8 - half)
    return element(lo, hi, cable_faces(texture, lo, hi, cull='north' if near == 0.0 else None))


def climb(cable):
    """The run going up the wall of the block alongside, to reach a run on top of it."""
    half, thick, texture = cable['half'], cable['thick'], 'electricity:block/' + cable['texture']
    lo = (8 - half, 0.0, 0.0)
    hi = (8 + half, 16.0, thick)
    faces = {
        'north': face(texture, (8 - half, 0, 8 + half, 16), cull='north'),
        'south': face(texture, (8 - half, 0, 8 + half, 16)),
        'west': face(texture, (0, 0, thick, 16)),
        'east': face(texture, (0, 0, thick, 16)),
    }
    return element(lo, hi, faces)


def bedding():
    """The sand a buried cable is laid in, and the rim that keeps the block boundary solid.

    Four rim boxes rather than a ring, because two boxes that overlap at a corner would put two
    faces on the same plane and they would flicker against each other along every trench in the
    world.  They are cut so no two of them share any volume.
    """
    box = element((0.0, 0.0, 0.0), (16.0, GROUND, 16.0), {
        'down': face(TRENCH, (0, 0, 16, 16), cull='down'),
        'up': face(TRENCH, (0, 0, 16, 16)),
        'north': face(TRENCH, (0, 16 - GROUND, 16, 16), cull='north'),
        'south': face(TRENCH, (0, 16 - GROUND, 16, 16), cull='south'),
        'west': face(TRENCH, (0, 16 - GROUND, 16, 16), cull='west'),
        'east': face(TRENCH, (0, 16 - GROUND, 16, 16), cull='east'),
    })

    rims = []
    spans = (
        ((0.0, 0.0), (16.0, RIM), 'north'),
        ((0.0, 16.0 - RIM), (16.0, 16.0), 'south'),
        ((0.0, RIM), (RIM, 16.0 - RIM), 'west'),
        ((16.0 - RIM, RIM), (16.0, 16.0 - RIM), 'east'),
    )
    for (x0, z0), (x1, z1), outward in spans:
        faces = {
            'up': face(TRENCH, (x0, z0, x1, z1)),
            'down': face(TRENCH, (x0, z0, x1, z1)),
            'north': face(TRENCH, (x0, 0, x1, RIM), cull='north' if outward == 'north' else None),
            'south': face(TRENCH, (x0, 0, x1, RIM), cull='south' if outward == 'south' else None),
            'west': face(TRENCH, (z0, 0, z1, RIM), cull='west' if outward == 'west' else None),
            'east': face(TRENCH, (z0, 0, z1, RIM), cull='east' if outward == 'east' else None),
        }
        rims.append(element((x0, GROUND, z0), (x1, 16.0, z1), faces))

    return [box] + rims


def models(name, cable):
    """Every model one gauge of cable needs, keyed by file name."""
    texture = 'electricity:block/' + cable['texture']
    thick = cable['thick']

    flat = {'parent': 'block/block', 'ambientocclusion': False,
            'textures': {'particle': texture}}
    trench = {'parent': 'block/block', 'textures': {'particle': TRENCH}}

    return {
        name: dict(flat, elements=[dot(cable, 0.0, thick)]),
        name + '_arm': dict(flat, elements=[arm(cable, 0.0, thick, 0.0)]),
        name + '_climb': dict(flat, elements=[climb(cable)]),
        name + '_trench': dict(trench, elements=bedding() + [dot(cable, GROUND, 16.0)]),
        name + '_trench_arm': dict(trench, elements=[arm(cable, GROUND, 16.0, RIM)]),
    }


def blockstate(name):
    """Multipart, because a cable's shape is the sum of its connections rather than one of a list."""
    model = 'electricity:block/' + name
    parts = [{'when': {'buried': 'false'}, 'apply': {'model': model}}]

    rotations = (('north', 0), ('east', 90), ('south', 180), ('west', 270))
    for side, turn in rotations:
        for suffix, values in (('_arm', 'side|up'), ('_climb', 'up')):
            apply = {'model': model + suffix}
            if turn:
                apply['y'] = turn
            parts.append({'when': {'buried': 'false', side: values}, 'apply': apply})

    parts.append({'when': {'buried': 'true'}, 'apply': {'model': model + '_trench'}})
    for side, turn in rotations:
        apply = {'model': model + '_trench_arm'}
        if turn:
            apply['y'] = turn
        # a trench has nothing to climb: the pair is already at the surface, so it meets a run on
        # top of the next block along without going anywhere
        parts.append({'when': {'buried': 'true', side: 'side|up'}, 'apply': apply})

    return {'multipart': parts}


def main():
    for name, cable in CABLES.items():
        for file_name, model in models(name, cable).items():
            write(os.path.join(BLOCK_MODELS, file_name + '.json'), model)
            print('models/block/%s.json' % file_name)

        write(os.path.join(BLOCKSTATES, name + '.json'), blockstate(name))
        print('blockstates/%s.json  %d parts' % (name, len(blockstate(name)['multipart'])))

        write(os.path.join(ITEM_MODELS, name + '.json'),
              {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'electricity:item/' + name}})
        print('models/item/%s.json' % name)


if __name__ == '__main__':
    main()
