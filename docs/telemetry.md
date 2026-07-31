# Telemetry — ComputerCraft API

← [Back to the README](../README.md) · [Integrations](integrations.md) · [Getting started](getting-started.md)

Two peripherals: `electricity_wind_turbine` and `electricity_solar_panel`.

```lua
local t = peripheral.find("electricity_wind_turbine")
local p = peripheral.find("electricity_solar_panel")
```

**Where to attach.** A turbine's machine block sits at the *top* of its tower, but the
tower answers on behalf of the machine it carries — so a computer or a Wired Modem
against **any block of the tower**, the foot included, reaches the same turbine. That is
deliberate: a real turbine gathers its cables at the tower base, which is where you will
build. Two modems on one structure resolve to one peripheral. A solar panel answers on
its own block.

**Threading.** Reads come off the computer thread and never block: every value is served
from an immutable snapshot the machine publishes once per tick, so a call can never mix
values from two different ticks. Methods that *change* the machine run on the server
thread instead, because they mark the block entity dirty and push state to clients.

---

## 1. Wind turbine

### 1.1 Mekanism-compatible methods

These carry the names Mekanism uses on its own generators, so a program written against
a Mekanism generator reads this turbine unchanged. Energy is in Joules.

| Function | Returns | Notes |
|---|---|---|
| `getProductionRate()` | number | Joules produced in the last tick, gross |
| `getMaxOutput()` | number | ceiling on Joules handed out per tick |
| `getEnergy()` | number | Joules still unclaimed in this tick's budget |
| `getMaxEnergy()` | number | same as `getMaxOutput()` |
| `getEnergyNeeded()` | number | always `0`; the turbine is a source |
| `getEnergyFilledPercentage()` | number | `0`–`1` |
| `isBlacklistedDimension()` | boolean | always `false`; this mod has no dimension list |

One kW is **125 J/tick**. That follows from the Power Box's own 50 FE per kW and
Mekanism's default 2.5 J per FE, so every part of the mod agrees on the scale.

### 1.2 Control

| Function | Returns | Notes |
|---|---|---|
| `stop()` | — | applies the brake; the rotor comes to a stand |
| `start()` | — | releases **your** stop only, not redstone and not a cut-out |
| `isStopped()` | boolean | true only if *this program* stopped it |
| `isRunning()` | boolean | true if nothing at all is holding it off load |
| `isWindCutOut()` | boolean | the machine shut itself down for the weather |
| `isStoppedByRedstone()` | boolean | the mode and the signal are holding it down |
| `getRedstoneMode()` | string | `DISABLED`, `HIGH` or `LOW` |
| `setRedstoneMode(mode)` | — | throws on anything else |
| `getActivePowerLimit()` | number | curtailment setpoint, kW |
| `setActivePowerLimit(kW)` | — | clamped to this machine's own nameplate |

Redstone modes: `DISABLED` ignores redstone, `HIGH` runs only while powered, `LOW` only
while unpowered. Mekanism's fourth mode, `PULSE`, is rejected rather than silently
accepted — a generator runs continuously and has nothing to pulse.

`isStopped()` answers for your own command alone, and that matters: a control loop that
read a player's hand-stop as its own would leave a turbine down believing it had put it
there. Ask `isRunning()` for the fact and the three `stoppedBy*` tags for the reason.

**A setpoint of zero does not stop the machine.** Curtailment is an instruction from the
grid: a machine told to export nothing disconnects and idles with its blades feathered.
It does not brake, because it has to be able to come back in seconds, and because
standing still is what harms a main bearing. Only `stop()`, a player at the panel and
redstone put the brake on. A partly curtailed machine also visibly slows — curtailment is
done by pitching, and power goes with the cube, so half output is four fifths of the
speed rather than half.

### 1.3 Reading the instruments

