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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ElectricCabinBlock extends Block implements EntityBlock, MachineShell {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	/** The facing cab.obj was modelled at, which is not north. */
	public static final Direction AUTHORED = Direction.EAST;

	/**
	 * The whole cabin as collision, cell by cell, cut from cab.obj rather than guessed.
	  *
	 * In the model's own frame, facing {@link #AUTHORED}, and turned with the geometry.
	 */
	private static final List<Cell> CELLS = List.of(
			new Cell(0, 0, 0, Shapes.block()),
			new Cell(0, 0, -1, Block.box(0.00, 0.00, 6.34, 16.00, 16.00, 16.00)),
			new Cell(0, 0, 1, Block.box(0.00, 0.00, 0.00, 16.00, 16.00, 9.66)),
			new Cell(0, 1, 0, Shapes.block()),
			new Cell(0, 1, -1, Block.box(0.00, 0.00, 6.34, 16.00, 16.00, 16.00)),
			new Cell(0, 1, 1, Block.box(0.00, 0.00, 0.00, 16.00, 16.00, 9.66)),
			new Cell(0, 2, 0, Block.box(0.00, 0.00, 0.00, 16.00, 10.03, 16.00)),
			new Cell(0, 2, -1, Shapes.or(Block.box(0.00, 0.00, 6.34, 16.00, 5.73, 16.00),
					Block.box(0.00, 5.77, 5.48, 16.00, 10.03, 16.00),
					Block.box(5.88, 8.79, 11.54, 9.31, 12.38, 14.97))),
			new Cell(0, 2, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 16.00, 5.73, 9.66),
					Block.box(0.00, 5.77, 0.00, 16.00, 10.03, 10.52),
					Block.box(3.80, 8.79, 3.21, 7.23, 12.38, 6.64))));

	public ElectricCabinBlock(Properties properties) {
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
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return 0;
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Nullable @Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ElectricCabinBlockEntity(pos, state);
	}

	@Nullable @Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
		return (lvl, pos, blockState, blockEntity) -> {
			if (blockEntity instanceof ElectricCabinBlockEntity electricCabin) {
				electricCabin.tick();
				// and the cells, because a cabin placed before they existed has none: the collision was
				// one cube under three blocks of steel
				MachineShell.heal(lvl, pos, blockState);
			}
		};
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
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		MachineShell.place(level, pos, state);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			MachineShell.clear(level, pos, state);
			if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
				BlockEntity blockEntity = level.getBlockEntity(pos);
				if (blockEntity instanceof ElectricCabinBlockEntity cabin) {
					Electricity.wireManager.removeConnectionsForInsulators(serverLevel, cabin.getInsulatorIds());
				}
			}
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
