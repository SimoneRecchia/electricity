package com.dooji.electricity.main.weather;

import com.dooji.electricity.api.WorldConditions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * What the ground under a machine does to the wind and the sunlight reaching it.
 *
 * Everything here is a property of the place rather than of the moment, which is what
 * makes siting a decision worth making: a player who walks a ridge line looking for
 * somewhere to build is doing what a wind developer does, and the answer does not
 * change from one day to the next.
 *
 * @param roughness           aerodynamic roughness length in metres, the height at which the
 *                            wind profile extrapolates to nothing
 * @param downfall            the biome's rainfall, 0 to 1: stands in for how much water there is
 *                            in the ground and the air, which sets both the daily temperature
 *                            swing and how cloudy the place is
 * @param biomeTemperature    the biome's own climate figure, on Minecraft's 0..2 scale
 * @param albedo              fraction of the sunlight this ground throws back up, which is what a
 *                            bifacial module's back lives on and what an albedometer measures
 * @param surroundingGroundY  mean ground level of the land around, or {@link Double#NaN} when
 *                            too little of it is loaded to say
 */
public record SiteConditions(double roughness, double downfall, double biomeTemperature, double albedo, double surroundingGroundY) {
	/** How far out the land is measured to decide whether a site stands proud of it. */
	private static final int EXPOSURE_RADIUS = 32;
	/** Metres of relative elevation worth a full share of the speed-up below. */
	private static final double EXPOSURE_SCALE = 500.0;
	private static final double MAX_SPEED_UP = 0.30;
	private static final double MAX_SHELTER = -0.12;

	/**
	 * Roughness lengths, in metres, for the kinds of ground a biome stands for.
	 *
	 * These are the published figures for real terrain, not invented ones: open water is
	 * 0.0002, cut grass 0.03, and mature forest around 1. The span from one end to the other
	 * is a factor of five thousand, which sounds enormous and is worth about a third of the
	 * wind speed at a hub - the log profile is generous that way, and it is also why a short
	 * tower in a forest is such a poor idea.
	 */
	private static final double WATER = Atmosphere.SMOOTHEST_ROUGHNESS;
	private static final double SNOW_OR_SAND = 0.005;
	private static final double BARE_GROUND = 0.012;
	private static final double SCRUB = 0.05;
	private static final double ROCK = 0.06;
	private static final double FOREST = 0.55;
	private static final double DENSE_FOREST = 1.0;

	/**
	 * Albedos, and they are the published figures for the same ground the roughnesses above describe.
	 *
	 * Two orders of magnitude from end to end, and unlike the roughness that span is worth real money:
	 * the back of a bifacial module lives on this, so the same array over fresh snow makes a tenth more
	 * than over water and several percent more over sand than over grass. It is why developers gravel
	 * the ground under bifacial plants and why an albedometer is the one instrument that only earns its
	 * keep on a bifacial site.
	 *
	 * Snow is the outlier and it pulls both ways at once: it buries the modules, which costs everything,
	 * and once shed it turns the ground into a mirror, which is the single best thing that can happen to
	 * a bifacial array.
	 */
	private static final double WATER_ALBEDO = 0.07;
	private static final double SNOW_ALBEDO = 0.80;
	private static final double SAND_ALBEDO = 0.35;
	private static final double BARE_GROUND_ALBEDO = 0.30;
	private static final double ROCK_ALBEDO = 0.20;
	private static final double GRASS_ALBEDO = 0.22;
	private static final double FOREST_ALBEDO = 0.14;
	private static final double DENSE_FOREST_ALBEDO = 0.12;

	/**
	 * Fraction to add to the wind aloft for a site that stands above the country around it.
	 *
	 * Air cannot pile up in front of a hill, so it accelerates over the top: a real ridge
	 * crest can hold half again the wind of the plain beside it, and a valley floor rather
	 * less. This is the crudest possible version of that - how high the site stands over the
	 * land within a couple of chunks - but it is the effect that matters most, and it is
	 * measured in the mod's own ten metres to the block, so standing ten blocks above your
	 * surroundings is a hundred metre hill and worth a fifth more wind.
	 *
	 * A site whose surroundings are not loaded gets nothing rather than a guess. That
	 * direction is deliberate: an unknown must never be able to manufacture wind, which is
	 * the mistake the model this one replaced was built on.
	 */
	public double exposure(double groundY) {
		if (Double.isNaN(surroundingGroundY)) return 0.0;

		double relativeM = (groundY - surroundingGroundY) * WorldConditions.METRES_PER_BLOCK;
		return Mth.clamp(relativeM / EXPOSURE_SCALE, MAX_SHELTER, MAX_SPEED_UP);
	}

	/**
	 * Surveys the column a machine stands on.
	 *
	 * The biome decides the roughness by what grows on it. Tags come first, because they
	 * carry the families that matter and they carry modded biomes with them; where no tag
	 * applies, rainfall stands in, on the reasoning that vegetation is what makes ground
	 * rough and rain is what makes vegetation. So an unknown biome from a datapack lands
	 * somewhere sensible without anyone registering anything.
	 */
	public static SiteConditions survey(ServerLevel level, BlockPos groundPos) {
		var biome = level.getBiome(groundPos);
		var climate = biome.value().getModifiedClimateSettings();
		double downfall = climate.downfall();

		return new SiteConditions(roughnessOf(biome, downfall), downfall, biome.value().getBaseTemperature(),
				albedoOf(level, groundPos, biome, downfall), surveySurroundings(level, groundPos));
	}

	/**
	 * How much of the light this ground throws back up.
	 *
	 * Read off the same biome tags the roughness uses, because the same thing decides both - what is
	 * growing on the ground - and then overridden by what is actually lying on it. Snow and ice are
	 * checked in the world rather than inferred from the climate, because a snowfall is a thing that
	 * happens rather than a property of a place, and it changes the albedo by a factor of four.
	 */
	private static double albedoOf(ServerLevel level, BlockPos groundPos, net.minecraft.core.Holder<Biome> biome, double downfall) {
		BlockState surface = level.getBlockState(groundPos);
		if (surface.is(Blocks.SNOW) || surface.is(Blocks.SNOW_BLOCK) || surface.is(Blocks.POWDER_SNOW) || surface.is(Blocks.ICE)
				|| surface.is(Blocks.PACKED_ICE) || surface.is(Blocks.BLUE_ICE)) {
			return SNOW_ALBEDO;
		}

		if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER)) return WATER_ALBEDO;
		if (biome.is(BiomeTags.IS_BEACH)) return SAND_ALBEDO;
		if (biome.is(BiomeTags.IS_JUNGLE)) return DENSE_FOREST_ALBEDO;
		if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA)) return FOREST_ALBEDO;
		if (biome.is(BiomeTags.IS_BADLANDS)) return BARE_GROUND_ALBEDO;
		if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) return ROCK_ALBEDO;
		if (biome.is(BiomeTags.IS_SAVANNA)) return GRASS_ALBEDO;

		// nothing claimed it, so rainfall decides again: a desert is sand and anywhere wetter is
		// vegetation, which is darker
		return downfall < 0.2 ? SAND_ALBEDO : GRASS_ALBEDO;
	}

	private static double roughnessOf(net.minecraft.core.Holder<Biome> biome, double downfall) {
		if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER)) return WATER;
		if (biome.is(BiomeTags.IS_BEACH)) return SNOW_OR_SAND;
		// checked before the mountain tags on purpose: a windswept forest carries both, and it
		// is the trees that decide what the wind does, not the slope they stand on
		if (biome.is(BiomeTags.IS_JUNGLE)) return DENSE_FOREST;
		if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA)) return FOREST;
		if (biome.is(BiomeTags.IS_SAVANNA)) return SCRUB;
		if (biome.is(BiomeTags.IS_BADLANDS)) return BARE_GROUND;
		if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) return ROCK;

		// nothing claimed it: read the roughness off how much grows there. Tuned so a desert
		// lands on bare ground and plains on grassland, which are the two this actually decides
		return Mth.clamp(0.008 * Math.pow(12.0, Mth.clamp(downfall, 0.0, 1.0)), WATER, DENSE_FOREST);
	}

	/**
	 * Mean ground level of the eight points a couple of chunks out, or NaN if fewer than half
	 * of them are in loaded chunks.
	 *
	 * Only loaded chunks are read, and never generated, because a weather sample must not be
	 * able to force worldgen: a turbine ticking at the edge of a player's render distance
	 * would otherwise drag new terrain into existence twenty times a second.
	 */
	private static double surveySurroundings(ServerLevel level, BlockPos groundPos) {
		int[][] offsets = {{EXPOSURE_RADIUS, 0}, {-EXPOSURE_RADIUS, 0}, {0, EXPOSURE_RADIUS}, {0, -EXPOSURE_RADIUS}, {23, 23}, {23, -23}, {-23, 23}, {-23, -23}};
		double total = 0.0;
		int found = 0;

		for (int[] offset : offsets) {
			int x = groundPos.getX() + offset[0];
			int z = groundPos.getZ() + offset[1];
			if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;

			total += level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			found++;
		}

		return found < offsets.length / 2 ? Double.NaN : total / found;
	}
}