| Function | Returns | Notes |
|---|---|---|
| `getTelemetry()` | table | every tag at once, one tick-consistent snapshot |
| `getTelemetryKinds()` | table | tag → `"MEASURED"`, `"DERIVED"` or `"SIMULATED"` |
| `get<Tag>()` | number/boolean | one generated getter per tag |

Every tag below also has a generated getter, `get` plus the capitalised tag:
`getWindSpeed()`, `getPf()`, `getV12()`, `getGearBoxOilTemp()`. The single exception is
`activePowerLimit`, whose generated name would shadow the annotated method above — read
it from `getTelemetry()` or from `getActivePowerLimit()`.

One `getTelemetry()` beats sixty separate calls, and it is the only way to be certain
every value came from the same tick.

---

## 2. The tags

### 2.1 MEASURED — what the machine actually knows

| Tag | Unit | What it is |
|---|---|---|
| `windSpeed` | m/s | **ten-minute mean at hub height**, not the instantaneous wind |
| `windDir` | ° 0–360 | heading the air travels towards, friction turning included |
| `nacelleDir` | ° 0–360 | actual yaw; follows `windDir` at 0.25°/tick past a 7.5° deadband |
| `rotorRpm` | rpm | the real speed, within this machine's own published range |
| `activePower` | kW | what is leaving the machine now |
| `activePowerLimit` | kW | curtailment setpoint |
| `powerLimitationActive` | boolean | braked, pitching above rated, or held by the setpoint |
| `ambientTemp` | °C | air at the nacelle: biome, elevation, day cycle, sky |
| `turbulence` | — | turbulence intensity σ/U — 0.08 over water, 0.13 farmland, 0.20 forest |
| `yawCableTwist` | ° | signed running total; random-walks about zero |
| `running` | boolean | nothing is holding it off load |
| `windCutOut` | boolean | shut down for the weather |
| `stoppedByComputer` | boolean | a program called `stop()` |
| `stoppedByPlayer` | boolean | a player used the machine's own control panel |
| `stoppedByRedstone` | boolean | |

`windSpeed` is the **mean**, because that is what a controller supervises on and what a
wind report quotes. The machine also knows an instantaneous wind, which is what the rotor
is actually in, and a three-second gust, which is what trips it at a fifth past the
cut-out. Plotting the mean against `activePower` gives a scatter that looks like a real
power curve; plotting the instantaneous wind would not, because it moves faster than the
rotor can follow.

`turbulence` is a **real turbulence intensity**, so its useful range is 0.05 to 0.30 and
anything past 0.20 is rough air. It used to run to 1.0 and mean nothing in particular.

### 2.2 DERIVED — worked out from the above

| Tag | Unit | Relation |
|---|---|---|
| `generatorRpm` | rpm | `rotorRpm × gearboxRatio`, the ratio being `1800 / maxRotorRpm` |
| `apparentPower` | kVA | `P / pf` |
| `reactivePower` | kvar | `√(S² − P²)` |
| `pf` | — | `0.90 + 0.08 × load`, and exactly `0` below 10 W |
| `f` | Hz | 50, wandering ±0.04 |
| `v12` `v23` `v31` | V | 690 line-to-line, −1.2% at full load, never perfectly balanced |
| `i1` `i2` `i3` | A | `S / (√3 · V)`, ±0.5% between phases |
| `bladePitchAngle` | ° 0–90 | collective |
| `bladePitchAngle1..3` | ° 0–90 | collective ±0.25, clamped to the mechanical travel |
| `airPressure` | hPa | **the weather model's own field**, at this site's elevation |

The relations are exact. The constants inside them — 690 V, 50 Hz, a generator at 1800
rpm — are chosen rather than modelled, but they are the usual ones for a machine of this
size, and the gear ratio is derived per model so a slow wide rotor gets a taller one.

**Pitch** is 0° from cut-in to the rated wind, ramps to 25° across the regulating band,
carries on to 60° through storm control, and sits at 90° — full feather — whenever the
machine is off load.

