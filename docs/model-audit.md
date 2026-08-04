# The models, the textures and the hitboxes

← [Back to the README](../README.md) · [Photovoltaics](photovoltaics.md) · [Getting started](getting-started.md)

Everything a player can place is now built from the same figures, at the same level of detail, with
its collision cut from its own geometry. This is what that means and how it is kept true.

## What was wrong, in a player's words

Two things, said about the game rather than about the code.

**"The turbine and the cabin look right and the rest doesn't."** Those two are a modelling package's
output over 1024-pixel textures; everything else was either boxes with 128-pixel drawings on them or,
in the case of the lamp and the workbench, a cube with a picture of the object on all six faces. The
difference is not resolution. It is that a photograph of concrete has structure at every scale -
blotches, aggregate, form lines, a rust stain - and a drawing of concrete has structure at exactly
one.

**"You walk into air beside things."** Collision was a hand-written box per machine, and a box is
wrong in both directions at once: too big where the machine is thin and absent where it is not.

## The two numbers everything round now uses

The turbine answers this, because the turbine is the model the rest were brought up to:

| part | sides |
|---|---|
| its tower, 1.24 blocks across | **80** |
| its insulator, 0.53 across | **32** |

So `modellib.ROUND` is 80 and `modellib.FITTING` is 32. A principal body gets 80 — the pole shaft,
the met mast, a torque tube, a pedestal column, a lamp column, a kiosk bushing's core. A fitting gets
32 — an insulator shed, a cable gland, an instrument body, a fan hub.

What deliberately keeps fewer is the geometry that is genuinely faceted, where more sides would not
be smoother but wrong: a **bolt head is a hexagon**, a pier is an **I**, a crossarm is a **channel**,
a luminaire housing is a **casting with flat fins on it**.

## Object by object

Each of these was looked up before it was drawn, and each carries the parts a photograph of the real
thing shows from ten metres away — and nothing smaller, because at a block to ten metres a bonding
jumper is one pixel of noise.

| object | what it is now | was |
|---|---|---|
| **Utility pole** | A spun concrete pole, tapered, mould seam and rust bloom, two galvanised channel crossarms with strap plates and braces, eight brown-glazed porcelain pin insulators, climbing steps, an earth strap, a number plate | A square post with two flat bars, 2364 faces of which nearly all were the insulators |
| **Power box** | A pad-mounted kiosk: cast plinth, sheet body, two moss-green door leaves with louvres low down, a crowned hood with a drip edge, an HV label, a roof bushing, a cable conduit | 43 faces. A cuboid with a door drawn on one side, hugging the west edge of its block and hanging outside it |
| **Electric lamp** | A post-top LED area light: base plate on bolts, galvanised column, a die-cast finned head, and a glass bowl with a diode array behind it at six brightnesses | A full cube with a picture of a light on all six faces |
| **Workbench** | A steel bench: four legs, two shelves, a drawer stack whose fronts face the player, a plate top scribed and scratched, a perforated tool board, a vice, a parts tray | A cube with a top texture and a side texture |
| **Flat array** | A ballasted table: precast blocks, channel perimeter, two rails, two whole modules, mid and end clamps | Four leg stubs, a frame, two plates |
| **Fixed rack** | Driven I-section piers, a bolted cleat on each, channel purlins open side down, a diagonal brace per row, four rails up the slope, clamps at every module edge | Four posts and a plate at 25° |
| **Single-axis tracker** | A 1P row: an I pier, a slew drive with a finned geared motor, an 80-sided torque tube, module rails across it with saddles, one whole module either side of the drive bay | A post, a 16-sided tube, four part-modules and purlins running the wrong way |
| **Dual-axis tracker** | A tapered pedestal on a gusseted base, a slew ring, a central elevation bearing with its worm box, a torque tube, two whole modules | A column, two yoke arms, four part-modules |
| **Inverter** | A station machine: channel plinth, corner posts, two door leaves (one carrying the interface, one plain), a crowned hood with lifting eyes, two guarded roof fans, side louvre banks, a DC compartment with fuse ways and glands, an AC bushing | A box, one door, one grille, one fan |
| **Combiner** | An enclosure on an 80-sided post with two bracket cleats, a rain hood, a window over the fuse ways, a rotary isolator, six glands and a trunk gland | A box on a 12-sided post |
| **Met mast** | Two tapered tube sections with a coupling, a hinged base plate on anchor bolts, a logger enclosure, a boom with three radiometers on levelling feet under glass domes, a six-plate radiation shield, a snow gauge, cups and vane, a lightning finial | A tube, three cup-and-disc instruments, a four-plate stack |
| **DC cables** | The pair as it is: two conductors, clips, armour bands on the trunk, and a trench of disturbed spoil | Same shape, 16-pixel textures |

