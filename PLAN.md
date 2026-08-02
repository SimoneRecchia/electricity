# Realistic photovoltaics — plan

Branch `feat/realistic-solar-plant`, cut from `main`. Written before the research pass and
revised after it. Everything here is a checklist; each numbered block is one commit, and
nothing is left half done.

---

## 0. Where we start

The mod already has all of this, and the work has to fit *inside* it rather than beside it.
Every integration point below was read, not guessed:

| Piece | File | What it gives us |
|---|---|---|
| Machine datasheet as a record | `api/power/TurbineSpec.java` | Only figures a real datasheet prints are fields; every relation is a method |
| Catalogue registered in a loop | `main/registry/TurbineCatalog.java`, `main/Electricity.java:150` | A spec cannot exist without a block that places it |
| Weather as pure functions | `main/weather/Atmosphere.java` | Stateless, sampled per position and tick |
| Site survey, cached | `main/weather/SiteConditions.java` | Roughness / downfall / biome temperature / exposure per column |
| Snapshot record | `main/weather/WeatherSnapshot.java` | One immutable value per sample |
| Sampler | `main/weather/GlobalWeatherManager.java:91` | `sample(pos, towerBlocks)`; holds no weather state |
| SCADA tag set + kinds | `api/power/TurbineTelemetry.java` | MEASURED / DERIVED / SIMULATED, tag-keyed, rounded to 2 dp |
| Instrument simulator | `power/TurbineTelemetrySimulator.java` | First-order thermal lag off a `Sample` record |
| Peripheral, tag-driven | `compat/computercraft/CCTweakedPeripherals.java` | `IDynamicPeripheral` generates one getter per tag |
| Control panel | `client/screen/WindTurbineScreen.java` | Plain `Screen`, no menu; reads the client's block entity copy |
| Control channel | `main/network/payloads/TurbineControlPayload.java` | `(pos, action, value)`, server-validated + distance-checked |
| OBJ pipeline | `client/render/obj/*` | `o <name>` + `usemtl <mat>` → group `<name>_<mat>`; per-group matrices |
| Renderer pattern | `client/render/block/WindTurbineRenderer.java` | `RenderLevelStageEvent`, `withAlignedPose`, `Map<String,Matrix4f>` poses, `BUFFER_CACHE` keyed by `BlockPos` |
| Wire grid | `main/power/PowerNetwork.java` | Generator → cabin → pole → power box; `canTransfer` gates the topology |
| Wire anchors | `wire/InsulatorPartHelper.java` | Block type string, insulator part names, power direction |
| Forge/Mekanism export | `compat/energy/EnergyBridge.java` | `IEnergyBudget`, 125 J per kW |

The current solar panel is a **placeholder**, and this is the whole of it: one flat block,
one hardcoded 20 kW `PhotovoltaicArray` with no catalogue, a vanilla cube model, three
16×16 PNGs, no wire connection at all (Forge Energy / Mekanism only), no GUI, 8 Lua
methods, no inverter, no strings, no tracker, no sensors, no shading.

Two facts about the existing weather that this work has to *change* rather than build on:

* `Atmosphere.solarElevationSin` returns only an elevation, and its own doc says a panel
  "wants no tilt at all, because the sun comes overhead" — true for a fixed panel, and
  precisely what makes a tracker pointless. A tracker needs an **azimuth**, so the sun
  has to be a direction rather than a height.
* `clearSkyIrradiance` folds the diffuse into a single `DIFFUSE_FACTOR = 1.10`. Shading
  cannot be right on top of that: blocking the beam under a clear sky should cost ~90% and
  under overcast ~10%, and one lumped number cannot express the difference.

## 1. Research (do first, then revise this plan)

- [ ] **Modules.** Real dimensions and datasheet figures for utility and commercial c-Si
      modules: half-cut PERC, TOPCon, HJT, bifacial. Vendors, model designations, and what
      a datasheet actually prints (Pmax, Vmp/Imp, Voc/Isc, NOCT/NMOT, temperature
      coefficients, bifaciality, dimensions, weight, cell count).
- [ ] **Strings and arrays.** Modules in series into strings, strings in parallel into
      combiner boxes, MPPT inputs per inverter, string length limits at 1000/1500 V,
      DC/AC ratio, GCR and row pitch.
