# Photovoltaics — the plant, the physics and the catalogue

← [Back to the README](../README.md) · [Telemetry](telemetry.md) · [Integrations](integrations.md) · [Getting started](getting-started.md)

A photovoltaic plant here is three kinds of block. **Arrays** turn light into direct current.
An **inverter** turns that into alternating current, holds a power factor, and is the thing
the grid and your computer talk to. A **meteorological mast** measures the sky.

The division is the real one, and the first consequence of it is the one that surprises
people: **an array with no inverter in range produces nothing at all.** That is not a rule
imposed on it. An open-circuit string sits at its open-circuit voltage and passes no
current, so it makes exactly zero watts however bright the day is — which is why a solar
plant is an inverter with modules attached rather than the other way round. The array still
reports what it *could* have made, and its panel says so in as many words.

---

## 1. Building one

1. Place an **inverter**. Everything else is decided by which one.
2. Place **arrays** within twelve blocks of it. It claims them nearest first, up to the
   number of string terminals it actually has — four on the smallest machine and two hundred
   and eighty-eight on the central one, because a central inverter's strings arrive through
   combiner boxes. An array past that limit is not wired in and says so, and so does one whose
   string voltage falls outside the machine's tracking window.
3. Run a **wire** from the fitting on top of the inverter to the **right** insulator of an
   **Electric Cabin**, then on to poles and a Power Box exactly as a turbine does.
4. Optionally place a **mast**. It needs no power and feeds nothing; it measures.

A **Power Wrench** opens a panel on any of the three.

There is no cable to draw between an array and its inverter, and that is also how a real
plant looks: the only overhead line on a solar farm is the one leaving it.

The claim is a **lease**, not a wire. The inverter renews it every tick it runs, and an array
that stops hearing from its inverter for five seconds releases itself and goes to standby.
That is what a string actually sees — it talks to its inverter over a serial link and knows
nothing about the world beyond it — and it is why nothing has to be told when the other end
goes away: a cabinet that is broken, or standing in a chunk that is no longer loaded, simply
stops renewing.

### How much glass to put in front of an inverter

More than it can pass. That is not a mistake, it is the entire design convention: real
utility plant is built at a DC-to-AC ratio of 1.30 to 1.55, throws away the top of the best
few hours of the best days, and in exchange has an inverter that is properly loaded for the
rest of the year instead of idling at a third of nameplate all winter. Each inverter here
accepts half again its AC nameplate in DC and will clip above that.

Clipping is visible on the inverter's panel as a gap between the two bars, and it is worth
looking at once: the direct-current bar shows the array's offer running past the point the
inverter has pulled the operating point back to. That is what clipping physically *is*.

---

## 2. The array catalogue

One block is a hundred square metres of ground at the mod's ten metres to the block, so the
module count is not a figure anybody chose — it is however many of that module the mounting
can cover the block with. Everything else follows.

| Block | Product | Mounting | Module | Modules | DC | Ground cover |
|---|---|---|---|---|---|---|
| `solar_panel` | Meridian FT-415 | flat table | Helion HP-108-415 PERC | 44 (22×2) | 18.3 kW | 86% |
| `pv_flat_430` | Meridian FT-430 | flat table | Helion HJ-132-430 HJT | 44 (22×2) | 18.9 kW | 85% |
| `pv_tilt_580` | Meridian TR-580 | fixed tilt, 25° | Helion HN-144-580 TOPCon | 18 (18×1) | 10.4 kW | 47% |
| `pv_tilt_530` | Meridian TR-530 | fixed tilt, 25° | Helion HT-268-530 CdTe | 18 (6×3) | 9.5 kW | 50% |
| `pv_track_700` | Meridian HX-700 | single-axis tracker | Helion HN-132-700 TOPCon | 13 (13×1) | 9.1 kW | 40% |
| `pv_dual_440` | Meridian AE-440 | dual-axis tracker | Helion HB-066-440 IBC | 13 (13×1) | 5.7 kW | 25% |

