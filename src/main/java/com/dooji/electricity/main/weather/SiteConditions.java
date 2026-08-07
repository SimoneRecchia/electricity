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

/** What the ground under a machine does to the wind and the sunlight reaching it. */
public record SiteConditions(double roughness, double downfall, double biomeTemperature, double albedo, double surroundingGroundY) {
	/** How far out the land is measured to decide whether a site stands proud of it. */
	private static final int EXPOSURE_RADIUS = 32;
	/** Metres of relative elevation worth a full share of the speed-up below. */
	private static final double EXPOSURE_SCALE = 500.0;
	private static final double MAX_SPEED_UP = 0.30;
	private static final double MAX_SHELTER = -0.12;

	/** Roughness lengths, in metres */
	private static final double WATER = Atmosphere.SMOOTHEST_ROUGHNESS;
	private static final double SNOW_OR_SAND = 0.005;
	private static final double BARE_GROUND = 0.012;
	private static final double SCRUB = 0.05;
	private static final double ROCK = 0.06;
	private static final double FOREST = 0.55;
	private static final double DENSE_FOREST = 1.0;

	/** Albedos, and they are the published figures for the same ground the roughnesses above describe. */
	private static final double WATER_ALBEDO = 0.07;
	private static final double SNOW_ALBEDO = 0.80;
	private static final double SAND_ALBEDO = 0.35;
	private static final double BARE_GROUND_ALBEDO = 0.30;
	private static final double ROCK_ALBEDO = 0.20;
	private static final double GRASS_ALBEDO = 0.22;
	private static final double FOREST_ALBEDO = 0.14;
	private static final double DENSE_FOREST_ALBEDO = 0.12;

	/** Fraction to add to the wind aloft for a site that stands above the country around it. */
	public double exposure(double groundY) {
		if (Double.isNaN(surroundingGroundY)) return 0.0;

		double relativeM = (groundY - surroundingGroundY) * WorldConditions.METRES_PER_BLOCK;
		return Mth.clamp(relativeM / EXPOSURE_SCALE, MAX_SHELTER, MAX_SPEED_UP);
	}

	/** Surveys the column a machine stands on. */
	public static SiteConditions survey(ServerLevel level, BlockPos groundPos) {
		var biome = level.getBiome(groundPos);
		var climate = biome.value().getModifiedClimateSettings();
		double downfall = climate.downfall();

		return new SiteConditions(roughnessOf(biome, downfall), downfall, biome.value().getBaseTemperature(),
				albedoOf(level, groundPos, biome, downfall), surveySurroundings(level, groundPos));
	}

	/** How much of the light this ground throws back up. */
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
		// checked before the mountain tags on purpose: a windswept forest carries both
		// is the trees that decide what the wind does
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
	 * Mean ground level of the eight points a couple of chunks out, or NaN if fewer than half of them are in loaded chunks.
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