**`airPressure` is a cause now, not a decoration.** It used to be worked backwards from
the wind speed. It is read from the pressure map the wind is *computed from*, so a station
reading 993 hPa beside a 22 m/s wind is the cause standing next to its effect — and a
barometer log leads the wind by hours, exactly as a real one does.

`pf` reads exactly zero below 10 W rather than a misleading 0.9: with no current there is
nothing to have a phase angle relative to.

### 2.3 SIMULATED — invented instrumentation

Not physics. Invented, but invented carefully: each value tracks load and ambient
temperature through a first-order lag that reaches 63% of a step in about 25 seconds, so
they ramp like a thermal mass instead of snapping when the wind changes. A control program
written against them behaves as it would against a real machine, which is the point. But
there is no energy balance behind them and **no failure modes**, so an alarm written
against them will never fire. `getTelemetryKinds()` says so at runtime.

Temperatures are the rise above ambient; each starts at ambient on a freshly loaded
turbine and warms up.

| Tag | Unit | Roughly |
|---|---|---|
| `gearBoxOilTemp` | °C | ambient + 18 + 42 × load |
| `gearBoxOilTempSump` | °C | ambient + 12 + 34 × load |
| `gearBearTemp1Gen` | °C | ambient + 22 + 48 × load |
| `gearBearTemp2Rot` | °C | ambient + 20 + 44 × load |
| `genBearTempBS` | °C | ambient + 25 + 50 × load |
| `genBearTempDEnd` | °C | ambient + 24 + 47 × load |
| `mainBearTemp1` | °C | ambient + 15 + 30 × load |
| `generatorL1Temp` `L2Temp` `L3Temp` | °C | ambient + 30 + ~78 × **copper loss** |
| `genCWTempGenInlt` | °C | ambient + 8 + 15 × load |
| `genCWTempGenOutlt` | °C | inlet + 6 + 14 × load |
| `nacelleTemp` | °C | ambient + 8 + 12 × load |
| `hydOilTemp` | °C | ambient + 10 + 20 × load |
| `airTempCtrlCab` | °C | ambient + 6 + 10 × load |
| `airTempPwrCabCtrlFld` | °C | ambient + 9 + 16 × load |
| `airTempPwrCabPwrFld` | °C | ambient + 12 + 24 × load |
| `airTempTowerBott` | °C | ambient + 3 + 4 × load |
| `mvTrafoTempAreaCoil` | °C | ambient + 20 + 55 × copper loss |
| `pitch1MotorTemp` `2` `3` | °C | ambient + 5 + ~9 × pitch activity |
| `pitch1BoxTemp` `2` `3` | °C | ambient + 4 + ~6 × pitch activity |
| `gearBoxOilPress` | bar | 2.0 + 1.5 × load; **0 with the rotor stopped** |
| `gearBoxOilPressPmp` | bar | 4.5 + 2.0 × load; 0 stopped |
| `hydrSystemPress` | bar | 190 + 20 × load |
| `hydrMainBrakesPressure` | bar | 178 with the brake on, 4 off |
| `yawHAccuPress` | bar | 148 + 6 × load |
| `yawHydrBrkPress` | bar | 58 while yawing, 12 at rest |
| `vibYDirection` | mm/s | 0.4 + 1.8 × load + 2.5 × turbulence |
| `vibZDirection` | mm/s | 0.3 + 1.5 × load + 2.0 × turbulence |

**Copper loss**, not load: stator windings heat with the *square of the current*, and
current follows apparent power. At part load the power factor is worse, so the machine
carries more current than its kW suggest and runs correspondingly hotter.

---

## 3. The catalogue

Every figure is the real machine's. The C line follows Vestas — a C90 is a V90, a C112 a
V112. Hub height is the tower you built, ten metres to the block.

