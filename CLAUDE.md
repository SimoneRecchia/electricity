# Electricity — how this mod is built

Minecraft 1.20.1, Forge 47.4.0, Java 17. Read this before touching a model, a texture or a hitbox.

The rule underneath all of it: **every placeable object is a real machine, and the way to draw it is to
find out how the real one is made and then draw that.** Not "a box that reads as an inverter" — an
inverter is a sheet-steel cabinet with two doors on piano hinges, roof fans, louvres low on the doors
and a DC compartment down one side, so that is what the model has. When a decision comes down to
taste, go and look up the object; the answer is nearly always in a datasheet or a standard.

## 1. How round a round thing is

Taken from the wind turbine, which is the model everything else was measured against:

| | sides | what gets it |
|---|---|---|
| `ROUND` | **80** | a body a player stands next to: a pole shaft, a mast, a torque tube, a pedestal column, a tower |
| `FITTING` | **32** | anything bolted to one: an insulator's sheds, a bushing, a gland, a boss, a vent stack |
| `HEX` | **6** | a bolt head, because a bolt head really is a hexagon |

In [tools/modellib.py](tools/modellib.py). Never lower one to save faces. Some parts legitimately keep
few sides — an I-beam, a channel, a rolled angle, a cast fin — because the real part is faceted, and
that is a different thing from a cylinder drawn coarsely.

Vanilla JSON models **cannot** express a cylinder: an element with a rotation is a *union*, so three
boxes at 22.5° make a twelve-pointed star, not a twelve-sided prism. Anything with a round body must
therefore be OBJ. Anything that is all boxes may stay JSON and gets chunk batching, ambient occlusion
and an inventory model for free.

## 2. Textures

Written by [tools/gen_block_textures.py](tools/gen_block_textures.py) on top of
[tools/texlib.py](tools/texlib.py). Never hand-paint one; add a function.

**Resolution is chosen by how close a player gets to the face, not by uniformity.**

| px | for |
|---|---|
| 1024 | a module's glass — looked at from a metre |
| 512 | a sheet a player stands in front of: cabinet sides, doors, coats, vents, concrete |
| 256 | profiles, plates, cable jackets, instruments |
| 128 | something a few centimetres across: a switch boss, a cleat, a connector |

**A surface is a material, not a picture of one.** That is the difference between the parts of this mod
that read well and the parts that did not. Build every surface from `texlib`'s value noise over
octaves, soft edges and one light direction (`LIGHT`, shared by the whole mod) — then the material
(`brushed`, `galvanised`, `powder`, `concrete`, `rubber`, `porcelain`, `wood`), then the weathering
(`grime` in the corners, `streak` where rain runs, `rust` where water sits).

**A texture that goes on more than one face must be a pattern, not a bordered picture.** A printed
legend or an end band lands somewhere different on every face it is mapped onto;
`check_model_textures.py` reports it as `ALLSIDES`.

## 3. Circles must not be stretched

The single most common fault. A circle drawn on a square tile and mapped onto a face that is not
square comes out an oval — the status lamp, the lock, the warning triangle, the rings on a cabinet
roof have all been caught by it.

**So map uv off world coordinates, never `(0, 0, 16, 16)` onto everything.** A face 6 px wide and 2 px
tall takes `uv (x0, z0, x1, z1)` from the part's own position, so u and v get the same scale and a
circle stays a circle. `modellib`'s `box(..., uv=)` and `clad_box` and the cable generator's
`plain_faces` all do this; follow them.

The exception is deliberate anisotropy, where the tile has *no features* along the stretched axis — a
cable core's cross-section gradient is constant along the run, so stretching it along the run is free.
Say so in a comment and exempt it explicitly.

## 4. Hitboxes

**Every hitbox is cut from the model it draws, and it is checked mechanically.**

```bash
python3 tools/check_hitboxes.py          # the models and the tables must agree
python3 tools/check_hitboxes.py --java   # prints the tables to paste
```

The rules that came out of doing it:

