package com.dooji.electricity.main.weather;

import com.dooji.electricity.api.WorldConditions;
import net.minecraft.util.Mth;

/**
 * The physics of the mod's weather, as pure functions.
  *
 * Space is compressed by {@link #MAP_METRES_PER_BLOCK}, and only for the pressure map.
 */
public final class Atmosphere {
	/** Seconds of atmosphere one tick of the day clock stands for. */
	private static final double SECONDS_PER_DAY_TICK = WorldConditions.SECONDS_PER_DAY_TICK;
	/** Horizontal scale of the pressure map. */
	private static final double MAP_METRES_PER_BLOCK = 400.0;
	/** One high-and-low pair across, in blocks. */
	private static final double SYSTEM_BLOCKS = 6000.0;
	/** How long a system takes to deepen and fill, in day-clock ticks. */
	private static final double SYSTEM_TICKS = 24000.0 * 4.0;
	/** Speed the whole pattern travels at, in m/s. */
	private static final double STEERING_MS = 11.0;
	/** Distance the gradient is measured over. */
	private static final double GRADIENT_STEP_BLOCKS = 75.0;

	/** Spread of mean sea level pressure in the mid-latitudes. */
	private static final double PRESSURE_SD = 8.0;
	/** Measured spread of the three octaves in {@link #pressureAt}, so the line above means what it says. */
	private static final double PRESSURE_NOISE_SD = 0.4335;

	/** Coriolis parameter at 45 degrees latitude. */
	private static final double CORIOLIS = 1.0e-4;
	/** Specific gas constant for dry air, J/(kg K). */
	private static final double GAS_CONSTANT_DRY = 287.05;
	private static final double GRAVITY = 9.80665;
	/** Environmental lapse rate, 6.5 C per km, in degrees per metre. */
	private static final double LAPSE_RATE = 0.0065;

	/** Blending height, in metres: high enough that the ground below has stopped mattering. */
	private static final double BLEND_HEIGHT_M = 200.0;
	/** Wind at the blending height as a fraction of the wind aloft, in neutral air. */
	private static final double BLEND_FRACTION = 0.76;
	/** How much the wind above the stable layer gains on a clear night. */
	private static final double NIGHT_JET = 0.16;
	/** The westerlies: a mean flow the systems ride on, so a wind rose has a prevailing sector. */
	private static final double PREVAILING_MS = 4.0;
	/** Nothing at any height beats the wind aloft by more than the jet can overshoot it. */
	private static final double JET_CEILING = 1.15;

	/** Roughness of the smoothest ground there is, open water. */
	public static final double SMOOTHEST_ROUGHNESS = 0.0002;
	private static final double ROUGHNESS_SPAN = Math.log(1.0 / SMOOTHEST_ROUGHNESS);

	/** Measured spread of the two octaves in {@link #turbulentFluctuation}. */
	private static final double GUST_NOISE_SD = 0.4697;
	/**
	 * Peak factor for a three-second gust: the peak of a ten-minute record sits about three standard deviations above its mean.
	 */
	private static final double GUST_PEAK_FACTOR = 3.0;

	private Atmosphere() {
	}

	/** Where in the day a day-clock reading falls: 0 sunrise, 0.25 noon, 0.5 sunset, 0.75 midnight. */
	public static double dayPhase(long dayTime) {
		return Math.floorMod(dayTime, 24000L) / 24000.0;
	}

	// ---- the pressure map ----

	/** Mean sea level pressure at a block column, in hPa. */
	public static double pressureAt(long seed, double blockX, double blockZ, long dayTime) {
		double travel = STEERING_MS * SECONDS_PER_DAY_TICK * dayTime / MAP_METRES_PER_BLOCK;
		double heading = Math.toRadians(prevailingDirection(seed));
		double u = (blockX - Math.cos(heading) * travel) / SYSTEM_BLOCKS;
		double v = (blockZ - Math.sin(heading) * travel) / SYSTEM_BLOCKS;
		double w = dayTime / SYSTEM_TICKS;

		double n = WeatherNoise.sample(seed, u, v, w);
		n += 0.42 * WeatherNoise.sample(seed ^ 0x51EDL, u * 2.3, v * 2.3, w * 1.7);
		n += 0.16 * WeatherNoise.sample(seed ^ 0xA713L, u * 5.1, v * 5.1, w * 2.6);

		return WorldConditions.SEA_LEVEL_PRESSURE + PRESSURE_SD * n / PRESSURE_NOISE_SD;
	}

