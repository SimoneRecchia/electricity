# Plan: sprites, hitboxes, a texture audit, and the whole crafting tree

Self-approved on the user's standing instruction to work autonomously. **Every item below gets done -
none is dropped, none is deferred, and this line is the reminder that says so.** Anything I find I
cannot do, I finish everything else and say plainly what is left and why.

The order is chosen so the cheap, self-contained work lands first and the crafting tree - which is the
largest item by far - is built on top of a codebase that has already been audited and cleaned.

## 1. Turbine inventory sprites

Every turbine gets its own 16x16 sprite, and it is the **nacelle and rotor only** - no tower, because
`turbine_tower` already has its own sprite and a turbine item that repeated it would read as the same
object twice in the hotbar. The 3D models stay as they are.

- One sprite per product in `TurbineCatalog`, drawn from that machine's own proportions: rotor
  diameter against hub height, three blades, the nacelle's own shape.
- Small turbines read differently from large ones in reality - a 5 kW machine is a bare nacelle with a
  tail vane, a 15 MW machine is a bus-sized housing - so the sprites differ the same way rather than
  being one drawing scaled.
- Written into `tools/gen_pv_textures.py`'s sprite family (`sprite()`, `limb()`, the palettes), so they
  are reproducible in a diff like everything else.

## 2. A hitbox for every block that is missing one

The cabinet, the combiner, the met mast, the turbine tower and anything else whose OBJ stands taller or
wider than the block it occupies: the model is a metre of machine and the collision is one cube, so a
player walks through most of it.

- Audit: for every block with an OBJ definition, compare the model's own bounding box against the
  block's `getShape`/`getCollisionShape`. A script, so it cannot go stale - `tools/check_hitboxes.py`.
- Give each one a shape built from the model's real extents, in the block's four facings.
- Multi-block-tall machines need the blocks above them to collide too, or the shape has to be capped at
  the block boundary and the rest given to a companion. Decide per machine, and say which in the doc.

## 3. Texture and model audit of everything we added since the fork

Only ours: the PV family, the DC cabling, the met mast, the combiner, the cabinet, the electric cab,
the power box, the utility pole. Not the turbines' own inherited assets.

Three classes of defect to hunt, all of which have already bitten once:

- **Off-centre or sub-sampled pictures.** A bordered texture drawn onto a face with `uv_scale` under
  one takes a slice out of the middle of the frame. The rule that came out of it: a texture with a
  frame needs a face that gives it the whole image, otherwise draw it as a repeating pattern.
- **A picture repeated on faces that want their own.** The pair texture on the side of a cable, the
  cell pattern on a module's edge, a lid's bolt grid on a face one pixel tall.
- **Overlaps that read badly**: two coplanar exposed faces (which flicker), and solids sharing a volume
  where the seam shows.

Then, per the user's own words: anything not up to standard gets **redrawn at a higher resolution**,
and surfaces get **more polygons** where roundness is what is missing - the turbine nacelle and the
cabinet are the two he named as the standard to match, so a cylinder at eight sides becomes twelve or
sixteen where it is close enough to be seen.

Deliverables: `tools/check_model_textures.py` for the mechanical part of it, the previewer
(`tools/preview_models.py`, added with the cabling work) for the part only eyes can do, and a written
list of what was found and what was done about each.

## 4. Crafting for every item, researched against how these are really made

The largest item. It gets its own research pass first, then a tree.

**Research** (what a real supply chain looks like, so the recipes are not invented):
- A PV module: polysilicon to ingot to wafer to cell to laminate - glass, EVA, backsheet, an aluminium
  frame, a junction box with bypass diodes, MC4 connectors.
- An inverter: IGBT or SiC power modules, DC-link capacitors, magnetics (chokes, transformers), a
  control board, a DC section with fuses and a switch, a sheet-steel enclosure with a fan.
- A turbine: cast hub, pitch bearings and drives, blades (glass or carbon fibre in epoxy over a spar),
  main shaft and bearing, gearbox or a direct-drive permanent-magnet generator, a yaw drive and
  bearing, a converter cabinet, a tower section rolled from plate and flanged.
- A combiner box: gPV fuse holders, a DC load-break switch, a Type 2 surge arrester, busbar, an IP66
  enclosure, glands.
- The met station: pyranometers, an anemometer and vane, an RTD, a data logger, a mast.
- Cable: tinned copper conductor, XLPO insulation, a jacket; a trunk adds armour.

**Intermediates** - the "pezzi di supporto" the user asked for, not placeable and not usable, only
inputs to other recipes. Grouped in tiers so a turbine is genuinely a long road:
- Tier 1, from vanilla: copper wire, steel plate, steel section, insulator, resin, glass sheet.
- Tier 2: printed circuit board, power semiconductor module, capacitor bank, magnetic core, bearing,
  gear set, servo drive, sensor head, solar cell, tempered glass pane.
- Tier 3: control board, converter module, generator stator, generator rotor, gearbox, pitch assembly,
  yaw assembly, blade, laminate, DC section, enclosure.

**The rule the user set, and it decides the shape of the whole tree**: recipes for different products
of the same *kind* stay close to each other, and differ only where the real machines differ. So every
inverter shares a spine and the central machine adds busbars and a bigger enclosure; every array
shares a laminate and a mounting and the tracked ones add a drive; every turbine shares nacelle,
generator and blades and the direct-drive ones swap the gearbox for a bigger stator.

Every intermediate needs its **own sprite**, drawn in the same family as the rest.

## 5. A coherence pass over every item, written down

A `docs/items.md` with a diagram of everything a player can do: what each item is, what makes it, what
it goes into, what it connects to, and what reads it in the physics. Written to answer the four
questions asked - does everything work, does everything make sense, is everything usable, does
everything match reality and connect the right way - and to surface what is *missing*, which is the
real reason for writing it.

## 6. Code review: delete what is not earning its place

Dead code, superseded helpers, fields nothing reads, comments describing something that no longer
exists. The mod has been through several redesigns in this branch alone - the radius scan that the DC
walk replaced, the harness geometry that three rounds of feedback rewrote - and each one leaves
sediment.

## 7. Then, and only then: a proper model previewer

The user's closing question. What exists now (`tools/preview_models.py`) is flat-coloured SVG with no
textures and a painter's sort - useful, and not the same as seeing it in game.

- Search first for something already built: a headless Minecraft block/model renderer, an MCP server
  that renders MC assets, or one of the resource-pack model viewers.
- If nothing fits: the honest options are (a) render the OBJ *with* its textures and a real depth
  buffer, which catches z-fighting and mapping errors and needs no game, or (b) drive the game itself
  from a dev mod - a command that places one block, points the camera at it from a list of angles and
  writes screenshots, which is the only way to see exactly what a player sees, because it *is* the
  game's renderer.
- Report which, why, and what it costs before building it.
