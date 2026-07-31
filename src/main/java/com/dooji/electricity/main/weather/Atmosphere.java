package com.dooji.electricity.main.weather;

import net.minecraft.util.Mth;

/**
 * The physics of the mod's weather, as pure functions.
 *
 * <h2>Where the wind comes from</h2>
 *
 * It comes from a pressure map, the same way it does outside. A noise field stands in
 * for the pattern of highs and lows over the world; the wind aloft is read off the
 * gradient of that field by the geostrophic relation, which is why it blows along the
 * isobars rather than across them, why it is strongest where they crowd together, and
 * why it turns as a system passes rather than wandering at random. Everything else -
 * the speed at a hub, the turbulence, the gusts, the direction at the ground - is that
 * one wind brought down through the boundary layer to a particular height over a
 * particular kind of ground.
 *
 * The reward for doing it this way is that the model holds no state. The old one was a
 * cellular automaton whose cells fed each other, and it had the defect every such
 * scheme has if the feedback is not balanced exactly: a cell surrounded by loaded
 * neighbours behaved differently from an isolated one, so the weather depended on how
 * much of the world was loaded. Nothing here can do that.
 *
 * <h2>Scale</h2>
 *
 * Two compressions are needed to fit a planet's weather into a Minecraft world, and
 * both are stated rather than hidden.
 *
 * Time is not compressed at all, it is already compressed: a Minecraft day is a real
 * day in twenty minutes, so one tick of the day clock stands for
 * {@link #SECONDS_PER_DAY_TICK} seconds of atmosphere. Every timescale below is quoted
 * in real weather terms and converted through that, which is why a front takes a
 * couple of days to pass here as it does anywhere.
 *
 * Space is compressed by {@link #MAP_METRES_PER_BLOCK}, and only for the pressure map.
 * A mid-latitude high-and-low pair is some 2400 km across; at that scale it comes out
 * {@link #SYSTEM_BLOCKS} blocks wide, which is far enough that a wind farm stands
 * under one weather system and close enough that a journey of a few thousand blocks
 * reaches another. Heights are not compressed by that figure - they use the mod's own
 * ten metres to the block, the same as the towers - because a tower's height has to
 * mean the same thing to the wind profile as it does to the rotor bolted on top of it.
 */
public final class Atmosphere {
	/** Seconds of atmosphere one tick of the day clock stands for: 86400 s over a 24000 tick day. */
	public static final double SECONDS_PER_DAY_TICK = 3.6;
	/** Horizontal scale of the pressure map. Only the pressure map: heights are in the mod's own metres. */
	public static final double MAP_METRES_PER_BLOCK = 400.0;
	/** One high-and-low pair across, in blocks. 2400 km at the scale above. */
	private static final double SYSTEM_BLOCKS = 6000.0;
	/** How long a system takes to deepen and fill, in day-clock ticks. Four days. */
	private static final double SYSTEM_TICKS = 24000.0 * 4.0;
	/** Speed the whole pattern travels at, in m/s. A steering flow, not the wind you feel. */
	private static final double STEERING_MS = 11.0;
	/** Distance the gradient is measured over. Small against a system, large against the noise. */
	private static final double GRADIENT_STEP_BLOCKS = 75.0;

	public static final double SEA_LEVEL_PRESSURE = 1013.25;
	/** Spread of mean sea level pressure in the mid-latitudes. Puts the field in 990..1036 hPa. */
	private static final double PRESSURE_SD = 8.0;
	/** Measured spread of the three octaves in {@link #pressureAt}, so the line above means what it says. */
	private static final double PRESSURE_NOISE_SD = 0.4335;

	/** Coriolis parameter at 45 degrees latitude. */
	private static final double CORIOLIS = 1.0e-4;
	public static final double REFERENCE_AIR_DENSITY = 1.225;
	/** Specific gas constant for dry air, J/(kg K). */
	private static final double GAS_CONSTANT_DRY = 287.05;
	private static final double GRAVITY = 9.80665;
	/** Environmental lapse rate, 6.5 C per km, in degrees per metre. */
	private static final double LAPSE_RATE = 0.0065;

