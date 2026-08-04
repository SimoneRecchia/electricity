package com.dooji.electricity.block;

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
import java.util.List;

/**
 * A meteorological mast: seven instruments on one bus.
 *
 * Everything a photovoltaic plant measures about the sky rather than about itself. IEC 61724 asks for
 * one or two of these for a whole plant rather than one per array, because the sky is the same across a
 * site - so this is a single block that a plant has one of, not a component every array carries.
 *
 * The two instruments that belong to the array rather than to the sky are still read through here,
 * because that is where the readings are wanted: the mast finds the nearest array and takes its
 * plane-of-array irradiance and its back-of-module temperature from it. That is exactly how a real
 * plant is wired - a tilted pyranometer and a resistance thermometer out on the racking, cabled back
 * to the same data logger the mast instruments are on.
 */
public class MetStationBlock extends HorizontalDirectionalBlock implements EntityBlock, MachineShell {
	/**
	 * The facing met_mast.obj was modelled at: one of the mod's own models, so it faces north like the rest of them.
	 *
	 * Declared here because more than one thing has to agree about it - the renderer turns the model
	 * by it, and whatever else reads the geometry turns with it. See {@link ModelFacing}.
	 */
	public static final Direction AUTHORED = Direction.NORTH;

	/**
	 * The mast as collision, cut from met_mast.obj and turned with the block.
	 *
	 * It used to be one box four pixels across for the whole machine, which had two faults at once: a
	 * player walked through both instrument booms as though they were not there, and the shape did not
	 * turn, so on two of the four facings it stood across the mast rather than on it. Declared as a cell
	 * table for the same reason the cabin's is - {@link MachineShell} turns both the offset and the shape,
	 * and {@code tools/check_hitboxes.py} reads the table back and fails if it has drifted from the model.
	 *
	 * One cell: everything the mast has is inside its own block.
	 */
	private static final List<Cell> CELLS = List.of(
			new Cell(0, 0, 0, Shapes.or(Block.box(6.16, 0.00, 6.16, 9.84, 16.00, 9.84),
					Block.box(7.07, 2.75, 11.87, 8.93, 4.74, 13.73),
					Block.box(7.08, 5.41, 11.60, 8.92, 7.65, 13.84),
					Block.box(7.08, 5.76, 9.56, 8.92, 6.81, 11.40),
					Block.box(7.19, 5.43, 13.99, 8.81, 6.73, 15.61),
					Block.box(7.23, 2.78, 7.23, 8.77, 6.08, 14.72),
					Block.box(7.36, 3.20, 2.56, 8.64, 4.08, 8.16))));


	public MetStationBlock(Properties properties) {
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

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MetStationBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide ? null : (lvl, pos, blockState, blockEntity) -> {
			if (blockEntity instanceof MetStationBlockEntity station) {
				station.serverTick();
			}
		};
	}
}