| Model | Rotor | Nameplate | IEC | Cut-in | **Rated wind** | Storm onset | Cut-out | Rotor speed | Tip speed | Gearbox | Towers |
|---|---|---|---|---|---|---|---|---|---|---|---|
| SW-10 | 7 m | 10 kW | — | 3.0 | 13.9 | — | 25 | 60–250 rpm | 92 m/s | 7:1 | 2–4 (20–40 m) |
| C52-0.85 | 52 m | 850 kW | IA | 4.0 | 14.9 | 22 | 25 | 14.0–31.4 | 86 m/s | 57:1 | 4–7 (40–70 m) |
| C80-2.0 | 80 m | 2000 kW | IIA | 4.0 | 14.6 | 22 | 25 | 9.0–19.0 | 80 m/s | 95:1 | 6–10 (60–100 m) |
| C90-3.0 | 90 m | 3000 kW | IIA | 3.5 | 15.5 | 22 | 25 | 9.9–18.4 | 87 m/s | 98:1 | 8–11 (80–110 m) |
| C112-3.0 | 112 m | 3000 kW | IIIA | 3.0 | 13.3 | 22 | 25 | 6.2–17.7 | 104 m/s | 102:1 | 8–12 (80–120 m) |
| C130-4.0 | 130 m | 4000 kW | IIIA | 3.0 | 13.3 | **22.5** | 25 | 5.5–14.0 | 95 m/s | 129:1 | 9–13 (90–130 m) |

The rotor speed ranges are the published ones — a V52 runs 14.0 to 31.4 rpm, a V112 6.2
to 17.7 — which is why the tip speeds differ: 80 m/s is right for a V80 and thirty percent
short of what a V112's longer blades are allowed. The C130 cuts out at 22.5 m/s like the
rest of its platform. Rated wind speed is derived from the curve rather than declared
beside it, and lands on the published figures.

**The power curve has no corner in it.** The textbook one does — cubic, then flat at the
nameplate — but a real machine approaches its ceiling over three to five m/s, because the
rotor reaches its speed limit first and its efficiency falls away as the tip speed ratio
drops, while the pitch controller takes over by degrees. That knee is rounded here, which
is what puts the rated wind speeds on the published figures instead of two to five m/s
early.

**Speed follows the wind, not the load.** The rotor holds its tip speed ratio until it
reaches the top of its range, then holds that while the power goes on climbing — which is
what pitching the blades is for. Across the partial-load band the speed varies by two
while the output varies by more than thirty: watching a rotor tells you the wind, not the
megawatts. Below cut-in and above cut-out it idles at a couple of rpm, feathered, rather
than parking; on screen every rotor is drawn at a common rate so they read alike, while
`rotorRpm` reports the real figure.

---

## 4. How a turbine comes to produce anything

### 4.1 Where the wind comes from

From a pressure map, the same way it does outside.

1. A field of highs and lows covers the world: mean 1013 hPa, spread 8, so it runs about
   990 to 1036. One high-and-low pair is 6000 blocks across.
2. The whole pattern slides downwind at 11 m/s and turns over in place, so a system
   arrives, passes and decays. A front takes a day or two to go by.
3. The wind aloft is the **gradient** of that field, by the geostrophic relation
   `V = |∇p| / (ρ f)`. It blows *along* the isobars, low pressure on its left.

That third point is the one to keep: **the wind is strong where the isobars crowd, not
where the pressure is low.** The middle of a deep low is slack; the gale is on its flank,
and a lazy high is a dead calm. Aloft it runs about 10 m/s on average, 17 at the ninetieth
percentile and 37 at its worst.

So watch `airPressure`. A steady fall means a gradient is tightening nearby, and the wind
follows it.

### 4.2 What gets it down to your hub

The wind aloft is the same for every machine in the area. What each one stands in is that
wind brought down through the boundary layer, and four things decide how much survives
the trip.

- **Height.** Under a blending height of 200 m each site follows its own logarithmic
  profile. The exponent runs from 0.07 over water on a summer afternoon to 0.35 over
  forest on a still night.
