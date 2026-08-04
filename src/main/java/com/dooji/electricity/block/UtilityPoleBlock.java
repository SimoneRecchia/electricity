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
	 * The facing utility_pole.obj was modelled at, as far as the cells need to care.
	 *
	 * East, the same as the other inherited models. The renderer's own mapping is not quite a quarter turn
	 * from east - it comes out half a turn the other way for east and west, because the pole's model is
	 * mirrored rather than merely turned - and that difference cannot show here: the pole's geometry and
	 * the table below are both unchanged by a half turn, mast, arms and every insulator on them.
	 */
	public static final Direction AUTHORED = Direction.EAST;

	/**
	 * The whole pole as collision, cell by cell, cut from utility_pole.obj.
	 *
	 * A cube was both too big and in mostly the wrong place: the mast is 0.494 of a block across and six
	 * blocks tall, so the one block a player could not walk through was the place the pole barely fills,
	 * and the five above it - the whole of the pole - were air.
	 *
	 * The mast is here as the 7.92 pixels it actually is, up to the 2.92 pixels of it that reach the
	 * seventh block. So are the two crossarms, which are three pixels thick and reach four blocks across,
	 * and the eight insulators standing on them, which are 2.2 across. That is a change of mind: the arms
	 * were left out when a cell of collision meant a whole cell, because an invisible floor in the sky
	 * four blocks wide is worse than no floor at all. A three-pixel plate where the plate is drawn is not
	 * that - it is the arm, and a player can still walk under it.
	 *
	 * Written by {@code tools/check_hitboxes.py --java}, in the model's own frame, facing
	 * {@link #AUTHORED}.
	 */
	private static final List<Cell> CELLS = List.of(
			new Cell(0, 0, 0, Block.box(4.04, 0.00, 4.04, 11.96, 16.00, 11.96)),
			new Cell(0, 1, 0, Block.box(4.04, 0.00, 4.04, 11.96, 16.00, 11.96)),
			new Cell(0, 2, 0, Block.box(4.04, 0.00, 4.04, 11.96, 16.00, 11.96)),
			new Cell(0, 3, 0, Block.box(4.04, 0.00, 4.04, 11.96, 16.00, 11.96)),
			new Cell(0, 4, 0, Shapes.or(Block.box(3.10, 11.51, 0.00, 12.90, 14.51, 16.00),
					Block.box(4.04, 0.00, 4.04, 11.96, 16.00, 11.96))),
			new Cell(0, 4, -1, Shapes.or(Block.box(3.10, 11.51, 0.00, 12.90, 14.51, 16.00),
					Block.box(6.91, 14.48, 10.27, 9.09, 16.00, 12.45))),
			new Cell(0, 4, 1, Shapes.or(Block.box(3.10, 11.51, 0.00, 12.90, 14.51, 16.00),
					Block.box(6.91, 14.48, 3.55, 9.09, 16.00, 5.73))),
			new Cell(0, 4, -2, Shapes.or(Block.box(3.10, 11.51, 6.62, 12.90, 14.51, 16.00),
					Block.box(6.91, 14.48, 13.93, 9.09, 16.00, 16.00))),
			new Cell(0, 4, 2, Shapes.or(Block.box(3.10, 11.51, 0.00, 12.90, 14.51, 9.38),
					Block.box(6.91, 14.48, 0.00, 9.09, 16.00, 2.07))),
			new Cell(0, 5, 0, Shapes.or(Block.box(3.10, 10.70, 0.00, 12.90, 13.70, 16.00),
					Block.box(4.04, 0.00, 4.04, 11.96, 16.00, 11.96))),
			new Cell(0, 5, -1, Shapes.or(Block.box(3.10, 10.70, 0.00, 12.90, 13.70, 16.00),
					Block.box(6.91, 0.00, 10.27, 9.09, 2.49, 12.45),
					Block.box(6.91, 13.65, 3.83, 9.09, 16.00, 6.01),
					Block.box(6.91, 13.65, 13.07, 9.09, 16.00, 15.25))),
			new Cell(0, 5, 1, Shapes.or(Block.box(3.10, 10.70, 0.00, 12.90, 13.70, 16.00),
					Block.box(6.91, 0.00, 3.55, 9.09, 2.49, 5.73),
					Block.box(6.91, 13.65, 0.75, 9.09, 16.00, 2.93),
					Block.box(6.91, 13.65, 9.99, 9.09, 16.00, 12.17))),
			new Cell(0, 5, -2, Shapes.or(Block.box(3.10, 10.70, 13.16, 12.90, 13.70, 16.00),
					Block.box(6.91, 0.00, 13.93, 9.09, 2.49, 16.00))),
			new Cell(0, 5, 2, Shapes.or(Block.box(3.10, 10.70, 0.00, 12.90, 13.70, 2.84),
					Block.box(6.91, 0.00, 0.00, 9.09, 2.49, 2.07))),
			new Cell(0, 6, 0, Block.box(4.04, 0.00, 4.04, 11.96, 2.92, 11.96)));

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
