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
import net.minecraft.world.phys.shapes.VoxelShape;

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
public class MetStationBlock extends HorizontalDirectionalBlock implements EntityBlock {
	/** A mast with a boom: thin, and tall enough that the anemometer is clear of the array. */
	private static final VoxelShape SHAPE = Block.box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);

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
		return SHAPE;
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