`solar_panel` keeps that registry name because worlds already contain blocks under it — and
it is the right one to keep it, because a flat table of cheap modules is what the old
placeholder always was.

### Why the trackers carry so much less

Because rows that tilt have to be spaced so they do not shade each other, and a tracker
spends the morning and evening tilted hard over. Those ground cover figures are the
published ranges: 0.40–0.60 fixed tilt, 0.28–0.50 for a horizontal single axis, 0.20–0.30
dual axis, and up to 0.95 on a flat ballasted roof.

What a tracker buys is not a higher peak — the peak is the same — but a **plateau instead of
a bell**, so it makes considerably more energy per kilowatt installed and less per block.
On a clear day here a horizontal single axis collects about a third more light on its plane
than a flat table does, and a dual-axis frame about four percent more than that.

Which of land and hardware is your scarce thing is the same argument the industry has been
having for twenty years, and the answer here depends on the same things: how much space you
have, and whether it snows.

### The modules

Six, and they behave differently in ways that matter.

| Module | Technology | W | Efficiency | γPmax | Voc | Bifaciality |
|---|---|---|---|---|---|---|
| HP-108-415 | PERC | 415 | 21.3% | −0.34 %/K | 37.6 V | 70% |
| HJ-132-430 | HJT | 430 | 22.2% | −0.24 %/K | 39.5 V | — |
| HB-066-440 | IBC | 440 | 22.8% | −0.27 %/K | 80.5 V | — |
| HT-268-530 | CdTe thin film | 530 | 18.9% | −0.32 %/K | 223.0 V | — |
| HN-144-580 | TOPCon | 580 | 22.5% | −0.29 %/K | 51.2 V | 80% |
| HN-132-700 | TOPCon, 210 mm | 700 | 22.5% | −0.29 %/K | 46.7 V | 80% |

* **The temperature coefficient decides the desert argument.** Heterojunction at −0.24 %/K
  keeps about three percent more of its nameplate at seventy degrees of cell temperature than
  PERC at −0.34 does. Every hot afternoon, for twenty years.
* **Voltage decides string length.** A back-contact module at 80.5 V open circuit goes
  seventeen to a 1500 V string where a 51 V TOPCon goes twenty-seven, so the same nameplate
  arrives as a completely different pair of numbers on the inverter's input.
* **Thin film is the outlier on purpose.** Two thirds the efficiency, so it needs half again
  the glass. Two hundred and twenty-three volts at three amps, so six fill a string. And the
  flattest low-light curve here with a spectral response shifted blue — so under the sort of
  overcast that ruins a silicon plant it is barely troubled.

Efficiency is not in the catalogue as a declared figure. It is the nameplate over the area,
so it cannot disagree with either.

---

## 3. The inverters

| Block | Product | AC | MPPT | Strings | Window | Max DC | ηpeak | ηeuro | Night |
|---|---|---|---|---|---|---|---|---|---|
| `inverter_10` | Volterra VX-10K | 10 kW | 2 | 4 | 140–980 V | 15 kW | 98.6% | 98.2% | 1 W |
| `inverter_110` | Volterra VX-110K | 110 kW | 9 | 18 | 200–1000 V | 165 kW | 98.7% | 98.3% | 2 W |
| `inverter_350` | Volterra VX-350K | 352 kW | 16 | 32 | 500–1500 V | 528 kW | 99.0% | 98.7% | 3 W |
| `inverter_2500` | Volterra VC-2500K | 2500 kW | 1 | 288 | 875–1325 V | 3125 kW | 98.9% | 98.5% | 100 W |

The European efficiency is not a declared figure — it is the load curve averaged at the six
standard load points, which is what it means.

### Not every array goes in front of every inverter

**The tracking window is a real constraint**, and it is the one mismatch you can make without
being able to see it. A string sits at a voltage decided by how many modules are in series and
how hot they are; if that lands outside the machine's window it cannot be tracked, and the
plant sits in standby forever. So the panel names the window and the voltage rather than
leaving it at "standby".

