package com.dooji.electricity.block;

import com.dooji.electricity.api.power.SwitchgearSpec;
import com.dooji.electricity.main.Electricity;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A switch in a line: a disconnector or a circuit breaker, told apart by its spec.
 *
 * Both are one block and both carry six fittings - one each side of three poles, {@code insulator_1..3} on
 * the line side and {@code insulator_4..6} on the load side. What open <em>means</em> is
 * {@link SwitchgearBlockEntity#busOf}: the two sides stop being one bus, and PowerNetwork then has two
 * clusters at one position with nothing joining them.
 *
 * Right-click throws it. A disconnector refuses to open under load, which is the real interlock: open the
 * breaker first. A redstone signal holds either of them open, the way an energised trip coil does.
 */
public class SwitchgearBlock extends Block implements EntityBlock, MachineShell {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** Whether the switch is open, which is the one thing about it a player can see. */
	public static final BooleanProperty OPEN = BooleanProperty.create("open");

	/** The facing both models are authored at: the line runs along z and the three poles along x. */
	public static final Direction AUTHORED = Direction.NORTH;

	/** ---- mv_disconnector, printed by tools/check_hitboxes.py --java ---- */
	private static final List<Cell> DISCONNECTOR_CELLS = List.of(
			new Cell(0, 0, 0, Shapes.or(Block.box(0.80, 0.00, 4.16, 15.20, 1.95, 11.84),
					Block.box(1.28, 1.28, 10.00, 14.85, 5.73, 11.15),
					Block.box(2.98, 1.28, 9.78, 4.38, 5.70, 11.18),
					Block.box(2.98, 1.28, 4.82, 4.38, 5.73, 6.22),
					Block.box(7.30, 1.28, 9.78, 8.70, 5.70, 11.18),
					Block.box(7.30, 1.28, 4.82, 8.70, 5.73, 6.22),
					Block.box(11.62, 1.28, 9.78, 13.02, 5.70, 11.18),
					Block.box(11.62, 1.28, 4.82, 13.02, 5.73, 6.22))));

	/** ---- mv_breaker, printed by tools/check_hitboxes.py --java ---- */
	private static final List<Cell> BREAKER_CELLS = List.of(
			new Cell(0, 0, 0, Shapes.or(Block.box(0.80, 0.00, 4.16, 15.20, 8.08, 11.84),
					Block.box(2.81, 6.89, 5.14, 4.55, 8.69, 10.86),
					Block.box(7.13, 6.89, 5.14, 8.87, 8.69, 10.86),
					Block.box(11.45, 6.89, 5.14, 13.19, 8.69, 10.86))));

	private final SwitchgearSpec spec;

	public SwitchgearBlock(Properties properties, SwitchgearSpec spec) {
		super(properties.sound(SoundType.METAL));
		this.spec = spec;
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
	}

	public SwitchgearSpec spec() {
		return spec;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OPEN);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
				.setValue(OPEN, false);
	}

	/** Whether this state is open, asked of the state so the client can ask it too. */
	public static boolean open(BlockState state) {
		return state.getBlock() instanceof SwitchgearBlock && state.getValue(OPEN);
	}

	/**
	 * Throws the switch, or says why it will not go.
	 *
	 * A disconnector under load is the one refusal, and it is the real one: its contacts are bare metal in
	 * air and there is nothing in it to put an arc out.
	 */
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		boolean wanted = !state.getValue(OPEN);
		if (level.isClientSide) return InteractionResult.SUCCESS;

		if (wanted && !spec.breaksLoad() && underLoad(level, pos)) {
			player.displayClientMessage(Component.translatable("message.electricity.switch.under_load"), true);
			return InteractionResult.CONSUME;
		}

		throwSwitch(level, pos, state, wanted);
		player.displayClientMessage(Component.translatable(
				wanted ? "message.electricity.switch.opened" : "message.electricity.switch.closed"), true);
		return InteractionResult.CONSUME;
	}

	/** Sets the position, plays the noise it makes, and puts the network right again. */
	public void throwSwitch(Level level, BlockPos pos, BlockState state, boolean open) {
		if (state.getValue(OPEN) == open) return;

		level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
		// a disconnector is a spring and a clang; a vacuum breaker is a solenoid and a thump
		level.playSound(null, pos, spec.breaksLoad() ? SoundEvents.PISTON_CONTRACT : SoundEvents.IRON_TRAPDOOR_OPEN,
				SoundSource.BLOCKS, 0.7f, spec.breaksLoad() ? 0.6f : 1.1f);
		// the network is rebuilt from the wires every tick anyway, but not until the next one - and a switch
		// a player throws should read as thrown before they have let go of the button
		if (!level.isClientSide && Electricity.powerNetwork != null) {
			Electricity.powerNetwork.updatePowerNetwork();
		}
	}

	/** Whether there is current in it worth arcing, read off what the network last delivered here. */
	private static boolean underLoad(Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof SwitchgearBlockEntity gear && gear.underLoad();
	}

	/**
	 * A redstone signal holds the switch open, the way an energised trip coil does.
	 *
	 * The signal opens it and letting the signal go closes it again: a switch with no memory is the one a
	 * player can reason about, and a trip that had to be reset by hand would need a panel to reset it from.
	 */
	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block from, BlockPos fromPos,
			boolean moving) {
		super.neighborChanged(state, level, pos, from, fromPos, moving);
		if (level.isClientSide) return;

		boolean powered = level.hasNeighborSignal(pos);
		if (powered != state.getValue(OPEN)) {
			// under load a disconnector still goes: a trip coil does not ask, and a player who wires one up
			// to redstone has taken the interlock off themselves
			throwSwitch(level, pos, state, powered);
		}
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shellShape(state);
	}

	@Override
	public List<Cell> shellCells(BlockState state) {
		return spec.breaksLoad() ? BREAKER_CELLS : DISCONNECTOR_CELLS;
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
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return 0;
	}

	@Nullable @Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SwitchgearBlockEntity(pos, state);
	}

	@Nullable @Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide ? null : (lvl, pos, blockState, blockEntity) -> {
			if (blockEntity instanceof SwitchgearBlockEntity gear) gear.serverTick();
		};
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
			if (!level.isClientSide && level instanceof ServerLevel serverLevel
					&& level.getBlockEntity(pos) instanceof SwitchgearBlockEntity gear) {
				Electricity.wireManager.removeConnectionsForInsulators(serverLevel, gear.getInsulatorIds());
			}
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
