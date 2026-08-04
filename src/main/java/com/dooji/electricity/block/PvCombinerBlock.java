package com.dooji.electricity.block;

import com.dooji.electricity.api.power.CombinerSpec;
import com.dooji.electricity.api.power.DcCableSpec;
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
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A direct-current combiner box on its post.
 *
 * The least glamorous object on a solar farm and one of the two that make a large one possible. Strings
 * arrive at it on string cable, each through its own pair of fuses; one heavy pair leaves it for the
 * cabinet. What it buys is the difference between sixteen long thin runs and one long thick one, which
 * is a real percent or two of the plant's output, and fuses for a central inverter that has none.
 *
 * <h2>The switch</h2>
 *
 * {@link #ISOLATED} is the output load-break switch, and an empty hand on the box throws it - which is
 * exactly what a hand does to one, and is why it needs no panel control. Open, the group is off the
 * inverter and safe to work on, and the arrays behind it go to standby with the operating point at zero.
 * That is what isolating a combiner does on a real plant, and it is the only maintenance action in this
 * mod that a player can perform with their hands.
 */
public class PvCombinerBlock extends HorizontalDirectionalBlock implements EntityBlock, DcTerminal {
	/**
	 * The facing pv_combiner.obj was modelled at: one of the mod's own models, so it faces north like the rest of them.
	 *
	 * Declared here because more than one thing has to agree about it - the renderer turns the model
	 * by it, and whatever else reads the geometry turns with it. See {@link ModelFacing}.
	 */
	public static final Direction AUTHORED = Direction.NORTH;

	/** The output load-break switch, open. */
	public static final BooleanProperty ISOLATED = BooleanProperty.create("isolated");

	/** An enclosure on a post: narrow, shallow, and standing about waist high on the mod's scale. */
	private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 6.0, 12.0, 13.0, 10.0);

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
		return SHAPE;
	}

	/**
	 * Both gauges land here, and that is the whole point of the object.
	 *
	 * Strings in on the thin cable, one pair out on the thick one. It is the only block in the mod that
	 * takes both, because it is the only one whose job is to turn many of the first into one of the
	 * second.
	 */
	@Override
	public boolean acceptsCable(BlockState state, DcCableSpec cable, Direction side) {
		return true;
	}

	/**
	 * An empty hand throws the switch.
	 *
	 * Anything else falls through, so a reel of cable clicked at the box still lays cable and the wrench
	 * still opens the panel. A player with a full hand cannot throw a switch either.
	 */
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

	/**
	 * Lets the arrays go when the box is actually broken.
	 *
	 * Here rather than in the block entity's own removal, for the same reason the inverter does it here:
	 * the block entity is also removed every time its chunk unloads, and reaching into another chunk
	 * while one is unloading is what stops a world from ever finishing its save.
	 */
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && !level.isClientSide
				&& level.getBlockEntity(pos) instanceof PvCombinerBlockEntity combiner) {
			combiner.releaseArrays();
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