| Array | String at 55 °C | VX-10K | VX-110K | VX-350K | VC-2500K |
|---|---|---|---|---|---|
| FT-415 | 635 V | ✓ | ✓ | ✓ | — |
| FT-430 | 674 V | ✓ | ✓ | ✓ | — |
| TR-580 | 725 V | ✓ | ✓ | ✓ | — |
| TR-530 | 1006 V | — | — | ✓ | ✓ |
| HX-700 | 476 V | ✓ | ✓ | — | — |
| AE-440 | 810 V | ✓ | ✓ | ✓ | — |

The thin-film rack is the only thing the central machine can use, because 223 V a module fills
a 1500 V string in six and six of them sit inside that narrow high window. That is not an
accident of this catalogue — it is why real central inverters are sold with a string design
rather than a voltage range.

**String against central** is the real choice. A string inverter has a maximum power point
tracker per few strings, so a shaded, soiled or failed row only drags down its own tracker.
The central machine has one tracker for two and a half megawatts, so the weakest string in
the plant sets the operating point for all of them — and in exchange it is cheaper per watt
and lives in one place. That is what the MPPT count in the table is telling you, and it is
why the central machine's input window is narrow and high: it is designed around one string
length and the strings are built to suit it.

**They draw power overnight.** A watt on the residential machine, a hundred on the central
one, and it is why a real plant's meter reads slightly negative between sunset and sunrise —
the controller, the communications and the insulation monitoring all have to stay alive. The
panel shows it as a negative active power. A stopped inverter still draws it.

**They derate.** Full output to 45 or 50 °C of air and backing off above, down to half
nameplate at the top of the range. On the panel that is the cabinet gauge crossing into the
red zone, and it looks exactly like clipping on a trend — the difference is that clipping is
cured by a bigger inverter and derating is cured by shade.

---

## 4. The trackers

| Tracker | Axes | Range | Slew | Deadband | Wind stow | Snow stow | Fitted to |
|---|---|---|---|---|---|---|---|
| Meridian Horizon R | 1, independent row, self-powered | ±60° | 4.4 °/min | 2.0° | 20 m/s | 60° | HX-700 |
| Meridian Zenith AE | 2, azimuth-elevation pedestal | ±80° | 3.0 °/min | 1.0° | 18 m/s | 70° | AE-440 |

Two, and the table says which array carries each — because a datasheet nothing can be built
with is a datasheet that is wrong. A third was written for a linked-row drive and then had no
product to go in, so it went.

The slew rate is against the **sky**, not the tick counter. One tick of Minecraft's day clock
stands for 3.6 seconds of real weather, so 4.4 degrees a minute converts to about seventeen
times the fifteen degrees an hour the sun moves — which is the ratio the real pair have.

### The deadband, which is why a row stands still and then jerks

**A controller does not follow the sun.** It works out where the sun is, compares that with an
inclinometer, and starts the motor only when the error passes the deadband. Three reasons, all
of them binding: a slew drive cannot be run at the sun's own quarter of a degree a minute, a
motor's starting current is paid per start so you want few and decisive ones, and being two
degrees off costs `cos(2°)` — six hundredths of one percent.

So a row holds still for seconds at a time and then snaps two degrees in half a second. That
is not a concession to the eye; it is what the datasheet describes, and it is why a real solar
farm looks like a photograph until you watch one row for a minute. Trying to spend the drive's
capability smoothly instead gives a row turning at a fifth of a degree a second — real,
correct, and completely invisible, so the machine reads as broken and the only motion anybody
ever sees is a stow.

The band is a **tracking** tolerance, so it applies only while the row is following the sun. A
stow and a hand position are commanded positions and are driven home exactly: the point of
going flat for the night is to be flat, not to be within two degrees of it.

### Modes

**AUTO** follows the sun, backtracks when it has to, and stows itself. **MANUAL** holds the
angle on the slider. **STOW** goes flat and stays there — what to set before weather you can
see coming and the controller cannot.

