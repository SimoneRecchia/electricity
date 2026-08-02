# Integrations

← [Back to the README](../README.md) · [ComputerCraft API reference](telemetry.md) · [Photovoltaics](photovoltaics.md)

Everything on this page is what the fork adds. Both integrations are optional: Mekanism
and CC:Tweaked are compile-time only dependencies, so the mod builds and runs without
either. Every reference to them is confined to a single class each, reached only behind
a `ModList` check.

---

## Mekanism and Forge Energy

### What already worked

The **Power Box** has always exposed `ForgeCapabilities.ENERGY` and pushed Forge Energy
to its neighbours. Mekanism's own `ForgeEnergyCompat` converts Forge Energy both ways, so
a Universal Cable against a Power Box has always carried power. That part is upstream's,
not this fork's.

The gap was the **turbine**: it had no energy buffer and no capability on any face. It
existed only inside the wire graph.

### What the turbine does now

It exposes its output as **Joules** through Mekanism's `IStrictEnergyHandler`, and as
**Forge Energy** for everything else, on two faces: the **bottom** and the face **away
from the direction it faces**. That mirrors Mekanism's own Wind Generator, which uses its
front and bottom — cables belong at the foot of the tower, not halfway up it.

Each tick it **pushes** to whatever is adjacent, splitting fairly between multiple
acceptors, the same way a Mekanism generator emits rather than waiting to be pulled from.
Mekanism's Joule handler is preferred over Forge Energy when both are present, so a
transfer is not needlessly rounded to whole FE.

### The exchange rate

**1 kW = 125 J/tick.**

That is not invented. The Power Box has always run at 50 FE per kW, and Mekanism defaults
to 2.5 J per FE. 50 × 2.5 = 125, so the wire network, the Power Box and the turbine all
agree on one scale.

### Why nothing is double-spent

The wire network does not conserve energy. It reads a generator's output every tick and
distributes it without ever debiting the generator — fine while wires are the only
consumer, but it would hand the same Joule to a Mekanism cable *and* to the wires.

So generation opens a **budget** each tick. Foreign cables claim from it, and the wire
network is given whatever is left. Nothing carries over between ticks, so there is no
stored energy to lose or duplicate.

Block entities tick before the power network runs, so the wires see the residual in the
same tick rather than one tick late.

### The output cap

`turbineMaxJoulesPerTick`, default **2000.0**, limits only what **foreign cables** may
draw. The wire network still receives everything left over.

Without a cap a turbine reaches 9844 J/t, roughly 20× Mekanism's own Wind Generator
maximum of 480 J/t. The default sits at about 4× that Generator, which suits a machine
costing a workbench, circuit boards, a CPU and a motor core against a single block, and
stays under the 5000 J/t the Power Box can already push — so the Power Box remains the
primary bridge and the turbine's faces are a secondary tap.

At the default, a turbine at peak wind gives Mekanism **16 kW** and keeps **62.75 kW** on
the wires. Change it in `<world>/serverconfig/Electricity/server.toml`; existing worlds
keep whatever value they already have saved, since the default only applies to new ones.

### Mekanism-compatible methods

The ComputerCraft peripheral answers to the same method names Mekanism uses on its own
generators, so a program written for a Mekanism generator reads this turbine unchanged:
`getProductionRate`, `getMaxOutput`, `getEnergy`, `getMaxEnergy`, `getEnergyNeeded`,
`getEnergyFilledPercentage`, `isBlacklistedDimension`. Redstone control uses Mekanism's
names too — `getRedstoneMode` / `setRedstoneMode` with `DISABLED`, `HIGH`, `LOW`.

Names were taken from Mekanism's own generated `computer_help/methods.csv`, not guessed.

---

## ComputerCraft

The turbine is a peripheral of type `electricity_wind_turbine`.

### Attaching

Place a **Computer** against the turbine's **base block** — the model is a tall tower,
but the block entity only occupies the block at its foot. Any side works.

For distance, put a **Wired Modem** on the base block and run **network cable** to a
computer somewhere else, then right-click the modem to attach it.

```lua
local t = peripheral.find("electricity_wind_turbine")
print(t.getActivePower() .. " kW")
```

### What it reports

63 signals modelled on real turbine SCADA tags — power, wind, yaw, rotor and generator
speed, phase voltages and currents, blade pitch, gearbox and bearing temperatures,
hydraulic pressures, vibration.

They are not all equal, and `getTelemetryKinds()` says which is which:

| Kind | Count | Meaning |
|---|---|---|
| `MEASURED` | 14 | Read from mod state or the world |
| `DERIVED` | 16 | Computed by a relation that holds on a real machine |
| `SIMULATED` | 33 | Instrumentation the mod does not model |

The simulated values track load and ambient temperature through a first-order lag, so
they ramp like a thermal mass rather than snapping. They behave correctly — but there is
no energy balance behind them and **no failure modes**, so a gearbox will never overheat
and an alarm written against those tags will never fire. Watchdogs belong on the measured
signals.

### Control

| | |
|---|---|
| `stop()` / `start()` | Brake on and off |
| `setActivePowerLimit(kW)` | Curtail without braking |
| `setRedstoneMode(mode)` | `DISABLED`, `HIGH`, `LOW` |

Stopping and curtailing are different: a curtailed turbine keeps turning and still reads
`isRunning() == true`; only the brake stops it. The three reasons a turbine can be down —
your command, redstone, and a storm shutdown — are reported separately, so a program can
tell its own action from the machine protecting itself.

### Threading

Reads come off the computer thread and are served from an immutable snapshot the turbine
publishes once per tick. That means no lock, no wait for a tick boundary, and no chance of
reading a mix of values from two different ticks.

Methods that *change* the turbine run on the server thread instead, because they mark the
block entity dirty and push state to clients.

→ [Full API reference, all 63 tags with units and ranges](telemetry.md)
