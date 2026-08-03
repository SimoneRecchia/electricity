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
	 * The rest of the cabin, cell by cell, measured off cab.obj rather than guessed.
	 *
	 * The numbers, in the block's own coordinates with its corner at the origin: the body runs x -0.009 to
	 * 1.009 and z -0.604 to 1.604 and is 2.358 tall; the roof over it is wider, x -0.054 to 1.054 and
	 * z -0.657 to 1.657, and stops at 2.627. So the machine stands in nine cells and exactly one of them
	 * used to be solid.
	 *
	 * Which is why the cells are not all whole. Each one carries the part of the cell its own body
	 * actually fills, to the nearest pixel:
	 *
	 * <ul>
	 * <li>the column it stands in, twice over, is full</li>
	 * <li>the four beside it get ten pixels of slab leaning towards the machine, because the body reaches
	 *     0.604 into them - a whole cell there would be a third of a block of air a player cannot walk
	 *     through</li>
	 * <li>the roof is ten pixels tall, and at its two corners it is ten pixels tall *and* ten deep, which
	 *     is what an eave is and what no single slab can be</li>
	 * </ul>
	 *
	 * Authored facing north like the model, and turned with it.
	 *
	 * Deliberately left out: the two insulators on the roof and the door handle. The insulators are 0.2 of
	 * a block across and hold the wires; the handle stands 0.05 past the east wall. Neither is something to
	 * stand on, and a cell for either would be a step in mid-air.
	 */
	private static final List<Cell> CELLS = List.of(
			new Cell(0, 0, -1, MachineShellBlock.Fill.SLAB_SOUTH),
			new Cell(0, 0, 1, MachineShellBlock.Fill.SLAB_NORTH),
			new Cell(0, 1, 0, MachineShellBlock.Fill.FULL),
			new Cell(0, 1, -1, MachineShellBlock.Fill.SLAB_SOUTH),
			new Cell(0, 1, 1, MachineShellBlock.Fill.SLAB_NORTH),
			new Cell(0, 2, 0, MachineShellBlock.Fill.SLAB_DOWN),
			new Cell(0, 2, -1, MachineShellBlock.Fill.EAVE_SOUTH),
			new Cell(0, 2, 1, MachineShellBlock.Fill.EAVE_NORTH));

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
				// and the cells, because a cabin placed before they existed has none: the collision was
				// one cube under three blocks of steel, and nothing else would ever go back and fix it
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