- **Ground.** Roughness by biome, from published figures for the terrain each stands for:
  open water 0.0002 m, grassland 0.03, mature forest 1.0. A factor of five thousand end to
  end, worth about a third of the wind speed at a hub.
- **Standing proud.** Air cannot pile up in front of a hill, so it accelerates over the
  top: up to +30% on a ridge, −12% on a valley floor.
- **The hour.** Sunshine stirs the whole layer and the profile flattens. A clear night
  decouples it — slow at the ground and 16% faster above — which puts a crossover near
  80 m. Short machines are day machines and tall ones are night machines.

Turbulence then adds the second-by-second wiggle, and the gust is what trips the machine.

### 4.3 What the machine does with it

`P = ½ ρ A Cp v³`, held under the nameplate by that rounded knee.

The cube is everything. Ten percent more wind is a third more power; twice the wind is
eight times. Every lever below is really a lever on wind speed, which is why a site is
worth walking before you build on it.

### 4.4 So how does it reach nameplate?

It needs its rated wind at the hub — 13.3 to 15.5 m/s depending on the machine. On open
farmland, on the tallest tower each is sold on, that happens this often:

| Model | Rated wind | Wind above it | At nameplate | Capacity factor |
|---|---|---|---|---|
| SW-10 | 13.9 | 3.8% | 2.6% | 29.1% |
| C52-0.85 | 14.9 | 4.5% | 3.0% | 29.6% |
| C80-2.0 | 14.6 | 6.0% | 4.5% | 33.5% |
| C90-3.0 | 15.5 | 5.1% | 3.7% | 31.3% |
| C112-3.0 | 13.3 | 9.1% | 7.3% | 41.1% |
| C130-4.0 | 13.3 | 9.7% | 7.7% | 42.1% |

A turbine is at its nameplate a few percent of the time, and that is correct — real ones
are too. The bar on the panel should be moving nearly always and full rarely. Pinned at
either end means something is wrong.

### 4.5 What you can do about it

All measured on a C90-3.0 against a reference of 11 blocks of tower on flat plains, 31.3%.

| Change | Capacity factor | |
|---|---|---|
| **On a ridge, +30% exposure** | 45.2% | **+44%** |
| On a beach instead of plains | 32.2% | +3% |
| *reference* | 31.3% | — |
| In a forest | 28.9% | −8% |
| 8 blocks of tower instead of 11 | 28.8% | −8% |
| In a jungle | 28.1% | −10% |
| **In a valley, −12% shelter** | 24.7% | **−21%** |

**Siting beats everything.** A ridge is worth more than three extra blocks of tower and
more than any biome. Walk the high ground first — that is what a wind developer does, and
the answer does not change from one day to the next.

**Height is the dependable one.** One more block is about 3% on plains, and considerably
more over rough ground where the profile is steeper. The panel quotes the figure for
*your* site, because only the server knows the ground your tower stands on.

| Tower | Mean wind | Capacity factor |
|---|---|---|
| 8 blocks (80 m) | 6.94 m/s | 28.8% |
| 9 blocks (90 m) | 7.07 m/s | 29.7% |
| 10 blocks (100 m) | 7.19 m/s | 30.5% |
| 11 blocks (110 m) | 7.30 m/s | 31.3% |

**A wider rotor on the same generator is not a smaller machine.** The C90 and the C112 are
both 3 MW:

| | Rotor | Specific power | Capacity factor |
|---|---|---|---|
| C90-3.0 | 90 m | 472 W/m² | 31.3% |
| C112-3.0 | 112 m | 305 W/m² | **40.4%** |

Same generator, nine points of capacity factor. The C112 reaches its plateau in less wind
and stays there longer. That is the whole lesson of the IEC class printed beside each
machine: a wide rotor belongs on a quiet site, a narrow one needs a windy one to be worth
its generator.

