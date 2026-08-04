#!/usr/bin/env python3
"""Generates the vanilla JSON block models: the workbench.

    python3 tools/gen_json_models.py

Writes into src/main/resources/assets/electricity/{blockstates,models/block}/.

Why the workbench stays a JSON model when the lamp did not
---------------------------------------------------------
Because nothing on a workbench is round.  A steel bench is a frame, a drawer stack, a plate top, a
tool board and a vice - boxes, all of them, and a box is exactly what the vanilla format expresses.
Staying in it keeps everything that comes free with it: the block is batched into the chunk mesh
rather than drawn one at a time, it gets ambient occlusion, and the item in the inventory is the
model rather than a sprite of it.

The lamp went the other way for the opposite reason: its column is round, and a rotated box in this
format is a *union* rather than an intersection, so three of them make a twelve-pointed star instead
of a twelve-sided prism.  There is no way to write a cylinder here at all.

The figures below are the ones ``WorkbenchBlock`` cuts its collision from, so if a drawer moves the
shape moves with it.
"""

import json
import os

ASSETS = os.path.join('src', 'main', 'resources', 'assets', 'electricity')
BLOCKSTATES = os.path.join(ASSETS, 'blockstates')
BLOCK_MODELS = os.path.join(ASSETS, 'models', 'block')

TOP = 'electricity:block/workbench_top'
SIDE = 'electricity:block/workbench_side'
FRONT = 'electricity:block/workbench_front'
BACK = 'electricity:block/workbench_back'
STEEL = 'electricity:block/pv_steel'

# The bench, part by part, in the sixteenths the game authors in and with the front at north - which
# is the facing the block is placed with, so the drawers face the player.
#
# Each entry is (from, to, {face: texture}), '*' standing for the faces not named.  The collision
# shape is cut from exactly this list by WORKBENCH_SHAPE below, which is printed for the Java.
BENCH = [
    # the four legs
    ((0.5, 0.0, 0.5), (2.5, 12.0, 2.5), {'*': SIDE}),
    ((13.5, 0.0, 0.5), (15.5, 12.0, 2.5), {'*': SIDE}),
    ((0.5, 0.0, 13.5), (2.5, 12.0, 15.5), {'*': SIDE}),
    ((13.5, 0.0, 13.5), (15.5, 12.0, 15.5), {'*': SIDE}),
    # the lower shelf, between the legs rather than through them
    ((2.5, 1.5, 2.5), (13.5, 3.0, 13.5), {'up': TOP, '*': SIDE}),
    # The drawer stack, on the left half as you face it, its front flush with the legs.
    #
    # Flush matters: set back to the shelf's line it sat *behind* the two front legs, so the one face
    # with drawers drawn on it was the one face nothing could see.
    ((2.5, 3.0, 0.5), (8.0, 12.0, 13.5), {'north': FRONT, '*': SIDE}),
    # the open half's shelf
    ((8.0, 7.5, 2.5), (13.5, 9.0, 13.5), {'up': TOP, '*': SIDE}),
    # the top: a steel-clad plate, proud of the frame all round the way a bench top is
    ((0.0, 12.0, 0.0), (16.0, 14.0, 16.0), {'up': TOP, 'down': SIDE, '*': SIDE}),
    # the tool board across the back
    ((0.5, 14.0, 13.5), (15.5, 16.0, 15.5), {'north': BACK, 'south': BACK, '*': SIDE}),
    # the vice, bolted to the near left corner where a bench vice goes
    ((2.0, 14.0, 2.0), (6.0, 15.6, 5.0), {'*': STEEL}),
    ((3.4, 15.6, 2.6), (4.6, 16.0, 4.4), {'*': STEEL}),
    # and a parts tray on the other side of the top
    ((9.0, 14.0, 2.5), (14.0, 14.8, 7.5), {'*': STEEL}),
]

FACE_UV = {
    'down': lambda lo, hi: [lo[0], 16.0 - hi[2], hi[0], 16.0 - lo[2]],
    'up': lambda lo, hi: [lo[0], lo[2], hi[0], hi[2]],
    'north': lambda lo, hi: [16.0 - hi[0], 16.0 - hi[1], 16.0 - lo[0], 16.0 - lo[1]],
    'south': lambda lo, hi: [lo[0], 16.0 - hi[1], hi[0], 16.0 - lo[1]],
    'west': lambda lo, hi: [lo[2], 16.0 - hi[1], hi[2], 16.0 - lo[1]],
    'east': lambda lo, hi: [16.0 - hi[2], 16.0 - hi[1], 16.0 - lo[2], 16.0 - lo[1]],
}


def element(lo, hi, textures):
    """One box, with every face taking the part of its texture its own position implies.

    Mapping by position rather than stretching the whole picture onto every face is what makes the
    grain of the steel line up between the top and the plate beside it - a bench whose every panel
    carried the whole texture read as a collage of squares.
    """
    faces = {}
    for face, uv in FACE_UV.items():
        texture = textures.get(face, textures.get('*'))
        if texture is None:
            continue
        faces[face] = {'uv': [round(v, 2) for v in uv(lo, hi)], 'texture': '#' + name_of(texture)}
    return {'from': list(lo), 'to': list(hi), 'faces': faces}


def name_of(texture):
    return texture.rsplit('/', 1)[-1]


def workbench():
    used = {}
    for _, _, textures in BENCH:
        for texture in textures.values():
            used[name_of(texture)] = texture

    return {
        'parent': 'block/block',
        'textures': dict(used, particle=SIDE),
        'elements': [element(lo, hi, textures) for lo, hi, textures in BENCH],
    }


def blockstate():
    """One variant per facing, turned by whole quarters - the front is authored at north."""
    variants = {}
    for facing, angle in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        variant = {'model': 'electricity:block/workbench'}
        if angle:
            variant['y'] = angle
        variants['facing=' + facing] = variant
    return {'variants': variants}


def shape_java():
    """The collision, cut from the same list the model is drawn from."""
    boxes = []
    for lo, hi, _ in BENCH:
        boxes.append('Block.box(%.1f, %.1f, %.1f, %.1f, %.1f, %.1f)' % (lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]))
    return 'Shapes.or(%s)' % (',\n\t\t\t'.join(boxes))


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def main():
    write(os.path.join(BLOCK_MODELS, 'workbench.json'), workbench())
    write(os.path.join(BLOCKSTATES, 'workbench.json'), blockstate())
    print('workbench: %d elements' % len(BENCH))
    print('\nthe shape WorkbenchBlock declares, cut from the same list:\n')
    print(shape_java())


if __name__ == '__main__':
    main()
