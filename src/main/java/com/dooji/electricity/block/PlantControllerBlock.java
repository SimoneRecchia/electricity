package com.dooji.electricity.block;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The plant control cabinet: one setpoint for every dispatchable machine within reach of its radio. */
public class PlantControllerBlock extends HorizontalDirectionalBlock implements EntityBlock, MachineShell {
	/** The facing plant_controller.obj was modelled at - see {@link ModelFacing}. */
	public static final Direction AUTHORED = Direction.NORTH;

	// ---- printed by tools/check_hitboxes.py --java ----

	private static final List<Cell> CELLS = List.of(
			new Cell(0, 0, 0, Block.box(1.28, 0.00, 3.97, 14.72, 16.00, 11.52)),
			new Cell(0, 1, 0, Shapes.or(Block.box(1.28, 0.00, 3.97, 14.72, 5.12, 11.52),
					Block.box(12.32, 5.12, 7.52, 13.60, 15.04, 8.48))));

	public PlantControllerBlock(Properties properties) {
		super(properties.sound(SoundType.METAL));
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shellShape(state);
	}

	@Override
	public List<Cell> shellCells(BlockState state) {
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

	/** A comparator on the cabinet reads how loaded the plant is, nought to fifteen. */
	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof PlantControllerBlockEntity controller
				? controller.getComparatorOutput() : 0;
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		MachineShell.place(level, pos, state);
	}

	/**
	 * A cabinet that is taken away hands its units back what they had, and gives up its second cell.
	 *
	 * Without the first, a plant curtailed to a tenth stays there for ever with nothing standing in the
	 * world to say why - and the machine that would have explained it is the one the player just broke.
	 */
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			if (level.getBlockEntity(pos) instanceof PlantControllerBlockEntity controller) controller.release();
			MachineShell.clear(level, pos, state);
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PlantControllerBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return (lvl, pos, blockState, blockEntity) -> {
			MachineShell.heal(lvl, pos, blockState);
			if (!lvl.isClientSide && blockEntity instanceof PlantControllerBlockEntity controller) {
				controller.serverTick();
			}
		};
	}
}