In AUTO the order of precedence is the safe one, and the panel says which applies:

| Stow | When | Where it goes |
|---|---|---|
| Wind | mean over the threshold, or a gust a fifth past it | flat, so a horizontal gust has no leverage on the tube |
| Snow | snow lying on the modules | steep, to drop the lot |
| Night | the sun is down | flat, so the dew runs off |
| Diffuse | flat would collect more | flat, to see as much of the dome as possible |

**Wind and snow are protections**, and they are slow on purpose: the wind stow latches with
hysteresis, releases at four fifths of the threshold, and is held for thirty-six minutes after
its cause has gone. You do not come out of a wind stow the instant a gust drops, because the
next gust is a minute away and standing a row up between them is how they get destroyed.

**The wind is supervised on the mean and on the gust separately**, against their own limits,
which is how the real pair are written and what the turbines here are already held to. A stow
wind of 20 m/s on a datasheet is a sustained figure; the gust limit that goes with it is about
a fifth higher. Watching a three-second gust against the *mean's* number is a real mistake and
this had it: a gust runs about 1.4 times the mean, so the row went flat whenever the mean
passed fourteen. Measured over six worlds and three kinds of ground, that is **six to thirteen
percent of all daylight spent stowed**, in rare episodes lasting minutes — a tracker lying down
for no reason a player could see. Against the right pair it is one to four percent.

**Night is not held**, because its release is the one condition here that cannot chatter: the
sun comes up once and stays up. Dwelling on it only delayed every dawn by half a minute.

The fourth is not a protection but a **choice between two angles**, and it is decided by
working out what each would collect — through the same transposition model the array's own
output is worked out with — rather than by testing the sky against a number. A flat plane
sees the whole sky dome; a tilted one sees `(1 + cos(tilt))/2` of it and makes the loss back
on the beam. Under thick cloud there is no beam to make it back with, so flat wins; under any
real beam it does not. Where the crossover falls depends on the cover, on how high the sun
is, and on the ground's albedo — which is exactly why no fixed number could express it.

Two earlier versions of this were threshold laws, first on the diffuse fraction and then on
the beam, and **both were worse than having no diffuse mode at all**. Measured against an
oracle that picks the better plane at every instant, across six worlds, three sites and six
days:

| law | flat, as a share of daylight | changes of mind per day | of the ceiling |
|---|---|---|---|
| diffuse fraction over 0.80 | — | many an hour | — |
| beam under 60 W/m², 36 min dwell | 45% | 2.1 | 98.1% |
| **flat collects 5% more, 6 min dwell** | **35%** | **3.3** | **99.5%** |
| no diffuse mode at all | 2% | 0 | 99.3% |

A fixed threshold cannot know that at a low sun a tilted plane still beats a flat one even
under thick cloud, so it lay the row down when it should have been tracking. Note also that
the long dwell is *harmful* here: six hundred ticks instead of a hundred gives up a whole
point of the ceiling, which is more than the whole mode is worth. A dwell that protects a
drive is not the same thing as a dwell that filters a measurement.

### Backtracking

At low sun a tracker pointed straight at it would shadow the row behind. So below a certain
elevation it backs off on purpose, giving up beam to keep every module in the string lit —
because a shaded cell in a series string drags the whole string down through its bypass diode
and costs far more than the cosine does.

A row normal to the sun casts a shadow `L / sin(α)` wide, so shading begins the moment
`sin(α) < GCR`. Below that, holding the shadow to exactly the row pitch needs

```
cos(R_true − R) = sin(α) / GCR
```

which, with true tracking at `R_true = 90° − α`, lands the row flat at sunrise and leaves
true tracking without a step in it. The panel shows the backtracking angle as a needle
against the drive's reachable band, and reports `backtracking` while it is happening.

### What the second axis is worth here, honestly

Almost nothing. Minecraft's sun rises due east, passes through the zenith and sets due west,
so it never leaves one vertical plane: a north-south horizontal axis holds a plane square to
it all day on its own, and an azimuth drive has nothing to chase. A tracker placed here
snaps its axis to north-south for that reason, whichever way you were facing.

