# Realistic photovoltaics — plan

Branch `feat/realistic-solar-plant`, cut from `main`. §0–§2 were written before the research
pass; everything from §R on is the research and the design it forces. Each numbered block in
§12 is one commit, and nothing is left half done.

---

## 0. Where we start

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
| OBJ pipeline | `client/render/obj/*` | `o <name>` + `usemtl <mat>` → group `<name>_<mat>`; `map_Kd x.png` → `textures/block/x.png` |
| Renderer pattern | `client/render/block/WindTurbineRenderer.java` | `RenderLevelStageEvent`, `withAlignedPose`, `Map<String,Matrix4f>` poses, buffer cache keyed by `BlockPos` |
| Wire grid | `main/power/PowerNetwork.java` | `canTransfer` gates the topology; generator nodes are recognised by class |
| Wire anchors | `wire/InsulatorPartHelper.java` | Block type string, insulator part names, power direction |
| Forge/Mekanism export | `compat/energy/EnergyBridge.java` | `IEnergyBudget`, 125 J per kW |

The current solar panel is a **placeholder**, and this is the whole of it: one flat block, one
hardcoded 20 kW `PhotovoltaicArray` with no catalogue, a vanilla cube model, three 16×16 PNGs,
no wire connection at all (Forge Energy / Mekanism only), no GUI, 8 Lua methods, no inverter,
no strings, no tracker, no sensors, no shading.

Two facts about the existing weather that this work has to **change** rather than build on:

* `Atmosphere.solarElevationSin` returns only an elevation. A tracker needs a direction.
* `clearSkyIrradiance` folds the diffuse into a single `DIFFUSE_FACTOR = 1.10`. Shading cannot
  be right on top of that: blocking the beam under a clear sky should cost ~90% and under
  overcast ~10%, and one lumped number cannot express the difference.

## 1. Research — done, findings in §R

## 2. Naming — the fictional vendors

The turbine convention exactly (`Cube` + rotor diameter, from Vestas): every real name
replaced, every *figure* kept real.

* **Modules** — `Helio` + cell count + watts, the way a `JKM580N-72HL4` does.
* **Inverters** — `Helio` + AC kW + family letter, the way an `SG110CX` or `SUN2000-100KTL` does.
* **Trackers** — a name, not a number, like `NX Horizon` or `DuraTrack`.
* **Instruments** — instrument-class designations, like `CMP11`, `SMP10`, `SR30`, `SR50A`.

---

## R. Research findings — the figures this is built on

### R.1 Modules — six real datasheets, one per turbine in the catalogue

All six are real products with real numbers, renamed. Efficiency is Pmax / area, so it is
derived rather than declared.

| # | Drawn from | W | mm | m² | kg | Cells | Vmp | Imp | Voc | Isc | η | γPmax %/K | βVoc | αIsc | NOCT | Bifacial |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Jinko Tiger Neo 72HL4-BDV | 580 | 2278×1134×35 | 2.583 | 32.0 | 144 half TOPCon | 43.52 | 13.33 | 51.19 | 14.06 | 22.5% | −0.29 | −0.25 | +0.045 | 45 | 0.80 |
| 2 | Trina Vertex N NEG21C.20 | 700 | 2384×1303×33 | 3.106 | 38.3 | 132 half G12 TOPCon | 39.6 | 17.68 | 46.7 | 18.75 | 22.5% | −0.29 | −0.25 | +0.045 | 43 | 0.80 |
| 3 | 108-cell PERC mono | 415 | 1722×1134×30 | 1.953 | 21.5 | 108 half PERC | 31.4 | 13.22 | 37.6 | 13.93 | 21.3% | −0.34 | −0.27 | +0.048 | 45 | 0.70 |
| 4 | REC Alpha Pure HJT | 430 | 1730×1118×30 | 1.934 | 21.5 | 132 half HJT | 33.0 | 13.03 | 39.5 | 13.85 | 22.2% | −0.24 | −0.24 | +0.040 | 44 | — |
| 5 | Maxeon 6 IBC | 440 | 1872×1032×40 | 1.932 | 22.5 | 66 IBC | 67.0 | 6.57 | 80.5 | 6.98 | 22.8% | −0.27 | −0.235 | +0.050 | 43 | — |
| 6 | First Solar Series 7 CdTe | 530 | 2300×1216×32 | 2.797 | 39.7 | 268 CdTe | 183.0 | 2.90 | 223.0 | 3.19 | 18.9% | −0.32 | −0.28 | +0.040 | 45 | — |

What the spread is *for*, and every one of these is a real behavioural difference:

* **γPmax** decides the desert argument. HJT at −0.24 %/K keeps a fifth more of its rating at
  75 °C than PERC at −0.34.
