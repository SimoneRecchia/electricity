package com.dooji.electricity.api.power;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * One turbine model, described the way a manufacturer's datasheet describes it.
 *
 * Everything a machine differs by lives in here, so a new model is data rather
 * than code. The split between what is declared and what is computed is
 * deliberate: only the figures a datasheet actually prints are fields, and every
 * relation that would hold on a real machine is a method. Rated wind speed is the
 * clearest case — it is not a free parameter, it is wherever the generation curve
 * happens to meet the generator's nameplate, so declaring it separately would just
 * be a second number free to drift away from the first.
 *
 * <h2>Scale</h2>
 *
 * The dimensions here are the real machine's, in metres, and they are what the
 * physics and the telemetry work on. The world renders them at
 * {@link #METRES_PER_BLOCK}, because a 130 m rotor drawn at one block per metre
 * would not fit under the build height together with its tower. So a C130 has a
 * 130 m rotor that occupies thirteen blocks, and the GUI reports the 130.
 *
 * That divisor also means the swept area in blocks is a hundredth of the real one,
 * which is why {@link #powerAtKw} must never be fed the on-screen geometry: the
 * numbers it works on are metres throughout, and the block grid is presentation.
 */
public record TurbineSpec(
		ResourceLocation id,
		/** Model designation, e.g. {@code C90-3.0}. A proper noun: never translated. */
		String displayName,
		/** IEC 61400-1 site class, e.g. {@code IIA}. Empty for machines outside the classification. */
		String iecClass,
		double ratedPowerKw,
		double rotorDiameterM,
		/**
		 * Peak power coefficient: the best fraction of the wind's power this rotor ever
		 * takes. Real utility machines reach 0.44 to 0.48, small ones nearer 0.35, and
		 * nothing can pass {@link #BETZ_LIMIT}.
		 */
		double peakCp,
		double cutInSpeed,
		double cutOutSpeed,
		/** Where storm control starts shedding output. Equal to {@link #cutOutSpeed} on a machine without it. */
		double stormOnsetSpeed,
		int minTowerSegments,
		int maxTowerSegments,
		/**
		 * Visual exaggeration of the rotor, on top of {@link #METRES_PER_BLOCK}.
		 *
		 * 1.0 for the whole C line, which is what keeps the convention legible: a C90
		 * really is nine blocks across. The small-wind machine is the one exception,
		 * because a 7 m rotor divided by ten is smaller than its own nacelle and would
		 * read as a desk fan rather than a turbine.
		 */
		double rotorRenderScale,
		Nacelle nacelle
) {
	/** How many metres of real machine one block of world represents. */
	public static final double METRES_PER_BLOCK = 10.0;
	/** Air density at sea level and 15 C, the reference every power curve is quoted at. */
	public static final double REFERENCE_AIR_DENSITY = 1.225;
	/** 16/27: no rotor of any design extracts more than this fraction of the wind's power. */
	public static final double BETZ_LIMIT = 16.0 / 27.0;
	/**
	 * Rotor diameter of the authored model, in blocks, measured from the hub centre of
	 * {@code wind_turbine.obj}. Every model renders as this one scaled, so the figure is
	 * the denominator of that scale rather than a property of any particular machine —
	 * it happens to come out at the C130, which is why that one draws at 1:1.
	 */
	public static final double MODEL_ROTOR_DIAMETER_BLOCKS = 13.044;
	/** Smallest the nacelle is ever drawn, so the small-wind machine keeps a body worth texturing. */
	private static final double SMALL_WIND_NACELLE_FLOOR = 0.45;
	/** Fraction of rated output shed per m/s above the storm onset. */
	private static final double STORM_DERATE_PER_MS = 0.2;

	public enum Nacelle {
		/** Three blades, pitch regulated, active yaw. Everything from the C52 up. */
		UTILITY,
		/** Downwind of a tail vane, no yaw drive. The small-wind machine. */
		SMALL_WIND
	}

	public TurbineSpec {
		if (ratedPowerKw <= 0.0) throw new IllegalArgumentException(id + ": rated power must be positive");
		if (rotorDiameterM <= 0.0) throw new IllegalArgumentException(id + ": rotor diameter must be positive");
		if (peakCp <= 0.0 || peakCp > BETZ_LIMIT) {
			throw new IllegalArgumentException(id + ": peak Cp " + peakCp + " is outside (0, " + BETZ_LIMIT + "], the Betz limit");
		}

		if (cutInSpeed < 0.0 || cutOutSpeed <= cutInSpeed) throw new IllegalArgumentException(id + ": cut-out must be above cut-in");
		if (stormOnsetSpeed < cutInSpeed || stormOnsetSpeed > cutOutSpeed) {
			throw new IllegalArgumentException(id + ": storm onset must fall between cut-in and cut-out");
		}

		if (minTowerSegments < 1 || maxTowerSegments < minTowerSegments) throw new IllegalArgumentException(id + ": invalid tower segment range");
		if (rotorRenderScale <= 0.0) throw new IllegalArgumentException(id + ": rotor render scale must be positive");
	}

	// ---- geometry ----

	public double sweptAreaM2() {
		double radius = rotorDiameterM / 2.0;
		return Math.PI * radius * radius;
	}

	/**
	 * Nameplate power per square metre of rotor, the ratio that decides what kind of
	 * site a machine belongs on. Real designs run from about 200 W/m² for a low-wind
	 * rotor to 450 for a high-wind one; the same generator on a bigger rotor gives a
	 * lower figure, reaches its plateau in less wind, and is therefore the machine for
	 * a quiet site.
	 */
	public double specificPowerWPerM2() {
		return ratedPowerKw * 1000.0 / sweptAreaM2();
	}

	/** On-screen rotor diameter in blocks, exaggeration included. */
	public double rotorDiameterBlocks() {
		return rotorDiameterM / METRES_PER_BLOCK * rotorRenderScale;
	}

	/** Scale to draw the authored rotor at. */
	public double renderScale() {
		return rotorDiameterBlocks() / MODEL_ROTOR_DIAMETER_BLOCKS;
	}

	/**
	 * Scale for the nacelle and the tower, which is not always the rotor's.
	 *
	 * Real machines are not self-similar in this one respect: the smaller the turbine the
	 * chunkier its nacelle is next to its rotor, because the gearbox and generator inside
	 * do not shrink as fast as the blades outside. Scaling the authored nacelle straight
	 * down would go the wrong way and, on the small-wind machine, would put it under a
	 * fifth of a block, which is geometry too thin to read or to texture.
	 *
	 * So the C line takes the rotor's scale unchanged - the authored proportions are a
	 * utility machine's and stay right across that range - and the small-wind machine gets
	 * a floor instead, coming out chunkier than its rotor exactly as the real thing is.
	 */
	public double nacelleRenderScale() {
		double scale = renderScale();
		return nacelle == Nacelle.SMALL_WIND ? Math.max(scale, SMALL_WIND_NACELLE_FLOOR) : scale;
	}

	public int clampTowerSegments(int segments) {
		return Mth.clamp(segments, minTowerSegments, maxTowerSegments);
	}

	/**
	 * Hub height of a tower this many segments tall.
	 *
	 * This is the number the power curve cares about, because wind speed grows with
	 * height above the ground and the block grid is ten times too short to show it.
	 */
	public double hubHeightM(int towerSegments) {
		return clampTowerSegments(towerSegments) * METRES_PER_BLOCK;
	}

	/**
	 * Shortest tower that keeps the blade tips out of the ground, in segments.
	 *
	 * A rotor cannot be more than twice its hub height across, and a real machine
	 * leaves clearance on top of that. Callers get this as a floor on
	 * {@link #minTowerSegments} rather than a validation error, since it is a
	 * consequence of the rotor rather than a separate choice.
	 */
	public int minimumClearanceSegments() {
		return (int) Math.ceil(rotorDiameterBlocks() / 2.0 + 0.5);
	}

	// ---- the power curve ----

	/**
	 * Wind speed at which this machine first reaches its nameplate power.
	 *
	 * Derived rather than declared: it is where the cubic below meets the generator's
	 * limit, so the two cannot disagree. A datasheet usually prints a higher figure,
	 * because a real rotor's efficiency tails off gradually as it approaches rated and
	 * the last few percent arrive slowly. Below the knee the cubic tracks a real curve
	 * closely; above it, both are flat. The disagreement is confined to the band
	 * between the two knees.
	 */
	public double ratedSpeed() {
		double available = 0.5 * REFERENCE_AIR_DENSITY * sweptAreaM2() * peakCp;
		return Math.cbrt(ratedPowerKw * 1000.0 / available);
	}

	public double powerAtKw(double windSpeed) {
		return powerAtKw(windSpeed, REFERENCE_AIR_DENSITY);
	}

	/**
	 * Output at a given wind speed and air density, in kW.
	 *
	 * Density enters the cubic directly, which is the same correction IEC 61400-12
	 * applies to the wind speed by a factor of (rho/rho0)^(1/3) — cubing that factor
	 * puts it back exactly here. It matters more than it looks: cold air is denser, so
	 * the same wind on a winter night carries appreciably more power than on a summer
	 * afternoon.
	 */
	public double powerAtKw(double windSpeed, double airDensity) {
		if (windSpeed < cutInSpeed || windSpeed >= cutOutSpeed) return 0.0;

		double harvested = 0.5 * airDensity * sweptAreaM2() * peakCp * windSpeed * windSpeed * windSpeed / 1000.0;
		return Math.min(harvested, ratedPowerKw) * stormDerating(windSpeed);
	}

	/**
	 * How much of its output the machine keeps in a storm: 1.0 below the onset, falling
	 * to 0 at the cut-out.
	 *
	 * Coming off full load in one step is a shock to the drivetrain and to the grid
	 * behind it, so a real machine sheds output as the wind keeps rising instead of
	 * tripping. The ramp is continuous rather than stepped at whole m/s, since a
	 * staircase would put fresh discontinuities in the very curve this exists to smooth.
	 */
	public double stormDerating(double windSpeed) {
		if (windSpeed < stormOnsetSpeed) return 1.0;
		if (windSpeed >= cutOutSpeed) return 0.0;

		return Mth.clamp(1.0 - STORM_DERATE_PER_MS * (windSpeed - stormOnsetSpeed + 1.0), 0.0, 1.0);
	}

	/**
	 * Power coefficient actually being achieved at this wind speed.
	 *
	 * Unlike {@link #peakCp} this is a reading, not a property, and it is low at both
	 * ends: there is nothing to harvest below the cut-in, and above the knee the blades
	 * are deliberately spilling wind to hold the plateau. It peaks at the knee.
	 */
	public double cpAt(double windSpeed) {
		if (windSpeed <= 0.0) return 0.0;

		double wind = 0.5 * REFERENCE_AIR_DENSITY * sweptAreaM2() * windSpeed * windSpeed * windSpeed / 1000.0;
		if (wind <= 0.0) return 0.0;

		return Mth.clamp(powerAtKw(windSpeed) / wind, 0.0, BETZ_LIMIT);
	}

	/**
	 * Air density at a given temperature, from the ideal gas law at constant pressure.
	 *
	 * Pressure varies too little across the build height to matter — under four percent
	 * from bedrock to the height limit — while a swing from a snowy night to a desert
	 * afternoon moves density by fifteen, so temperature is the term worth carrying.
	 */
	public static double airDensityAt(double celsius) {
		return REFERENCE_AIR_DENSITY * 288.15 / Math.max(1.0, 273.15 + celsius);
	}

	// ---- drivetrain ----

	/**
	 * Tip speed ratio the rotor is held at below its rated wind: the blade tips travel
	 * about seven times the wind speed, which is where a three-bladed rotor of this kind
	 * takes the most out of the air.
	 */
	public static final double TIP_SPEED_RATIO = 7.0;
	/** Tip speed no real machine exceeds, in m/s. A noise limit rather than a strength one. */
	public static final double MAX_TIP_SPEED = 80.0;
	/** Synchronous speed of a 4-pole generator at 50 Hz: what every gearbox here is sized to reach. */
	public static final double GENERATOR_SYNCHRONOUS_RPM = 1500.0;

	/**
	 * Rotor speed in rpm at a given wind.
	 *
	 * Holding the tip speed ratio is what makes a big rotor turn visibly slower than a
	 * small one in the same wind: the tips have further to travel for each revolution. So
	 * the whole catalogue does not spin at one rate — the C130 comes out near 10 rpm and
	 * the small-wind machine near 200, which is the difference between the two that is
	 * most obvious from the ground.
	 *
	 * Above the rated wind the speed stops climbing, because that is what pitching the
	 * blades out is for: the machine holds both its speed and its power there.
	 */
	public double rotorRpmAt(double windSpeed) {
		if (windSpeed <= 0.0) return 0.0;

		double regulated = Math.min(windSpeed, ratedSpeed());
		double tipSpeed = Math.min(TIP_SPEED_RATIO * regulated, MAX_TIP_SPEED);
		return tipSpeed * 60.0 / (Math.PI * rotorDiameterM);
	}

	public double ratedRotorRpm() {
		return rotorRpmAt(ratedSpeed());
	}

	/**
	 * Gearbox step-up, derived so every machine reaches synchronous speed at its own
	 * rated wind. It has to be derived: a slow wide rotor needs a far taller ratio than
	 * a fast narrow one to arrive at the same 1500 rpm, so a single constant would put
	 * the generator badly off speed on most of the catalogue. A ratio near 1 means the
	 * machine is effectively direct drive.
	 */
	public double gearboxRatio() {
		double rotorRpm = ratedRotorRpm();
		return rotorRpm <= 0.0 ? 1.0 : GENERATOR_SYNCHRONOUS_RPM / rotorRpm;
	}

	/**
	 * Hub height the weather model's wind speed is quoted at.
	 *
	 * The zone wind is a single figure per area with no height to it, so it has to be
	 * pinned to one before a tower can be taller or shorter than it. Eighty metres is
	 * both the standard hub height for the 2 to 3 MW class and, more practically, the
	 * middle of this catalogue's range: pinning it there leaves the balance the mod
	 * already had roughly where it was, whereas quoting the same wind at the
	 * meteorological ten metres would extrapolate it up by nearly half and leave every
	 * machine pinned to its plateau in ordinary weather, with the power curve doing
	 * nothing.
	 */
	public static final double REFERENCE_HUB_HEIGHT_M = 80.0;
	/** Power-law shear exponent for open terrain. */
	private static final double SHEAR_EXPONENT = 0.14;

	/**
	 * Wind speed at a hub this high, from the speed at {@link #REFERENCE_HUB_HEIGHT_M}.
	 *
	 * Wind is slowed by friction with the ground, so a taller tower stands in faster
	 * air. The exponent is small, but power goes with the cube: across this catalogue's
	 * range of tower heights the wind varies by a third and the output by more than
	 * double, which is the whole reason real towers are worth what they cost.
	 *
	 * Terrain elevation deliberately does not enter here. The weather model already
	 * carries it as its own altitude bias, and counting it twice would pay a mountain
	 * turbine for its mountain in both places.
	 */
	public static double windAtHubHeight(double referenceWind, double hubHeightM) {
		return referenceWind * windRatio(REFERENCE_HUB_HEIGHT_M, hubHeightM);
	}

	/**
	 * How much faster the wind is at one hub height than at another.
	 *
	 * Separate from {@link #windAtHubHeight} because the interesting question is usually
	 * comparative - what one more tower segment would be worth - and asking it this way
	 * needs no detour back through the reference height, which would mean undoing a shear
	 * only to reapply it.
	 */
	public static double windRatio(double fromHubHeightM, double toHubHeightM) {
		if (fromHubHeightM <= 0.0 || toHubHeightM <= 0.0) return 1.0;

		return Math.pow(toHubHeightM / fromHubHeightM, SHEAR_EXPONENT);
	}
}
