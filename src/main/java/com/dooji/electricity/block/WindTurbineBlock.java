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
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape SHAPE = Shapes.block();

	/**
	 * Which machine this block is.
	 *
	 * One block per model rather than one block storing a model id, because the models
	 * differ by more than numbers: each needs its own recipe, its own item and its own
	 * entry in a recipe viewer, and a player shopping for a turbine is choosing between
	 * products rather than configuring one. The block entity reads the spec back off the
	 * block, so nothing about a placed turbine has to be persisted to know what it is.
	 */
	private final TurbineSpec spec;

	public WindTurbineBlock(Properties properties, TurbineSpec spec) {
		super(properties);
		this.spec = spec;
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
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
		return SHAPE;
	}

	/**
	 * A machine sits on a tower, at a height that tower is certified for.
	 *
	 * Both ends of the range are enforced, and the range is the manufacturer's rather than
	 * something invented: real turbines are sold on specific tower heights, a V90 on 80,
	 * 95 or 105 metres and not on whatever is to hand. The low end is also physics - the
	 * blades would be in the ground - and the published minimum is at or above the tip
	 * clearance for every machine in the catalogue, so one check covers both.
	 *
	 * A small rotor on a tall tower would be merely uneconomic rather than impossible, but
	 * it is refused too: a 10 kW nacelle a hundred metres up looks wrong, and the certified
	 * range is the honest reason to say no.
	 */
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

	/**
	 * Tells the tower's foot that its machine arrived or left.
	 *
	 * The foot answers capability queries on the machine's behalf, and it can be thirteen
	 * blocks away - far outside the neighbour updates that placing or breaking this block
	 * sends. Without this a cable already sitting at the tower base would keep the empty
	 * answer it got before the machine existed.
	 */
	private static void refreshTowerFoot(Level level, BlockPos pos) {
		if (level.isClientSide()) return;

		int height = TurbineTowerBlock.countBelow(level, pos);
		if (height <= 0) return;

		BlockPos foot = pos.below(height);
		if (level.getBlockEntity(foot) instanceof TurbineTowerBlockEntity tower) {
			tower.invalidateCaps();
			level.updateNeighbourForOutputSignal(foot, level.getBlockState(foot).getBlock());
		}

		// the tower narrows to whichever machine stands on it, so gaining or losing one changes it
		TurbineTowerBlock.refreshThickness(level, foot);
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