	/**
	 * Blending height, in metres: high enough that the ground below has stopped mattering.
	 *
	 * Above it every site in an area shares one wind, and below it each follows its own
	 * profile down to its own roughness. It is a standard device in mesoscale work and it
	 * is what makes a tower's height worth different amounts in different places - far
	 * more over a forest than over water.
	 */
	private static final double BLEND_HEIGHT_M = 200.0;
	/** Wind at the blending height as a fraction of the wind aloft, in neutral air. */
	private static final double BLEND_FRACTION = 0.76;
	/**
	 * How much the wind above the stable layer gains on a clear night.
	 *
	 * The ground cools, the air just above it stops mixing with the air higher up, and that
	 * higher air accelerates because it is no longer being dragged by the ground. It is the
	 * nocturnal jet, and together with the shear below it, it puts a crossover height near
	 * 80 m: under that a machine does better by day, over it by night. The catalogue
	 * straddles that height, so the small machines here are day machines and the tall ones
	 * are night machines, exactly as their real counterparts are.
	 */
	private static final double NIGHT_JET = 0.16;
	/** The westerlies: a mean flow the systems ride on, so a wind rose has a prevailing sector. */
	private static final double PREVAILING_MS = 4.0;
	/** Nothing at any height beats the wind aloft by more than the jet can overshoot it. */
	private static final double JET_CEILING = 1.15;

	/** Roughness of the smoothest ground there is, open water. The bottom of the scale. */
	public static final double SMOOTHEST_ROUGHNESS = 0.0002;
	private static final double ROUGHNESS_SPAN = Math.log(1.0 / SMOOTHEST_ROUGHNESS);

	/** Measured spread of the two octaves in {@link #turbulentFluctuation}. */
	private static final double GUST_NOISE_SD = 0.4697;
	/**
	 * Peak factor for a three-second gust: the peak of a ten-minute record sits about three
	 * standard deviations above its mean. With ordinary turbulence that puts the gust factor
	 * near 1.4, which is what open country measures.
	 */
	private static final double GUST_PEAK_FACTOR = 3.0;

	private Atmosphere() {
	}

	/** Where in the day a day-clock reading falls: 0 sunrise, 0.25 noon, 0.5 sunset, 0.75 midnight. */
	public static double dayPhase(long dayTime) {
		return Math.floorMod(dayTime, 24000L) / 24000.0;
	}

	// ---- the pressure map ----

	/**
	 * Mean sea level pressure at a block column, in hPa.
	 *
	 * Three octaves, weighted steeply, because a real pressure map is smooth: almost all of
	 * it is one or two large features and the rest is detail. The whole field slides across
	 * the world at the steering speed and turns over slowly in place, so a system arrives,
	 * passes and decays instead of appearing where it stands.
	 */
	public static double pressureAt(long seed, double blockX, double blockZ, long dayTime) {
		double travel = STEERING_MS * SECONDS_PER_DAY_TICK * dayTime / MAP_METRES_PER_BLOCK;
		double heading = Math.toRadians(prevailingDirection(seed));
		double u = (blockX - Math.cos(heading) * travel) / SYSTEM_BLOCKS;
		double v = (blockZ - Math.sin(heading) * travel) / SYSTEM_BLOCKS;
		double w = dayTime / SYSTEM_TICKS;

		double n = WeatherNoise.sample(seed, u, v, w);
		n += 0.42 * WeatherNoise.sample(seed ^ 0x51EDL, u * 2.3, v * 2.3, w * 1.7);
		n += 0.16 * WeatherNoise.sample(seed ^ 0xA713L, u * 5.1, v * 5.1, w * 2.6);

		return SEA_LEVEL_PRESSURE + PRESSURE_SD * n / PRESSURE_NOISE_SD;
	}

	/** The world's prevailing wind direction, fixed by its seed. */
	public static float prevailingDirection(long seed) {
		return (float) (WeatherNoise.hashUnit(seed, 0xFEED, 0xC0FFEE, 0x50FA) * 360.0);
	}

	/**
	 * Wind above the boundary layer, as a vector in blocks-per-nothing - it is metres per
	 * second, laid out on the world's x and z axes.
	 *
	 * The geostrophic balance: away from the ground, the pressure gradient and the Coriolis
	 * force cancel, which leaves the air travelling along the isobars rather than down the
	 * slope, with the low on its left. Speed follows the gradient directly, so the wind is
	 * strong where the isobars crowd and slack in the middle of a high.
	 *
	 * The gradient is taken numerically. It could be had analytically from the noise, but
	 * four extra samples of a function this cheap cost less than the complication, and doing
	 * it this way means the gradient cannot drift out of step with the field it belongs to.
	 */
	public static Flow geostrophicWind(long seed, double blockX, double blockZ, long dayTime) {
		double step = GRADIENT_STEP_BLOCKS;
		double east = pressureAt(seed, blockX + step, blockZ, dayTime);
		double west = pressureAt(seed, blockX - step, blockZ, dayTime);
		double south = pressureAt(seed, blockX, blockZ + step, dayTime);
		double north = pressureAt(seed, blockX, blockZ - step, dayTime);

		// hPa per block into Pa per metre, then divided by rho*f to give metres per second
		double scale = 100.0 / MAP_METRES_PER_BLOCK / (REFERENCE_AIR_DENSITY * CORIOLIS);
		double gradientX = (east - west) / (2.0 * step) * scale;
		double gradientZ = (south - north) / (2.0 * step) * scale;

		double heading = Math.toRadians(prevailingDirection(seed));
		return new Flow(-gradientZ + Math.cos(heading) * PREVAILING_MS, gradientX + Math.sin(heading) * PREVAILING_MS);
	}