	/** The world's prevailing wind direction, fixed by its seed. */
	public static float prevailingDirection(long seed) {
		return (float) (WeatherNoise.hashUnit(seed, 0xFEED, 0xC0FFEE, 0x50FA) * 360.0);
	}

	/**
	 * Wind above the boundary layer, as a vector in blocks-per-nothing - it is metres per second, laid out on the world's x and z axes.
	 */
	public static Flow geostrophicWind(long seed, double blockX, double blockZ, long dayTime) {
		double step = GRADIENT_STEP_BLOCKS;
		double east = pressureAt(seed, blockX + step, blockZ, dayTime);
		double west = pressureAt(seed, blockX - step, blockZ, dayTime);
		double south = pressureAt(seed, blockX, blockZ + step, dayTime);
		double north = pressureAt(seed, blockX, blockZ - step, dayTime);

		// hPa per block into Pa per metre
		double scale = 100.0 / MAP_METRES_PER_BLOCK / (WorldConditions.REFERENCE_AIR_DENSITY * CORIOLIS);
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

		/** Heading the air is travelling towards */
		public float direction() {
			return wrapDegrees((float) Math.toDegrees(Math.atan2(z, x)));
		}
	}

	// ---- the boundary layer ----

	/** How the air is layered, from -1 on a convective afternoon through 0 for neutral to +1 on a clear calm night. */
	public static double stability(double dayPhase, boolean raining, boolean thundering) {
		double sun = Math.sin((dayPhase - 0.06) * 2.0 * Math.PI);
		double s = Mth.clamp(-sun * 2.2, -1.0, 1.0);
		if (thundering) return s * 0.1;
		if (raining) return s * 0.35;

		return s;
	}

	/** Exponent of the wind profile over ground of a given roughness. */
	public static double profileExponent(double roughness, double stability) {
		double neutral = 1.0 / Math.log(BLEND_HEIGHT_M / roughness);
		return neutral * (stability > 0.0 ? 1.0 + 1.2 * stability : 1.0 + 0.3 * stability);
	}

	/** Wind speed at a height above the ground, from the wind aloft. */
	public static double windAtHeight(double windAloft, double heightM, double roughness, double stability) {
		double atBlend = windAloft * BLEND_FRACTION * (1.0 + NIGHT_JET * Math.max(0.0, stability));
		double profile = Math.pow(Math.max(heightM, 1.0) / BLEND_HEIGHT_M, profileExponent(roughness, stability));
		return Math.min(atBlend * profile, windAloft * JET_CEILING);
	}

	/** Degrees the surface wind backs across the isobars towards the low. */
	public static double frictionTurn(double roughness, double stability) {
		double rough = Math.max(0.0, Math.log(roughness / SMOOTHEST_ROUGHNESS)) / ROUGHNESS_SPAN;
		return 8.0 + 20.0 * rough + 10.0 * Math.max(0.0, stability);
	}

	/** Turbulence intensity: the standard deviation of the wind over its mean. */
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

	/** The turbulent part of the wind, in standard deviations either side of the mean. */
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

	/** Air pressure at the site, in hPa: the map's sea level value reduced up to the ground it actually stands on. */
	public static double stationPressure(double seaLevelPressure, double elevationM, double celsius) {
		double scaleHeight = GAS_CONSTANT_DRY * (273.15 + celsius) / GRAVITY;
		return seaLevelPressure * Math.exp(-elevationM / scaleHeight);
	}

	/** Air density from the ideal gas law, kg/m3. */
	public static double airDensity(double celsius, double pressureHpa) {
		return pressureHpa * 100.0 / (GAS_CONSTANT_DRY * Math.max(1.0, 273.15 + celsius));
	}

