package com.dooji.electricity.main.weather;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What stands between an array and the sky.
  *
 * It is also why {@link PlaneIrradiance} carries its three components apart rather than added up.
 */
public final class Shading {
	/** How far a beam ray is traced, in blocks. */
	private static final int BEAM_TRACE_BLOCKS = 24;
	/** Step along the ray, in blocks. */
	private static final double TRACE_STEP = 0.5;
	/** How far a sky ray is traced. */
	private static final int SKY_TRACE_BLOCKS = 12;
	/** How far along the sun's direction an entity can still cast its shadow onto the array, in blocks. */
	private static final double ENTITY_SHADOW_REACH = 6.0;

	/** What a block passes, as a fraction */
	private static final double GLASS = 0.88;
	private static final double GLASS_PANE = 0.92;
	private static final double ICE = 0.70;
	private static final double PACKED_ICE = 0.35;
	private static final double WATER = 0.50;
	private static final double LEAVES = 0.25;
	/** What a block that is not solid but does not pass skylight either lets through. */
	private static final double PARTIAL = 0.05;

	/** Metres of real snow one vanilla snow layer stands for. */
	public static final double METRES_PER_SNOW_LAYER = 0.05;
	/** Depth of snow that halves what reaches the cells, in metres. */
	private static final double SNOW_HALF_DEPTH_M = 0.014;

	/** Soiling accumulation, as a fraction of output lost per day of dry weather. */
	private static final double SOILING_PER_DAY_DRY = 0.0030;
	private static final double SOILING_PER_DAY_WET = 0.0005;
	/** Where soiling stops getting worse. */
	private static final double SOILING_CEILING = 0.15;

	private Shading() {
	}

	/** What the snow on the glass passes. */
	public static double snowTransmittance(double snowDepthM) {
		return snowDepthM <= 0.0 ? 1.0 : Math.exp(-snowDepthM * Math.log(2.0) / SNOW_HALF_DEPTH_M);
	}

	// ---- what is overhead ----

	/** Fraction of the beam reaching a block, traced along the sun's own direction. */
	public static double beamFraction(Level level, BlockPos arrayPos, SunPosition sun) {
		if (!sun.up()) return 0.0;

		Vec3 towardsSun = towardsSun(sun);
		Vec3 from = new Vec3(arrayPos.getX() + 0.5, arrayPos.getY() + 1.0, arrayPos.getZ() + 0.5);
		return traceTransmittance(level, from, towardsSun, BEAM_TRACE_BLOCKS);
	}

	/** Fraction of the sky dome a block can see */
	public static double skyViewFactor(Level level, BlockPos arrayPos) {
		Vec3 from = new Vec3(arrayPos.getX() + 0.5, arrayPos.getY() + 1.0, arrayPos.getZ() + 0.5);

		double weighted = traceTransmittance(level, from, new Vec3(0.0, 1.0, 0.0), SKY_TRACE_BLOCKS);
		double totalWeight = 1.0;

		double sin45 = Math.sin(Math.toRadians(45.0));
		double cos45 = Math.cos(Math.toRadians(45.0));
		for (int i = 0; i < 8; i++) {
			double bearing = Math.toRadians(i * 45.0);
			Vec3 direction = new Vec3(cos45 * Math.cos(bearing), sin45, cos45 * Math.sin(bearing));
			weighted += sin45 * traceTransmittance(level, from, direction, SKY_TRACE_BLOCKS);
			totalWeight += sin45;
		}

		return Mth.clamp(weighted / totalWeight, 0.0, 1.0);
	}

	/** Unit vector pointing at the sun, on the world's axes. */
	private static Vec3 towardsSun(SunPosition sun) {
		double bearing = Math.toRadians(sun.azimuthDeg());
		double cosElevation = sun.cosElevation();
		return new Vec3(cosElevation * Math.cos(bearing), sun.sinElevation(), cosElevation * Math.sin(bearing));
	}

	private static double traceTransmittance(Level level, Vec3 from, Vec3 direction, int blocks) {
		Vec3 step = direction.normalize().scale(TRACE_STEP);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		BlockPos last = null;
		double transmittance = 1.0;

		int steps = (int) Math.ceil(blocks / TRACE_STEP);
		for (int i = 1; i <= steps; i++) {
			cursor.set(Mth.floor(from.x + step.x * i), Mth.floor(from.y + step.y * i), Mth.floor(from.z + step.z * i));
			if (cursor.getY() > level.getMaxBuildHeight()) break;
			if (cursor.getY() < level.getMinBuildHeight()) continue;
			// an unloaded chunk ends the trace rather than blocking it: an unknown must not be able to
			// manufacture a shadow that appears and disappears with a player's render distance
			if (!level.hasChunkAt(cursor)) break;
			if (cursor.equals(last)) continue;

			last = cursor.immutable();
			transmittance *= blockTransmittance(level, level.getBlockState(last), last);
			if (transmittance <= 0.0) return 0.0;
		}

		return transmittance;
	}

