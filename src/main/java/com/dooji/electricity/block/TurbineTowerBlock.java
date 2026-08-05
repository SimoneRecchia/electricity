package com.dooji.electricity.block;

import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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

/** One block of turbine tower. */
public class TurbineTowerBlock extends Block implements EntityBlock {
	/** Blocks a tower carries without complaint. */
	public static final int SAFE_HEIGHT = 16;
	/** Blocks a tower cannot hold up at all, even undisturbed. */
	public static final int MAX_HEIGHT = 20;
	/** Where a walk up or down a tower gives up. */
	private static final int SCAN_LIMIT = MAX_HEIGHT + 4;
	/** Radius the collision octagon is built at, in blocks. */
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
	 * Drawn by the mod's own OBJ pipeline, like every other machine here, so the tube is the authored one rather than an approximation in cuboids.
	 */
	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	/** Empty, and only present so the renderer can find the tower. */
	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TurbineTowerBlockEntity(pos, state);
	}

	/** How many tower blocks stand under this position. */
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

	/** A tower needs a tower under it */
	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		BlockState support = level.getBlockState(below);
		if (support.getBlock() instanceof TurbineTowerBlock) return true;

		return !support.getCollisionShape(level, below).isEmpty();
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		if (!canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();

		return state;
	}

	/** Weight on top of an over-tall tower brings the excess down. */
	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		super.stepOn(level, pos, state, entity);
		if (level.isClientSide() || countAbove(level, pos) > 0) return;

		collapse(level, pos, entity);
	}

	/** A tower stacked past what it can hold up comes down as it is built. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide() || countBelow(level, pos) + 1 <= MAX_HEIGHT) return;

		collapse(level, pos, placer);
	}

	/** Starts the whole tower failing, if it has been built past what it can hold. */
	private static void collapse(Level level, BlockPos anywhere, @Nullable Entity cause) {
		if (!(level instanceof ServerLevel serverLevel)) return;

		int below = countBelow(level, anywhere);
		int height = below + 1 + countAbove(level, anywhere);
		if (height <= SAFE_HEIGHT) return;

		TowerCollapse.begin(serverLevel, anywhere.below(below), height);

		if (cause instanceof Player player) {
			player.displayClientMessage(Component.translatable("message.electricity.tower.collapsed").withStyle(ChatFormatting.RED), true);
		}
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

	/** The turbine this tower carries, if it has one yet. */
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