	/** Air temperature in Celsius, assembled from the things that would drive one. */
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

	// ---- sunlight ----

	/** Total solar irradiance outside the atmosphere, W/m2. */
	private static final double SOLAR_CONSTANT = 1361.0;
	/** Broadband transmittance of one air mass of clean dry air, from Meinel & Meinel's {@code 0.7^(AM^0.678)}. */
	private static final double CLEAR_AIR_TRANSMITTANCE = 0.70;
	private static final double AIR_MASS_EXPONENT = 0.678;
	/**
	 * Liu & Jordan's clear-sky diffuse: the sky's own contribution as a share of what arrives above the atmosphere, less a share of what got through as beam.
	  *
	 * The pair replaces a flat {@code DIFFUSE_FACTOR = 1.10} that used to stand in for the whole of it.
	 */
	private static final double CLEAR_SKY_DIFFUSE_SHARE = 0.271;
	private static final double CLEAR_SKY_DIFFUSE_BEAM_OFFSET = 0.294;

	/** Angular loss coefficient for a glass module front, from Martin & Ruiz. */
	private static final double ANGULAR_LOSS_COEFFICIENT = 0.16;

	/** Air mass the spectrum is defined at: AM1.5 */
	private static final double REFERENCE_AIR_MASS = 1.5;
	/** Diffuse fraction under the clear sky that reference spectrum assumes. */
	private static final double REFERENCE_DIFFUSE_FRACTION = 0.20;
	/** Output lost per air mass of extra path, for a module of the reference spectral sensitivity. */
	private static final double SPECTRAL_AIR_MASS_SLOPE = 0.020;
	/** Output gained per unit of extra diffuse fraction, for the same module. */
	private static final double SPECTRAL_DIFFUSE_SLOPE = 0.030;
	/** Spectral sensitivity the two slopes above are quoted for: crystalline silicon's. */
	private static final double REFERENCE_SPECTRAL_SENSITIVITY = 0.20;
	/** How far the spectral correction is allowed to run either way. */
	private static final double SPECTRAL_LIMIT = 0.12;

	/** Size of a fair weather cumulus, in blocks. */
	private static final double CUMULUS_BLOCKS = 110.0;
	private static final double CUMULUS_AMPLITUDE = 0.42;
	/** Measured spread of the single octave in {@link #cloudCover}. */
	private static final double CLOUD_NOISE_SD = 0.4040;
	/** How much of a full sky of lifting a hPa of pressure fall is worth. */
	private static final double PRESSURE_TO_LIFT = 11.0;
	private static final double LIFT_AT_MEAN_PRESSURE = 0.45;
	/** How readily a place turns lifting into cloud, from its rainfall. */
	private static final double MOISTURE_BASE = 0.50;
	private static final double MOISTURE_PER_DOWNFALL = 1.30;
	/** Overcast floors. */
	private static final double RAIN_COVER_FLOOR = 0.90;
	private static final double STORM_COVER_FLOOR = 0.97;
	/** How long a cumulus field takes to grow and dissolve in place, in day-clock ticks. */
	private static final double CUMULUS_TICKS = 6000.0;

	/**
	 * Where the sun is at a point in the day.
	  *
	 * See {@link SunPosition} for what having no axial tilt does to everything downstream.
	 */
	public static SunPosition sunPosition(double dayPhase) {
		double elevation = Math.toDegrees(Math.asin(Math.sin(dayPhase * 2.0 * Math.PI)));
		return new SunPosition(elevation, dayPhase < 0.25 || dayPhase >= 0.75 ? SunPosition.EAST : SunPosition.WEST);
	}

	/** Solar irradiance above the atmosphere, W/m2. */
	public static double extraterrestrialNormal() {
		return SOLAR_CONSTANT;
	}

	/** Relative optical air mass: how much atmosphere the light has crossed, in zenith paths. */
	public static double airMass(double sinElevation, double pressureHpa) {
		if (sinElevation <= 0.0) return Double.POSITIVE_INFINITY;

		double elevationDeg = Math.toDegrees(Math.asin(Math.min(1.0, sinElevation)));
		double am = 1.0 / (sinElevation + 0.50572 * Math.pow(elevationDeg + 6.07995, -1.6364));
		return am * pressureHpa / WorldConditions.SEA_LEVEL_PRESSURE;
	}