What the dual-axis frame does buy is **reach**. A row limited to sixty degrees of rotation is
square to the sun only once the sun is thirty degrees up, and takes the first and last hour
of every day at an angle. A pedestal frame that tilts to eighty degrees faces a sun ten
degrees off the horizon. That is worth a few percent of the day and it costs a great deal of
ground — which is why the real world builds single-axis trackers by the gigawatt and
dual-axis ones for concentrators and car parks.

---

## 5. Where the light comes from

Three quantities, not one, because everything about shading depends on telling them apart.

* **Direct normal** — the beam, on a surface square to the sun. What a shadow takes away
  entirely and what a tracker exists to collect.
* **Diffuse horizontal** — the sky itself. What is left on an overcast day.
* **Global horizontal** — both, on the flat. What a single pyranometer reads.

They are not independent: `GHI = DNI · sin(α) + DHI` always, and that identity is what keeps
the model honest when cloud takes the beam away and hands part of it back as diffuse.

Under a clear zenith sun the model gives **953 W/m² of beam, 89 of diffuse and 1041 on the
flat** — which is the thousand every module is rated at, arrived at rather than assumed.

### What cloud does

The beam falls linearly in cover, because cover is the fraction of the sky with cloud in
front of it and therefore the chance the sun's own patch is covered. The global falls as a
steep power of it (Kasten & Czeplak). Between them:

| Cover | DNI | DHI | GHI | Diffuse fraction |
|---|---|---|---|---|
| 0.0 | 953 | 89 | 1041 | 0.09 |
| 0.5 | 476 | 491 | 967 | 0.51 |
| 0.9 | 95 | 400 | 496 | 0.81 |
| 1.0 | 0 | 260 | 260 | 1.00 |

At half cover the total has barely moved and the split has gone from a tenth diffuse to a
half. **That is the whole shape of a partly cloudy day**, and it is why a broken sky costs a
fixed array almost nothing and costs a tracker a great deal — a tracker's advantage is
entirely in the beam.

### Onto the plane

The beam by the cosine of the incidence angle and what the glass passes at that angle
(Martin & Ruiz, about 4% lost at 60° and a third at 80°). The sky by Hay & Davies with
Reindl's horizon term, so a tilted plane sees less sky than a flat one but gets a share of
the diffuse from the sun's own direction. The ground by albedo × global × how much ground the
plane can see.

A **bifacial** module's back sees mostly lit ground, so it collects albedo × global × a view
factor that belongs to the mounting: 0.08 on a flat table, 0.14 on a fixed tilt, 0.20 on an
elevated tracker. Times the module's bifaciality, that is a couple of percent over grass and
a tenth over fresh snow — which is why developers gravel the ground under bifacial plants and
why an albedometer is the one instrument that earns nothing on a monofacial site.

| Ground | Albedo |
|---|---|
| Fresh snow | 0.80 |
| Sand, beach, desert | 0.35 |
| Badlands | 0.30 |
| Grass, savanna | 0.22 |
| Rock, mountain | 0.20 |
| Forest | 0.14 |
| Jungle | 0.12 |
| Water | 0.07 |

### Cell temperature

Faiman, which IEC 61853 adopts, and the important part is that **wind is in it**: the same
module in the same sun runs some fifteen degrees cooler in a stiff breeze than in still air,
and on a hot calm afternoon that is worth four or five percent of the output.

The still-air coefficient is derived from each module's own NOCT rather than shared, because
those are two descriptions of one thing — so a module whose datasheet says it runs cool
actually runs cool. A 45 °C module comes out at 25.2 W/m²K against Faiman's own measured 25.0.

### Spectrum, and low light

Two small corrections that pull opposite ways. A long atmospheric path reddens the light,
which costs any cell that collects blue. Diffuse light is blue, because scattering is what
made it diffuse — so an overcast sky, which costs a great deal of intensity, hands back a
little quality. Silicon swings a couple of percent either way and thin film about three times
as far, which is a real part of why thin film holds up in climates that disappoint silicon.

