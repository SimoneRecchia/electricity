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

/**
 * One block of photovoltaic array, on whichever mounting its product came with.
 *
 * The block carries the {@link PvArraySpec} the way {@link WindTurbineBlock} carries a turbine's:
 * one block per catalogue entry, so a spec cannot exist without something that places it, and a
 * placed array cannot disagree with the item that placed it because there is nothing saved to drift.
 *
 * <h2>Why the height differs per mounting</h2>
 *
 * Because the mountings really are different heights, and a player has to be able to tell them apart
 * from across a field. A flat table is ankle high and walked over. A tilted rack stands about half a
 * block. A tracker's torque tube is carried on piers, so it is higher again and there is room to walk
 * underneath - which is the real reason trackers are built that way, since a row that has to rotate
 * through sixty degrees needs the clearance. A dual-axis pedestal is the tallest of the four.
 *
 * The collision is a single conservative box in every case, including the tracked ones. A rotating
 * collision shape would be correct and would also mean a player standing on a tracker at dawn is
 * inside it by mid-morning, which is worse than slightly wrong.
 *
 * <h2>Orientation</h2>
 *
 * A tilted rack faces the way it was placed, and that decides which way it tips - so it is a real
 * choice, unlike on Earth where a fixed rack faces the equator and there is nothing to decide. A
 * tracker's axis has to run north-south, because that is the only orientation from which it can
 * follow a sun that travels east to west, so a tracker snaps to that whatever direction the player
 * was looking.
 */
public class PvArrayBlock extends HorizontalDirectionalBlock implements EntityBlock, DcTerminal, MachineShell {
	/**
	 * The facing the four mounting models was modelled at: the mod's own, so they face north - and the physics reads a plane's bearing off the same facing.
	 *
	 * Declared here because more than one thing has to agree about it - the renderer turns the model
	 * by it, and whatever else reads the geometry turns with it. See {@link ModelFacing}.
	 */
	public static final Direction AUTHORED = Direction.NORTH;

	/**
	 * Whether the strings have leads on them.
	 *
	 * A block state rather than block entity data, because it decides what the cable alongside gets to
	 * draw and that answer is wanted while a chunk is being meshed. It is also the honest place for it:
	 * a set of leads worked onto an array is part of the array, the way a plug is part of an appliance.
	 *
	 * An array without them makes exactly nothing. That is not a technicality invented for the game -
	 * strings with no leads on them are not connected to anything, and a field of glass wired to nothing
	 * is a field of glass.
	 */
	public static final BooleanProperty HARNESSED = BooleanProperty.create("harnessed");

	/**
	 * The four mountings as collision, each cut from its own model.
	 *
	 * A box the full width of the block and a hand-picked height was wrong in both directions at once: a
	 * ballasted table three pixels tall claimed three, when it is two and its ballast is under the frame
	 * rather than beside it; and a rack claimed eight pixels over its whole footprint, so a player walked
	 * into a wall of air in front of its low edge and stood on air above it.
	 *
	 * The two fixed mountings are cut to the model to a hundredth of a pixel, the rack's tilted plane as a
	 * staircase of two-pixel treads that follows the slope. The two tracked ones cannot be: their plane
	 * turns through the day, so their collision is the fixed body cut from the model plus the volume the
	 * plane sweeps - see {@link #SWEPT_ROW} and {@link #SWEPT_FRAME}. Written by
	 * {@code tools/check_hitboxes.py --java}.
	 */
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

	/**
	 * What a tracked row's plane can be anywhere in, over a day.
	 *
	 * A plane 0.94 of a block wide turning about a tube 0.62 up sweeps a disc of that radius, so the box
	 * is the disc's extent in x and y and the modules' own extent along the tube. It reaches the top of
	 * the block because the plane does, at 55 degrees and beyond.
	 */
	private static final VoxelShape SWEPT_ROW = Block.box(0.34, 2.26, 0.32, 15.66, 16.00, 15.68);

	/**
	 * The same for a pedestal frame, which turns about two axes.
	 *
	 * The elevation axis is 0.75 up and the frame also spins in azimuth, so the swept volume is a sphere
	 * rather than a disc - and a sphere of that radius is wider than the block, which is why this one is
	 * the whole of the block above the frame's lowest reach.
	 */
	private static final VoxelShape SWEPT_FRAME = Block.box(0.00, 4.42, 0.00, 16.00, 16.00, 16.00);