* **A group per part.** A bounding box is only tight if the group it bounds is one object. Eight
  insulators in one group is a slab through the middle of the pole; split them into `insulator_1 …
  insulator_8`.
* **Slice what slopes.** A rack at 25° is not a box. `check_hitboxes.py` cuts 2-px slabs where the
  slab tops are monotone, so a tilt becomes a staircase and a cone stays one box.
* **A quarter of a pixel is a real part.** The threshold is `MIN_OWN = 0.25` px: a ballasted table is
  2 px tall in total, and a 1-px threshold discarded it entirely.
* **A cable's hitbox is a few pixels, and that is right.** Not the block.
* **Two hitboxes cannot be the drawn shape, and the checker prints why**: a tracked array sweeps a
  volume as it follows the sun, and the turbine model carries its own tower, which in the world is a
  stack of blocks with their own collision.
* Groups whose names begin `pivot_`, `rotate_`, `harness` or `entry` move or are conditional and are
  excluded from the comparison.

## 5. Group and material names are a contract

The renderers read them. Renaming one silently breaks something:

| prefix | meaning |
|---|---|
| `rotate_*` | the renderer turns it |
| `pivot_*` | a zero-size marker the renderer measures a hinge from |
| `harness*`, `entry*` | drawn only when a cable is connected |
| `insulator*` | a wire fitting, **named in `ObjDefinitions`** — and a wire is stored against its *index* in that list, so the order cannot change without moving every wire in every existing world |

## 6. Generate, never hand-edit

Every model, texture, recipe and blockstate in this mod is written by a script in `tools/`. Editing
the output is a change that the next regeneration silently discards. The generators are also where the
reasoning lives — read the module docstring before changing figures.

```bash
python3 tools/gen_block_textures.py     # every block texture
python3 tools/gen_pv_textures.py        # item sprites and panel layouts
python3 tools/gen_pv_models.py          # arrays, inverter, combiner, met mast
python3 tools/gen_grid_models.py        # the pole and the pad-mount kiosk
python3 tools/gen_cab_models.py         # the substation cabin
python3 tools/gen_cable_models.py       # the cables (--java prints the shape tables)
python3 tools/gen_crafting.py           # recipes, part item models, language entries
python3 tools/gen_insulators.py         # patches the one insulator into the machines that carry it
python3 tools/gen_tower_models.py       # the three lattice towers (--java prints the cell tables)
python3 tools/gen_transformer_models.py # the two transformers
python3 tools/gen_conductor_models.py   # the ground-laid line conductors (--java prints the shapes)
```

## 7. What must pass before anything is done

```bash
python3 tools/check_hitboxes.py         # "the models and the tables agree, to a hundredth of a pixel"
python3 tools/check_model_textures.py   # "nothing mechanical left to find"
python3 tools/check_pv_clearance.py     # "no clash possible at any angle"
python3 tools/check_gui_fits.py         # "0 problems"
python3 tools/gen_cable_models.py       # "nothing a run draws touches anything else it draws"
python3 tools/gen_conductor_models.py   # the same, per conductor, and the shapes match the block
python3 tools/gen_tower_models.py       # "LatticeTowerBlock declares the cells this authors"
python3 tools/render_blocks.py          # and then *look* at build/render/
./gradlew build -x test
```

A checker that finds nothing is not evidence the work is right; a checker that has never found
anything is usually not looking. Every one of these was written because something shipped broken.

## 8. Comments are pointers, not essays

A comment is there so the next person knows **where to go and what not to break**. One or two lines.
Name the constraint, the other file that has to agree, or the trap — and stop.

```python
# Forge's loader reads the block's own frame, corner at the origin. Authored centred, every piece comes
# out half a block out. check_inside_block fails the build on it.
```

Not the history of what was tried, not the reasoning that led there, not a paragraph of prose. If it
takes more than two lines to say, it belongs in the module docstring — and a module docstring is the
command to run it, the files it writes, and the two or three figures everything else follows from.

Commit messages are the opposite: that is where the reasoning goes, in full, in prose. Nothing is
"improved" or "enhanced" — say what changed and what it fixes.
