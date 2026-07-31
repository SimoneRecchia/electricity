package com.dooji.electricity.api.power;

import com.dooji.electricity.api.WorldConditions;
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
 * {@link WorldScale#METRES_PER_BLOCK}, because a 130 m rotor drawn at one block per metre
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
		 * Visual exaggeration of the rotor, on top of {@link WorldScale#METRES_PER_BLOCK}.
		 *
		 * 1.0 for the whole C line, which is what keeps the convention legible: a C90
		 * really is nine blocks across. The small-wind machine is the one exception,
		 * because a 7 m rotor divided by ten is smaller than its own nacelle and would
		 * read as a desk fan rather than a turbine.
		 */
		double rotorRenderScale,
		Nacelle nacelle
) {
	/** 16/27: no rotor of any design extracts more than this fraction of the wind's power. */
	public static final double BETZ_LIMIT = 16.0 / 27.0;
	/**
	 * Rotor diameter of the authored model, in blocks, measured from the hub centre of
	 * {@code wind_turbine.obj}. Every model renders as this one scaled, so the figure is
	 * the denominator of that scale rather than a property of any particular machine —
	 * it happens to come out at the C130, which is why that one draws at 1:1.
	 */
	public static final double MODEL_ROTOR_DIAMETER_BLOCKS = 13.044;
	/**
	 * Height of the authored tower, in blocks.
	 *
	 * A tower of N segments is that tube stretched to N, which keeps it one continuous
	 * piece of geometry at any height. The taper being stretched is slight - radius 0.381
	 * at the foot to 0.312 at the top - so the stretch itself is invisible.
	 */
	public static final double MODEL_TOWER_HEIGHT_BLOCKS = 12.43;
	/**
	 * Smallest the nacelle is ever drawn, so the small-wind machine keeps a body worth
	 * texturing.
	 *
	 * A real small turbine does carry a chunkier body next to its rotor than a utility machine
	 * does, but not by much: at 0.45 this drew one two and a half times the authored
	 * proportion, which read as a barrel with a propeller taped to it. A quarter keeps the
	 * nacelle about four pixels tall - thin, but present - and lands within half again of the
	 * proportion the model was built at.
	 */
	private static final double SMALL_WIND_NACELLE_FLOOR = 0.25;
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

	/** On-screen rotor diameter in blocks, exaggeration included. */
	public double rotorDiameterBlocks() {
		return rotorDiameterM / WorldConditions.METRES_PER_BLOCK * rotorRenderScale;
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

	/** Whether a tower this many segments tall is one this machine is sold on. */
	public boolean acceptsTowerHeight(int segments) {
		return segments >= minTowerSegments && segments <= maxTowerSegments;
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
		return clampTowerSegments(towerSegments) * WorldConditions.METRES_PER_BLOCK;
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
		double available = 0.5 * WorldConditions.REFERENCE_AIR_DENSITY * sweptAreaM2() * peakCp;
		return Math.cbrt(ratedPowerKw * 1000.0 / available);
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
	/**
	 * Output at a given wind speed and air density, in kW, for a machine known to be on load.
	 *
	 * Density enters the cubic directly, which is the same correction IEC 61400-12 applies to
	 * the wind speed by a factor of (rho/rho0)^(1/3) - cubing that factor puts it back exactly
	 * here. It matters more than it looks: cold air is denser, so the same wind on a winter
	 * night carries appreciably more power than on a summer afternoon.
	 *
	 * The cut-out is deliberately not applied. Once the wind arrives gust by gust rather than
	 * as a ten-minute average, a gust past the cut-out must not switch a running turbine off -
	 * storm control just holds it further down - and the decision to come off load belongs to
	 * the controller, which supervises the mean and the gust separately with hysteresis on both.
	 */
	public double powerWhileRunningKw(double windSpeed, double airDensity) {
		if (windSpeed < cutInSpeed) return 0.0;

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

	// The wind profile used to live here, as a fixed 0.14 exponent applied to a single wind
	// speed quoted at a reference hub height of 80 m. Both have gone: the weather model is
	// asked for the wind at the height the machine actually stands at, and it works out the
	// profile from the roughness of the ground under that particular tower and from how the
	// air is layered at that hour. The exponent is not a constant of nature - it runs from 0.07
	// over water on a summer afternoon to 0.35 over forest on a still night - and a mod whose
	// whole premise is that towers are worth what they cost had no business pretending it was.
}