- [ ] **Inverters.** String vs central vs micro vs optimiser. Vendors and model families,
      kW ranges, CEC/Euro efficiency, MPPT windows, clipping, night self-consumption,
      cabinet temperatures and derating.
- [ ] **Trackers.** Single-axis (horizontal, tilted, backtracking) and dual-axis. Vendors.
      Rotation range, tracking accuracy, stow angles, wind-stow thresholds, backtracking
      geometry, gain over fixed tilt, motor power, slew rate.
- [ ] **Mounting.** Fixed-tilt racking, torque tube, pier spacing, row pitch.
- [ ] **Sensors.** Pyranometer, albedometer, diffuse (shadow-band/ball) pyranometer, snow
      depth, ambient and module temperature (PT1000 on the back sheet), anemometer and
      vane. Which are internal to the array and which are separate; ISO 9060 classes;
      what a real met station on a PV plant carries; what SCADA tags they publish.
- [ ] **Physics of sunlight on panels.** Beam/diffuse/ground-reflected decomposition
      (Erbs), transposition (isotropic / Hay-Davies / Perez), incidence-angle modifier,
      air-mass spectral correction, soiling, snow cover, low-light behaviour, cell
      temperature models (NOCT vs Faiman vs Sandia), inverter clipping, row-to-row and
      near-object shading, and how tightly neighbouring panels correlate.
- [ ] **Plant SCADA tag names.** The target list, from the screenshot the user supplied:
      Active Energy, Active Power, Ambient Temp, Cabinet Temp, Frequency, Grid Current,
      Grid Voltage, I1/I2/I3, Irradiation, Module Temp, PF, PV Active Power, Reactive
      Power, V1/V2/V3, V12/V23/V31 — plus the met-station bus (`M1.COM3-1..7`): Module
      Temp, Ambient Temp, Irradiation, Diffused Irradiation, Global Irradiation, Wind
      Direction, Wind Speed, Snow Distance, Snow Height.
- [ ] **Trend shape.** Also from the screenshots: a clean bell over the day, power tracking
      irradiance almost exactly, small high-frequency ripple, flat zero at night, ~77.6 kW
      at 976.5 W/m² — so ~79.5 W per W/m², a plant around 80–100 kWp — and the power
      leading irradiance slightly on the morning ramp.

## 2. Naming — the fictional vendors

Follow the turbine convention exactly (`Cube` + rotor diameter, from Vestas): every real
name replaced, every *figure* kept real.

- **Modules** — a maker whose designation carries cell count and watts, the way a
  `JKM580N-72HL4-BDV` or a `TSM-DE21` does.
- **Inverters** — designation carries AC kW, like `SUN2000-100KTL` or `SG110CX`.
- **Trackers** — a name, not a number, like `NX Horizon` or `Terrasmart`.
- **Sensors** — instrument-class designations, like `CMP11`, `SMP10`, `SR30`.

## 3. The datasheet layer (`api/power/`)

- [ ] `PvModuleSpec` — one module: cell technology, cell count, dimensions in metres,
      Pmax, Voc/Isc/Vmp/Imp, temperature coefficients (Pmax, Voc, Isc), NOCT, bifaciality,
      degradation. Derived: area, efficiency, power at a cell temperature and irradiance,
      low-light behaviour, string voltage at temperature.
- [ ] `PvArraySpec` (replaces `PhotovoltaicArray`) — one *block* of array: which module,
      modules per string, strings, mounting (fixed / 1-axis / 2-axis), GCR, tilt, DC
      nameplate. Derived: module count, area, land coverage, DC/AC ratio against an inverter.
- [ ] `InverterSpec` — AC nameplate, DC window, MPPT count and window, peak/Euro/CEC
      efficiency, night self-consumption, nominal voltage and frequency, power-factor
      range, apparent-power ceiling, cabinet thermal limits and derating temperature.
      Derived: efficiency at load as a real curve, clipping.
- [ ] `TrackerSpec` — axis count, rotation limits, tracking rate, stow angles (night /
      wind / snow / maintenance), wind-stow threshold, backtracking, motor power. Derived:
      target angle for a sun position, backtracking angle for a GCR, incidence on the
      tracked plane.
- [ ] `SensorSpec` + `SensorCatalog` — the instruments, with ISO 9060 class, spectral
      range, response time, accuracy.
