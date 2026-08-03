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
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public class UtilityPoleBlock extends Block implements EntityBlock, MachineShell {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	/**
	 * The mast, rather than the block round it.
	 *
	 * A cube was both too big and in mostly the wrong place: the pole is 0.494 of a block across and six
	 * blocks tall, so the block a player could not walk through was the one place the pole barely fills,
	 * and the five above it - the whole of the pole - were air.
	 */
	private static final VoxelShape SHAPE = MachineShellBlock.Fill.POST.shape();

	/**
	 * The mast above the block it was planted in, one cell per block, measured off utility_pole.obj.
	 *
	 * The crossarms are deliberately not here. They are two bars a fifth of a block thick reaching four
	 * blocks across at the top, and a cell of collision each would be an invisible floor in the sky four
	 * blocks wide - so a player can walk under the arms, which is what a player does under a real one.
	 */
	private static final List<Cell> CELLS = List.of(
			new Cell(0, 1, 0, MachineShellBlock.Fill.POST),
			new Cell(0, 2, 0, MachineShellBlock.Fill.POST),
			new Cell(0, 3, 0, MachineShellBlock.Fill.POST),
			new Cell(0, 4, 0, MachineShellBlock.Fill.POST),
			new Cell(0, 5, 0, MachineShellBlock.Fill.POST));

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
		return SHAPE;
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
