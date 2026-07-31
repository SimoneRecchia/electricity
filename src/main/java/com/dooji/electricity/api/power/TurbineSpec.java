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
		/**
		 * The rotor's operating speed range in rpm, as the datasheet prints it.
		 *
		 * Declared rather than derived, and that is a correction: it used to come out of one
		 * tip speed limit shared by the whole catalogue, which was right for a V80 at 80 m/s
		 * and thirty percent wrong for a V112, whose longer blades are allowed 104. The two
		 * ends are the real ones - a V52 runs 14.0 to 31.4 rpm, a V112 6.2 to 17.7 - and the
		 * bottom end is not decoration: below it there is not enough in the generator to stay
		 * on load, so the controller holds the speed there instead of tracking the wind.
		 */
		double minRotorRpm,
		double maxRotorRpm,
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
	 * How sharply the curve turns over as it approaches nameplate power.
	 *
	 * A real power curve has no corner in it. The textbook one does - it is the cubic until the
	 * generator's limit and flat after - but a machine approaches its ceiling over three to five
	 * m/s, because the rotor reaches its speed limit first and its efficiency falls away as the
	 * tip speed ratio drops, while the pitch controller takes over by degrees rather than at a
	 * stroke. That corner was this model's largest error against the real machines: the sharp
	 * knee arrived two to five m/s early, so every datasheet's rated wind speed disagreed with
	 * the mod's.
	 *
	 * Four rounds it about right. Checked against the published curves: a V90 comes to 98% of
	 * nameplate at 15 m/s against a datasheet 15, a V112 to 96% at 12 against a datasheet 12,
	 * and the implied power coefficient collapses from 0.44 to 0.16 across a V52's regulating
	 * band, which is what that machine's narrow speed range forces on it.
	 */
	private static final double KNEE_SHARPNESS = 4.0;
	/** What counts as having reached nameplate, for a curve that only ever approaches it. */
	private static final double RATED_POWER_FRACTION = 0.99;
	/** Where that fraction is reached, as a multiple of the sharp knee. Derived, so the two agree. */
	private static final double RATED_SPEED_FACTOR = ratedSpeedFactor();

	private static double ratedSpeedFactor() {
		double reached = Math.pow(RATED_POWER_FRACTION, KNEE_SHARPNESS);
		return Math.cbrt(Math.pow(reached / (1.0 - reached), 1.0 / KNEE_SHARPNESS));
	}

	/**
	 * Wind speed at which this machine reaches its nameplate power, in the sense a datasheet
	 * means it: where the curve gets to {@link #RATED_POWER_FRACTION} of nameplate.
	 *
	 * Still derived rather than declared, so it cannot drift away from the curve it describes -
	 * but now derived from the rounded curve rather than the cornered one, which is what brings
	 * it up onto the published figures. The C line lands between 14.6 and 15.5 m/s, against
	 * Vestas figures of 15 and 16 for the machines it is drawn from.
	 */
	public double ratedSpeed() {
		return aerodynamicKneeSpeed() * RATED_SPEED_FACTOR;
	}

	/** Where the unrounded cubic would have crossed nameplate. The scale of the knee, not a speed a machine reaches. */
	private double aerodynamicKneeSpeed() {
		double available = 0.5 * WorldConditions.REFERENCE_AIR_DENSITY * sweptAreaM2() * peakCp;
		return Math.cbrt(ratedPowerKw * 1000.0 / available);
	}

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
		return roundedToNameplate(harvested) * stormDerating(windSpeed);
	}

	/**
	 * Holds a figure under the nameplate without putting a corner in it.
	 *
	 * The smooth minimum of the harvested power and the nameplate: it follows the cubic while
	 * that is well under, approaches the nameplate from below without ever quite arriving, and
	 * passes through 84% of it exactly where the sharp version would have cornered.
	 */
	private double roundedToNameplate(double harvested) {
		if (harvested <= 0.0) return 0.0;

		double ratio = harvested / ratedPowerKw;
		return harvested / Math.pow(1.0 + Math.pow(ratio, KNEE_SHARPNESS), 1.0 / KNEE_SHARPNESS);
	}

	/**
	 * How much of its output the machine keeps in a storm: 1.0 at the onset, falling to 0 at the
	 * cut-out.
	 *
	 * Coming off full load in one step is a shock to the drivetrain and to the grid behind it,
	 * so a real machine sheds output as the wind keeps rising instead of tripping.
	 *
	 * The slope is derived from the two speeds rather than fixed, which also closes a hole: at a
	 * fixed fifth per m/s the ramp began at 80% instead of 100%, so a machine crossing its own
	 * storm onset dropped a fifth of its output in one tick - a step, in the middle of the curve
	 * that exists to remove steps. Now it leaves full load at the onset and arrives at nothing
	 * exactly at the cut-out, continuous at both ends.
	 */
	public double stormDerating(double windSpeed) {
		if (windSpeed < stormOnsetSpeed) return 1.0;
		if (windSpeed >= cutOutSpeed) return 0.0;

		// a machine with no storm control has its onset sitting on its cut-out, so there is no
		// band to ramp across and the two tests above have already answered
		double band = cutOutSpeed - stormOnsetSpeed;
		return band <= 0.0 ? 0.0 : Mth.clamp((cutOutSpeed - windSpeed) / band, 0.0, 1.0);
	}

	// ---- drivetrain ----

	/**
	 * Tip speed ratio the rotor is held at while it can be: the blade tips travel about eight
	 * times the wind speed, which is where a modern three-bladed rotor takes the most out of
	 * the air - the optimum sits between seven and nine.
	 *
	 * Eight rather than seven because seven left the longer-bladed machines short of their own
	 * datasheet speed: a C112 only reached 15.9 rpm of its published 17.7 by the time it was at
	 * rated power. At eight, every machine in the catalogue arrives at the top of its range
	 * exactly as it reaches nameplate, which is where a real one arrives.
	 */
	private static final double TIP_SPEED_RATIO = 8.0;
	/**
	 * Speed a four-pole generator runs at when the machine is at full load.
	 *
	 * Twenty percent above its 1500 rpm synchronous speed, because these are doubly-fed
	 * machines and that is where one sits at rated. Using the synchronous speed instead put
	 * every derived gear ratio a quarter low - a V80's works out at 95 against the 1:101 its
	 * datasheet prints, where synchronous gave 79.
	 */
	private static final double GENERATOR_RATED_RPM = 1800.0;
	/**
	 * Tip speed ratio of a rotor that is idling rather than working.
	 *
	 * A machine that is off load has its blades pitched most of the way out of the wind, so it
	 * makes almost no torque and settles at a far lower ratio than the 7 it is held at while
	 * generating. Two is about it.
	 */
	private static final double IDLING_TIP_SPEED_RATIO = 2.0;
	/**
	 * Ceiling on an idling rotor's speed, as a fraction of rated.
	 *
	 * Feathered blades make so little torque that the speed stops following the wind and is
	 * held down by the drivetrain's own friction instead - which is why a parked machine turns
	 * at much the same lazy pace in a gale as in a breeze. Real large machines idle at one to
	 * three rpm whatever the storm outside is doing.
	 */
	private static final double IDLING_SPEED_FRACTION = 0.15;
	/** Degrees of rotation per tick for one rpm: 360 degrees over 60 seconds over 20 ticks. */
	public static final double DEGREES_PER_TICK_PER_RPM = 0.3;

	/**
	 * Speed of a rotor that is turning but not working, in rpm.
	 *
	 * A turbine off load does not stand still, and that is worth stating because it is not the
	 * obvious behaviour. Below the cut-in wind and above the cut-out one alike, a real machine
	 * feathers its blades and lets the rotor idle at a couple of rpm rather than parking it:
	 * standing still lets the oil film drain off the main bearing and risks the races
	 * brinelling under the rotor's own weight, and an idling machine can come back on load in
	 * seconds where a stopped one has to be spun up. The mechanical brake exists for
	 * emergencies and for maintenance, not for weather.
	 */
	public double idlingRpmAt(double windSpeed) {
		if (windSpeed <= 0.0) return 0.0;

		return Math.min(rpmForTipSpeed(IDLING_TIP_SPEED_RATIO * windSpeed), maxRotorRpm * IDLING_SPEED_FRACTION);
	}

	/**
	 * Rotor speed in rpm at a given wind.
	 *
	 * <h2>What the speed follows</h2>
	 *
	 * The wind, not the load. A variable-speed pitch-regulated machine holds its tip speed
	 * ratio while it can, so the rotor speed rises in proportion to the wind; once it reaches
	 * the top of its speed range it stops rising, and holding speed while the power goes on
	 * climbing is exactly what pitching the blades does. Power meanwhile goes with the cube of
	 * the wind, so the two are only loosely related: across the partial-load band the speed
	 * varies by two while the output varies by more than thirty. Watching a real rotor tells
	 * you the wind, not the megawatts.
	 *
	 * The two ends of the range are the datasheet's, so the figures come out on the published
	 * ones: 31.4 rpm for the C52 against a V52's 31.4, 17.7 for the C112 against a V112's 17.7.
	 */
	public double rotorRpmAt(double windSpeed) {
		// below the cut-in there is nothing to hold a speed with, so the rotor is idling
		if (windSpeed < cutInSpeed) return idlingRpmAt(windSpeed);

		return Mth.clamp(rpmForTipSpeed(TIP_SPEED_RATIO * windSpeed), minRotorRpm, maxRotorRpm);
	}

	private double rpmForTipSpeed(double tipSpeed) {
		return tipSpeed * 60.0 / (Math.PI * rotorDiameterM);
	}

	/**
	 * How fast a rotor at its rated speed is drawn turning, in revolutions a second.
	 *
	 * A presentation figure rather than a physical one, and the only one in this class that is
	 * chosen by eye rather than derived. It is here because no single time scale suits the
	 * catalogue: every rotor is drawn a tenth of its real size, so a C130 reads as a thirteen
	 * metre rotor while turning at the ten rpm of a hundred-and-thirty metre one, and looks
	 * becalmed - while the small-wind machine, drawn three times larger than its scale, looked
	 * like a desk fan. Preserving the real speeds keeps that pair five times apart on screen,
	 * with one of them wrong at each end.
	 *
	 * So the drawn speed is not the real speed. It is the machine's speed as a fraction of its
	 * own rated speed, put on one common rate, which means every machine looks the same at full
	 * speed and slower in proportion when it is running slower. What a player reads off a rotor
	 * is then how hard that machine is working - which is what a rotor is worth watching for -
	 * rather than a rpm figure the panel prints anyway.
	 */
	private static final double DRAWN_REVOLUTIONS_PER_SECOND_AT_RATED = 0.40;

	/**
	 * The rotation to draw, from the rotation the machine is actually doing.
	 *
	 * Only the drawing is scaled. The speed the machine reports, the generator speed derived
	 * from it and everything the telemetry publishes stay the real ones - which is why the two
	 * had to be pulled apart before this could exist at all.
	 *
	 * @see #DRAWN_REVOLUTIONS_PER_SECOND_AT_RATED
	 */
	public double drawnRotation(double realDegreesPerTick) {
		double atRated = maxRotorRpm * DEGREES_PER_TICK_PER_RPM;
		if (atRated <= 0.0) return 0.0;

		return realDegreesPerTick / atRated * DRAWN_REVOLUTIONS_PER_SECOND_AT_RATED * 360.0 / 20.0;
	}

	/**
	 * Gearbox step-up, derived so every machine reaches synchronous speed at its own
	 * rated wind. It has to be derived: a slow wide rotor needs a far taller ratio than
	 * a fast narrow one to arrive at the same 1500 rpm, so a single constant would put
	 * the generator badly off speed on most of the catalogue. A ratio near 1 means the
	 * machine is effectively direct drive.
	 */
	public double gearboxRatio() {
		return maxRotorRpm <= 0.0 ? 1.0 : GENERATOR_RATED_RPM / maxRotorRpm;
	}

	// The wind profile used to live here, as a fixed 0.14 exponent applied to a single wind
	// speed quoted at a reference hub height of 80 m. Both have gone: the weather model is
	// asked for the wind at the height the machine actually stands at, and it works out the
	// profile from the roughness of the ground under that particular tower and from how the
	// air is layered at that hour. The exponent is not a constant of nature - it runs from 0.07
	// over water on a summer afternoon to 0.35 over forest on a still night - and a mod whose
	// whole premise is that towers are worth what they cost had no business pretending it was.
}