- [ ] `PvCatalog`, `InverterCatalog`, `TrackerCatalog` — at least six modules (matching the
      six turbines), 4–6 inverters, 3–4 trackers, 5+ instruments.
- [ ] `SolarTelemetry` — the screenshot's tag set with kinds, same shape as `TurbineTelemetry`.
- [ ] The **inverter** is the SCADA node, as on a real plant; the array reports through it.

## 4. Solar physics in the weather model (`main/weather/`)

Rewrite the solar half of `Atmosphere` to be right rather than adequate. The wind half is
untouched.

- [ ] **Sun as a direction.** Keep Minecraft's east-to-west zenith path, but expose azimuth
      as well as elevation, because a tracker needs both.
- [ ] **Three components, not one.** DNI, DHI and GHI as separate quantities with a
      clearness-index split (Erbs), so an overcast sky is nearly all diffuse and a clear one
      nearly all beam. This is what makes shading behave correctly.
- [ ] **Plane-of-array transposition.** Beam by cosine of incidence, diffuse by an
      isotropic-plus-horizon-brightening model, ground-reflected by albedo × GHI × view
      factor. Albedo from the biome (snow ~0.8, sand ~0.35, grass ~0.20, water ~0.06),
      which also gives the albedometer something real to read.
- [ ] **Cloud field.** Keep the two-scale cumulus model; verify its trend shape against the
      screenshots and that two panels 30 blocks apart diverge and rejoin the way the real
      pair do.
- [ ] **Snow on modules.** Depth from the world's snow layers plus a shed model driven by
      tilt and module temperature; zero output under lying snow, which is what the snow
      sensors are for.
- [ ] **Soiling.** A slow climate-dependent loss that washes off in rain.
- [ ] **Shading**, which the user asked for explicitly:
  - [ ] Occlusion by blocks above: opaque ⇒ the beam goes entirely, and the diffuse is cut
        by the fraction of sky hidden; translucent (glass, water, leaves, ice) ⇒ attenuated
        by that block's own light-blocking value.
  - [ ] Entities standing over a panel cast a moving shadow.
  - [ ] Row-to-row shading between adjacent array blocks at low sun — which is what GCR and
        backtracking are *for*.
  - [ ] Correlation between nearby panels: a big field should produce nearly the same
        everywhere, with the cumulus field the only source of divergence.
- [ ] **One `SolarConditions` sample record**: DNI/DHI/GHI, POA beam/diffuse/ground,
      effective POA, sun elevation and azimuth, albedo, clearness index, snow, soiling — so
      an array asks once and gets everything.

## 5. The blocks

- [ ] **PV array block**, one per catalogue entry, mounting baked into the spec:
  - [ ] Fixed-tilt table — walkable, low.
  - [ ] Single-axis tracker — torque tube plus a rotating module plane.
  - [ ] Dual-axis tracker — pedestal, azimuth ring, elevation frame.
- [ ] **Inverter block** — a real cabinet, the SCADA node, the thing that clips, and the
      thing a computer talks to.
- [ ] **Met station block** — a mast carrying the instruments (matches the screenshot's
      seven-instrument bus).
- [ ] Wiring: array → inverter (DC, by proximity), inverter → cabin (the existing wire and
      insulator system), so a PV plant joins the same grid the turbines do.
- [ ] Placement: a tracker needs a clear rotation envelope.

## 6. Models and textures

No Blender here, so the geometry is generated: a Python script (stdlib only — no PIL and no
numpy on this machine, so PNGs are written through `zlib` by hand) emits OBJ + MTL and the
textures, committed alongside the assets so the geometry is reproducible and reviewable.

- [ ] `tools/gen_pv_models.py` — emits `models/pv_*/*.obj` + `.mtl` with the group naming
      the OBJ pipeline needs, and a separate group for every moving part:
  - [ ] fixed table: `frame`, `modules`, `legs`, `insulator`
  - [ ] single-axis: `pier`, `torque_tube`, `rotate_plane` (moving), `motor`, `insulator`
  - [ ] dual-axis: `pedestal`, `rotate_azimuth` (moving), `rotate_elevation` (moving), `insulator`
  - [ ] inverter: `cabinet`, `door`, `display`, `fan` (moving), `insulator`
  - [ ] met mast: `mast`, `boom`, `pyranometer`, `albedometer`, `diffuse`, `snow`,
        `temp_shield`, `rotate_cups` (moving), `rotate_vane` (moving)