The **turbine** and the **electric cabin** are untouched. They are what the rest was measured
against.

## The textures

`tools/texlib.py` is what changed. It has three things the old drawing code did not:

* **Value noise over octaves that wraps at the tile edge.** A surface gets blotches *and* grain *and*
  the faint unevenness between them. White noise has one scale by construction; a real sheet has all
  of them.
* **Soft edges.** A bolt head is round rather than a stepped square, and a swage line can sit at 12.3
  pixels instead of at 12.
* **One light direction for the whole mod**, over the viewer's left shoulder, so a bevel, a dome, a
  louvre blade and a hex head are all lit the same way. A drawn highlight is what makes a flat pixel
  read as a raised edge.

On top of those sit the materials — brushed aluminium, hot-dip galvanising with its spangle,
powder coat with its orange peel, cast concrete with aggregate, sawn wood, cable rubber, fired
porcelain glaze — and the weathering: dirt in the low places, rain streaks down vertical faces, rust
bleed at a steel edge.

Sizes are chosen by how close a player gets to the face, not by uniformity:

| texture | size | why |
|---|---|---|
| `pv_module` | 1024 | 144 half-cut cells with chamfered corners, three tinned ribbons each, a glass reflection over all of it. It is the surface a player looks at from a metre |
| the sheets, coats, doors, vents, concrete, benches, lenses | 512 | Read from a few metres |
| the profiles, plates, cable, instruments, domes | 256 | Small faces, or repeated along something long |
| `pv_switch` | 128 | A handle boss three centimetres across |

`tools/gen_block_textures.py` writes all of them; `tools/gen_pv_textures.py` keeps the item sprites
and the panel layouts, where sixteen hard pixels is the right answer.

**The item icons of the rebuilt placeables are renders of the models themselves**, at 128 pixels, on
transparent ground. Three of them used to be painted by hand from models that have since been rebuilt
from scratch, so a kiosk in the inventory was grey with one door while the one in the world is green
with two. A sprite taken from the model cannot drift.

## The hitboxes

Every placeable's collision is cut from its own geometry, and `tools/check_hitboxes.py` reads both
back and fails if they have moved apart:

```
$ python3 tools/check_hitboxes.py
the models and the tables agree, to a hundredth of a pixel
```

Three things had to change for that to be possible.

**A group per part.** The shape is cut from each object in the OBJ, so an object holding two parts at
different heights has a bounding box that fills the air between them. The pole's eight insulator
spindles and its seven climbing steps were one `hardware` group, whose box was a slab four blocks
wide and five tall through the middle of the pole; a tracked row's two module bays were one group,
whose box covered the drive between them. They are one object each now, which the clearance checker
wanted anyway.

**Slabs where something slopes.** A tilted plane's bounding box is not the plane: a rack at 25° has a
box seven pixels tall over its whole footprint, so a player walked into a wall of air in front of its
low edge and stood on air above it. Each group is now sliced into two-pixel slabs and each slab
clipped to the geometry, which gives a staircase that follows the slope — the same way the game draws
one. Only where the top climbs or falls all the way across: an 80-sided cone's top goes up and comes
back down, so a cone is one box, within half a pixel of the truth.

**Quarter-pixel parts count.** A ballasted table is two pixels tall in total, so a threshold of a
whole pixel threw away its ballast, its frame and its glass and left it with no collision at all. A
kiosk's plinth and its rain hood are both under a pixel and both plainly visible.

Cable is the case worth stating, because it looks like a mistake and is not: a surface run's outline
is now the pair itself — two pixels across and one tall, plus an arm per connected side and the climb
where it goes up a wall — rather than a slab the width of the block. **Collision stays empty**, so a
player does not catch a boot on every metre of their own plant.

### What cannot be cut to the model, and why

Two of the eleven, and in both cases the alternative is worse:

* **A tracked row and a pedestal frame** turn through the day. Their collision is the fixed body cut
  from the model plus the volume the plane sweeps, because a shape cut from the flat position would
  let a player fall through the row every afternoon.
* **The turbine** carries its own tower in the model, which in the world is a stack of
  `turbine_tower` blocks with their own collision, and a hundred metres of turning rotor above it
  that nothing should be able to stand on.

`check_hitboxes.py` prints both cases with the reason rather than passing them silently.

## Which way a machine faces, and the three places that have to agree

The renderer poses the model, the block entity turns the points its wires hang from, and the
collision cells turn with it. One fact, three readers, so it is declared once as `AUTHORED` on the
block and the arithmetic lives once in `ModelFacing`.