And a module's efficiency is not flat in irradiance: 98% of rated at 200 W/m² and 95% at 100,
which is the shunt resistance carrying a fixed share of a shrinking current.

---

## 6. Shading

Four kinds, and they are not interchangeable.

**Something overhead** is traced along the sun's actual direction, so a wall to the east
shades an array at breakfast and not at lunch. Obstructions **attenuate rather than switch**:

| Over the array | Beam that gets through |
|---|---|
| Glass, stained glass | 88% |
| Glass panes, iron bars | 92% |
| Ice | 70% |
| Packed and blue ice | 35% |
| Water | 50% |
| Leaves | 25% |
| Anything solid | 0% |

The **sky** is shaded separately, by how much of the dome the array can see — nine rays,
weighted by how much each direction contributes to a plane, resurveyed every ten seconds
because it changes when somebody builds rather than when the sun moves.

That separation is the point. Blocking the beam under a clear sky costs **91%** of the
plane's irradiance. The same obstruction under a full overcast costs **nothing at all**,
because there is no beam left to block. A model with one lumped irradiance figure gets both
ends wrong.

**Something moving over it** — a player standing on an array, a mob wandering across —
takes its own footprint's share of the block along the sun's direction, so a player costs
about a third of one array block and it moves with them.

**The array shading itself** is geometry rather than obstruction, and it is what ground cover
ratio is *for*. A flat array never shades itself at any ground cover, which is why the flat
table here can be packed nearly solid. A 25° rack at 47% cover starts shading below about 19°
of sun elevation and loses a third of its rows by 10°. A tracker holds it at zero by
backtracking.

**Neighbouring arrays do not shade each other** beyond that, and that is correct rather than
a simplification: a block is a hundred square metres carrying several rows, so the next block
along simply continues the same rows at the same pitch. The shadow crossing the boundary is
the shadow the rows inside the block are already casting on each other, and counting it twice
would be counting it twice.

**So a large field produces very nearly the same everywhere.** The only thing that separates
one array from its neighbour is cloud drifting across, plus a couple of percent of factory
tolerance — which is exactly what a real field's trends look like.

---

## 7. Snow and dirt

**Snow buries an array**, and this is the loss a cold-climate plant actually loses its winter
to. It is read out of the world rather than accumulated, so if there is snow on the block
there is snow on the modules. One vanilla snow layer stands for five centimetres, and five
centimetres passes about 8% of the light.

An array **sheds** snow if it is tilted at least 30° and its modules are above freezing — and
when it does, it *removes the snow block*, because reporting itself clear while sitting under
a snow layer that never went anywhere would be a lie. Below 30° it cannot, which is the field
data's own threshold: elevated modules at 30 and 45 degrees shed within a day or two and lose
5 to 12% of the year, while below thirty an array covered early in the winter can stay covered
until spring.

That is the tracker's one unanswerable advantage in a cold climate. It stands up and drops the
lot; a flat table waits for a thaw.

**Soiling** accumulates at a tenth to three tenths of a percent a day depending on how dry the
biome is — and the causation runs the way you would not guess: a desert is not worse because
it is dusty, it is worse because it never rains to wash it. It saturates at 15%.

Rain washes it off, and **how much depends on the tilt**: rain running off a tilted module
carries the dust with it, while rain on a flat one pools, spreads it about and dries dirty. So
a flat array in a dry climate carries a standing soiling loss that never quite washes out,
which is a real and well documented nuisance.

---

## 8. The meteorological mast

Nine instruments, seven on the mast and two on the array — because that is where they belong.
Module temperature has to be measured on a module, and a module a hundred metres away on
different racking is not representative of this one. Everything else measures the sky, and the
sky is the same across a site, so IEC 61724 asks for one or two masts for a whole plant.