- [ ] `tools/gen_pv_textures.py` — procedural PNGs: cell grid with busbars and the half-cut
      split, anodised aluminium frame, galvanised steel, painted cabinet, white instrument
      domes, item icons, GUI backgrounds.
- [ ] Blockstates, block models, item models, loot tables, `mineable/pickaxe`, and lang
      entries for every new block, item and GUI string.

## 7. Renderers

- [ ] `PvArrayRenderer` — per-group matrices like `WindTurbineRenderer`.
- [ ] **Tracker animation**, the hard part: the rotating plane needs its own pivot (the
      torque-tube axis for one axis; azimuth ring plus elevation pivot for two),
      interpolated on the client the way `smoothYaw` does, driven by a server-synced target
      angle. The OBJ buffer cache is keyed by `BlockPos` and rebuilt only when light or
      texture change, so per-frame motion must live in the matrix and never in the vertices.
- [ ] `InverterRenderer` — fan spin proportional to cabinet temperature, display lit when
      producing.
- [ ] `MetStationRenderer` — cups spinning with wind speed, vane pointing downwind.

## 8. GUIs

Modelled on what the real hardware shows: string inverters have a small LCD plus a web UI,
and plant SCADA has a dashboard.

- [ ] **Inverter screen** — the plant dashboard: AC and DC power, today's and lifetime
      energy, per-MPPT string voltage and current, grid V/I/f/PF, cabinet temperature,
      efficiency, clipping indicator, state (standby / MPPT / clipping / derating / fault /
      night), curtailment slider, power-factor setpoint, redstone mode, stop.
- [ ] **Array screen** — the datasheet: module model, count, string layout, DC nameplate,
      POA irradiance, cell temperature, tracker angle and mode, soiling, snow, performance
      ratio, shading indicator; tracker controls (auto / manual / stow).
- [ ] **Met station screen** — the instrument readings, laid out like a met display.
- [ ] Power Wrench opens all three (`PowerWrenchClientHooks`).
- [ ] `SolarControlPayload`, server-validated and distance-checked exactly like the turbine's.

## 9. Peripherals

- [ ] `electricity_pv_inverter` — the full tag set generated from `SolarTelemetry.kinds()`
      through `IDynamicPeripheral`, plus the Mekanism-compatible names and the control
      methods (`stop`, `start`, `setActivePowerLimit`, `setRedstoneMode`, `setPowerFactor`).
- [ ] `electricity_pv_array` — module, string and tracker readings, and tracker control.
- [ ] `electricity_met_station` — the instrument tags.
- [ ] `electricity_solar_panel` keeps answering, so existing programs still work.

## 10. Grid integration

- [ ] The inverter is a generator node in `PowerNetwork`: arrays feed it, it feeds a cabin.
      Distance losses, the surge model and Forge/Mekanism export all apply.
- [ ] `ElectricityServerConfig` — export fraction and ceiling for PV, mirroring the turbines'.

## 11. Docs

- [ ] `docs/photovoltaics.md` — the catalogue, the physics, the tags, the shading rules, the
      tracker modes, written the way `docs/telemetry.md` is.
- [ ] `docs/telemetry.md` — the new peripherals.
- [ ] `README.md` — solar out of alpha, and the new rows in the block table.

## 12. Commits

One per block, in this order, each compiling and each self-contained:

1. `docs: plan the photovoltaic plant` (this file)
2. `feat: a datasheet for modules, inverters, trackers and instruments` (§3)
3. `feat: split the sunlight into beam, diffuse and ground-reflected` (§4 physics)
4. `feat: shade a panel with whatever stands over it` (§4 shading)
5. `feat: build the array out of modules on a mounting` (§5 blocks)
6. `feat: draw the arrays, the inverter and the met mast` (§6 assets, §7 renderers)
7. `feat: turn the tracker to the sun` (§7 animation, tracker control)
8. `feat: an inverter that publishes what a plant's SCADA does` (§3 telemetry, §9)
9. `feat: control panels for the array, the inverter and the mast` (§8)
10. `feat: put a photovoltaic plant on the same grid as the turbines` (§10)
11. `docs: write down the photovoltaic plant` (§11)

Verification after each: `./gradlew compileJava`, and `./gradlew build` at the end.
