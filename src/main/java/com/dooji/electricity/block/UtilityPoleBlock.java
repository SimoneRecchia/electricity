package com.dooji.electricity.block;

import com.dooji.electricity.client.hooks.UtilityPoleClientHooks;
import com.dooji.electricity.main.Electricity;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public class UtilityPoleBlock extends Block implements EntityBlock, MachineShell {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	/** The facing utility_pole.obj is modelled at. */
	public static final Direction AUTHORED = Direction.NORTH;

	/**
	 * The whole pole as collision, cell by cell, cut from utility_pole.obj.
	  *
	 * Written by {@code tools/check_hitboxes.py --java}, in the model's own frame, facing {@link #AUTHORED}.
	 */
	private static final List<Cell> CELLS = List.of(
			new Cell(0, 0, 0, Block.box(2.88, 0.00, 3.73, 13.26, 16.00, 12.27)),
			new Cell(0, 1, 0, Block.box(2.88, 0.00, 3.73, 13.26, 16.00, 12.27)),
			new Cell(0, 2, 0, Block.box(2.88, 0.00, 3.73, 13.26, 16.00, 12.27)),
			new Cell(0, 3, 0, Block.box(2.88, 0.00, 3.73, 13.26, 16.00, 12.27)),
			new Cell(0, 4, 0, Shapes.or(Block.box(0.00, 4.25, 6.25, 16.00, 14.99, 9.75),
					Block.box(2.88, 0.00, 3.73, 13.26, 16.00, 12.27))),
			new Cell(-1, 4, 0, Shapes.or(Block.box(0.00, 14.61, 6.67, 0.37, 16.00, 9.33),
					Block.box(0.00, 4.25, 6.25, 16.00, 14.99, 9.75),
					Block.box(10.03, 14.61, 6.67, 12.69, 16.00, 9.33))),
			new Cell(1, 4, 0, Shapes.or(Block.box(0.00, 4.25, 6.25, 16.00, 14.99, 9.75),
					Block.box(3.31, 14.61, 6.67, 5.97, 16.00, 9.33),
					Block.box(15.63, 14.61, 6.67, 16.00, 16.00, 9.33))),
			new Cell(-2, 4, 0, Shapes.or(Block.box(6.62, 4.25, 6.25, 16.00, 14.99, 9.75),
					Block.box(13.71, 14.61, 6.67, 16.00, 16.00, 9.33))),
			new Cell(2, 4, 0, Shapes.or(Block.box(0.00, 14.61, 6.67, 2.29, 16.00, 9.33),
					Block.box(0.00, 4.25, 6.25, 9.38, 14.99, 9.75))),
			new Cell(0, 5, 0, Shapes.or(Block.box(0.00, 3.46, 6.25, 16.00, 14.19, 9.75),
					Block.box(2.88, 0.00, 3.73, 13.26, 16.00, 12.27))),
			new Cell(-1, 5, 0, Shapes.or(Block.box(0.00, 0.00, 6.67, 0.37, 1.38, 9.33),
					Block.box(0.00, 3.46, 6.25, 16.00, 14.19, 9.75),
					Block.box(3.63, 13.81, 6.67, 6.29, 16.00, 9.33),
					Block.box(10.03, 0.00, 6.67, 12.69, 1.38, 9.33),
					Block.box(12.83, 13.81, 6.67, 15.49, 16.00, 9.33))),
			new Cell(1, 5, 0, Shapes.or(Block.box(0.00, 3.46, 6.25, 16.00, 14.19, 9.75),
					Block.box(0.51, 13.81, 6.67, 3.17, 16.00, 9.33),
					Block.box(3.31, 0.00, 6.67, 5.97, 1.38, 9.33),
					Block.box(9.71, 13.81, 6.67, 12.37, 16.00, 9.33),
					Block.box(15.63, 0.00, 6.67, 16.00, 1.38, 9.33))),
			new Cell(-2, 5, 0, Shapes.or(Block.box(13.17, 3.46, 6.25, 16.00, 14.19, 9.75),
					Block.box(13.71, 0.00, 6.67, 16.00, 1.38, 9.33))),
			new Cell(2, 5, 0, Shapes.or(Block.box(0.00, 0.00, 6.67, 2.29, 1.38, 9.33),
					Block.box(0.00, 3.46, 6.25, 2.83, 14.19, 9.75))),
			new Cell(0, 6, 0, Block.box(2.88, 0.00, 3.73, 13.26, 4.24, 12.27)));

	public UtilityPoleBlock(Properties properties) {
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

	@Nullable @Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new UtilityPoleBlockEntity(pos, state);
	}

	/** A pole has nothing of its own to tick */
	@Nullable @Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
		return (lvl, pos, blockState, blockEntity) -> MachineShell.heal(lvl, pos, blockState);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> UtilityPoleClientHooks.openConfigScreen(pos));
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
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
				if (blockEntity instanceof UtilityPoleBlockEntity pole) {
					Electricity.wireManager.removeConnectionsForInsulators(serverLevel, pole.getInsulatorIds());
				}
			}
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
