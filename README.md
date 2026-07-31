<div align="center">

# ⚡ Electricity

**Real electrical grids for Minecraft.** Utility poles, wind turbines driven by a live
weather model, wires that lose power over distance — and now a turbine that talks to
Mekanism and answers to ComputerCraft.

![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen?style=flat-square)
![Forge 47.4.0](https://img.shields.io/badge/Forge-47.4.0-1f425f?style=flat-square)
![Java 17](https://img.shields.io/badge/Java-17-orange?style=flat-square)
![Code GPLv3](https://img.shields.io/badge/code-GPL--3.0--only-blue?style=flat-square)

</div>

> [!NOTE]
> This is a **fork** of [dooji2/electricity](https://github.com/dooji2/electricity).
> The grid simulation, the weather model, the blocks and the art are all dooji's work.
> What this fork adds is everything under [Integrations](#integrations): the turbine
> now feeds other mods' energy networks, reports itself as a ComputerCraft peripheral
> with 63 plant signals, and can be stopped and curtailed.

---

## How a grid works

Power flows along a fixed chain. Wires connect *insulators*, not blocks, so components
can sit far apart — at the cost of losing power over the distance.

```mermaid
flowchart LR
    W["🌬️ Wind Turbine<br/><i>generates</i>"] -->|wire| C["Electric Cabin<br/><i>collects</i>"]
    C -->|wire| P1["Utility Pole"]
    P1 -->|wire| P2["Utility Pole"]
    P2 -->|wire| B["Power Box<br/><i>distributes in a radius</i>"]
    B -.->|power field| L["💡 Electric Lamp"]
    W -->|Joules / Forge Energy| M["🔌 Mekanism cable,<br/>or any FE machine"]
    style W fill:#2d6a4f,stroke:#95d5b2,color:#fff
    style M fill:#5a189a,stroke:#c77dff,color:#fff
    style B fill:#1d3557,stroke:#a8dadc,color:#fff
```

The chain is enforced: a turbine only feeds a cabin, a cabin only feeds a pole, a pole
feeds poles or a power box. The branch to Mekanism is what this fork adds.

**Two things about wires.** Power drops with distance — roughly 1% per block of wire.
And running *more* wires between the same two poles preserves it better, so a long run
is worth doubling up.

## Integrations

<table>
<tr><td width="50%" valign="top">

### 🔌 Mekanism & Forge Energy

The turbine exposes its output as **Joules** on its bottom and back faces and pushes to
whatever is adjacent each tick, the same way a Mekanism generator emits. Universal
Cables draw from it directly; any Forge Energy machine works too.

One kW is **125 J/tick**, derived from the mod's own numbers rather than invented: the
Power Box has always run at 50 FE per kW, and Mekanism defaults to 2.5 J per FE.

Nothing is double-spent. Generation opens a per-tick budget, foreign cables claim from
it, and the wire network receives only what is left.

→ [Integrations guide](docs/integrations.md)

</td><td width="50%" valign="top">

### 💻 ComputerCraft

A peripheral of type `electricity_wind_turbine`, readable directly or over a wired
modem on network cable.

```lua
local t = peripheral.find("electricity_wind_turbine")
print(t.getActivePower() .. " kW")
print(t.getGearBoxOilTemp() .. " C")
t.setActivePowerLimit(40)   -- curtail
t.stop()                    -- brake
```

63 signals modelled on real turbine SCADA tags, and method names shared with Mekanism's
generators so existing programs work unchanged.

→ [Full API reference](docs/telemetry.md)

</td></tr>
</table>

### 🎛️ Control

Stop, start and curtail a turbine from a computer, or wire it to redstone with
`DISABLED` / `HIGH` / `LOW` modes — the same names Mekanism uses.

A **stop** brakes the rotor and feathers the blades. A **curtailment** just holds output
at a setpoint while the machine keeps turning. The telemetry tells the two apart, and
tells both apart from the machine shutting itself down in a gale.

### 🌪️ Storm control

Above 22 m/s the turbine no longer trips straight to zero. It sheds a fifth of rated per
m/s — 80%, 60%, 40% at 22, 23 and 24 — and brakes at 25. Coming off full load in one
step is a shock to the drivetrain, and no real turbine does it.

→ [Power curve, thresholds and all the numbers](docs/telemetry.md#3-the-catalogue)

## Blocks and items

| | Name | What it does |
|---|---|---|
| 🌬️ | **Wind Turbine** | Six machines from 10 kW to 4 MW, following the real Vestas line. Yaws to follow the wind, pitches its blades above rated, idles through a storm. |
| 🗼 | **Turbine Tower** | Stack it to the height you want, then seat a turbine on top. Hub height is something you build, and it is worth about 3% a block. |
| 📦 | **Electric Cabin** | Collects from generators and passes it to utility poles. Two insulators: **left is output, right is input**. |
| 🗼 | **Utility Pole** | Carries power across distance. Eight insulators, configurable. |
| 🔋 | **Power Box** | Distributes within a radius, and bridges to Forge Energy. |
| 💡 | **Electric Lamp** | Example consumer. Has wear, burns out, reacts to power quality. |
| 🔧 | **Power Wrench** | Opens a live diagnostics panel on any electric block. |
| 🧵 | **Wire** | Right-click one insulator, then another. |
| 🛠️ | **Electric Workbench** | Crafts every component below. |
| ☀️ | **Solar Panel** | 20 kW of modules. **Alpha** — it works and it is honest about the physics, but it is not finished. |
| 📡 | **Weather Tablet** | Weather intensity map. Not functional yet. |

Components — Circuit Board, CPU, Screen, Insulator, Metal Casing, Motor Core — are all
made at the Electric Workbench. Recipes are visible in-game; use JEI or the workbench UI.

## Getting started

1. Craft an **Electric Workbench** at a crafting table, then the components at it.
2. Stack **Turbine Towers** to the height you want and seat a **Wind Turbine** on top.
   Each machine is sold on a range of tower heights and refuses to mount outside it.
   Below its cut-in wind — 3 to 4 m/s — it produces nothing. High ground is worth more
   than anything else you can do: a ridge beats three extra blocks of tower.
3. With a **Wire** in hand, right-click the turbine's insulator, then the **right**
   insulator of an **Electric Cabin**.
4. Cabin's **left** insulator → a **Utility Pole** → more poles → the **Power Box**
   (its insulator is underneath).
5. Place **Electric Lamps** inside the Power Box's radius.

→ [Step-by-step guide](docs/getting-started.md)

## Configuration

`<world>/serverconfig/Electricity/server.toml` — server configs live inside the world
folder, not in `config/`.

| Key | Default | Meaning |
|---|---|---|
| `powerBoxRadius` | 5 | Radius of the Power Box's field, in blocks |
| `externalEnergyEnabled` | true | Let generators feed other mods' energy systems |
| `turbineMaxJoulesPerTick` | 2000.0 | Cap on what a turbine hands to foreign cables |

The 2000 J/t default is about 4× Mekanism's own Wind Generator, which suits a machine
costing a workbench, circuit boards, a CPU and a motor core against a single block. It
gives Mekanism **16 kW** and leaves 62.75 kW on the wires at peak wind, and stays under
the 5000 J/t the Power Box can already push, so the Power Box remains the primary bridge.
Uncapped a turbine would reach 9844 J/t, roughly 20× that Generator.

## Building from source

```bash
./gradlew build          # jar lands in build/libs/
./gradlew runClient      # dev client, with Mekanism and CC:Tweaked included
```

Java 17. Mekanism and CC:Tweaked are `compileOnly`, so the mod builds and runs without
either installed — every reference to them is confined to one class each, behind a
`ModList` check.

## Credits and licence

Created by **dooji** — [dooji2/electricity](https://github.com/dooji2/electricity).
Upstream is the origin of the grid simulation, the weather model, the blocks and all the
art. This fork adds the integrations described above.

- **Code** — GNU GPL v3.0 only, see [`LICENSES/LICENSE-CODE`](LICENSES/LICENSE-CODE)
- **Assets** (models, textures, audio) — **All Rights Reserved** to the original author,
  see [`LICENSES/LICENSE-ASSETS`](LICENSES/LICENSE-ASSETS)

> [!IMPORTANT]
> The asset licence is why this fork is not published anywhere. Redistributing it with
> the original models and textures would need dooji's permission, or replacement art.
