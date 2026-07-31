package com.dooji.electricity.main.weather;

import com.dooji.electricity.api.power.TurbineSpec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The mod's weather, for one level.
 *
 * There is very little here, and that is the point. {@link Atmosphere} is a set of pure
 * functions of position and time, so this class holds no weather state at all: no cells,
 * no saved data, nothing to prune and nothing to keep in step. All it does is know the
 * level's seed, read its clock, and remember what the ground is like where machines have
 * asked - which is the one input that cannot be had from a seed, since players move it
 * around with shovels.
 *
 * What that buys, beyond the size: the weather is the same for every player whatever they
 * have loaded, it is identical after a restart, and a world's climate follows from its
 * seed alone. The model this replaced saved a grid of cells that fed each other, and
 * because that feedback was not balanced, a cell with loaded neighbours climbed somewhere
 * a lonely one never reached - so the weather depended on how much of the world happened
 * to be in memory.
 *
 * <h2>Two clocks</h2>
 *
 * Everything on the scale of hours and days runs off {@link ServerLevel#getDayTime()}
 * rather than the tick counter, so the weather keeps pace with the sun. That matters
 * because the sun does not always keep pace with the ticks: with the real-time clock rule
 * on a day takes a real day, and a front that took two game days to pass has to take two
 * real ones. Gusts are the exception - they run off the raw tick counter, because a gust
 * is a few seconds long whatever the sun is doing.
 */
public final class GlobalWeatherManager {
	private static final Map<ServerLevel, GlobalWeatherManager> INSTANCES = new ConcurrentHashMap<>();
	/** Ground is surveyed per eight-block square; finer than that is below the resolution of anything here. */
	private static final int SITE_GRID_SHIFT = 3;
	/** How long a survey stands before it is taken again, in ticks. Players do reshape hills. */
	private static final long SITE_TTL = 1200L;
	/** Past this many remembered sites the stale ones are dropped. */
	private static final int SITE_CACHE_LIMIT = 4096;

	private final ServerLevel level;
	private final long seed;
	private final Map<Long, Survey> sites = new ConcurrentHashMap<>();

	private record Survey(SiteConditions conditions, long takenAt) {
	}

	public static GlobalWeatherManager get(ServerLevel level) {
		return INSTANCES.computeIfAbsent(level, GlobalWeatherManager::new);
	}

	public static void clear(ServerLevel level) {
		INSTANCES.remove(level);
	}

	private GlobalWeatherManager(ServerLevel level) {
		this.level = level;
		this.seed = level.getSeed();
	}

	/** Nothing to advance any more. This only stops the site cache growing without bound. */
	public void tick() {
		if (sites.size() <= SITE_CACHE_LIMIT) return;

		long now = level.getGameTime();
		sites.values().removeIf(survey -> now - survey.takenAt() > SITE_TTL);
	}

	/** The world's prevailing wind direction. Fixed by the seed, which is what gives a wind rose its shape. */
	public float prevailingDirection() {
		return Atmosphere.prevailingDirection(seed);
	}

	/**
	 * Weather at the standard meteorological height: ten metres over the ground the given
	 * block sits on, which is what a mast at that spot would report.
	 */
	public WeatherSnapshot sample(BlockPos pos) {
		return sample(pos, 1);
	}

	/**
	 * Weather at a machine standing on a tower this many blocks tall.
	 *
	 * The height is in blocks because that is the unit the player built in, and one block is
	 * ten metres throughout the mod - which also happens to make a one-block tower a met
	 * mast, since ten metres is the height the world's weather services quote wind at.
	 *
	 * Two ends of the structure are read for two different things. The sky is checked at the
	 * machine, because that is what is out in the rain; the ground is surveyed under the
	 * foot, because that is what the wind has been rubbing against on its way here.
	 */
	public WeatherSnapshot sample(BlockPos machinePos, int towerBlocks) {
		BlockPos groundPos = machinePos.below(Math.max(0, towerBlocks));
		SiteConditions site = siteAt(groundPos);

		long dayTime = level.getDayTime();
		double phase = Atmosphere.dayPhase(dayTime);
		boolean raining = level.isRainingAt(machinePos.above());
		boolean thundering = level.isThundering() && raining;

		double stability = Atmosphere.stability(phase, raining, thundering);
		double heightM = Math.max(1, towerBlocks) * TurbineSpec.METRES_PER_BLOCK;
		double elevationM = elevationOf(groundPos);

		Atmosphere.Flow aloft = Atmosphere.geostrophicWind(seed, machinePos.getX(), machinePos.getZ(), dayTime);
		double windAloft = aloft.speed() * (1.0 + site.exposure(groundPos.getY()));

		double mean = Atmosphere.windAtHeight(windAloft, heightM, site.roughness(), stability);
		double turbulence = Atmosphere.turbulenceIntensity(heightM, site.roughness(), stability, raining, thundering);
		double fluctuation = Atmosphere.turbulentFluctuation(seed, machinePos.getX(), machinePos.getZ(), level.getGameTime());
		double instant = Math.max(0.0, mean * (1.0 + turbulence * fluctuation));

		float direction = Atmosphere.wrapDegrees(aloft.direction() - (float) Atmosphere.frictionTurn(site.roughness(), stability));

		double temperature = Atmosphere.temperatureAt(site.biomeTemperature(), site.downfall(), elevationM, phase, raining, thundering);
		double pressure = Atmosphere.stationPressure(seaLevelPressure(machinePos), elevationM, temperature);

		return new WeatherSnapshot(mean, instant, Atmosphere.gustFrom(mean, turbulence), turbulence, direction, stability, temperature, pressure,
				Atmosphere.airDensity(temperature, pressure), Atmosphere.profileExponent(site.roughness(), stability));
	}

	/** Mean sea level pressure over this column, in hPa: the map itself, before any site correction. */
	public double seaLevelPressure(BlockPos pos) {
		return Atmosphere.pressureAt(seed, pos.getX(), pos.getZ(), level.getDayTime());
	}

	/** Metres the ground here stands above sea level, on the mod's ten metres to the block. */
	public double elevationOf(BlockPos groundPos) {
		return (groundPos.getY() - level.getSeaLevel()) * TurbineSpec.METRES_PER_BLOCK;
	}

	/**
	 * What the ground is like at a column, surveyed at most once every {@link #SITE_TTL} ticks.
	 *
	 * Cached because the survey reads a biome and eight heightmap columns, far more work than
	 * the arithmetic it feeds, and because terrain is the one input here a player can change -
	 * so it is remembered rather than assumed, and briefly rather than for good.
	 */
	public SiteConditions siteAt(BlockPos groundPos) {
		long key = (((long) (groundPos.getX() >> SITE_GRID_SHIFT)) << 32) ^ ((groundPos.getZ() >> SITE_GRID_SHIFT) & 0xffffffffL);
		long now = level.getGameTime();

		Survey cached = sites.get(key);
		if (cached != null && now - cached.takenAt() < SITE_TTL) return cached.conditions();

		SiteConditions surveyed = SiteConditions.survey(level, groundPos);
		sites.put(key, new Survey(surveyed, now));
		return surveyed;
	}
}
