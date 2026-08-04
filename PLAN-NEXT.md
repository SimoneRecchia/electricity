# Plan: sprites, hitboxes, a texture audit, and the whole crafting tree

Self-approved on the user's standing instruction to work autonomously. **Every item below gets done -
none is dropped, none is deferred, and this line is the reminder that says so.** Anything I find I
cannot do, I finish everything else and say plainly what is left and why.

Status as of the end of the night: **all seven done.** One of them is written and compiled but could not
be run, and that is called out where it happens rather than at the bottom.

## 1. Turbine inventory sprites — done

Every turbine had the same inherited 128-pixel line drawing, which at inventory size was a smudge, so all
six machines looked identical in a hotbar. Each has its own now, nacelle and rotor and no tower, since the
tower is a separate item with a sprite of its own.

One drawing parameterised, and the parameters carry the catalogue's own story: the nacelle grows with
nameplate, the blades slim as the rotor-to-nameplate ratio rises, the C90 and the C112 share a nacelle
because they share a generator, and the small-wind machine has a tail vane because it has no yaw motor.

## 2. A hitbox for every block that was missing one — done, then done properly

The electric cabin is drawn 1.0 by 2.7 by 2.2 blocks and collided with one cube; the utility pole is six
blocks tall and did the same. An oversized `VoxelShape` does not fix that and it is worth knowing why:
collisions are gathered from the block positions overlapping an entity's own box grown by one, so a shape
three blocks tall hanging off a block three below a player is never consulted.

So `MachineShellBlock` — invisible, solid, never an item, and mining, picking or using any of it reaches
the machine instead. The first version of it filled cells with whole cubes, then with slabs rounded to
eight pixels, and both are wrong the same way: the player collides with air. **The cells now carry the
exact geometry**, cut out of the OBJ by `tools/check_hitboxes.py --java` — the cabin's roof stops at 10.03
pixels because that is where the steel stops, the cells beside its body are filled to 6.34 because that is
how far the body reaches, and the pole's crossarms are three-pixel plates because that is what a crossarm
is. Two thresholds decide what is worth keeping: a pixel inside the machine's own block, where a box is
free, and two pixels to claim a cell from the player, who can no longer build in it.

A cell holds no shape of its own — it holds the way back to its machine, and asks. That is what lets the
model stay the single authority, and it is what mends a world built with an older version: a cell that is
standing but wrong is replaced, not only an empty one.

And then the whole lot was a quarter turn out, which is the second thing a screenshot caught. The cabin's
model is authored facing **east**, not north — an inherited model — and the renderer knew, the wire anchors
knew, the cells did not. So the collision stood at a right angle to the cabin: air a metre from the wall,
and no wall. The power box had the same fault, written out as four hand-rotated boxes. That fact now lives
on the block as `AUTHORED`, the renderer reads it from there, and the checker fails if anything works it
out for itself again.

`tools/check_hitboxes.py` now compares both directions to a hundredth of a pixel — geometry that nothing
collides with, and collision where the model draws nothing — and cross-checks the authored facing against
the renderer and the wire anchors.

Verified against a running server through `tools/rcon.py`: 22 cells holding exactly the right state,
fourteen dropped probes landing on the model's own surfaces to six decimal places (the roof at 92.626875,
the insulator on it at 93.0, nothing at all half a pixel past the eave), all four facings including the
shapes turning inside their cells, a cell belonging to nothing mended within three seconds, an orphan
removing itself, two machines built into each other not fighting over the cell between them, and a cell
blocked by terrain left as terrain.

## 3. Texture and model audit — done

Three real faults, all in `docs/model-audit.md`: a rack's laminates shared a plane with the frame they are
clamped to (a third of a block of flicker), the inverter's cooling grille was buried inside the cabinet
wall with a fan turning in front of nothing, and the combiner's cable riser shared its volume with the hub
at the foot of the post.

`tools/check_model_textures.py` now finds all four classes mechanically — coplanar faces that both show,
geometry inside other geometry, bordered pictures sub-sampled, pictures worn on all six faces of a box —
and had to be taught two things that are *not* faults: a glass dome's side is meant to take the middle of
its texture, and a cylinder cap's quads each take a wedge of the whole thing by design.

Resolution: every drawing unchanged, every structural feature in it multiplied by `DETAIL`. Modules at
256, the sheet metals at 128, end caps at 64. The per-pixel grain deliberately does not scale, because
grain that stays one pixel across is what turns a speckled tile into smooth metal at four times the size.
Geometry: prisms are twelve sides by default and sixteen for the three a player walks up to.

## 4. The crafting tree — done

Thirty-two new parts in three tiers, and everything in the mod is craftable at a vanilla bench or in a
furnace. What gates a power station is the depth of the tree rather than a table: a player who wants a
tracker works down to iron and back up.

The spine rule holds: every array is three laminates over a mounting, every inverter is bridges over an
enclosure, every combiner is fuse ways over an enclosure, every turbine is three blades over a drivetrain
— and two models differ only where the real machines do. `tools/gen_crafting.py` is the whole tree in one
table and refuses to write it if two recipes share a grid, if a part has no recipe, or if a part is made
and never used.

## 5. The coherence pass — done

`docs/items.md`: two power chains, what every item is for, what makes it, what it connects to. The last
section is the one worth reading — six gaps, none of them a bug, from an inverter whose AC terminals are
not drawn anywhere on the cabinet to the fact that nothing consumes the met station's readings but its own
panel.

## 6. Dead code — done

Three imports nothing used, and one real piece of duplication: eight renderers each carried their own
four-case switch turning a facing into an angle, seven of them the same three lines in a different order.
What differs between them is one fact — which way the geometry was authored facing — so that fact is the
argument now and the arithmetic lives once. The utility pole keeps its switch and says why: its model is
mirrored rather than merely turned, and a shared helper with an exception in it would be worse.

## 7. A real previewer — done, and untested

Nothing off the shelf can do it, and the reason decides the answer: almost nothing here is a vanilla JSON
block model, and every existing renderer reads vanilla JSON. So the mod renders itself — `PreviewStage`
builds a stage, steps a camera through five views and calls the same screenshot the F2 key does, either
from `/preview <block> [pair]` or unattended from `-Delectricity.preview=all`.

**It has not been run.** It compiles, the dedicated server starts with it on the classpath, and the client
cannot be launched on this machine at the moment: the display is asleep, GLFW reports no primary monitor,
and the game exits before Forge loads. The first `/preview` is the test.
