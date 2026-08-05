package com.dooji.electricity.block;

import com.dooji.electricity.item.ConductorItem;
import com.dooji.electricity.main.Electricity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A cell of a machine that is bigger than the block it was placed in.
  *
 * An oversized {@link VoxelShape} is the obvious fix and it does not work past one block.
 */
public class MachineShellBlock extends Block {
	/** The way back to the machine, as the offset from it to this cell. */
	public static final IntegerProperty HOST_X = IntegerProperty.create("host_x", 0, 2 * MachineShell.REACH_SIDE);
	public static final IntegerProperty HOST_Y = IntegerProperty.create("host_y", 0, MachineShell.REACH_UP);
	public static final IntegerProperty HOST_Z = IntegerProperty.create("host_z", 0, 2 * MachineShell.REACH_SIDE);

	public MachineShellBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(HOST_X, MachineShell.REACH_SIDE).setValue(HOST_Y, 0).setValue(HOST_Z, MachineShell.REACH_SIDE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HOST_X, HOST_Y, HOST_Z);
	}

	/** The state a cell at {@code cell} takes for the machine at {@code host}. */
	public static BlockState pointingAt(BlockPos host, BlockPos cell) {
		BlockPos offset = cell.subtract(host);
		return Electricity.MACHINE_SHELL_BLOCK.get().defaultBlockState()
				.setValue(HOST_X, offset.getX() + MachineShell.REACH_SIDE)
				.setValue(HOST_Y, offset.getY())
				.setValue(HOST_Z, offset.getZ() + MachineShell.REACH_SIDE);
	}

	/** Where the machine is, according to the cell itself. */
	public static BlockPos pointsAt(BlockPos pos, BlockState state) {
		return pos.offset(MachineShell.REACH_SIDE - state.getValue(HOST_X),
				-state.getValue(HOST_Y),
				MachineShell.REACH_SIDE - state.getValue(HOST_Z));
	}

	/** The part of the machine that reaches into this cell. */
	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		BlockPos host = pointsAt(pos, state);
		BlockState hostState = level.getBlockState(host);
		if (!(hostState.getBlock() instanceof MachineShell machine)) return Shapes.block();

		int quarters = machine.shellTurns(hostState);
		BlockPos offset = pos.subtract(host);
		for (MachineShell.Cell cell : machine.shellCells()) {
			if (cell.at(quarters).equals(offset)) return cell.shape(quarters);
		}

		return Shapes.block();
	}

	/** Because the shape is the machine's, and the machine is at another position. */
	@Override
	public boolean hasDynamicShape() {
		return true;
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return 0;
	}

	@Override
	public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
		return false;
	}

	/** Whatever the machine is, so middle-clicking any part of it picks the machine. */
	@Override
	public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
		BlockPos host = MachineShell.hostOf(level, pos);
		return host == null ? ItemStack.EMPTY : level.getBlockState(host).getBlock().asItem().getDefaultInstance();
	}

	/** Using any part of a machine uses the machine. */
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		BlockPos host = MachineShell.hostOf(level, pos);
		if (host == null || player.getItemInHand(hand).getItem() instanceof ConductorItem) return InteractionResult.PASS;

		return level.getBlockState(host).use(level, player, hand,
				new BlockHitResult(hit.getLocation(), hit.getDirection(), host, hit.isInside()));
	}

	/** And mining any part of a machine mines the machine, for the same reason. */
	@Override
	public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
		BlockPos host = MachineShell.hostOf(level, pos);
		if (host != null) {
			level.destroyBlock(host, !player.isCreative(), player);
			return true;
		}

		return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return MachineShell.hostOf(level, pos) != null;
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
	}
}
