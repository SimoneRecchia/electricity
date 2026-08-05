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
import net.minecraft.world.phys.shapes.Shapes;
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
			new Cell(0, 0, 0, Shapes.or(Block.box(0.48, 0.72, 0.48, 15.52, 1.68, 15.52),
					Block.box(0.72, 1.62, 0.48, 15.28, 2.45, 15.52),
					Block.box(0.96, 0.00, 0.96, 15.04, 0.72, 15.04))));

	private static final List<Cell> TILT_CELLS = List.of(
			new Cell(0, 0, 0, Shapes.or(Block.box(0.48, 0.89, 1.55, 15.52, 1.96, 2.73),
					Block.box(0.48, 6.50, 13.58, 15.52, 7.57, 14.76),
					Block.box(0.61, 1.71, 0.83, 15.39, 3.07, 2.83),
					Block.box(0.61, 2.47, 2.83, 15.39, 4.01, 4.83),
					Block.box(0.61, 4.41, 6.83, 15.39, 5.87, 8.83),
					Block.box(0.61, 6.27, 10.83, 15.39, 7.81, 12.83),
					Block.box(0.61, 7.14, 12.83, 15.39, 8.50, 14.67),
					Block.box(0.72, 1.39, 1.04, 15.28, 2.64, 3.04),
					Block.box(0.72, 2.25, 3.04, 15.28, 3.57, 5.04),
					Block.box(0.72, 3.18, 5.04, 15.28, 4.50, 7.04),
					Block.box(0.72, 4.12, 7.04, 15.28, 5.44, 9.04),
					Block.box(0.72, 5.05, 9.04, 15.28, 6.37, 11.04),
					Block.box(0.72, 5.98, 11.04, 15.28, 7.30, 13.04),
					Block.box(0.72, 6.91, 13.04, 15.28, 8.06, 14.82),
					Block.box(0.96, 3.48, 4.83, 15.04, 4.94, 6.83),
					Block.box(0.96, 5.34, 8.83, 15.04, 6.80, 10.83),
					Block.box(1.82, 0.00, 1.48, 14.18, 1.28, 3.48),
					Block.box(1.82, 0.00, 13.48, 14.18, 6.77, 15.10),
					Block.box(2.35, 0.92, 3.48, 13.65, 2.22, 5.48),
					Block.box(2.35, 1.86, 5.48, 13.65, 3.16, 7.48),
					Block.box(2.35, 2.80, 7.48, 13.65, 4.10, 9.48),
					Block.box(2.35, 3.75, 9.48, 13.65, 5.04, 11.48),
					Block.box(2.35, 4.69, 11.48, 13.65, 5.98, 13.48))));

	/** What a tracked row's plane can be anywhere in, over a day. */
	private static final VoxelShape SWEPT_ROW = Block.box(0.34, 3.22, 0.32, 15.66, 16.00, 15.68);

	/** The same for a pedestal frame, which turns about two axes. */
	private static final VoxelShape SWEPT_FRAME = Block.box(0.00, 4.37, 0.00, 16.00, 16.00, 16.00);

	private static final List<Cell> TRACK_CELLS = List.of(
			new Cell(0, 0, 0, Shapes.or(
					Block.box(5.92, 0.00, 7.12, 10.08, 9.20, 8.88),
					Block.box(6.32, 9.20, 7.15, 11.31, 12.56, 8.85),
					SWEPT_ROW)));

	private static final List<Cell> DUAL_CELLS = List.of(
			new Cell(0, 0, 0, Shapes.or(
					Block.box(4.96, 0.00, 4.96, 11.04, 8.80, 11.04),
					SWEPT_FRAME)));

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