* **Voltage per module** decides string length. An IBC module at 80.5 V Voc takes 17 in series
  at 1500 V where a 51 V TOPCon takes 27 — so the same DC nameplate arrives as very different
  string currents, and the telemetry shows it.
* **CdTe** is the outlier on purpose: low efficiency, high voltage, tiny current, the best
  temperature coefficient of the thin films, better low-light and better spectral response in
  humid air. It should win where the others lose.
* Max system voltage 1500 V (1000 V on the residential module), series fuse 25–30 A,
  −40…+85 °C operating range, 16 busbars.

### R.2 Strings, arrays, ground cover

* Modules in series into a **string**; strings in parallel into an **MPPT input** or a
  **combiner box** aggregating 16–32 strings.
* 1500 V systems run **28–32 modules per string**; 1000 V systems 18–22. The limit is Voc at
  the coldest expected temperature, which is why βVoc is on the datasheet.
* **GCR** = module row width / row pitch. Real ranges: fixed tilt 0.40–0.60, horizontal
  single-axis **0.28–0.50** (NX Horizon's own published range), dual-axis 0.20–0.30, and
  rooftop/flat ballast up to 0.85–0.95.
* **ILR / DC-AC ratio** 1.30–1.55 for utility plant, optimum near 1.35–1.40, with 0–3 % annual
  clipping loss treated as acceptable.

### R.3 Inverters — four real families

| # | Drawn from | AC kW | MPPTs | MPPT window | Max DC V | Startup V | ηmax | ηeuro | Night W | Cooling |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Huawei SUN2000-10KTL-M1 | 10 | 2 | 140–980 | 1100 | 200 | 98.6 % | 98.3 % | 1 | natural |
| 2 | Sungrow SG110CX | 110 | 9 | 200–1000 | 1100 | 200 | 98.7 % | 98.4 % | 2 | fan |
| 3 | Sungrow SG350HX | 352 | 16 | 500–1500 | 1500 | 550 | 99.0 % | 98.7 % | 3 | fan |
| 4 | SMA Sunny Central 2500-EV | 2500 | 1 | 875–1325 | 1500 | 875 | 98.9 % | 98.6 % | 100 | forced air |

* Nominal AC 400 V / 800 V line-to-line, 50 Hz, power factor 0.8 leading to 0.8 lagging,
  apparent power ≈ 1.1 × active nameplate.
* **Full power to ~45–50 °C ambient, derating above** — so the cabinet temperature in the
  telemetry has to actually do something.
* Night self-consumption is why a plant reads slightly *negative* at night on a real meter.
  A microinverter's is 50 mW; a central inverter's is 100 W.
* Efficiency curve shape: steeply up to ~15 % load, flat and high from 20 % to 100 %. That is
  what the existing `INVERTER_KNEE = 0.017` was groping at and it is the right shape.

### R.4 Trackers — three real families, and one hard fact about this world

| # | Drawn from | Axes | Range | GCR | Slew | Stow | Motor |
|---|---|---|---|---|---|---|---|
| 1 | Nextracker NX Horizon | 1, independent row, self-powered | ±60° | 0.28–0.50 | 4.4 °/min | wind, night, snow | 24 V DC slew drive |
| 2 | Array Tech DuraTrack HZ v3 | 1, mechanically ganged | ±52° | 0.33–0.50 | 4 °/min | wind | one drive per ~200 kW |
| 3 | azimuth-elevation dual axis | 2 | ±135° az, 10–80° el | 0.20–0.30 | 3 °/min | wind, snow | 5000 Nm slew, ~3 kWh/yr |

* Wind stow above ~20 m/s; tested to 200 km/h stowed; self-powered controllers stow on battery
  so a grid outage cannot leave a row exposed.
* **Backtracking.** Derived, and the derivation matters because it is the one place the mod's
  geometry has to be exactly right. A row of width `L` normal to the sun intercepts a beam of
  width `L` and casts a ground shadow `L/sin α` wide, so shading starts when `sin α < GCR`.
  Below that, holding the shadow to exactly the pitch needs
  `cos(R_true − R) = sin α / GCR`, and with true tracking at `R_true = 90° − α`:

      R_backtrack = (90° − α) − arccos( sin α / GCR )

  which is continuous at `sin α = GCR` and lies flat at sunrise. Matches the published
  `θ_t = θ_s − arccos(cos θ_s / GCR)` with `θ_s` the zenith angle.

* **The hard fact.** Minecraft's sun rises due east, passes through the zenith and sets due
  west: the world has no axial tilt and no latitude, so the sun stays in one vertical plane all
  day. A north-south horizontal single axis therefore tracks it **perfectly**, and a second
  axis has no azimuth travel left to earn anything. Two real behaviours save the dual-axis
  machine from being decoration, and both are what real controllers do:
  1. **Rotation limits.** A ±60° single-axis row cannot face a sun below 30° elevation and
     spills the rest; a dual-axis frame tilting to 80° reaches down to 10°. So the dual-axis
     machine collects the morning and evening the single-axis one throws away.
  2. **Diffuse mode.** Under a heavily overcast sky the brightest thing is the whole dome, not
     the sun, so a real dual-axis controller goes flat to maximise sky view factor. A
     single-axis row cannot.
  Plus snow stow (steep, to shed) and wind stow (flat). This is stated in the docs rather than
  hidden, because it is the sort of thing a player will work out and be annoyed by otherwise.

### R.5 Instruments — what a real PV met station carries, and where each one lives

The research answers the user's question directly: **module temperature is internal to the
array** (a PT1000 taped to the back sheet of a representative module), and **everything else is
external**, on a met mast, one or two per plant per IEC 61724.

| Instrument | Drawn from | Reads | Real specification |
|---|---|---|---|
| Pyranometer | Kipp & Zonen CMP11 / SMP10 | GHI, and POA when tilted with the array | ISO 9060 Secondary Standard / Class A, 285–2800 nm, 7–14 µV/W/m², <5 s to 95 % (1.66 s to 63 %), non-linearity <0.2 %, temperature dependence <1 % over −10…+40 °C, directional error <10 W/m², range 0–4000 W/m² |
| Albedometer | Kipp & Zonen CMA11 | ground albedo | two Class A pyranometers back to back, one up one down |
| Diffuse pyranometer | CMP11 + CM121 shadow ring | DHI | shadow ring occludes the solar disc; needs a seasonal adjustment on a real one |
| Reference cell | c-Si reference device | effective POA | same spectral response as the array, so it tracks module current better than a pyranometer and reads *lower* at low sun |
| Back-of-module temperature | PT1000 Class A RTD | module temperature | ±0.15 °C at 0 °C, taped mid-cell to the back sheet |
| Snow depth | Campbell SR50A | snow distance, snow height | ultrasonic, 0.5–10 m, ±1 cm — which is exactly why the screenshot has **two** tags: the sensor measures *distance to the surface* and the height is derived from it |
| Anemometer + vane | cup + vane | wind speed, wind direction | 0–75 m/s ±0.3 m/s; 0–360° ±3° |

Seven instruments on one bus, which is what the screenshot's `M1.COM3-1..7` layout is.

### R.6 The physics, with the correlations named

* **Clearness index** `kt = GHI / (E0 · sin α)`, `E0` the extraterrestrial normal irradiance.
* **Erbs** diffuse fraction `Kd = DHI/GHI` — `kt ≤ 0.22`: `1 − 0.09 kt`; `0.22 < kt ≤ 0.80`:
  `0.9511 − 0.1604 kt + 4.388 kt² − 16.638 kt³ + 12.336 kt⁴`; `kt > 0.80`: `0.165`.
  **Researched and then rejected**, and the reason is worth recording: Erbs is for when GHI is the
  only measurement there is, and here the cloud field is *known*. Feeding a statistical fit a total
  in order to have it guess back what we already have would throw information away and pay for it —
  Erbs under-predicts clear-sky DNI by about a tenth, which is exactly the quantity a tracker's
  whole value rests on. So instead: **clear-sky beam** from Meinel & Meinel, **clear-sky diffuse**
  from Liu & Jordan (`0.271·E0·sinα − 0.294·DNI·sinα`), then cloud attenuates the beam linearly in
  cover (cover *is* the probability the sun's own patch of sky is covered) and the global by Kasten
  & Czeplak, with the diffuse as the remainder. That keeps `GHI = DNI·sinα + DHI` exact by
  construction and lands the clear zenith sun on DNI 953 / DHI 89 / GHI 1041 W/m².
* **Transposition, Hay-Davies with the Reindl horizon term.**
  `POA_sky = DHI · [ Ai·Rb + (1−Ai)·(1+cos β)/2 · (1 + f·sin³(β/2)) ]`, `Ai = DNI/E0`,
  `Rb = cos AOI / cos Z`, `f = √(GHI_beam/GHI)`.
  **Ground reflected** `= albedo · GHI · (1 − cos β)/2`.
* **IAM, Martin & Ruiz**: `(1 − e^{−cos AOI / ar}) / (1 − e^{−1/ar})`, `ar = 0.16` for glass
  (range 0.08–0.25). Costs ~2 % at 60° and ~30 % at 80°, and is why a tracker's *flat* output
  curve is not quite flat.
* **Cell temperature, Faiman** — adopted by IEC 61853, and better than NOCT because wind
  enters: `Tm = Ta + POA/(u0 + u1·WS)`, `u0 = 25.0`, `u1 = 6.84 W/m²K`. NOCT stays as the
  datasheet figure the module is *sold* on, and the two agree at 800 W/m², 20 °C, 1 m/s.
* **Soiling, Kimber**: 0.1–0.3 %/day accumulation, rain over ~6 mm/day resets to clean, then a
  two-week grace period.
* **Snow**: 5–12 % annual loss for elevated unobstructed modules in a snowy climate, least at
  the steepest tilt; below ~30° tilt a module can stay covered for a whole season; winter-month
  reductions of 70 % at 23° and 40 % at 40° are on record.
* **Albedo**: fresh snow 0.80–0.90, sand 0.30–0.40, concrete 0.30–0.40, grass 0.20–0.25,
  water 0.06–0.10, white membrane 0.60–0.70.
* **Bifacial gain** ≈ bifaciality × albedo × view factor; view factor 0.10–0.20 fixed tilt and
  higher on an elevated tracker. Each +0.1 of albedo is worth 3–5 % of rear generation.
  Bifaciality 0.80–0.90 for TOPCon/HJT, 0.65–0.75 for PERC.
* **Low light**: relative efficiency at 200 W/m² is ~97–98 % of STC for a good module and falls
  away below 100 W/m² — a shunt-resistance effect, not a rounding error.
* **Cloud transmittance** stays Kasten & Czeplak, which the mod already has and which is right.

### R.7 The trend the user supplied, as a calibration target

77.6 kW at 976.5 W/m² is 79.5 W per W/m², so `P_dc · PR = 79.5 kW` — at a performance ratio of
0.82 that is a **97 kWp plant on a ~90 kW inverter**, ILR ≈ 1.08. The shape to reproduce: a
clean bell, power tracking irradiance almost exactly, small high-frequency ripple, flat zero at
night, and power leading irradiance slightly on the morning ramp because the modules are still
cold. All four fall out of the model above rather than needing to be drawn.

---

## 3. The datasheet layer (`api/power/`) — commit 2

- [x] `PvModuleSpec` — record: id, designation, technology, cell count, width/height/depth m,
      Pmax, Vmp/Imp/Voc/Isc, γPmax/βVoc/αIsc, NOCT, bifaciality, max system volts, fuse.
      Derived: `areaM2`, `efficiency`, `powerAt(poa, cellTemp)` with the low-light term,
      `vocAt(cellTemp)`, `maxSeriesModules(coldestC)`.
- [x] `PvMounting` enum — `FLAT`, `FIXED_TILT`, `SINGLE_AXIS`, `DUAL_AXIS`, each carrying
      whether it tracks, its default GCR band and its snow-shed behaviour.
- [x] `PvArraySpec` — record: id, designation, module, modules per string, strings, mounting,
      GCR, fixed tilt, tracker (nullable). Derived: module count, module area, DC nameplate,
      land coverage, string voltage and current, `poaTilt(sunElevation, …)`.
- [x] `InverterSpec` — record: id, designation, AC kW, MPPT count, MPPT window, max DC volts
      and power, startup volts, ηmax, ηeuro, night watts, nominal AC volts, frequency, PF
      range, apparent ceiling, derate-onset ambient, max ambient. Derived: efficiency at load
      (a real curve, calibrated so it passes through ηmax and ηeuro), clipping, `maxDcKw`.
- [x] `TrackerSpec` — record: id, designation, axes, rotation limit, elevation range, slew
      °/min, stow angles (night/wind/snow), wind stow threshold, backtracking flag, motor W.
      Derived: `trueAngle`, `backtrackAngle(gcr, elevation)`, `targetAngle(conditions)`.
- [x] `SensorSpec` + `SensorCatalog` — the seven instruments with their real classes,
      spectral ranges, response times and accuracies.
- [x] `PvCatalog`, `InverterCatalog`, `TrackerCatalog`.
- [x] `SolarTelemetry` — the screenshot's tag set with kinds, same shape as `TurbineTelemetry`.

## 4. Solar physics in the weather model (`main/weather/`) — commits 3 and 4

- [x] **Sun as a direction.** `SunPosition` record: elevation sine, azimuth degrees. Minecraft's
      sun is due east before noon and due west after, so the azimuth is honest about being a
      two-valued thing rather than pretending to sweep.
- [x] **Three components.** `SkyConditions` record: DNI, DHI, GHI, extraterrestrial normal, air
      mass, albedo, cloud cover. Split as in R.6 — physical, not Erbs.
- [x] **Transposition.** `Atmosphere.planeOfArray(...)` → beam / sky-diffuse / ground-reflected,
      Hay-Davies + Reindl + albedo view factor, with the Martin & Ruiz IAM on the beam.
- [x] **Albedo from the biome.** In `SiteConditions`, from the same tags the roughness uses,
      plus snow cover: snow 0.85, sand/beach 0.35, badlands 0.30, grass 0.22, water 0.07.
- [x] **Snow on modules.** Depth from the world's snow layers over the array, shed above a tilt
      and module temperature threshold.
- [x] **Soiling.** Kimber: accumulate per day from the biome's dryness, reset on rain.
- [x] **Shading** (commit 4):
  - [x] Blocks above: opaque ⇒ the whole beam goes and the diffuse is cut by the fraction of the
        dome hidden; translucent ⇒ attenuated by that block's own light-blocking value.
  - [x] Entities over the array cast a moving shadow.
  - [x] Row-to-row shading between adjacent array blocks at low sun, which is what backtracking
        exists to avoid and which therefore has to be modelled for backtracking to mean anything.
  - [x] Nearby arrays stay correlated: only the cumulus field separates them.
- [x] **`SolarConditions`** — one record with everything, so an array asks once.

## 5. The blocks — commit 5

Six array blocks, four inverters, one met mast. The flat array keeps the registry name
`solar_panel`, exactly as the C130 keeps `wind_turbine`, so existing worlds load unchanged.

| Registry name | Product | Mounting | Module | GCR | Modules | DC kW |
|---|---|---|---|---|---|---|
| `solar_panel` | flat ballasted table | FLAT | 580 W TOPCon bifacial | 0.85 | 34 | 19.7 |
| `pv_tilt_580` | fixed tilt 25° | FIXED_TILT | 580 W TOPCon bifacial | 0.45 | 18 | 10.4 |
| `pv_tilt_530` | fixed tilt 25°, thin film | FIXED_TILT | 530 W CdTe | 0.45 | 17 | 9.0 |
| `pv_track_700` | horizontal single-axis | SINGLE_AXIS | 700 W G12 bifacial | 0.40 | 13 | 9.1 |
| `pv_track_580` | horizontal single-axis, ganged | SINGLE_AXIS | 580 W TOPCon bifacial | 0.45 | 18 | 10.4 |
| `pv_dual_440` | azimuth-elevation dual axis | DUAL_AXIS | 440 W IBC | 0.25 | 13 | 5.7 |

One block is 100 m² of ground at the mod's ten metres to the block, so the module count is
`GCR × 100 / module area` and the DC nameplate follows — which is why the flat array lands back
on the 19.7 kW the placeholder asserted, and why the trackers carry less nameplate per block
and earn it back in daily energy and in a flat output curve. The trade is real and gets said
out loud in the docs.

- [x] **DC coupling.** An array produces nothing without an inverter in range: the inverter
      scans a configurable radius (default 16 blocks) on a cached interval and claims arrays,
      first come first served up to its own max DC power. Over-subscription is not an error, it
      is **clipping**, which is what an ILR over 1.0 buys and what the trend screenshots show.
- [x] **Inverter → cabin** over the existing wire system: the inverter carries one insulator, is
      a generator node in `PowerNetwork`, and `canTransfer` lets it reach a cabin.
- [x] Met mast: standalone, publishes instrument readings, needs no power.

## 6. Models and textures — commit 6

Generated, not authored by hand: `tools/gen_pv_models.py` and `tools/gen_pv_textures.py`,
stdlib only (no PIL and no numpy here, so PNGs go out through `zlib` by hand), committed beside
the assets so the geometry is reproducible and reviewable.

- [x] Groups, with every moving part separate so the renderer can drive it by matrix:
  * flat / fixed tilt: `frame`, `modules`, `legs`, `insulator`
  * single axis: `pier`, `rotate_tube`, `rotate_modules`, `motor`, `insulator`
  * dual axis: `pedestal`, `rotate_azimuth`, `rotate_elevation`, `insulator`
  * inverter: `cabinet`, `door`, `display`, `rotate_fan`, `insulator`
  * met mast: `mast`, `boom`, `pyranometer`, `albedometer`, `diffuse`, `snow`, `shield`,
    `rotate_cups`, `rotate_vane`, `insulator`
- [x] Textures: cell grid with busbars and the half-cut split, anodised frame, galvanised steel,
      painted cabinet, white instrument domes, item icons, three GUI backgrounds.
- [x] Blockstates, block models, item models, loot tables, `mineable/pickaxe`, lang.

## 7. Renderers — commits 6 and 7

- [x] `PvArrayRenderer` — per-group matrices; the tracked groups rotate about the torque-tube
      axis (one axis) or the pedestal axis then the elevation pivot (two axes).
- [x] Client-side smoothing of the tracker angle towards the server's target, the way
      `smoothYaw` does for a nacelle. Per-frame motion lives in the matrix and never in the
      vertices, because the buffer cache is keyed by `BlockPos` and only rebuilt on light or
      texture change.
- [x] `InverterRenderer` — fan speed from cabinet temperature, display lit when producing.
- [x] `MetStationRenderer` — cups spinning with wind speed, vane pointing downwind.

## 8. GUIs — commit 9

- [x] **Inverter screen** — the plant dashboard: AC and DC power with the clipping gap visible,
      today's and lifetime energy, per-MPPT string voltage and current, grid V/I/f/PF, cabinet
      temperature against the derate onset, efficiency, state (night / starting / MPPT /
      clipping / derating / curtailed / stopped / fault), curtailment slider, power-factor
      setpoint, redstone mode, stop.
- [x] **Array screen** — the datasheet: module designation and count, string layout, DC
      nameplate, POA broken into beam / diffuse / ground, cell temperature, tracker angle and
      mode, soiling, snow, shading, performance ratio; tracker controls auto / manual / stow.
- [x] **Met station screen** — the seven instruments laid out like a met display.
- [x] Power Wrench opens all three; `SolarControlPayload` validated and distance-checked.

## 9. Peripherals — commit 8

- [x] `electricity_pv_inverter` — the full tag set generated from `SolarTelemetry.kinds()`, the
      Mekanism-compatible names, and the controls.
- [x] `electricity_pv_array` — module, string and tracker readings plus tracker control.
- [x] `electricity_met_station` — the instrument tags.
- [x] `electricity_solar_panel` keeps answering so existing programs still work.

## 10. Grid integration — commit 10

- [x] Inverter is a generator node in `PowerNetwork`; distance losses, the surge model and
      Forge/Mekanism export all apply.
- [x] `ElectricityServerConfig` — PV export fraction and ceiling, and the DC search radius.

## 11. Docs — commit 11

- [x] `docs/photovoltaics.md`, `docs/telemetry.md`, `README.md`.

## 12. Commits — all landed

1. `docs: plan the photovoltaic plant` ✔
2. `docs: revise the plan against the research` ✔
3. `feat: a datasheet for modules, inverters, trackers and instruments` ✔ (§3)
4. `feat: split the sunlight into beam, diffuse and ground-reflected` ✔ (§4)
5. `feat: shade a panel with whatever stands over it` ✔ (§4 shading)
6. `feat: build the array out of modules on a mounting` ✔ (§5)
7. `feat: draw the arrays, the inverter and the met mast` ✔ (§6, §7 — the planned
   separate tracker-animation commit folded in here, because the pivots, the moving
   groups and the client interpolation are one piece of work and splitting them would
   have left a commit that drew a tracker unable to turn)
8. `feat: an inverter that publishes what a plant's SCADA does` ✔ (§9)
9. `feat: control panels for the array, the inverter and the mast` ✔ (§8)
10. `feat: put a photovoltaic plant on the same grid as the turbines` ✔ (§10)
11. `fix: darken a roofed array while the player is still standing there` ✔ (found in testing)
12. `docs: write down the photovoltaic plant` ✔ (§11)

## 13. What was verified, and how

`./gradlew build` after every commit. Beyond that, two harnesses and one live server.

**Offline, against the classes themselves.** Every module's derived efficiency lands on its
datasheet's published figure; every inverter's derived European efficiency lands within a
tenth of a percent of its published one; every array's string layout fits its module's system
voltage limit at −10 °C; the Faiman coefficient derived from a 45 °C NOCT comes out at 25.16
against Faiman's own measured 25.0; a pyranometer's step response reaches 95% at exactly its
datasheet's five seconds; the tracker's slew is 17.6× the sun's own rate, matching the real
4.4 against 0.25 °/min.

**The two backtracking derivations agree to machine precision.** `TrackerSpec` derives the
angle from shadow widths on the ground and `Shading` derives the shaded fraction from a ray to
the next row's face; feeding the first into the second gives zero at every sun elevation.

**On a dedicated server**, via RCON, reading block entities back with `/data get block`:

* A 110 kW inverter with fifteen arrays in range claimed twelve of them and stopped at
  eighteen strings — its string-terminal limit, reached before its DC capacity.
* Under a fully overcast taiga sky: `poaBeam` 0, `poaDiffuse` 262, and the trackers reporting
  `stowReason: DIFFUSE` — flat, because there was no beam to point at.
* In a desert at 43.7 °C with 14 m/s of wind: module temperature 51.95 °C, which is Faiman to
  two decimals; `stowReason: WIND` with the stow latched; cabinet at 63.8 °C.
* Stone over an array took `obstruction` to 0 and left the diffuse. Glass took it to exactly
  0.88 and the beam with it.
* A fixed 25° rack at 15° of sun reported `rowShaded: 0.134`, matching the analytic formula.
* Tracking: target −60° at 30° of sun (the rotation limit), −45° at 45° of sun (exactly
  90 − α), and the row slewing 21.12° in four seconds — which is 0.264 °/tick, exactly
  `slewPerTick()`.
* A 10 kW inverter with 20.7 kW of DC on offer: `clipping: 1`, AC pinned at 10.0, and DC
  pulled back to 10.19 — the maximum power point tracker stepping off the peak.
* Snow: a flat table buried under eight layers produced nothing; a tracker at −45° cleared
  the snow block above it and carried on.
* An array with no inverter in range: `mpptFraction` 0, `availableDcPower` 10.29 — reporting
  what it could have made and delivering none of it.
* After the cleanup pass: the central inverter claimed twenty-five strings, tracked a 1166 V
  bus inside its 875–1325 V window, and turned 16.3 kW of DC into 10.1 kW of AC — a third of
  it lost, because 16 kW on a 2500 kW machine is two thirds of a percent of load. Which is the
  string-against-central argument arriving as a number.
* The same array in front of the 10 kW machine: `stringsOutOfWindow` true, DC zero, AC at
  minus one watt of night draw. Its panel names the voltage and the window.

The one bug this found is commit 11: the sky-view survey was cached for a minute, so roofing
an array took away the beam at once and the diffuse a minute later.

---

## 14. The direct-current collection system — commits 13 to 16, all landed

Everything above makes power and hands it over, and nothing above says how it gets from the
modules to the cabinet. The plant worked by proximity: an inverter swept a radius and claimed
whatever arrays it found, which is not a connection, it is a coincidence. This part makes the
copper real, and makes its length cost something.

### R.8 Research — how a real field is actually wired

1. **Module to module** is series inside the array, and stays abstracted: one block is one array
   section, so its strings are its own business.
2. **String to collector** is the *home run*. Solar cable — H1Z2Z2-K or PV1-F, 1.5 kV DC, tinned
   copper, cross-linked polyolefin — clipped along the racking in stainless or PVDF hangers,
   in free air under the modules and supported off the torque tube. Plastic ties are what fails:
   they go brittle under ultraviolet and drop the cable into the dirt. Where the run leaves the
   row it goes into conduit, a tray, or a direct-buried trench.
3. **The combiner box** — field combiner, string combiner — is an IP65 or IP66 enclosure mounted
   *near the array*, on a post or on the racking, to keep the parallel runs short. Inside it:
   one gPV fuse per pole per string, so two per string on a floating array; a Type 2 DC surge
   protector; and an output load-break switch. Four to thirty-two string inputs, output 125 to
   630 A.
4. **Combiner to inverter** is one heavy pair, trenched or in tray.
5. **String inverters take strings directly.** Their own MPPT terminals are the fusing and the
   disconnect — that is what the product *is*, and nobody puts a combiner in front of one.
6. **Central inverters have busbars** and take combiner outputs. Integrated DC sections are also
   real: the *virtual central* arrangement spreads combiners through the field and puts the
   machines together at one end.

So a combiner is both a thing in a field and an option inside a cabinet, and both get built.

### 14.1 Why length has to cost something

Otherwise a cable is decoration. The figures are IEC 60228 conductor resistance at 20 °C times
1.275 for a 90 °C conductor:

| | 6 mm² | 240 mm² |
|---|---|---|
| Resistance at 90 °C | 4.32 Ω/km | 0.0961 Ω/km |
| In free air at 60 °C ambient | 70 A | 570 A |

A string of a 210 mm cell module carries 17.7 A. Two hundred metres of 6 mm²: 1.73 Ω round trip,
31 V of drop, 542 W burnt on a 19.5 kW string — **2.8%**. Sixteen of those strings down one
240 mm² trunk over the same two hundred metres: 283 A, 0.038 Ω, 3.1 kW on 312 kW — **1.0%**.

Those 1.8 points are the entire reason combiner boxes exist, and they will be on the panel.
Ampacity is the other half: 240 mm² carries 570 A in free air and 0.82 of that where it is
buried, because IEC method D is a worse place to be than method E — so thirty-two strings will
not go down one buried trunk, and the copper is what says so.

### 14.2 The cable a player runs — commit 14 ✔

`DcCableSpec` and `CableCatalog`: 6 mm² string cable and 240 mm² trunk, with the figures above.

`DcCableBlock` is redstone dust with two conductors on it: vanilla's own `RedstoneSide` per
horizontal direction, so it lies flat, turns corners, and climbs a block the way dust does. The
rule is stated once and both the model and the walk obey it, so what a player sees connected is
what the plant finds connected.

Placed normally it goes in the air above the block clicked, which is a run in tray or clipped to
the racking. Placed while sneaking on soil it **digs a trench**: the cable takes the soil's place,
the soil drops, the cable renders flush with the ground on a gravel bedding and collides as a
full block so it is walked over rather than tripped in. Buried costs 18% of the cable's ampacity,
which is the real trade and is worth having in the game for exactly that reason.

The two gauges do not connect to each other. A 240 mm² trunk cannot be terminated in an MC4
plug, and one rule beats a page of exceptions.

### 14.3 The plant runs on the copper — commit 15 ✔

`DcNetwork` walks it: bounded breadth-first, over loaded positions only, `isLoaded` before
`getBlockState` — because reaching into an unloading chunk is precisely what stopped a world from
finishing its save once already.

An array needs its **harness** integrated before anything can be plugged into it: right-click it
with string cable and it grows a junction box and a stub, which is its string leads, and is what
a cable then connects to. Without it the strings go nowhere and the panel says so.

`InverterSpec` gains what its terminals actually are: `stringTerminals` and `trunkTerminals`. The
10 kW and 110 kW machines are plugs only, the 350 kW has both, and the central machine is busbars
only — so it cannot take a string at all until it is given fuses.

Cable loss and voltage drop are applied per array, both on the panel with the run length, and the
drop counts against the tracking window: a long enough run holds a string below the startup
voltage, which is a real morning failure.

### 14.4 The combiner box — commit 16 ✔

`CombinerSpec` and `CombinerCatalog`: three products with real fuse and switch ratings. A block,
a block entity, an OBJ model, a read-only panel. An empty hand on it throws the DC load-break
switch, because that is what a hand does to one.

It is integrable into an inverter that has no fused string terminals — the central machine — and
refused by one that has, with the reason said out loud rather than the click doing nothing.

An array is claimed by a *collector*, which is now either an inverter or a combiner, and the
operating point is forwarded down the same lease the inverters already use.

### 14.5 Three sprites — commit 13 ✔

Small, unrelated, and first because it is independent of all of the above.

* `power_wrench` pointed at **`crowbar.png`**, through `item/generated`. So it was a crowbar,
  drawn flat, and held like a sheet of paper. It gets its own sprite and the `item/handheld`
  parent, which is the actual answer to "the 3D model is ugly": handheld grips a tool diagonally
  and generated does not.
* `turbine_tower` pointed at `metal_casing.png`, a generic metal block. Own sprite: tapered tube,
  flange, bolts.
* `met_station`'s sprite is too thin and too pale to read at sixteen pixels. Redrawn.

### 14.6 What was verified, and how

All of it in a running game, over RCON, reading the block entities' own NBT rather than the panels.

* **A twenty-block run is 200 m.** The array's terminals read 495.5 V and 8.741 kW; the inverter's
  read 464.6 V and 8.195 kW. A 31.0 V drop and 6.25% burnt, identical fractions, which is the
  physics saying loss is IR over V. Back-solving the drop gives 17.9 A, the string current of a
  210 mm cell module in full sun.
* Six percent rather than the two the plan expected, and the difference is the lesson: that string
  is 495 V, so the same 31 V is six percent of it. On a 1500 V string it would be two.
* **Cutting the run** dropped the array to standby and the inverter to nothing. Mending it brought
  both back at 200 m. **Burying ten of the twenty blocks** read back as exactly half, and the
  trench met the surface run with a climb — dust's own rule doing its job.
* An array with cable laid to it but **no leads fitted** was not claimed. An array with leads and a
  **cut** run was not claimed. A **trunk cable** laid at an array stayed disconnected.
* **A CB-16 fed by two trackers**: two strings in, 364.64 A of headroom left of 400, bus at
  518.50 V, cabinet at 518.30 — a 0.204 V drop, exactly 35.36 A through 30 m of 240 mm². Both
  arrays name the *box* as their collector and the box names the inverter as its own.
* **A CB-6 with the same tracker on it**: refusal FUSE, nothing connected.
* **The central machine with no DC section** took nothing at all from three blocks of cable laid to
  it, then took the array and turned 8.55 kW of DC into 3.44 kW of AC the moment a CB-32 went in.
* Throwing a box's switch took the group to zero: bus current, operating point, and the cabinet's
  own output all followed.
* The client loads every new blockstate, model and texture with no missing-model or missing-texture
  warnings, and the server still shuts down clean in three seconds.

One thing found in testing that is not a bug in this branch and is worth writing down: a
**world's own copy of the config keeps the old defaults**. `pvExportFraction` and
`turbineExportFraction` were changed from 0.2 to 1.0 in commit 12, and a world saved before that
still holds 0.2 in `saves/<world>/serverconfig/Electricity/server.toml`. Forge has no migration for
a changed default, so an existing world has to be edited or that file deleted.
