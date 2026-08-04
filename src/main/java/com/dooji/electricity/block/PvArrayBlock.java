package com.dooji.electricity.block;

import com.dooji.electricity.api.power.DcCableSpec;
import com.dooji.electricity.api.power.PvArraySpec;
import com.dooji.electricity.main.Electricity;
import com.dooji.electricity.main.registry.CableCatalog;
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
public class PvArrayBlock extends HorizontalDirectionalBlock implements EntityBlock, DcTerminal {
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

	/** Ankle high: a flat table is walked over rather than round. */
	private static final VoxelShape FLAT_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0);
	/** A tilted rack, low edge on the ground and high edge about half a block up. */
	private static final VoxelShape TILT_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);
	/** A torque tube on piers, with the rotation envelope above it. */
	private static final VoxelShape TRACKER_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);
	/** A pedestal frame, which is the tallest thing in the catalogue that is not a turbine. */
	private static final VoxelShape PEDESTAL_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 14.0, 16.0);

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
		return switch (spec.mounting()) {
			case FLAT -> FLAT_SHAPE;
			case FIXED_TILT -> TILT_SHAPE;
			case SINGLE_AXIS -> TRACKER_SHAPE;
			case DUAL_AXIS -> PEDESTAL_SHAPE;
		};
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