**Every model the mod generates now faces north**, which is one fewer special case than before: the
pole's old geometry was *mirrored* rather than turned, so north and south came out swapped and it
needed a rotation table of its own that nothing else used and that three separate pieces of code had
to know about. That table is gone. The power box was authored east and off-centre; it is north and
centred. Only the turbine and the cabin still face south and east, because they are unchanged.

`check_hitboxes.py` fails if a renderer works the authored facing out for itself instead of reading
its block's constant.

## What the checkers cover

| script | what it proves |
|---|---|
| `check_hitboxes.py` | every machine collides with exactly the shape it draws, to a hundredth of a pixel, and prints the tables the blocks declare |
| `check_model_textures.py` | no coplanar faces that both show, no geometry inside other geometry, no bordered picture sub-sampled, no picture on all six faces of a box, and texel density measured along each face's own u and v |
| `check_pv_clearance.py` | no moving part can pass through a fixed one at any angle |
| `check_gui_fits.py` | no two lines of a panel overlap, and no line is behind a widget |
| `preview_models.py` | draws the geometry from three angles, for the half no rule can decide |

All four pass with nothing outstanding. Two of them learned something in this pass:

* the density check used to pair a face's u with its *longest world axis*, which is right for a box
  and backwards for the side of a cylinder — there u runs round the tube over a chord a few
  thousandths of a block wide. Every 80-sided prism in the mod therefore read as stretched by a
  factor of two hundred, and the one real case would have been lost among them. It now measures the
  world distance along the face's own u and v, and skips the wedge-shaped quads a cap is fanned into.
* the coplanar check knows that the lamp's six glass bowls are one bowl: they sit in the same place,
  one per glow state, and the renderer draws exactly one of them.

## Rendering with the game itself

`tools/preview_models.py` draws flat-coloured geometry, which answers where things are and not what
they look like. The only thing that can answer the second question is the game, and the reason is
worth stating because it decides what is possible: **almost nothing in this mod is a vanilla JSON
block model.** An array is an OBJ drawn by Java with a matrix per group, groups that appear only once
a reel of cable has been worked in, a tracker angle computed from the sun, and a per-product scale.
Every off-the-shelf renderer reads vanilla JSON models. None of them runs that Java.

So the mod renders itself. `PreviewStage` is a client-only dev tool that builds a stage, steps the
camera round it and calls the same screenshot the F2 key does:

```
/preview electricity:pv_tilt_530 pair
```

`pair` puts a second machine behind the subject and a run of cable in front of it, which is where
half the questions live. Five views come out per machine — front, side, three-quarter, plan, and the
eye level of somebody walking past, which is where anything floating or sunk shows up — into
`run/screenshots/`.

For a whole catalogue unattended, which is what makes it usable from a terminal:

```bash
JAVA_TOOL_OPTIONS="-Delectricity.preview=all" ./gradlew runClient
```

Two things it needs: a world with cheats on (it works by sending commands), and a display. It is a
*client* tool, so a machine whose screen is asleep cannot run it — GLFW has no monitor to open a
window on, and the game will not start at all.

## The two model formats, and which gets which

| | OBJ, drawn by Java | vanilla JSON |
|---|---|---|
| **who** | the turbine, the cabin, the pole, the kiosk, the lamp, the arrays, the inverter, the combiner, the mast | the workbench, the cables |
| **why** | anything with a round part, anything that moves, anything scaled per product | anything that is all boxes |

The lamp moved from JSON to OBJ in this pass and the workbench did not, for the same reason in both
cases. A rotated element in the vanilla format is a **union** rather than an intersection, so three
boxes at 22.5° make a twelve-pointed star and not a twelve-sided prism — there is no way to write a
cylinder in it at all. The lamp's column is round. Nothing on a workbench is: a bench is a frame, a
drawer stack, a plate, a board and a vice, so it stays JSON and keeps what comes free with that —
chunk batching, ambient occlusion, and an inventory model rather than a sprite.

## Regenerating any of it

```bash
python3 tools/gen_block_textures.py     # every block texture
python3 tools/gen_pv_textures.py        # item sprites and GUI panels
python3 tools/gen_pv_models.py          # the arrays, the inverter, the combiner, the mast
python3 tools/gen_grid_models.py        # the pole, the kiosk, the lamp
python3 tools/gen_json_models.py        # the workbench, and it prints the shape its block declares
python3 tools/gen_cable_models.py       # the cables
```

Everything is deterministic — every random-looking value comes from a hash of the coordinates and a
salt — so running a generator twice produces byte-identical files and a rebuild is not a diff.