	private static final List<Cell> TRACK_CELLS = List.of(
			new Cell(0, 0, 0, Shapes.or(
					Block.box(5.92, 0.00, 7.12, 10.08, 8.24, 8.88),
					Block.box(6.32, 8.24, 7.15, 11.31, 11.60, 8.85),
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
	 * A string has two ends. They are where the next row's string arrives and where this one's leaves, so
	 * a run of cable joins a row there and not down its flank - which is both what a real plant looks like
	 * and the reason the model has one lead along one edge instead of a run round all four.
	 *
	 * Which end takes what is {@link #meets}, the same method the plant is wired by and the models are
	 * drawn from. It used to require the leads to be on at either end, and that was the fault behind the
	 * question this all came from: a run laid to a bare row's *socket* refused to point at it, so a panel
	 * put down against a cable was connected to nothing until it had a reel of cable in it. A socket is
	 * for arriving at.
	 */
	@Override
	public boolean acceptsCable(BlockState state, DcCableSpec cable, Direction side) {
		return !cable.trunk() && meets(state, side);
	}

	/**
	 * Fitting the leads, which is what right-clicking an array with a reel of string cable does.
	 *
	 * Refused for trunk cable, and not out of pedantry: a 240 mm² conductor cannot be terminated in the
	 * plug on the end of a module, so there is nowhere for it to go.
	 */
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
	 * <h2>The rule the whole plant is wired by</h2>
	 *
	 * Two things are connected when the cables drawn at the boundary between them meet. Nothing else,
	 * and that is on purpose: a plant that looks wired and is not would be the worst thing this could
	 * be, so the picture is the rule rather than a description of it. {@link PvArrayRenderer} decides
	 * what to draw from these same two methods.
	 *
	 * What is drawn to be plugged into is the socket, and it is always there: a table or a rack has one
	 * on the end it faces, and a tracked row has one on each of the two ends its tube runs to, since
	 * placement forces that tube north-south whichever way the player was looking.
	 *
	 * So a row whose socket end faces a run of copper is wired with no harness in it at all. That is the
	 * answer to the question this was written for - place a cable, then a panel against it, and the panel
	 * is connected, the way redstone is.
	 */
	public static boolean takes(BlockState state, @Nullable Direction face) {
		if (face == null || !(state.getBlock() instanceof PvArrayBlock array)) return false;
		if (array.spec().tracked()) return face.getAxis() == Direction.Axis.Z;

		return face == state.getValue(FACING);
	}

	/**
	 * Whether the row's own cable leaves by this face, which is the whole of what a harness is for.
	 *
	 * A reel of cable fits a row's *outgoing* leads. So a row with no harness can be fed and cannot pass
	 * anything on, and the last row of a string needs none - while every row before it does, because
	 * that is the cable reaching the row behind.
	 *
	 * A table's and a rack's lead is on the end away from the socket. A tracked row's run goes the whole
	 * length of the block, so it leaves by both ends, and which one is the way on depends on which one it
	 * was fed at.
	 */
	public static boolean gives(BlockState state, @Nullable Direction face) {
		if (face == null || !harnessed(state) || !(state.getBlock() instanceof PvArrayBlock array)) return false;
		if (array.spec().tracked()) return face.getAxis() == Direction.Axis.Z;

		return face == state.getValue(FACING).getOpposite();
	}

	/**
	 * Whether a row has anything at all at this face for something to meet.
	 *
	 * Its socket, or its own outgoing lead. Everything that has an opinion about whether two things are
	 * connected asks this one method - the cable, which will not point anywhere else; {@link PvStrings},
	 * which wires the plant; and the renderer, which draws it - so the three cannot disagree.
	 */
	public static boolean meets(BlockState state, @Nullable Direction face) {
		return takes(state, face) || gives(state, face);
	}

	/**
	 * Hands the leads back when the array is taken away.
	 *
	 * A player who fitted a reel of cable into a machine should get it out again by breaking the
	 * machine, or the cable is a tax on rearranging a plant rather than a part of it.
	 */
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
		// however the player was standing. Everything else faces them, which for a tilted rack is
		// the direction it tips
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

	/**
	 * Which way the collision is turned.
	 *
	 * The same answer the renderer gives, and it has to be: a tracked row is drawn facing north whatever
	 * its state says, because its tube has to run north-south to follow the sun, so a shape turned by the
	 * state would stand across a row that is drawn along it.
	 */
	@Override
	public Direction shellFacing(BlockState state) {
		return spec.tracked() ? AUTHORED : state.getValue(FACING);
	}

	@Override
	public Direction shellAuthored() {
		return AUTHORED;
	}

	/**
	 * Which way the module plane faces, on the mod's compass: 0 east, 90 south, 180 west, 270 north.
	 *
	 * The same convention the sun and the wind use, so an incidence angle can be worked out without
	 * converting between two ideas of north. A flat plane's bearing means nothing - it is looking
	 * straight up - and it is answered anyway, because the transposition arithmetic multiplies it by
	 * the sine of a zero tilt and does not care.
	 */
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
