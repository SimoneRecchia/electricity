package com.dooji.electricity.main.weather;

import com.dooji.electricity.api.WorldConditions;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** The mod's weather, for one level. */
public final class GlobalWeatherManager {
	private static final Map<ServerLevel, GlobalWeatherManager> INSTANCES = new ConcurrentHashMap<>();
	/** Ground is surveyed per eight-block square; finer than that is below the resolution of anything here. */
	private static final int SITE_GRID_SHIFT = 3;
	/** How long a survey stands before it is taken again, in ticks. */
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

	/** Nothing to advance any more. */
	public void tick() {
		if (sites.size() <= SITE_CACHE_LIMIT) return;

		long now = level.getGameTime();
		sites.values().removeIf(survey -> now - survey.takenAt() > SITE_TTL);
	}

	/**
	 * Weather at the standard meteorological height: ten metres over the ground the given block sits on, which is what a mast at that spot would report.
	 */
	public WeatherSnapshot sample(BlockPos pos) {
		return sample(pos, 1);
	}

	/** Weather at a machine standing on a tower this many blocks tall. */
	public WeatherSnapshot sample(BlockPos machinePos, int towerBlocks) {
		BlockPos groundPos = machinePos.below(Math.max(0, towerBlocks));
		SiteConditions site = siteAt(groundPos);

		long dayTime = level.getDayTime();
		double phase = Atmosphere.dayPhase(dayTime);
		boolean raining = level.isRainingAt(machinePos.above());
		boolean thundering = level.isThundering() && raining;

		double stability = Atmosphere.stability(phase, raining, thundering);
		double heightM = Math.max(1, towerBlocks) * WorldConditions.METRES_PER_BLOCK;
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

		// the cloud drifts with the wind that has just been worked out
		// same shadow over two panels a few seconds apart instead of at the same instant
		double cover = Atmosphere.cloudCover(seed, machinePos.getX(), machinePos.getZ(), dayTime, level.getGameTime(), site.downfall(), mean, direction,
				raining, thundering);
		// the wind handed to the sky is the one at instrument height rather than at the sampled height,
		SkyConditions sky = Atmosphere.sky(Atmosphere.sunPosition(phase), pressure, cover, site.albedo(), temperature,
				Atmosphere.windAtHeight(windAloft, WorldConditions.METRES_PER_BLOCK, site.roughness(), stability));

		return new WeatherSnapshot(mean, instant, Atmosphere.gustFrom(mean, turbulence), turbulence, direction, temperature, pressure,
				Atmosphere.airDensity(temperature, pressure), Atmosphere.profileExponent(site.roughness(), stability), sky);
	}

	/** Mean sea level pressure over this column, in hPa: the map itself, before any site correction. */
	public double seaLevelPressure(BlockPos pos) {
		return Atmosphere.pressureAt(seed, pos.getX(), pos.getZ(), level.getDayTime());
	}

	/** Metres the ground here stands above sea level, on the mod's ten metres to the block. */
	public double elevationOf(BlockPos groundPos) {
		return (groundPos.getY() - level.getSeaLevel()) * WorldConditions.METRES_PER_BLOCK;
	}

	/** What the ground is like at a column, surveyed at most once every {@link #SITE_TTL} ticks. */
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
