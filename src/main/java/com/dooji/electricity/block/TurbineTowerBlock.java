package com.dooji.electricity.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block of turbine tower.
 *
 * A tower is stacked by hand, which is what makes hub height something a player
 * builds rather than a number they set. The machine goes on top and refuses to
 * mount outside the range of tower heights it is certified for.
 *
 * <h2>Why the shape and the model come from one table</h2>
 *
 * The whole point of this block is that what you collide with is what you see. So
 * {@link #radiusAt} is the only description of the tower's profile: the collision
 * shape here and the generated block models both derive from it, and neither can
 * drift from the other because there is nothing to drift from.
 *
 * Minecraft collision is a union of axis-aligned boxes confined to the block's own
 * cube, so a circle is not available. Two crossed boxes at cos(45 degrees) give an
 * octagonal column, which at a tower barely a block wide differs from a circle by a
 * couple of pixels - the same trick vanilla uses for lanterns and iron bars, and
 * cheap enough that a thirteen-block tower costs nothing to walk past.
 *
 * The profile is fixed from the bottom rather than scaled to the tower's total
 * height, which is both simpler and more honest: it is one tower design, and a
 * short tower is a shorter section of it, tapering less because it stops sooner.
 */
public class TurbineTowerBlock extends Block {
	/**
	 * How far up the stack this block sits, counted from the bottom.
	 *
	 * Held in the state rather than worked out on demand because the collision shape
	 * depends on it and collision is asked far more often than the tower changes.
	 */
	public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, 15);

	/** Tallest tower any machine in the catalogue is certified for, and the taper's span. */
	public static final int TAPER_SPAN = 13;
	/**
	 * Radius at the foot, in blocks. Chosen so the widest segment still fits inside its
	 * own block with room to spare - the authored model's 0.62 would have spilled over the
	 * edge and forced the bottom third of every tower to collide as a full cube, which is
	 * the opposite of the point.
	 */
	private static final double BASE_RADIUS = 0.46;
	/** Radius at the top of the taper. */
	private static final double TOP_RADIUS = 0.22;
	/** cos(45 degrees): how far the crossing box reaches, to turn a plus into an octagon. */
	private static final double OCTAGON = 0.70710678;

	private static final VoxelShape[] SHAPES = buildShapes();

	public TurbineTowerBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(SEGMENT, 0));
	}

	/**
	 * Tower radius at a given height up the stack, in blocks.
	 *
	 * The single source of the tower's shape: the collision boxes and the generated block
	 * models are both this, so they cannot disagree.
	 */
	public static double radiusAt(int segment) {
		double t = Mth.clamp(segment / (double) TAPER_SPAN, 0.0, 1.0);
		return Mth.lerp(t, BASE_RADIUS, TOP_RADIUS);
	}

	private static VoxelShape[] buildShapes() {
		VoxelShape[] shapes = new VoxelShape[16];
		for (int segment = 0; segment < shapes.length; segment++) {
			double radius = radiusAt(segment);
			double wide = radius;
			double narrow = radius * OCTAGON;
			shapes[segment] = Shapes.or(
					Shapes.box(0.5 - wide, 0.0, 0.5 - narrow, 0.5 + wide, 1.0, 0.5 + narrow),
					Shapes.box(0.5 - narrow, 0.0, 0.5 - wide, 0.5 + narrow, 1.0, 0.5 + wide));
		}

		return shapes;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SEGMENT);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(SEGMENT)];
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		int below = countBelow(context.getLevel(), context.getClickedPos());
		return defaultBlockState().setValue(SEGMENT, Math.min(below, 15));
	}

	/**
	 * How many tower blocks stand under this position.
	 *
	 * Counted from the world rather than stored anywhere, so the height a turbine sees and
	 * the tower a player can walk up are the same fact and cannot come apart. The scan
	 * stops at the state's own ceiling, which no machine's certified range reaches anyway.
	 */
	public static int countBelow(BlockGetter level, BlockPos pos) {
		int count = 0;
		BlockPos.MutableBlockPos cursor = pos.mutable().move(Direction.DOWN);
		while (count <= 16 && level.getBlockState(cursor).getBlock() instanceof TurbineTowerBlock) {
			count++;
			cursor.move(Direction.DOWN);
		}

		return count;
	}

	/**
	 * A tower needs a tower under it, or ground.
	 *
	 * One rule, and the collapse comes out of it for free: knock out the bottom and every
	 * block above fails the same check in turn and drops. That is the behaviour ladders and
	 * torches already have, so nobody has to be told, and it matches how a real tower fails.
	 */
	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		BlockState support = level.getBlockState(below);
		if (support.getBlock() instanceof TurbineTowerBlock) return true;

		return support.isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		if (!canSurvive(state, level, pos)) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

		// the index is what the shape is drawn and collided from, so it has to follow the
		// stack when a tower is built onto or cut down rather than staying as placed
		if (direction == Direction.DOWN) {
			int below = Math.min(countBelow(level, pos), 15);
			if (below != state.getValue(SEGMENT)) return state.setValue(SEGMENT, below);
		}

		return state;
	}

	/** Nothing about a tower blocks light in a way worth computing; it is mostly air. */
	@Override
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return 0;
	}

	@Override
	public boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		return true;
	}

	/**
	 * The turbine that this tower carries, if it has one yet.
	 *
	 * Walks up rather than down because the machine is always on top, and the answer is
	 * wanted from anywhere on the tower: a wrench used halfway up should open the panel of
	 * the machine it belongs to.
	 */
	@Nullable
	public static BlockPos findTurbineAbove(Level level, BlockPos pos) {
		BlockPos.MutableBlockPos cursor = pos.mutable().move(Direction.UP);
		for (int step = 0; step <= 16; step++) {
			BlockState state = level.getBlockState(cursor);
			if (state.getBlock() instanceof WindTurbineBlock) return cursor.immutable();
			if (!(state.getBlock() instanceof TurbineTowerBlock)) return null;
			cursor.move(Direction.UP);
		}

		return null;
	}
}