| Instrument | Reads | Specification |
|---|---|---|
| Kelvinsen SP-11 | global, and plane-of-array | ISO 9060 Class A, 285–2800 nm, 5 s to 95% |
| Kelvinsen SA-11 | albedo | Class A, two back to back, one inverted |
| Kelvinsen SD-11 | diffuse | Class A under a shadow ring |
| Kelvinsen RC-1 | effective plane irradiance | silicon reference cell, 350–1150 nm, 50 ms |
| Kelvinsen MT-1000 | module temperature | PT1000 Class A on the back sheet *(on the array)* |
| Kelvinsen AT-7 | air temperature | Class A in a naturally aspirated shield |
| Kelvinsen SN-50 | snow distance, snow height | ultrasonic, 0.5–10 m, ±1 cm |
| Kelvinsen WS-3 | wind speed | cup anemometer, 0–75 m/s |
| Kelvinsen WV-3 | wind direction | vane, ±3° |

**Each instrument is at the height its own standard puts it.** A block is ten metres
throughout this mod, so the mast is a ten-metre one — which is exactly where the world's
weather services measure wind, and where the anemometer and vane go. Everything else
belongs much lower: the radiometers on a boom at 4 m pointing away from the mast so its
shadow cannot fall on them, the radiation shield at 2.4 m which is the standard screen
height for air temperature, and the snow gauge on its own arm at 2.3 m looking down at
clear ground — the same two metres the depth is worked out from, so the picture and the
arithmetic agree.

**The readings are not simply the weather**, and that is the point of having a mast at all.
Each one has been through its instrument's own time constant, so a thermopile pyranometer
takes five seconds to answer a cloud edge that the modules answered instantly. Log the
pyranometer beside the inverter's output and you will see the power move first — which is how
a real operator knows the sensor is telling the truth slowly rather than the array telling
lies.

The pairs on the panel are deliberate and each is a check on something:

* **Global against plane-of-array** — the same sky through two mountings. Equal when the
  reference array is flat, and the gap is what its tilt or its tracker is worth.
* **Plane-of-array against the reference cell** — the same plane through two instruments. The
  cell sees only what the modules see, so it disagrees whenever the light is not AM1.5.
* **Diffuse fraction against clearness index** — two ways of saying how much sky there is.
* **Snow distance against snow height** — what the gauge measured and what was worked out from
  it. The two parting company is how a real plant learns its transducer has iced up.

The mast picks the nearest array to fit its two on-array instruments to, and its panel names
which one. That is exactly how a real plant is wired: a tilted pyranometer and a resistance
thermometer out on one representative array, cabled back to the same logger.

---

## 9. What to expect from a site

**Wind and sun fail together.** The same pressure map decides both, so calm weather and clear
skies arrive at once and a gale is overcast. A grid built on the two has to survive them going
quiet together, exactly as a real one does.

**Biome matters the way it really does.** Cloud is lifting times moisture rather than lifting
plus moisture, which is what makes a desert sunny: a deep low over sand brings wind and no
cloud, because there is nothing there to condense.

| | Desert | Temperate | Taiga | Rainforest |
|---|---|---|---|---|
| Clear-sky light reaching the ground | 0.92 | 0.74 | 0.66 | 0.64 |
| Albedo under the array | 0.35 | 0.22 | 0.14 | 0.12 |

A desert out-produces a rainforest by about a quarter, which is what the real pair do at one
latitude — and it does it *despite* running the cells thirty degrees hotter, because the
sunlight wins. Altitude helps too, part thinner atmosphere and part cooler cells.

**Yields here are equatorial.** Every Minecraft day is an equinox at the equator, so a
well-sited array makes something near a fifth of its nameplate over a year rather than the
eleventh a temperate country manages. It is also why a tilt is a maintenance decision here
rather than an optimisation: horizontal is square to the noon sun every day, and a 25° rack
gives up about 8% of the day's light in exchange for shedding snow and washing itself.

---

## 10. Crafting

Not yet. The array that keeps the `solar_panel` name keeps its old recipe; everything else is
creative-only for now.
