package com.dooji.electricity.api.power;

import com.dooji.electricity.api.WorldConditions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * One turbine model, described the way a manufacturer's datasheet describes it.
  *
 * So a C130 has a 130 m rotor that occupies thirteen blocks, and the GUI reports the 130.
 */
public record TurbineSpec(
		ResourceLocation id,
		/**
		 * Model designation, e.g.
		  *
		 * {@code C90-3.0}.
		 */
		String displayName,
		/**
		 * IEC 61400-1 site class, e.g.
		  *
		 * {@code IIA}.
		 */
		String iecClass,
		double ratedPowerKw,
		double rotorDiameterM,
		/**
		 * Peak power coefficient: the best fraction of the wind's power this rotor ever takes.
		  *
		 * Real utility machines reach 0.44 to 0.48, small ones nearer 0.35, and nothing can pass {@link #BETZ_LIMIT}.
		 */
		double peakCp,
		double cutInSpeed,
		double cutOutSpeed,
		/**
		 * Where storm control starts shedding output.
		  *
		 * Equal to {@link #cutOutSpeed} on a machine without it.
		 */
		double stormOnsetSpeed,
		/** The rotor's operating speed range in rpm */
		double minRotorRpm,
		double maxRotorRpm,
		int minTowerSegments,
		int maxTowerSegments,
		/**
		 * Visual exaggeration of the rotor, on top of {@link WorldScale#METRES_PER_BLOCK}.
		  *
		 * 1.0 for the whole C line, which is what keeps the convention legible: a C90 really is nine blocks across.
		 */
		double rotorRenderScale,
		Nacelle nacelle
) {
	/** 16/27: no rotor of any design extracts more than this fraction of the wind's power. */
	public static final double BETZ_LIMIT = 16.0 / 27.0;
	/** Rotor diameter of the authored model, in blocks, measured from the hub centre of {@code wind_turbine.obj}. */
	public static final double MODEL_ROTOR_DIAMETER_BLOCKS = 13.044;
	/** Height of the authored tower, in blocks. */
	public static final double MODEL_TOWER_HEIGHT_BLOCKS = 12.43;
	/** Smallest the nacelle is ever drawn, so the small-wind machine keeps a body worth texturing. */
	private static final double SMALL_WIND_NACELLE_FLOOR = 0.25;


	public enum Nacelle {
		/**
		 * Three blades, pitch regulated, active yaw.
		  *
		 * Everything from the C52 up.
		 */
		UTILITY,
		/** Downwind of a tail vane, no yaw drive. */
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

		if (minRotorRpm <= 0.0 || maxRotorRpm < minRotorRpm) throw new IllegalArgumentException(id + ": invalid rotor speed range");
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

	/** Scale for the nacelle and the tower, which is not always the rotor's. */
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

	/** Hub height of a tower this many segments tall. */
	public double hubHeightM(int towerSegments) {
		return clampTowerSegments(towerSegments) * WorldConditions.METRES_PER_BLOCK;
	}

	// ---- the power curve ----

	/** How sharply the curve turns over as it approaches nameplate power. */
	private static final double KNEE_SHARPNESS = 4.0;
	/** What counts as having reached nameplate */
	private static final double RATED_POWER_FRACTION = 0.99;
	/** Where that fraction is reached, as a multiple of the sharp knee. */
	private static final double RATED_SPEED_FACTOR = ratedSpeedFactor();

	private static double ratedSpeedFactor() {
		double reached = Math.pow(RATED_POWER_FRACTION, KNEE_SHARPNESS);
		return Math.cbrt(Math.pow(reached / (1.0 - reached), 1.0 / KNEE_SHARPNESS));
	}

	/**
	 * Wind speed at which this machine reaches its nameplate power, in the sense a datasheet means it: where the curve gets to {@link #RATED_POWER_FRACTION} of nameplate.
	 */
	public double ratedSpeed() {
		return aerodynamicKneeSpeed() * RATED_SPEED_FACTOR;
	}

	/** Where the unrounded cubic would have crossed nameplate. */
	private double aerodynamicKneeSpeed() {
		double available = 0.5 * WorldConditions.REFERENCE_AIR_DENSITY * sweptAreaM2() * peakCp;
		return Math.cbrt(ratedPowerKw * 1000.0 / available);
	}

	/** Output at a given wind speed and air density, in kW, for a machine known to be on load. */
	public double powerWhileRunningKw(double windSpeed, double airDensity) {
		if (windSpeed < cutInSpeed) return 0.0;

		double harvested = 0.5 * airDensity * sweptAreaM2() * peakCp * windSpeed * windSpeed * windSpeed / 1000.0;
		return roundedToNameplate(harvested) * stormDerating(windSpeed);
	}

	/** Holds a figure under the nameplate without putting a corner in it. */
	private double roundedToNameplate(double harvested) {
		if (harvested <= 0.0) return 0.0;

		double ratio = harvested / ratedPowerKw;
		return harvested / Math.pow(1.0 + Math.pow(ratio, KNEE_SHARPNESS), 1.0 / KNEE_SHARPNESS);
	}

	/** How much of its output the machine keeps in a storm: 1.0 at the onset */
	public double stormDerating(double windSpeed) {
		if (windSpeed < stormOnsetSpeed) return 1.0;
		if (windSpeed >= cutOutSpeed) return 0.0;

		// a machine with no storm control has its onset sitting on its cut-out
		// band to ramp across and the two tests above have already answered
		double band = cutOutSpeed - stormOnsetSpeed;
		return band <= 0.0 ? 0.0 : Mth.clamp((cutOutSpeed - windSpeed) / band, 0.0, 1.0);
	}

	// ---- drivetrain ----

	/**
	 * Tip speed ratio the rotor is held at while it can be: the blade tips travel about eight times the wind speed, which is where a modern three-bladed rotor takes the most out of the air - the optimum sits between seven and nine.
	 */
	private static final double TIP_SPEED_RATIO = 8.0;
	/** Speed a four-pole generator runs at when the machine is at full load. */
	private static final double GENERATOR_RATED_RPM = 1800.0;
	/** Tip speed ratio of a rotor that is idling rather than working. */
	private static final double IDLING_TIP_SPEED_RATIO = 2.0;
	/** Ceiling on an idling rotor's speed, as a fraction of rated. */
	private static final double IDLING_SPEED_FRACTION = 0.15;
	/** Degrees of rotation per tick for one rpm: 360 degrees over 60 seconds over 20 ticks. */
	public static final double DEGREES_PER_TICK_PER_RPM = 0.3;

	/** Speed of a rotor that is turning but not working, in rpm. */
	public double idlingRpmAt(double windSpeed) {
		if (windSpeed <= 0.0) return 0.0;

		return Math.min(rpmForTipSpeed(IDLING_TIP_SPEED_RATIO * windSpeed), maxRotorRpm * IDLING_SPEED_FRACTION);
	}

	/** Rotor speed in rpm at a given wind. */
	public double rotorRpmAt(double windSpeed) {
		// below the cut-in there is nothing to hold a speed with, so the rotor is idling
		if (windSpeed < cutInSpeed) return idlingRpmAt(windSpeed);

		return Mth.clamp(rpmForTipSpeed(TIP_SPEED_RATIO * windSpeed), minRotorRpm, maxRotorRpm);
	}

	private double rpmForTipSpeed(double tipSpeed) {
		return tipSpeed * 60.0 / (Math.PI * rotorDiameterM);
	}

	/** How fast a rotor at its rated speed is drawn turning */
	private static final double DRAWN_REVOLUTIONS_PER_SECOND_AT_RATED = 0.40;

	/**
	 * The rotation to draw, from the rotation the machine is actually doing.
	  *
	  * @see #DRAWN_REVOLUTIONS_PER_SECOND_AT_RATED
	 */
	public double drawnRotation(double realDegreesPerTick) {
		double atRated = maxRotorRpm * DEGREES_PER_TICK_PER_RPM;
		if (atRated <= 0.0) return 0.0;

		return realDegreesPerTick / atRated * DRAWN_REVOLUTIONS_PER_SECOND_AT_RATED * 360.0 / 20.0;
	}

	/** Gearbox step-up, derived so every machine reaches synchronous speed at its own rated wind. */
	public double gearboxRatio() {
		return maxRotorRpm <= 0.0 ? 1.0 : GENERATOR_RATED_RPM / maxRotorRpm;
	}

	// The wind profile used to live here, as a fixed 0.14 exponent applied to a single wind
}
