package com.dooji.electricity.block;

import com.dooji.electricity.api.power.DcCableSpec;
import com.dooji.electricity.api.power.PvArraySpec;
import com.dooji.electricity.api.power.PvMounting;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.registry.CableCatalog;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One block of photovoltaic array */
public class PvArrayBlock extends HorizontalDirectionalBlock implements EntityBlock, DcTerminal, MachineShell {
	/**
	 * The facing the four mounting models was modelled at: the mod's own, so they face north - and the physics reads a plane's bearing off the same facing.
	  *
	 * See {@link ModelFacing}.
	 */
	public static final Direction AUTHORED = Direction.NORTH;

	/** Whether the strings have leads on them. */
	public static final BooleanProperty HARNESSED = BooleanProperty.create("harnessed");

	/** The four mountings as collision, each cut from its own model. */
	private static final List<Cell> FLAT_CELLS = List.of(
			new Cell(0, 0, 0, Block.box(0.48, 0.00, 0.48, 15.52, 4.13, 15.52)));

	private static final List<Cell> TILT_CELLS = List.of(
			new Cell(0, 0, 0, Block.box(0.48, 0.00, 0.83, 15.52, 10.66, 15.10)));

	/**
	 * A turning row is one box, floor to the top of what its plane can sweep.
	  *
	 * The swept volume starts above the pier, so a second box for the pier only ever filled the gap under
	 * it - and a player cannot be in that gap either, because the volume above closes over their head.
	 */
	private static final List<Cell> TRACK_CELLS = List.of(
			new Cell(0, 0, 0, Block.box(0.34, 0.00, 0.32, 15.66, 16.00, 15.68)));

	/** The same for a pedestal frame, which turns about two axes and so sweeps the whole block. */
	private static final List<Cell> DUAL_CELLS = List.of(
			new Cell(0, 0, 0, Block.box(0.00, 0.00, 0.00, 16.00, 16.00, 16.00)));

	private static final Map<PvMounting, List<Cell>> CELLS = Map.of(
			PvMounting.FLAT, FLAT_CELLS,
			PvMounting.FIXED_TILT, TILT_CELLS,
			PvMounting.SINGLE_AXIS, TRACK_CELLS,
			PvMounting.DUAL_AXIS, DUAL_CELLS);

	private final PvArraySpec spec;

	public PvArrayBlock(Properties properties, PvArraySpec spec) {
		super(properties.sound(SoundType.GLASS));
		this.spec = spec;
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(HARNESSED, false));
	}

	public PvArraySpec spec() {
		return spec;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HARNESSED);
	}

	/**
	 * String cable only, and only where the row has something for it to land on.
	  *
	 * Which end takes what is {@link #meets}, the same method the plant is wired by and the models are drawn from.
	 */
	@Override
	public boolean acceptsCable(BlockState state, DcCableSpec cable, Direction side) {
		return !cable.trunk() && meets(state, side);
	}

	/** Fitting the leads, which is what right-clicking an array with a reel of string cable does. */
	@Override
	@Nullable
	public BlockState withCableFitted(BlockState state, DcCableSpec cable) {
		if (cable.trunk() || state.getValue(HARNESSED)) return null;

		return state.setValue(HARNESSED, true);
	}

	/** Whether this array has its leads, asked of the state so the client can ask it too. */
	public static boolean harnessed(BlockState state) {
		return state.getBlock() instanceof PvArrayBlock && state.getValue(HARNESSED);
	}

	/** Whether a block is one of these rows at all. */
	public static boolean row(BlockState state) {
		return state.getBlock() instanceof PvArrayBlock;
	}

	/**
	 * Whether a connection can arrive at this face of the row.
	  *
	 * {@link PvArrayRenderer} decides what to draw from these same two methods.
	 */
	public static boolean takes(BlockState state, @Nullable Direction face) {
		if (face == null || !(state.getBlock() instanceof PvArrayBlock array)) return false;
		if (array.spec().tracked()) return face.getAxis() == Direction.Axis.Z;

		return face == state.getValue(FACING);
	}

	/** Whether the row's own cable leaves by this face */
	public static boolean gives(BlockState state, @Nullable Direction face) {
		if (face == null || !harnessed(state) || !(state.getBlock() instanceof PvArrayBlock array)) return false;
		if (array.spec().tracked()) return face.getAxis() == Direction.Axis.Z;

		return face == state.getValue(FACING).getOpposite();
	}

	/** Whether a row has anything at all at this face for something to meet. */
	public static boolean meets(BlockState state, @Nullable Direction face) {
		return takes(state, face) || gives(state, face);
	}

	/** Hands the leads back when the array is taken away. */
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!level.isClientSide && !state.is(newState.getBlock()) && state.getValue(HARNESSED)) {
			popResource(level, pos, new ItemStack(Electricity.DC_CABLE_ITEMS.get(CableCatalog.STRING_6.id()).get()));
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// a tracker's axis is north-south or it cannot track at all, so it takes that orientation
		Direction facing = spec.tracked() ? Direction.NORTH : context.getHorizontalDirection().getOpposite();
		return defaultBlockState().setValue(FACING, facing).setValue(HARNESSED, false);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shellShape(state);
	}

	@Override
	public List<Cell> shellCells() {
		return CELLS.get(spec.mounting());
	}

	/** Which way the collision is turned. */
	@Override
	public Direction shellFacing(BlockState state) {
		return spec.tracked() ? AUTHORED : state.getValue(FACING);
	}

	@Override
	public Direction shellAuthored() {
		return AUTHORED;
	}

	/** Which way the module plane faces, on the mod's compass: 0 east, 90 south, 180 west, 270 north. */
	public static double planeAzimuthDeg(Direction facing) {
		return switch (facing) {
			case EAST -> 0.0;
			case SOUTH -> 90.0;
			case WEST -> 180.0;
			default -> 270.0;
		};
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PvArrayBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide ? (lvl, pos, blockState, blockEntity) -> {
			if (blockEntity instanceof PvArrayBlockEntity array) {
				array.clientTick();
			}
		} : (lvl, pos, blockState, blockEntity) -> {
			if (blockEntity instanceof PvArrayBlockEntity array) {
				array.serverTick();
			}
		};
	}
}
