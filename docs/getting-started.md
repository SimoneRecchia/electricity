# Getting started

← [Back to the README](../README.md)

## The items

Everything except the Electric Workbench is crafted **at** the Electric Workbench. The
workbench itself comes from a normal crafting table. Recipes show up in JEI, or in the
workbench's own UI.

| Item | Where | What it is for |
|---|---|---|
| **Electric Workbench** | Crafting table | Crafts every other component |
| **Wire** | Workbench | Connects two insulators |
| **Power Wrench** | Workbench | Live diagnostics on any electric block |
| **Wind Turbine** | Workbench | Generation. Six models, 10 kW to 4 MW |
| **Turbine Tower** | Workbench | Stack it; the turbine goes on top |
| **Electric Cabin** | Workbench | Collects from generators |
| **Utility Pole** | Workbench | Carries power across distance |
| **Power Box** | Workbench | Distributes in a radius, bridges to Forge Energy |
| **Electric Lamp** | Workbench | Example consumer |
| **Solar Panel** | Workbench | 20 kW of photovoltaic. **Alpha** |
| **Weather Tablet** | Workbench | Weather map — not functional yet |
| Circuit Board, CPU, Screen, Insulator, Metal Casing, Motor Core | Workbench | Components for the above |

## Building your first grid

The chain is fixed and enforced by the mod:

```
Wind Turbine → Electric Cabin → Utility Pole → … → Power Box → consumers
```

A turbine will only feed a cabin. A cabin will only feed a pole. A pole feeds other
poles or a power box. Wiring anything else together produces a wire that carries
nothing, with no error to tell you so — which is the single most common way to end up
staring at a grid reading 0 kW.

### 1. Find wind

Place the turbine somewhere exposed and check the wind with the Weather Tablet. Below
**3 m/s** it generates nothing at all, and the weather model gives more wind at altitude.

### 2. Turbine → Cabin

With a **Wire** in hand, right-click the turbine's insulator, then right-click the
cabin's **input** insulator.

> **Which insulator?** The cabin has two.
> **Left is the output. Right is the input.** The turbine connects to the **right** one.
>
> Get this backwards and the wire is created but carries nothing.

Insulators are parts of the model, not the block, so aim at the small insulator geometry
rather than at the block in general.

### 3. Cabin → Poles → Power Box

Right-click the cabin's **left** (output) insulator, then any insulator on a Utility
Pole. Poles have eight, and any of them works. Chain as many poles as you like.

The Power Box's insulator is **underneath** it.

### 4. Consumers

Place Electric Lamps within the Power Box's radius — 5 blocks by default, set by
`powerBoxRadius`. The lamp reacts to power quality: it has wear, dims under a brownout,
and can burn out.

## Wire losses

Two rules, and they interact:

- **Power drops with distance**, about **1% per block** of wire. A 30-block run keeps
  roughly 70%.
- **More wires between the same two points preserve it better.** Each extra wire on the
  same run reduces the distance penalty, so a long span is worth doubling or tripling up.

Losses bottom out at 10% — a wire will never carry less than a tenth of what entered it,
however long it is.

## The Power Wrench

Right-click any electric block's **model** with the wrench and a live diagnostics panel
opens. It does not pause the game, so you can leave it open and watch values move.

| Block | Shows |
|---|---|
| Wind Turbine | Generated power, wind speed, rotor and yaw figures |
| Electric Cabin | Power passing through |
| Utility Pole | Power passing through |
| Power Box | Power, plus Forge Energy stored and transfer rate |

Hovering a block shows the same power figures as a tooltip without opening anything.

> [!CAUTION]
> Right-clicking the Utility Pole's **block** rather than its model opens an
> experimental configuration screen for the model's offset and yaw/pitch. Combining
> yaw/pitch with an offset puts the wire anchor points in the wrong place.

## Turbine thresholds

| | Speed | What happens |
|---|---|---|
| Cut-in | 3 m/s | Starts generating, straight to 4.9 kW |
| Rated | 12 m/s | Full output, 78.75 kW; blades begin pitching out |
| Storm onset | 22 m/s | Output derates instead of tripping |
| Shutdown | 25 m/s | Brake on, blades feathered |
| Re-arm | 22 m/s | Releases a storm shutdown, back at 80% |

A turbine also loses power when it is not pointed into the wind. It yaws to follow the
wind but only corrects past a 7.5° deadband, so it normally sits slightly off and gives
up about 2.5%.

→ [The full power curve](telemetry.md#3-the-catalogue)

## Wind surges

In rough air a turbine can put surges down the line. Only Electric Lamps react — they go
into overdrive and eventually burn out. Other blocks ignore surges, because the surge
system is not exposed to other mods yet.

There is no game rule for this; it is always on. A `/gamerule electricityWindSurges` was
documented here for a while and never existed.

## Running on the real clock

```
/gamerule electricityRealTimeClock true
```

The sun stands where the real sun stands: six in the evening outside is six in the
evening in the world, a day takes a day, and a second of play is a second. The weather
slows with it, so a front takes a couple of real days to pass instead of forty minutes.

It is here because a power system is something you watch over hours. Sleeping still
heals, still sets a spawn and still clears the weather — it just cannot move the sun any
more, because there is nowhere for a night to be skipped to.

## For developers

To make your own block consume power from the grid, implement `IElectricPowerConsumer`
and expose it through `ElectricityCapabilities.ELECTRIC_CONSUMER`. Power is in kW, and
`onPowerSupplied` also hands you a `PowerDeliveryEvent` carrying surge, brownout and
disconnect information.

To take energy the other way, in Joules or Forge Energy, see
[Integrations](integrations.md).
