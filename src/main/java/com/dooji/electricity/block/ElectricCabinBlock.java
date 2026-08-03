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

	/**
	 * The whole cabin as collision, cell by cell, cut from cab.obj rather than guessed.
	 *
	 * The machine stands in nine cells and exactly one of them used to be solid. The numbers, in the
	 * block's own coordinates: the body runs x -0.009 to 1.009 and z -0.604 to 1.604 and is 2.358 tall;
	 * the roof over it is wider, x -0.054 to 1.054 and z -0.657 to 1.657, and stops at 2.627; the two
	 * insulators stand on the roof and reach 3.001.
	 *
	 * So most of these cells hold a piece of a box rather than a box. The four beside the body are filled
	 * to 6.34 pixels because that is exactly how far the body reaches into them, the roof stops at 10.03
	 * because that is where the steel stops, and above the roof there is nothing but the two insulators,
	 * which are 3.4 pixels across and get 3.4 pixels of collision. A whole cell anywhere here, or a slab
	 * rounded to the nearest eight pixels, is a third of a block of air the player cannot walk through.
	 *
	 * Written by {@code tools/check_hitboxes.py --java}, which also fails the build's check if the model
	 * and this table ever drift apart. Authored facing north like the model, and turned with it.
	 *
	 * What is left out is what is thinner than a pixel: the roof's 0.054 overhang past the block beside
	 * it, and the door leaf standing 0.05 past the east wall. Claiming a cell for either would cost the
	 * player a cubic metre of the world for nine tenths of a pixel of ledge.
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
					Block.box(5.88, 9.70, 11.54, 9.31, 16.00, 14.97))),
			new Cell(0, 2, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 16.00, 5.73, 9.66),
					Block.box(0.00, 5.77, 0.00, 16.00, 10.03, 10.52),
					Block.box(3.80, 9.70, 3.21, 7.23, 16.00, 6.64))));

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
