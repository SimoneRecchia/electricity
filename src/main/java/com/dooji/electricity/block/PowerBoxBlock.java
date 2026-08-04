package com.dooji.electricity.block;

import com.dooji.electricity.main.Electricity;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PowerBoxBlock extends Block implements EntityBlock, MachineShell {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	/**
	 * The facing power_box.obj was modelled at, which is not north.
	 *
	 * Another inherited model facing east, and the reason this block's shape is a table of one rather than
	 * four boxes written out. The four were each a quarter turn from where the cabinet is drawn, because
	 * they were worked out as though the model faced north: a player collided with the side of the block
	 * next to the box.
	 */
	public static final Direction AUTHORED = Direction.EAST;

	/**
	 * The cabinet, where it actually is.
	 *
	 * This was a whole cube, and the box is not: cut from power_box.obj it is 3.99 pixels across, 10.23
	 * tall and 8.16 deep, and it hangs against one side of its block rather than sitting in the middle. So
	 * three quarters of the cube a player could not walk through was empty air.
	 *
	 * One box, in the model's own frame, turned onto the facing by the same arithmetic the renderer uses.
	 * The door leaf and the bushing under the box are left out: a quarter of a pixel and a pixel and a
	 * half, neither of which a player can touch.
	 */
	private static final List<Cell> CELLS = List.of(
			new Cell(0, 0, 0, Block.box(0.00, 0.00, 3.92, 3.99, 10.23, 12.08)));

	public PowerBoxBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shellShape(state);
	}

	@Override
	public List<Cell> shellCells() {
		return CELLS;
	}

	@Override
	public Direction shellFacing(BlockState state) {
		return state.getValue(FACING);
	}

	@Override
	public Direction shellAuthored() {
		return AUTHORED;
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return 0;
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Nullable @Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PowerBoxBlockEntity(pos, state);
	}

	@Nullable @Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
		return (lvl, pos, blockState, blockEntity) -> {
			if (blockEntity instanceof PowerBoxBlockEntity powerBox) {
				powerBox.tick();
			}
		};
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
				BlockEntity blockEntity = level.getBlockEntity(pos);
				if (blockEntity instanceof PowerBoxBlockEntity powerBox) {
					Electricity.wireManager.removeConnectionsForInsulators(serverLevel, powerBox.getInsulatorIds());
				}
			}
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
