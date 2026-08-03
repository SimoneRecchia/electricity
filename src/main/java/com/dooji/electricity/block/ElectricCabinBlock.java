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
	private static final VoxelShape SHAPE = Shapes.block();

	/**
	 * The rest of the cabin, measured off cab.obj.
	 *
	 * The body is 1.02 wide, 2.67 tall and 2.21 deep, so it stands in nine cells and only one of them
	 * was solid. Three of the eight are the roof, which reaches 0.627 into the cell above - a ten pixel
	 * slab, which is what {@link MachineShellBlock.Fill#SLAB_DOWN} is - and the four beside it reach
	 * 0.657 in, which is the same slab stood on its side.
	 *
	 * Authored facing north like the model, and turned with it.
	 */
	private static final List<Cell> CELLS = List.of(
			new Cell(0, 0, -1, MachineShellBlock.Fill.SLAB_SOUTH),
			new Cell(0, 0, 1, MachineShellBlock.Fill.SLAB_NORTH),
			new Cell(0, 1, 0, MachineShellBlock.Fill.FULL),
			new Cell(0, 1, -1, MachineShellBlock.Fill.SLAB_SOUTH),
			new Cell(0, 1, 1, MachineShellBlock.Fill.SLAB_NORTH),
			new Cell(0, 2, 0, MachineShellBlock.Fill.SLAB_DOWN),
			new Cell(0, 2, -1, MachineShellBlock.Fill.SLAB_DOWN),
			new Cell(0, 2, 1, MachineShellBlock.Fill.SLAB_DOWN));

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
		return SHAPE;
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
