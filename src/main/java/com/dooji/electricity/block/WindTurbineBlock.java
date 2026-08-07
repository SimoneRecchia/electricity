package com.dooji.electricity.block;

import com.dooji.electricity.api.power.TurbineSpec;
import com.dooji.electricity.main.Electricity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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

public class WindTurbineBlock extends Block implements EntityBlock {
	/**
	 * The facing wind_turbine.obj was modelled at: an inherited model, and it faces south.
	  *
	 * See {@link ModelFacing}.
	 */
	public static final Direction AUTHORED = Direction.SOUTH;

	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	/** Nacelle height in the authored model, in blocks */
	private static final double NACELLE_HEIGHT = 0.95;
	/** Distance from the tower axis to the nacelle's farthest corner, in blocks at C130 scale. */
	private static final double NACELLE_REACH = 1.575;

	/** Which machine this block is. */
	private final TurbineSpec spec;
	private final VoxelShape shape;

	public WindTurbineBlock(Properties properties, TurbineSpec spec) {
		super(properties);
		this.spec = spec;
		this.shape = nacelleShape(spec);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	/** The nacelle, rather than the whole block it lives in. */
	private static VoxelShape nacelleShape(TurbineSpec spec) {
		double scale = spec.nacelleRenderScale();
		double half = Math.min(0.5, NACELLE_REACH * scale);
		double height = Math.min(1.0, NACELLE_HEIGHT * scale);

		return Shapes.box(0.5 - half, 0.0, 0.5 - half, 0.5 + half, height, 0.5 + half);
	}

	public TurbineSpec spec() {
		return spec;
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
		return shape;
	}

	/** A machine sits on a tower */
	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return spec.acceptsTowerHeight(TurbineTowerBlock.countBelow(level, pos));
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		// the tower under it went away or grew past what this machine mounts on
		if (direction == Direction.DOWN && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();

		return state;
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
		return new WindTurbineBlockEntity(pos, state);
	}

	@Nullable @Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
		return (level1, pos, state1, blockEntity) -> {
			if (blockEntity instanceof WindTurbineBlockEntity windTurbine) {
				windTurbine.tick();
			}
		};
	}

	/** Tells the tower's foot that its machine arrived or left. */
	private static void refreshTowerFoot(Level level, BlockPos pos) {
		if (level.isClientSide()) return;

		int height = TurbineTowerBlock.countBelow(level, pos);
		if (height <= 0) return;

		BlockPos foot = pos.below(height);
		if (level.getBlockEntity(foot) instanceof TurbineTowerBlockEntity tower) {
			tower.invalidateCaps();
			level.updateNeighbourForOutputSignal(foot, level.getBlockState(foot).getBlock());
		}
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		refreshTowerFoot(level, pos);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			refreshTowerFoot(level, pos);

			if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
				BlockEntity blockEntity = level.getBlockEntity(pos);
				if (blockEntity instanceof WindTurbineBlockEntity windTurbine) {
					Electricity.wireManager.removeConnectionsForInsulators(serverLevel, windTurbine.getInsulatorIds());
				}
			}
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