	/** A horizontal wind on the world's axes. */
	public record Flow(double x, double z) {
		public double speed() {
			return Math.sqrt(x * x + z * z);
		}

		/** Heading the air is travelling towards, in the same 0..360 convention the nacelle yaws in. */
		public float direction() {
			return wrapDegrees((float) Math.toDegrees(Math.atan2(z, x)));
		}
	}

	// ---- the boundary layer ----

	/**
	 * How the air is layered, from -1 on a convective afternoon through 0 for neutral to
	 * +1 on a clear calm night.
	 *
	 * This one number decides most of what the wind does near the ground. Sunshine heats
	 * the surface, the warm air rises, and the whole lower atmosphere stirs: gusty, and
	 * nearly the same speed at every height. At night the ground cools, the stirring stops
	 * and the layers slide over one another: smooth, slow at the ground, fast above.
	 *
	 * It saturates well short of sunrise and sunset because the real thing does - the
	 * ground is convective through most of a sunny day and stable through the whole of a
	 * clear night - and it lags the sun, because the ground goes on warming past noon and
	 * goes on cooling past midnight. Cloud flattens it towards neutral by shading the
	 * ground by day and holding its heat in by night, which is why an overcast night is
	 * neither still nor sheared.
	 */
	public static double stability(double dayPhase, boolean raining, boolean thundering) {
		double sun = Math.sin((dayPhase - 0.06) * 2.0 * Math.PI);
		double s = Mth.clamp(-sun * 2.2, -1.0, 1.0);
		if (thundering) return s * 0.1;
		if (raining) return s * 0.35;

		return s;
	}

	/**
	 * Exponent of the wind profile over ground of a given roughness.
	 *
	 * In neutral air it is the reciprocal of the log of the height in roughness lengths,
	 * which is where the textbook one-seventh comes from: open farmland at a hub height
	 * works out at 0.11 to 0.14. Rough ground raises it, and so does a stable night -
	 * asymmetrically, because a stable layer departs from neutral much further than a
	 * convective one does.
	 */
	public static double profileExponent(double roughness, double stability) {
		double neutral = 1.0 / Math.log(BLEND_HEIGHT_M / roughness);
		return neutral * (stability > 0.0 ? 1.0 + 1.2 * stability : 1.0 + 0.3 * stability);
	}

	/**
	 * Wind speed at a height above the ground, from the wind aloft.
	 *
	 * Anchored at the blending height and taken down by the power law, so the same wind
	 * aloft arrives as quite different winds at the same tower over different ground. The
	 * ceiling is there because a boundary layer cannot outrun what is driving it by more
	 * than the night's inertial overshoot.
	 */
	public static double windAtHeight(double windAloft, double heightM, double roughness, double stability) {
		double atBlend = windAloft * BLEND_FRACTION * (1.0 + NIGHT_JET * Math.max(0.0, stability));
		double profile = Math.pow(Math.max(heightM, 1.0) / BLEND_HEIGHT_M, profileExponent(roughness, stability));
		return Math.min(atBlend * profile, windAloft * JET_CEILING);
	}

	/**
	 * Degrees the surface wind backs across the isobars towards the low.
	 *
	 * Friction is what breaks the geostrophic balance: near the ground the air is slowed,
	 * the Coriolis force weakens with it, and the pressure gradient wins enough to turn the
	 * flow towards the low. Ten degrees over water, thirty over a forest, and more on a
	 * still night when the layers stop sharing momentum - which is why the wind at a hub
	 * veers overnight and back again the next morning, all on its own.
	 */
	public static double frictionTurn(double roughness, double stability) {
		double rough = Math.max(0.0, Math.log(roughness / SMOOTHEST_ROUGHNESS)) / ROUGHNESS_SPAN;
		return 8.0 + 20.0 * rough + 10.0 * Math.max(0.0, stability);
	}

