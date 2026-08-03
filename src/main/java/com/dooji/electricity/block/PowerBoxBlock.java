package com.dooji.electricity.block;

import com.dooji.electricity.main.Electricity;
import java.util.Map;
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

public class PowerBoxBlock extends Block implements EntityBlock {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	/**
	 * The cabinet, where it actually is.
	 *
	 * This was a whole cube, and the box is not: measured off power_box.obj it is 0.29 across, 0.75 tall
	 * and 0.51 deep, and it hangs against one side of the block rather than sitting in the middle of it.
	 * So three quarters of the cube a player could not walk through was empty air, and the quarter that
	 * is the box was in a different place depending on which way the thing was turned.
	 *
	 * One entry per facing rather than one shape rotated at runtime, because the four are known at
	 * compile time and the rotation is the same quarter turns the renderer applies to the model.
	 */
	private static final Map<Direction, VoxelShape> SHAPES = Map.of(
			Direction.NORTH, Block.box(0.0, 0.0, 3.9, 4.0, 10.3, 12.2),
			Direction.SOUTH, Block.box(12.0, 0.0, 3.8, 16.0, 10.3, 12.1),
			Direction.WEST, Block.box(3.9, 0.0, 12.0, 12.2, 10.3, 16.0),
			Direction.EAST, Block.box(3.8, 0.0, 0.0, 12.1, 10.3, 4.0));

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
		return SHAPES.get(state.getValue(FACING));
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