	/** What one block passes. */
	private static double blockTransmittance(BlockGetter level, BlockState state, BlockPos pos) {
		if (state.isAir()) return 1.0;
		if (state.is(BlockTags.LEAVES)) return LEAVES;
		if (state.is(BlockTags.IMPERMEABLE)) return GLASS;
		// glass panes and iron bars are the same class in vanilla, and both pass more than a solid
		// pane of glass does because most of what a player builds out of them is gaps
		if (state.getBlock() instanceof IronBarsBlock) return GLASS_PANE;
		if (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)) return ICE;
		if (state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) return PACKED_ICE;
		if (!state.getFluidState().isEmpty()) return WATER;

		int lightBlock = state.getLightBlock(level, pos);
		if (lightBlock >= 15 || state.isSolidRender(level, pos)) return 0.0;
		if (lightBlock <= 0) return 1.0;

		return PARTIAL;
	}

	// ---- what is moving over it ----

	/** Fraction of the beam an array loses to whatever is standing over it, 0 to 1 kept. */
	public static double entityBeamFraction(Level level, BlockPos arrayPos, SunPosition sun) {
		if (!sun.up()) return 1.0;

		Vec3 towardsSun = towardsSun(sun);
		Vec3 base = new Vec3(arrayPos.getX() + 0.5, arrayPos.getY() + 1.0, arrayPos.getZ() + 0.5);
		Vec3 far = base.add(towardsSun.scale(ENTITY_SHADOW_REACH));
		AABB corridor = new AABB(base, far).inflate(1.0);

		List<Entity> entities = level.getEntities((Entity) null, corridor, entity -> !entity.isSpectator() && !entity.isInvisible());
		if (entities.isEmpty()) return 1.0;

		double covered = 0.0;
		for (Entity entity : entities) {
			AABB box = entity.getBoundingBox();
			// the shadow of a body is its cross-section across the beam, and the footprint is a good
			// enough stand-in: a player is 0.6 of a block wide either way
			covered += Mth.clamp((box.getXsize() * box.getZsize()), 0.0, 1.0);
		}

		return Mth.clamp(1.0 - covered, 0.0, 1.0);
	}

	// ---- the array shading itself ----

	/** Fraction of a row that the row in front of it is shading, at a given sun elevation. */
	public static double rowShadedFraction(double groundCoverRatio, double tiltDeg, double sunElevationDeg) {
		if (groundCoverRatio <= 0.0 || tiltDeg <= 0.0 || sunElevationDeg <= 0.0) return 0.0;

		double tilt = Math.toRadians(tiltDeg);
		double elevation = Math.toRadians(Math.min(89.99, sunElevationDeg));
		double reach = Math.cos(tilt) + Math.sin(tilt) / Math.tan(elevation);
		double clear = groundCoverRatio * reach;
		if (clear <= 1.0) return 0.0;

		return Mth.clamp(1.0 - 1.0 / clear, 0.0, 1.0);
	}

	// ---- snow ----

	/** Snow lying on an array, in metres. */
	public static double snowDepthOver(Level level, BlockPos arrayPos) {
		BlockPos above = arrayPos.above();
		if (!level.hasChunkAt(above)) return 0.0;

		BlockState state = level.getBlockState(above);
		if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) return 8.0 * METRES_PER_SNOW_LAYER;
		if (state.getBlock() instanceof SnowLayerBlock) return state.getValue(SnowLayerBlock.LAYERS) * METRES_PER_SNOW_LAYER;

		return 0.0;
	}

	// ---- soiling ----

	/** How fast dust builds up here, as a fraction of output lost per day. */
	public static double soilingPerDay(double downfall) {
		double dryness = 1.0 - Mth.clamp(downfall, 0.0, 1.0);
		return SOILING_PER_DAY_WET + (SOILING_PER_DAY_DRY - SOILING_PER_DAY_WET) * dryness;
	}

	/** Where soiling stops getting worse, as a fraction of output. */
	public static double soilingCeiling() {
		return SOILING_CEILING;
	}

	/** How much of the accumulated dust a rain shower takes off, as a fraction. */
	public static double washedByRain(double tiltDeg) {
		return tiltDeg >= 10.0 ? 1.0 : 0.55;
	}
}