	/**
	 * Turbulence intensity: the standard deviation of the wind over its mean.
	 *
	 * The same reciprocal log as the profile exponent, and for the same reason - both come
	 * out of the friction velocity - so rough ground is both sheared and gusty while water
	 * is neither. Comes out near 0.08 over water at a hub height, 0.13 over farmland and
	 * 0.20 over forest, which is the range IEC 61400-1 writes its turbine classes around.
	 */
	public static double turbulenceIntensity(double heightM, double roughness, double stability, boolean raining, boolean thundering) {
		double ti = 1.0 / Math.log(Math.max(heightM, 10.0 * roughness) / roughness);
		ti *= 1.0 - 0.30 * stability;
		if (thundering) {
			ti *= 1.35;
		} else if (raining) {
			ti *= 1.15;
		}

		return Mth.clamp(ti, 0.03, 0.45);
	}

	/**
	 * The turbulent part of the wind, in standard deviations either side of the mean.
	 *
	 * Two octaves at a few seconds each: enough that a rotor's output moves the way a real
	 * one's does instead of tracking a ten-minute average, and slow enough that it reads as
	 * wind rather than as noise. Sampled at the site's own coordinates, so two turbines a
	 * hundred blocks apart are gusting out of step - which is the difference between a wind
	 * farm and one turbine multiplied.
	 *
	 * This one is on the raw tick counter rather than the day clock, because a gust is a
	 * gust however fast the sun is moving.
	 */
	public static double turbulentFluctuation(long seed, double blockX, double blockZ, long gameTime) {
		double t = gameTime / 20.0;
		double n = WeatherNoise.sample(seed ^ 0x7717L, blockX * 0.013, blockZ * 0.013, t / 3.2);
		n += 0.6 * WeatherNoise.sample(seed ^ 0x1D0BL, blockX * 0.031, blockZ * 0.031, t / 1.1);
		return n / GUST_NOISE_SD;
	}

	/** Three-second gust from a ten-minute mean and its turbulence. */
	public static double gustFrom(double meanWind, double turbulenceIntensity) {
		return meanWind * (1.0 + GUST_PEAK_FACTOR * turbulenceIntensity);
	}

	// ---- thermodynamics ----

	/**
	 * Air pressure at the site, in hPa: the map's sea level value reduced up to the ground
	 * it actually stands on.
	 *
	 * The barometric formula, with the scale height taken from the local temperature rather
	 * than a constant, so cold air thins with height faster than warm air does.
	 */
	public static double stationPressure(double seaLevelPressure, double elevationM, double celsius) {
		double scaleHeight = GAS_CONSTANT_DRY * (273.15 + celsius) / GRAVITY;
		return seaLevelPressure * Math.exp(-elevationM / scaleHeight);
	}

	/**
	 * Air density from the ideal gas law, kg/m3.
	 *
	 * Both terms matter to a turbine and they pull opposite ways up a mountain: the air is
	 * colder, which packs it, and thinner, which does not. Thinner wins, so a summit machine
	 * gives up a few percent of the power the same wind would have made at sea level.
	 */
	public static double airDensity(double celsius, double pressureHpa) {
		return pressureHpa * 100.0 / (GAS_CONSTANT_DRY * Math.max(1.0, 273.15 + celsius));
	}

	/**
	 * Air temperature in Celsius, assembled from the things that would drive one.
	 *
	 * Minecraft has no ambient temperature, so this is built from the biome's climate, the
	 * height above sea level at the real lapse rate, the day cycle and what the sky is
	 * doing. The daily swing is not one figure: dry ground under a clear sky swings twenty
	 * degrees between afternoon and dawn while a rainforest barely moves, because water -
	 * in the ground, in the air, in cloud - is what holds heat overnight. So the biome's own
	 * rainfall sets the size of its swing.
	 */
	public static double temperatureAt(double biomeBaseTemperature, double downfall, double elevationM, double dayPhase, boolean raining, boolean thundering) {
		double celsius = biomeBaseTemperature * 20.0 - 5.0;
		celsius -= Math.max(0.0, elevationM) * LAPSE_RATE;

		double swing = 4.0 + 8.0 * (1.0 - Mth.clamp(downfall, 0.0, 1.0));
		if (thundering) {
			swing *= 0.25;
		} else if (raining) {
			swing *= 0.5;
		}

		// peaks in the early afternoon and bottoms before dawn
		celsius += Math.sin((dayPhase - 2000.0 / 24000.0) * 2.0 * Math.PI) * swing;

		if (thundering) {
			celsius -= 6.0;
		} else if (raining) {
			celsius -= 3.0;
		}

		return celsius;
	}

	public static float wrapDegrees(float degrees) {
		float wrapped = degrees % 360.0f;
		return wrapped < 0.0f ? wrapped + 360.0f : wrapped;
	}
}
