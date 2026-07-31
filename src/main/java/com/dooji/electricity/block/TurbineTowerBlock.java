package com.dooji.electricity.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block of turbine tower.
 *
 * A tower is stacked by hand, which is what makes hub height something a player
 * builds rather than a number they set. The machine goes on top and refuses to mount
 * outside the range of tower heights it is certified for.
 *
 * <h2>What you see and what you walk into</h2>
 *
 * The tower is drawn from the authored model's own tube, so it looks exactly as it
 * always did. Collision cannot follow that geometry exactly - a VoxelShape is a union
 * of axis-aligned boxes confined to the block's own cube, so a circle is not on offer
 * - but it can come within half a pixel, and here it does.
 *
 * The reason it can is a measurement. The authored tube is barely tapered: it runs
 * from a radius of 0.381 at the foot to 0.312 at the top, over twelve and a half
 * blocks, and the 0.622 that looks like the tower's width is only the base plinth,
 * eight centimetres tall. One shape at the middle of that range is therefore never
 * more than 0.03 blocks out anywhere on any tower - half a pixel at block resolution
 * - which is why a shape per height would buy nothing.
 *
 * Two crossed boxes at cos(45 degrees) give the octagon, the same way vanilla builds
 * lanterns and iron bars.
 */
public class TurbineTowerBlock extends Block implements EntityBlock {
	/**
	 * Where a walk up or down a tower gives up.
	 *
	 * Above the tallest certified tower with room to spare, so it is a guard against an
	 * unbounded scan rather than a limit a player can reach.
	 */
	private static final int SCAN_LIMIT = 16;
	/**
	 * Radius the collision octagon is built at, in blocks.
	 *
	 * The middle of the authored tube's 0.381-to-0.312 taper, so the error is shared
	 * evenly between the foot and the top rather than piling up at one end.
	 */
	private static final double COLLISION_RADIUS = 0.3465;
	/** cos(45 degrees): how far the crossing box reaches, to turn a plus into an octagon. */
	private static final double OCTAGON = 0.70710678;

	private static final VoxelShape SHAPE = Shapes.or(
			Shapes.box(0.5 - COLLISION_RADIUS, 0.0, 0.5 - COLLISION_RADIUS * OCTAGON, 0.5 + COLLISION_RADIUS, 1.0, 0.5 + COLLISION_RADIUS * OCTAGON),
			Shapes.box(0.5 - COLLISION_RADIUS * OCTAGON, 0.0, 0.5 - COLLISION_RADIUS, 0.5 + COLLISION_RADIUS * OCTAGON, 1.0, 0.5 + COLLISION_RADIUS));

	public TurbineTowerBlock(Properties properties) {
		super(properties);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/**
	 * Drawn by the mod's own OBJ pipeline, like every other machine here, so the tube is
	 * the authored one rather than an approximation in cuboids.
	 */
	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	/**
	 * Empty, and only present so the renderer can find the tower.
	 *
	 * This mod draws through {@code RenderLevelStageEvent} over tracked block entities
	 * rather than through the chunk mesh, and a tower with nothing to be found by would
	 * be invisible until a machine was mounted on it - which is no way to stack thirteen
	 * of them. It never ticks and holds no state.
	 */
	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TurbineTowerBlockEntity(pos, state);
	}

	/**
	 * How many tower blocks stand under this position.
	 *
	 * Counted from the world rather than stored anywhere, so the height a turbine sees and
	 * the tower a player can walk up are one fact with one source and cannot come apart.
	 */
	public static int countBelow(BlockGetter level, BlockPos pos) {
		return count(level, pos, Direction.DOWN);
	}

	/** How many tower blocks stand above this position, for a stack with nothing on it yet. */
	public static int countAbove(BlockGetter level, BlockPos pos) {
		return count(level, pos, Direction.UP);
	}

	private static int count(BlockGetter level, BlockPos pos, Direction direction) {
		int found = 0;
		BlockPos.MutableBlockPos cursor = pos.mutable().move(direction);
		while (found <= SCAN_LIMIT && level.getBlockState(cursor).getBlock() instanceof TurbineTowerBlock) {
			found++;
			cursor.move(direction);
		}

		return found;
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
		if (!canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();

		return state;
	}

	/** A tube is mostly air, so it neither blocks light nor stops the sky. */
	@Override
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return 0;
	}

	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		return true;
	}

	/**
	 * The turbine this tower carries, if it has one yet.
	 *
	 * Walks up, because the machine is always on top and the answer is wanted from
	 * anywhere on the tower: a wrench used at the foot should open the panel of the
	 * machine it belongs to, and the renderer needs to know whether the machine is
	 * already drawing the tower.
	 */
	@Nullable
	public static BlockPos findTurbineAbove(BlockGetter level, BlockPos pos) {
		BlockPos.MutableBlockPos cursor = pos.mutable().move(Direction.UP);
		for (int step = 0; step <= SCAN_LIMIT; step++) {
			BlockState state = level.getBlockState(cursor);
			if (state.getBlock() instanceof WindTurbineBlock) return cursor.immutable();
			if (!(state.getBlock() instanceof TurbineTowerBlock)) return null;
			cursor.move(Direction.UP);
		}

		return null;
	}

	/** Whether this block is the foot of its stack, which is the one that draws the tube. */
	public static boolean isFoot(BlockGetter level, BlockPos pos) {
		return countBelow(level, pos) == 0;
	}
}
