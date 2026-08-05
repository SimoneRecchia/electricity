package com.dooji.electricity.block;

import com.dooji.electricity.api.power.CombinerSpec;
import com.dooji.electricity.api.power.DcCableSpec;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A direct-current combiner box on its post. */
public class PvCombinerBlock extends HorizontalDirectionalBlock implements EntityBlock, DcTerminal, MachineShell {
	/**
	 * The facing pv_combiner.obj was modelled at: one of the mod's own models, so it faces north like the rest of them.
	  *
	 * See {@link ModelFacing}.
	 */
	public static final Direction AUTHORED = Direction.NORTH;

	/** The output load-break switch, open. */
	public static final BooleanProperty ISOLATED = BooleanProperty.create("isolated");

	/** The box as collision, cut from pv_combiner.obj and turned with the block. */
	private static final List<Cell> CELLS = List.of(
			new Cell(0, 0, 0, Shapes.or(Block.box(4.24, 4.86, 6.00, 12.14, 12.80, 9.60),
					Block.box(5.72, 0.00, 5.72, 10.28, 11.36, 10.28))));


	private final CombinerSpec spec;

	public PvCombinerBlock(Properties properties, CombinerSpec spec) {
		super(properties.sound(SoundType.METAL));
		this.spec = spec;
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ISOLATED, false));
	}

	public CombinerSpec spec() {
		return spec;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ISOLATED);
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(ISOLATED, false);
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

	/** Both gauges land here, and that is the whole point of the object. */
	@Override
	public boolean acceptsCable(BlockState state, DcCableSpec cable, Direction side) {
		return true;
	}

	/** An empty hand throws the switch. */
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;

		boolean isolated = !state.getValue(ISOLATED);
		if (!level.isClientSide) {
			level.setBlock(pos, state.setValue(ISOLATED, isolated), Block.UPDATE_ALL);
			// a load-break switch is a heavy thing with a spring in it, and it is audible across a field
			level.playSound(null, pos, isolated ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN,
					SoundSource.BLOCKS, 0.7f, isolated ? 0.7f : 1.2f);
		}

		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PvCombinerBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide ? null : (lvl, pos, blockState, blockEntity) -> {
			if (blockEntity instanceof PvCombinerBlockEntity combiner) {
				combiner.serverTick();
			}
		};
	}

	/** Lets the arrays go when the box is actually broken. */
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && !level.isClientSide
				&& level.getBlockEntity(pos) instanceof PvCombinerBlockEntity combiner) {
			combiner.releaseArrays();
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