**And the small things.** Cold air is denser and carries more power, so a winter night
out-produces a summer afternoon in the same wind. A misaligned nacelle loses with the cube
of the cosine. And a curtailment setpoint you forgot about will hold the machine down all
day, while `powerLimitationActive` quietly says so.

---

## 5. Solar panel — alpha

**This one is not finished.** The irradiance model behind it is real and the numbers it
reports are honest, but the block itself is early: it has no control panel of its own, it
does not join the mod's own wire network — energy leaves only on the Forge and Mekanism
capabilities — and nothing here is settled. Build with it if you like, expect it to move.

Type `electricity_solar_panel`. One block is 20 kW of modules over a hundred square
metres — the whole block, because Minecraft's sun passes through the zenith, so there is
nothing to tilt and no row spacing to leave.

| Function | Returns | Notes |
|---|---|---|
| `getProductionRate()` | number | Joules in the last tick — Mekanism's name and unit |
| `canSeeSun()` | boolean | Mekanism's name |
| `getActivePower()` | number | kW at the inverter's terminals |
| `getRatedPower()` | number | 20 kW at standard test conditions |
| `getIrradiance()` | number | W/m² global horizontal; a clear zenith sun is just over 1000 |
| `getCellTemperature()` | number | °C — some thirty above the air in full sun |
| `getAmbientTemperature()` | number | °C |
| `getCloudCover()` | number | 0–1 |
| `getPerformanceRatio()` | number | output over nameplate, 0–1 |

`getPerformanceRatio()` is the one to log if you log only one: it folds the light, the
cell temperature, this panel's own modules and the inverter into the figure a plant is
actually judged on.

**Two panels side by side will not agree**, and all three reasons are the real ones. A
cloud reaches one before the other — the cumulus field is sampled at each panel's own
coordinates and drifts downwind, so a shadow crossing an array arrives a few seconds apart
along it. The modules are not identical: every real one is flash tested into a bin a few
percent wide, and that difference is fixed for the life of the panel. And dust settles
unevenly. Median disagreement across daylight is about 4% between neighbours, 7% at twenty
blocks, 9% across a farm — insignificant in the total, plain in a trend.

**Biome matters the way it really does.** Cloud is lifting times moisture, not lifting
plus moisture: a deep low over a desert brings wind and no cloud, because there is nothing
there to condense. Fraction of the clear-sky light that reaches the ground:

| Desert | Temperate | Taiga | Rainforest |
|---|---|---|---|
| 0.92 | 0.74 | 0.66 | 0.64 |

A desert out-produces a rainforest by about 27%, which is what the real pair do at one
latitude. Altitude helps too — 12% more at 2000 m, part thinner atmosphere and part cooler
cells. Yields here are equatorial, 17 to 21% of nameplate over a year, because every
Minecraft day is an equinox at the equator.

**Wind and sun fail together.** The same pressure map decides both, so calm weather and
clear skies arrive at once and a gale is overcast. A grid built on the two has to survive
both going quiet together, exactly as a real one does.

---

## 6. Two programs

Poll a farm:

```lua
for _, name in ipairs(peripheral.getNames()) do
  if peripheral.getType(name) == "electricity_wind_turbine" then
    local t = peripheral.wrap(name).getTelemetry()
    print(("%s  %7.1f kW  %5.1f m/s  %5.1f rpm  %s"):format(
      name, t.activePower, t.windSpeed, t.rotorRpm,
      t.running and "run" or t.windCutOut and "storm" or "stopped"))
  end
end
```

Curtail a farm to a target, and let it back up again:

```lua
local turbines = { peripheral.find("electricity_wind_turbine") }

local function curtail(targetKw)
  local share = targetKw / #turbines
  for _, t in ipairs(turbines) do t.setActivePowerLimit(share) end
end
```

Pass `0` and every machine disconnects and idles — feathered, turning slowly, ready to
come back the moment you raise it again.
