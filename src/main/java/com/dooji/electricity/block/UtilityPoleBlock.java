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

	/**
	 * The facing utility_pole.obj is modelled at.
	 *
	 * North, like every other model the mod generates for itself, and the arms run east-west - so the
	 * conductors run the way the pole faces, which is along the line.
	 *
	 * This used to be east *and* the model was mirrored rather than turned, so the pole needed a rotation
	 * table of its own that nothing else in the mod used and that three separate pieces of code had to
	 * know about. The model is now authored the ordinary way, so {@link ModelFacing} answers for it like
	 * everything else and the table is gone.
	 */
	public static final Direction AUTHORED = Direction.NORTH;

	/**
	 * The whole pole as collision, cell by cell, cut from utility_pole.obj.
	 *
	 * A cube was both too big and in mostly the wrong place: the shaft is half a block across and six
	 * blocks tall, so the one block a player could not walk through was the place the pole barely fills,
	 * and the five above it - the whole of the pole - were air.
	 *
	 * Here instead are the shaft as the eight and a half pixels it tapers through, the two crossarms as
	 * the three-pixel channels they are, the eight insulators standing on them, the braces under them, the
	 * earth strap down the back and every climbing step. The arms were left out when a cell of collision
	 * meant a whole cell, because an invisible floor in the sky four blocks wide is worse than no floor at
	 * all; a three-pixel plate where the plate is drawn is not that - it is the arm, and a player can
	 * still walk under it.
	 *
	 * Written by {@code tools/check_hitboxes.py --java}, in the model's own frame, facing
	 * {@link #AUTHORED}.
	 */
	private static final List<Cell> CELLS = List.of(
			new Cell(0, 0, 0, Block.box(3.73, 0.00, 3.73, 12.27, 16.00, 12.27)),
			new Cell(0, 1, 0, Shapes.or(Block.box(2.88, 11.19, 7.62, 4.64, 11.85, 8.38),
					Block.box(3.73, 0.00, 3.73, 12.27, 16.00, 12.27),
					Block.box(11.50, 1.27, 7.62, 13.26, 1.93, 8.38))),
			new Cell(0, 2, 0, Shapes.or(Block.box(3.17, 15.03, 7.62, 4.93, 15.69, 8.38),
					Block.box(3.73, 0.00, 3.73, 12.27, 16.00, 12.27),
					Block.box(11.21, 5.11, 7.62, 12.97, 5.77, 8.38))),
			new Cell(0, 3, 0, Shapes.or(Block.box(3.73, 0.00, 3.73, 12.27, 16.00, 12.27),
					Block.box(10.93, 8.95, 7.62, 12.69, 9.61, 8.38))),
			new Cell(0, 4, 0, Shapes.or(Block.box(0.00, 4.25, 6.25, 16.00, 14.99, 9.75),
					Block.box(3.46, 2.87, 7.62, 5.22, 3.53, 8.38),
					Block.box(3.73, 0.00, 3.73, 12.27, 16.00, 12.27))),
			new Cell(-1, 4, 0, Shapes.or(Block.box(0.00, 15.39, 6.67, 0.37, 16.00, 9.33),
					Block.box(0.00, 4.25, 6.25, 16.00, 14.99, 9.75),
					Block.box(10.03, 15.39, 6.67, 12.69, 16.00, 9.33),
					Block.box(10.94, 14.51, 7.58, 11.78, 15.41, 8.42))),
			new Cell(1, 4, 0, Shapes.or(Block.box(0.00, 4.25, 6.25, 16.00, 14.99, 9.75),
					Block.box(3.31, 15.39, 6.67, 5.97, 16.00, 9.33),
					Block.box(4.22, 14.51, 7.58, 5.06, 15.41, 8.42),
					Block.box(15.63, 15.39, 6.67, 16.00, 16.00, 9.33))),
			new Cell(-2, 4, 0, Shapes.or(Block.box(6.62, 4.25, 6.25, 16.00, 14.99, 9.75),
					Block.box(13.71, 15.39, 6.67, 16.00, 16.00, 9.33),
					Block.box(14.62, 14.51, 7.58, 15.46, 15.41, 8.42))),
			new Cell(2, 4, 0, Shapes.or(Block.box(0.00, 15.39, 6.67, 2.29, 16.00, 9.33),
					Block.box(0.00, 4.25, 6.25, 9.38, 14.99, 9.75),
					Block.box(0.54, 14.51, 7.58, 1.38, 15.41, 8.42))),
			new Cell(0, 5, 0, Shapes.or(Block.box(0.00, 3.46, 6.25, 16.00, 14.19, 9.75),
					Block.box(3.73, 0.00, 3.73, 12.27, 16.00, 12.27))),
			new Cell(-1, 5, 0, Shapes.or(Block.box(0.00, 0.00, 6.67, 0.37, 2.74, 9.33),
					Block.box(0.00, 3.46, 6.25, 16.00, 14.19, 9.75),
					Block.box(3.63, 14.59, 6.67, 6.29, 16.00, 9.33),
					Block.box(4.54, 13.71, 7.58, 5.38, 14.61, 8.42),
					Block.box(10.03, 0.00, 6.67, 12.69, 2.74, 9.33),
					Block.box(12.83, 14.59, 6.67, 15.49, 16.00, 9.33),
					Block.box(13.74, 13.71, 7.58, 14.58, 14.61, 8.42))),
			new Cell(1, 5, 0, Shapes.or(Block.box(0.00, 3.46, 6.25, 16.00, 14.19, 9.75),
					Block.box(0.51, 14.59, 6.67, 3.17, 16.00, 9.33),
					Block.box(1.42, 13.71, 7.58, 2.26, 14.61, 8.42),
					Block.box(3.31, 0.00, 6.67, 5.97, 2.74, 9.33),
					Block.box(9.71, 14.59, 6.67, 12.37, 16.00, 9.33),
					Block.box(10.62, 13.71, 7.58, 11.46, 14.61, 8.42),
					Block.box(15.63, 0.00, 6.67, 16.00, 2.74, 9.33))),
			new Cell(-2, 5, 0, Shapes.or(Block.box(13.17, 3.46, 6.25, 16.00, 14.19, 9.75),
					Block.box(13.71, 0.00, 6.67, 16.00, 2.74, 9.33))),
			new Cell(2, 5, 0, Shapes.or(Block.box(0.00, 0.00, 6.67, 2.29, 2.74, 9.33),
					Block.box(0.00, 3.46, 6.25, 2.83, 14.19, 9.75))),
			new Cell(0, 6, 0, Block.box(3.73, 0.00, 3.73, 12.27, 4.24, 12.27)));

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

	/**
	 * A pole has nothing of its own to tick, and this is not for it.
	 *
	 * It is here so that a pole planted before the mast had any collision gets it: six blocks of steel
	 * with one block of collision at the bottom does not fix itself, because it was placed long ago and
	 * placement is the only other thing that fills the cells.
	 */
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