	/** Beam irradiance on a surface square to the sun under a clear sky, W/m2. */
	public static double beamNormalClear(double sinElevation, double pressureHpa) {
		if (sinElevation <= 0.0) return 0.0;

		return SOLAR_CONSTANT * Math.pow(CLEAR_AIR_TRANSMITTANCE, Math.pow(airMass(sinElevation, pressureHpa), AIR_MASS_EXPONENT));
	}

	/** Sky irradiance on a horizontal surface under a clear sky, W/m2. */
	public static double diffuseHorizontalClear(double sinElevation, double pressureHpa) {
		if (sinElevation <= 0.0) return 0.0;

		double diffuse = CLEAR_SKY_DIFFUSE_SHARE * SOLAR_CONSTANT * sinElevation
				- CLEAR_SKY_DIFFUSE_BEAM_OFFSET * beamNormalClear(sinElevation, pressureHpa) * sinElevation;
		return Math.max(0.0, diffuse);
	}

	/** Global horizontal irradiance under a clear sky, W/m2: the beam projected onto the flat, plus the sky. */
	public static double clearSkyIrradiance(double sinElevation, double pressureHpa) {
		if (sinElevation <= 0.0) return 0.0;

		return beamNormalClear(sinElevation, pressureHpa) * sinElevation + diffuseHorizontalClear(sinElevation, pressureHpa);
	}

	/** Fraction of the sky covered by cloud */
	public static double cloudCover(long seed, double blockX, double blockZ, long dayTime, long gameTime, double downfall,
			double windSpeed, float windDirection, boolean raining, boolean thundering) {
		double lift = LIFT_AT_MEAN_PRESSURE - (pressureAt(seed, blockX, blockZ, dayTime) - WorldConditions.SEA_LEVEL_PRESSURE) / PRESSURE_TO_LIFT;

		double drift = windSpeed * gameTime / 20.0 / WorldConditions.METRES_PER_BLOCK;
		double heading = Math.toRadians(windDirection);
		double cx = (blockX - Math.cos(heading) * drift) / CUMULUS_BLOCKS;
		double cz = (blockZ - Math.sin(heading) * drift) / CUMULUS_BLOCKS;
		lift += CUMULUS_AMPLITUDE * WeatherNoise.sample(seed ^ 0xC10DL, cx, cz, dayTime / CUMULUS_TICKS) / CLOUD_NOISE_SD;

		double cover = (MOISTURE_BASE + MOISTURE_PER_DOWNFALL * Mth.clamp(downfall, 0.0, 1.0)) * lift;
		if (thundering) return Mth.clamp(cover, STORM_COVER_FLOOR, 1.0);
		if (raining) return Mth.clamp(cover, RAIN_COVER_FLOOR, 1.0);

		return Mth.clamp(cover, 0.0, 1.0);
	}

	/** What a cloudy sky passes, as a fraction of what the clear one would have. */
	public static double cloudTransmittance(double cover) {
		return 1.0 - 0.75 * Math.pow(Mth.clamp(cover, 0.0, 1.0), 3.4);
	}

	/** What a cloudy sky passes of the *beam*, as a fraction of the clear-sky beam. */
	public static double beamTransmittance(double cover) {
		return 1.0 - Mth.clamp(cover, 0.0, 1.0);
	}

	/** The whole sky at a place and a moment, assembled from the pieces above. */
	public static SkyConditions sky(SunPosition sun, double pressureHpa, double cover, double albedo, double ambientTempC, double windSpeed) {
		if (!sun.up()) return SkyConditions.night(sun, cover, albedo, ambientTempC, windSpeed);

		double sinElevation = sun.sinElevation();
		double mass = airMass(sinElevation, pressureHpa);

		double directNormal = beamNormalClear(sinElevation, pressureHpa) * beamTransmittance(cover);
		double global = clearSkyIrradiance(sinElevation, pressureHpa) * cloudTransmittance(cover);
		// whatever the global has that the beam does not account for is, by definition, diffuse.
		double diffuse = Math.max(0.0, global - directNormal * sinElevation);

		return new SkyConditions(sun, mass, cover, SOLAR_CONSTANT, directNormal, diffuse, global, albedo, ambientTempC, windSpeed);
	}

