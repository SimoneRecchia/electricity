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
 * <h2>Why shading gets its own file, and why the beam and the sky are shaded separately</h2>
 *
 * Because they are separate things and the difference is most of what makes a shadow behave
 * believably. The beam arrives from one direction, so a single block in that direction takes all of
 * it. The diffuse arrives from the whole dome, so the same block takes only the share of the dome it
 * covers. Under a clear sky, where nine tenths of the light is beam, a shadow costs nearly
 * everything; under an overcast one, where it is nearly all diffuse, the same shadow costs almost
 * nothing. A model with one lumped irradiance figure cannot say that and gets both cases wrong.
 *
 * It is also why {@link PlaneIrradiance} carries its three components apart rather than added up.
 *
 * <h2>Four kinds of shade, and they are not interchangeable</h2>
 *
 * <ul>
 * <li><b>Something overhead</b> - a roof, a tree, a hill, a pane of glass. Traced along the actual
 *     direction of the sun, and attenuated rather than switched, because glass and water and leaves
 *     pass a real fraction of what falls on them.</li>
 * <li><b>Something moving over it</b> - a player standing on a panel, a mob wandering across it.
 *     Costs a fraction of the block and moves off again, which is the one shadow a player will
 *     actually see happen.</li>
 * <li><b>The array shading itself</b> - one row's shadow falling on the next at low sun. This is not
 *     an obstruction, it is geometry, and it is the entire reason ground cover ratio and backtracking
 *     exist. See {@link #rowShadedFraction}.</li>
 * <li><b>Snow lying on the glass</b>, which is not shade from outside at all but has the same effect
 *     and is the loss a cold-climate plant actually loses its winter to.</li>
 * </ul>
 *
 * <h2>What this does not do, deliberately</h2>
 *
 * Two arrays standing side by side do not shade each other beyond what the row geometry already
 * accounts for, and that is correct rather than a simplification. A block is a hundred square metres
 * carrying several rows, so a neighbouring block simply continues the same rows at the same pitch -
 * the shadow that falls across the boundary is the same shadow the rows inside the block are already
 * casting on each other. Adding a second penalty for it would count it twice.
 *
 * The consequence is what a real field looks like from the fence: every array in a large one produces
 * very nearly the same, and the only thing that separates them is cloud drifting across. Which is
 * exactly the effect the cumulus field exists to produce.
 */
public final class Shading {
	/**
	 * How far a beam ray is traced, in blocks.
	 *
	 * Twenty-four, and the limit is a judgement about what shadows are worth finding. A tree or a wall
	 * near an array is what actually shades it; a mountain on the horizon lowers the effective sunrise
	 * and sunset, which the sky model would need to know about rather than the shading model. Tracing
	 * further would find hills that block the sun at three degrees of elevation, where there is almost
	 * nothing left to block.
	 */
	private static final int BEAM_TRACE_BLOCKS = 24;
	/** Step along the ray, in blocks. Half a block cannot skip a wall and is cheap enough to do every tick. */
	private static final double TRACE_STEP = 0.5;
	/** How far a sky ray is traced. Shorter than the beam: what shades the dome is what is close and overhead. */
	private static final int SKY_TRACE_BLOCKS = 12;
	/** How far along the sun's direction an entity can still cast its shadow onto the array, in blocks. */
	private static final double ENTITY_SHADOW_REACH = 6.0;

	/**
	 * What a block passes, as a fraction, when the light has to go through it.
	 *
	 * Real transmittances, not switches, because the difference is visible: a panel under glass loses
	 * about a tenth and keeps working, and a panel under leaves loses three quarters and does not. The
	 * mod's own weather already treats cloud this way; there is no reason a greenhouse roof should be
	 * treated as either absent or opaque.
	 */
	private static final double GLASS = 0.88;
	private static final double GLASS_PANE = 0.92;
	private static final double ICE = 0.70;
	private static final double PACKED_ICE = 0.35;
	private static final double WATER = 0.50;
	private static final double LEAVES = 0.25;
	/**
	 * What a block that is not solid but does not pass skylight either lets through.
	 *
	 * Slabs, stairs, fences, a carpet - all the geometry vanilla treats as "blocks light by one". Five
	 * percent rather than nothing, because these are things with gaps in them, and rather than a tenth
	 * because a shadow under a staircase is a shadow.
	 */
	private static final double PARTIAL = 0.05;

	/**
	 * Metres of real snow one vanilla snow layer stands for.
	 *
	 * Not the block grid's ten metres, and it cannot be: eight layers of snow is one block, and a block
	 * of real snow at the mod's scale would be ten metres deep. So the layers are read as what they
	 * look like instead - a light dusting to about a third of a metre across the eight - which puts a
	 * single layer just over the depth at which a tracker decides to stand up and shed it, and puts a
	 * full stack deep enough to bury a fixed array for the season.
	 */
	public static final double METRES_PER_SNOW_LAYER = 0.05;
	/**
	 * Depth of snow that halves what reaches the cells, in metres.
	 *
	 * Snow is translucent, which is the only reason a buried array ever frees itself: a centimetre or
	 * two passes enough light for the modules to make a little power, the power warms the glass, and the
	 * film of meltwater under the snow lets it slide. Five centimetres passes almost nothing and the
	 * array stays buried until the air warms up. That threshold is why tilt matters so much in a cold
	 * climate and why a tracker's snow stow is worth having.
	 */
	private static final double SNOW_HALF_DEPTH_M = 0.014;

	/**
	 * Soiling accumulation, as a fraction of output lost per day of dry weather.
	 *
	 * Kimber's figures from the American southwest: a tenth to three tenths of a percent a day, which
	 * sounds trivial and is not - left alone through a dry season it reaches double figures. Dry places
	 * are worse, and not because they are dusty so much as because it never rains to wash it off, so
	 * the rate is read off the biome's own rainfall.
	 */
	private static final double SOILING_PER_DAY_DRY = 0.0030;
	private static final double SOILING_PER_DAY_WET = 0.0005;
	/**
	 * Where soiling stops getting worse.
	 *
	 * It saturates, because a layer of dust shelters the glass under it from the next layer and because
	 * wind lifts loose dust off again. Fifteen percent is about the worst a real plant reaches between
	 * washes in a desert.
	 */
	private static final double SOILING_CEILING = 0.15;

	private Shading() {
	}

	/**
	 * What the snow on the glass passes.
	 *
	 * Exponential in depth with a very short half-depth, so the transition from producing a little and
	 * warming itself free to producing nothing at all happens over a couple of centimetres - which is
	 * where it happens outside. One vanilla snow layer, five centimetres, already passes only 8%.
	 */
	public static double snowTransmittance(double snowDepthM) {
		return snowDepthM <= 0.0 ? 1.0 : Math.exp(-snowDepthM * Math.log(2.0) / SNOW_HALF_DEPTH_M);
	}

	// ---- what is overhead ----

	/**
	 * Fraction of the beam reaching a block, traced along the sun's own direction.
	 *
	 * A ray march rather than a single check straight up, because the sun is only overhead at noon: a
	 * wall to the east shades an array at breakfast and not at lunch, which is the whole point of
	 * tracing a direction. Transmittances multiply along the way, so a panel under glass under leaves
	 * gets both.
	 *
	 * Unloaded chunks stop the trace and count as clear. That direction is deliberate and it matches
	 * how {@link SiteConditions} treats an unsurveyed hill: an unknown must never be able to invent a
	 * shadow, because a shadow that appears when a neighbouring chunk loads would make an array's
	 * output depend on where the players happen to be standing.
	 */
	public static double beamFraction(Level level, BlockPos arrayPos, SunPosition sun) {
		if (!sun.up()) return 0.0;

		Vec3 towardsSun = towardsSun(sun);
		Vec3 from = new Vec3(arrayPos.getX() + 0.5, arrayPos.getY() + 1.0, arrayPos.getZ() + 0.5);
		return traceTransmittance(level, from, towardsSun, BEAM_TRACE_BLOCKS);
	}

	/**
	 * Fraction of the sky dome a block can see, 0 to 1.
	 *
	 * Nine rays: one straight up and eight around the compass at 45 degrees, weighted by the cosine of
	 * their zenith angle because that is how much each direction contributes to a horizontal plane. It
	 * is a coarse hemisphere, and coarse is the right resolution - the answer only has to distinguish
	 * open sky from a courtyard from under a roof, and it changes when somebody builds rather than when
	 * the sun moves. Callers cache it.
	 *
	 * The cosine weighting is what makes a panel in a narrow gap behave correctly: the sky directly
	 * overhead is worth twice as much to it as the sky halfway down, so a slot open only at the zenith
	 * still collects most of the diffuse it would in the open.
	 */
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

	/**
	 * Unit vector pointing at the sun, on the world's axes.
	 *
	 * The mod's compass has 0 degrees at east, which is {@code +x}, and 90 at south, which is
	 * {@code +z} - the same convention {@link Atmosphere.Flow#direction()} uses, so a sun bearing and a
	 * wind heading are directly comparable.
	 */
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

	/**
	 * What one block passes.
	 *
	 * The named cases come first because they are the ones a player builds with on purpose - a
	 * greenhouse roof, a pond, a canopy - and vanilla's own light-blocking value cannot tell them
	 * apart: glass and air both propagate skylight and would both come out at nothing, which would
	 * make a panel under a glass roof produce exactly as much as one in the open.
	 */
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

	/**
	 * Fraction of the beam an array loses to whatever is standing over it, 0 to 1 kept.
	 *
	 * Along the sun's direction rather than straight up, so a player casts their shadow onto the panel
	 * beside them in the morning and onto the one they are standing on at noon, exactly as they do
	 * outside.
	 *
	 * The share taken is the entity's own footprint against the block's square metre, which puts a
	 * player at about a third of one array block - noticeable on the readout and gone as soon as they
	 * move. A crowd, or something very large, can take the lot.
	 */
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
			// enough stand-in: a player is 0.6 of a block wide either way, so about a third of one
			covered += Mth.clamp((box.getXsize() * box.getZsize()), 0.0, 1.0);
		}

		return Mth.clamp(1.0 - covered, 0.0, 1.0);
	}

	// ---- the array shading itself ----

	/**
	 * Fraction of a row that the row in front of it is shading, at a given sun elevation.
	 *
	 * <h2>Where this comes from</h2>
	 *
	 * Take the cross-section across the rows. A row of width {@code L} tilted at {@code b} stands with
	 * its face towards the sun; the next row's foot is {@code P} away. Follow the ray from the top edge
	 * of the first row down to where it meets the second, solve for how far up the second row it lands,
	 * and the shaded fraction comes out as
	 *
	 * <pre>  1 - 1 / (GCR (cos b + sin b cot a))</pre>
	 *
	 * clamped below at nothing. Three things make it worth trusting rather than merely plausible.
	 *
	 * A flat array never shades itself, whatever its ground cover: at {@code b = 0} the bracket is 1
	 * and the expression is {@code 1 - 1/GCR}, which is negative for any real array. That is exactly
	 * why this world's flat table can be packed nearly solid while a tilted rack cannot.
	 *
	 * Under true tracking, where the plane is normal to the sun and {@code b = 90 - a}, the bracket
	 * collapses to {@code 1/sin a} and the whole thing becomes {@code 1 - sin(a)/GCR}. So shading
	 * begins precisely when {@code sin a < GCR} - which is the condition
	 * {@link com.dooji.electricity.api.power.TrackerSpec#backtrackRotation} was derived from
	 * independently, on a different argument about shadow widths on the ground.
	 *
	 * And feeding this the backtracking angle gives exactly zero. Substituting
	 * {@code sin(a + b) = sin(a)/GCR}, which is what that method solves for, turns the bracket into
	 * {@code sin(a + b)/sin(a)} and the product into 1. Two derivations from different starting points
	 * meeting at the same answer is the best evidence available that either of them is right.
	 */
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

	/**
	 * Snow lying on an array, in metres.
	 *
	 * Read out of the world rather than accumulated, so it agrees with what a player can see: if there
	 * is snow on the block, there is snow on the modules. Snow blocks count as a full stack of layers,
	 * because eight layers and a block are the same amount of snow to vanilla.
	 */
	public static double snowDepthOver(Level level, BlockPos arrayPos) {
		BlockPos above = arrayPos.above();
		if (!level.hasChunkAt(above)) return 0.0;

		BlockState state = level.getBlockState(above);
		if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) return 8.0 * METRES_PER_SNOW_LAYER;
		if (state.getBlock() instanceof SnowLayerBlock) return state.getValue(SnowLayerBlock.LAYERS) * METRES_PER_SNOW_LAYER;

		return 0.0;
	}

	// ---- soiling ----

	/**
	 * How fast dust builds up here, as a fraction of output lost per day.
	 *
	 * From the biome's rainfall, and the causation is the other way round from the obvious one: a
	 * desert is not worse because it is dusty, it is worse because nothing ever washes it. So the rate
	 * follows the dryness.
	 */
	public static double soilingPerDay(double downfall) {
		double dryness = 1.0 - Mth.clamp(downfall, 0.0, 1.0);
		return SOILING_PER_DAY_WET + (SOILING_PER_DAY_DRY - SOILING_PER_DAY_WET) * dryness;
	}

	/** Where soiling stops getting worse, as a fraction of output. */
	public static double soilingCeiling() {
		return SOILING_CEILING;
	}

	/**
	 * How much of the accumulated dust a rain shower takes off, as a fraction.
	 *
	 * Not all of it, and how much depends on the tilt. Rain running off a tilted module carries the
	 * dust with it, which is most of what a tilt is for in a dusty climate. Rain on a flat module pools,
	 * spreads the dust about and dries dirty - so a flat array in a dry climate has a standing soiling
	 * loss that never quite washes out, which is a real and well documented nuisance.
	 */
	public static double washedByRain(double tiltDeg) {
		return tiltDeg >= 10.0 ? 1.0 : 0.55;
	}
}