	// ---- from the sky onto a plane ----

	/** Fraction of the beam that gets through the module's front glass at a given angle. */
	public static double incidenceAngleModifier(double incidenceDeg) {
		if (incidenceDeg >= 90.0) return 0.0;

		double cos = Math.cos(Math.toRadians(Math.max(0.0, incidenceDeg)));
		double normal = 1.0 - Math.exp(-1.0 / ANGULAR_LOSS_COEFFICIENT);
		return Mth.clamp((1.0 - Math.exp(-cos / ANGULAR_LOSS_COEFFICIENT)) / normal, 0.0, 1.0);
	}

	/** The sky's three components projected onto a module plane, W/m2. */
	public static PlaneIrradiance planeOfArray(SkyConditions sky, double tiltDeg, double azimuthDeg, double rearViewFactor) {
		if (!sky.sun().up() || sky.globalHorizontal() <= 0.0) return PlaneIrradiance.DARK;

		double tilt = Math.toRadians(Mth.clamp(tiltDeg, 0.0, 90.0));
		double cosIncidence = sky.sun().cosIncidence(tiltDeg, azimuthDeg);
		double incidenceDeg = Math.toDegrees(Math.acos(Mth.clamp(cosIncidence, -1.0, 1.0)));

		double beam = cosIncidence <= 0.0 ? 0.0 : sky.directNormal() * cosIncidence * incidenceAngleModifier(incidenceDeg);

		// the anisotropy index: how much of the diffuse to treat as coming from around the sun rather
		double anisotropy = Mth.clamp(sky.directNormal() / sky.extraterrestrialNormal(), 0.0, 1.0);
		double sunwardRatio = cosIncidence <= 0.0 ? 0.0 : cosIncidence / Math.max(0.01, sky.sun().sinElevation());
		double skyViewFactor = (1.0 + Math.cos(tilt)) / 2.0;
		double horizonBrightening = 1.0 + Math.sqrt(Mth.clamp(sky.beamHorizontal() / sky.globalHorizontal(), 0.0, 1.0))
				 * Math.pow(Math.sin(tilt / 2.0), 3.0);
		double skyDiffuse = sky.diffuseHorizontal() * (anisotropy * sunwardRatio + (1.0 - anisotropy) * skyViewFactor * horizonBrightening);

		double groundViewFactor = (1.0 - Math.cos(tilt)) / 2.0;
		double groundReflected = sky.albedo() * sky.globalHorizontal() * groundViewFactor;

		double rear = rearViewFactor <= 0.0 ? 0.0 : sky.albedo() * sky.globalHorizontal() * rearViewFactor;

		return new PlaneIrradiance(incidenceDeg, beam, skyDiffuse, groundReflected, rear);
	}

	/**
	 * Spectral correction: how much a module's own colour sensitivity gains or loses against the spectrum it was rated under.
	 */
	public static double spectralFactor(double airMass, double diffuseFraction, double spectralSensitivity) {
		if (Double.isInfinite(airMass)) return 1.0;

		double scale = spectralSensitivity / REFERENCE_SPECTRAL_SENSITIVITY;
		double reddening = -SPECTRAL_AIR_MASS_SLOPE * (Mth.clamp(airMass, 1.0, 10.0) - REFERENCE_AIR_MASS);
		double blueing = SPECTRAL_DIFFUSE_SLOPE * (Mth.clamp(diffuseFraction, 0.0, 1.0) - REFERENCE_DIFFUSE_FRACTION);
		return 1.0 + Mth.clamp(scale * (reddening + blueing), -SPECTRAL_LIMIT, SPECTRAL_LIMIT);
	}

	public static float wrapDegrees(float degrees) {
		float wrapped = degrees % 360.0f;
		return wrapped < 0.0f ? wrapped + 360.0f : wrapped;
	}
}
