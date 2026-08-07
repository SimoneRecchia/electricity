package com.dooji.electricity.block;

import com.dooji.electricity.api.power.DcCableSpec;
import com.dooji.electricity.main.registry.CableCatalog;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A run of direct-current cable, laid the way a player lays redstone.
  *
 * This carries nothing by itself; it is a shape that {@link DcNetwork} walks.
 */
public class DcCableBlock extends Block implements DcTerminal {
	public static final EnumProperty<RedstoneSide> NORTH = BlockStateProperties.NORTH_REDSTONE;
	public static final EnumProperty<RedstoneSide> EAST = BlockStateProperties.EAST_REDSTONE;
	public static final EnumProperty<RedstoneSide> SOUTH = BlockStateProperties.SOUTH_REDSTONE;
	public static final EnumProperty<RedstoneSide> WEST = BlockStateProperties.WEST_REDSTONE;
	/** Laid in a trench rather than on the surface. */
	public static final BooleanProperty BURIED = BooleanProperty.create("buried");

	private static final Map<Direction, EnumProperty<RedstoneSide>> SIDES = sides();

	private static final Map<Integer, VoxelShape> TRUNK_HUBS = Map.ofEntries(
			Map.entry(0b0000, Block.box(4.40, 0.00, 0.60, 11.60, 3.10, 13.20)),
			Map.entry(0b0001, Block.box(4.40, 0.00, 1.62, 11.60, 3.10, 13.20)),
			Map.entry(0b0010, Block.box(2.80, 0.00, 4.40, 14.38, 3.10, 11.60)),
			Map.entry(0b0011, Block.box(4.55, 0.00, 1.62, 14.38, 2.80, 11.45)),
			Map.entry(0b0100, Block.box(4.40, 0.00, 2.80, 11.60, 3.10, 14.38)),
			Map.entry(0b0101, Block.box(3.75, 0.00, 1.40, 12.25, 4.40, 14.60)),
			Map.entry(0b0110, Block.box(4.55, 0.00, 4.55, 14.38, 2.80, 14.38)),
			Map.entry(0b0111, Block.box(2.46, 0.00, 1.30, 14.70, 6.00, 14.70)),
			Map.entry(0b1000, Block.box(1.62, 0.00, 4.40, 13.20, 3.10, 11.60)),
			Map.entry(0b1001, Block.box(1.62, 0.00, 1.62, 11.45, 2.80, 11.45)),
			Map.entry(0b1010, Block.box(1.40, 0.00, 3.75, 14.60, 4.40, 12.25)),
			Map.entry(0b1011, Block.box(1.30, 0.00, 1.30, 14.70, 6.00, 13.54)),
			Map.entry(0b1100, Block.box(1.62, 0.00, 4.55, 11.45, 2.80, 14.38)),
			Map.entry(0b1101, Block.box(1.30, 0.00, 1.30, 13.54, 6.00, 14.70)),
			Map.entry(0b1110, Block.box(1.30, 0.00, 2.46, 14.70, 6.00, 14.70)),
			Map.entry(0b1111, Block.box(1.30, 0.00, 1.30, 14.70, 6.00, 14.70)));

	private static final Map<Direction, VoxelShape> TRUNK_ARMS = Map.of(
			Direction.NORTH, Block.box(3.88, 0.00, 0.00, 12.12, 3.58, 2.80),
			Direction.EAST, Block.box(13.20, 0.00, 3.88, 16.00, 3.58, 12.12),
			Direction.SOUTH, Block.box(3.88, 0.00, 13.20, 12.12, 3.58, 16.00),
			Direction.WEST, Block.box(0.00, 0.00, 3.88, 2.80, 3.58, 12.12));

	private static final Map<Integer, VoxelShape> STRING_HUBS = Map.ofEntries(
			Map.entry(0b0000, Block.box(5.65, 0.00, 3.20, 10.35, 2.20, 15.00)),
			Map.entry(0b0001, Block.box(5.65, 0.00, 5.40, 10.35, 2.20, 15.00)),
			Map.entry(0b0010, Block.box(1.00, 0.00, 5.65, 10.60, 2.20, 10.35)),
			Map.entry(0b0011, Block.box(6.30, 0.00, 4.97, 11.03, 1.30, 9.70)),
			Map.entry(0b0100, Block.box(5.65, 0.00, 1.00, 10.35, 2.20, 10.60)),
			Map.entry(0b0101, Block.box(5.65, 0.00, 4.97, 10.35, 2.20, 11.03)),
			Map.entry(0b0110, Block.box(6.30, 0.00, 6.30, 11.03, 1.30, 11.03)),
			Map.entry(0b0111, Block.box(5.10, 0.00, 4.04, 11.96, 4.10, 11.96)),
			Map.entry(0b1000, Block.box(5.40, 0.00, 5.65, 15.00, 2.20, 10.35)),
			Map.entry(0b1001, Block.box(4.97, 0.00, 4.97, 9.70, 1.30, 9.70)),
			Map.entry(0b1010, Block.box(4.97, 0.00, 5.65, 11.03, 2.20, 10.35)),
			Map.entry(0b1011, Block.box(4.04, 0.00, 4.04, 11.96, 4.10, 10.90)),
			Map.entry(0b1100, Block.box(4.97, 0.00, 6.30, 9.70, 1.30, 11.03)),
			Map.entry(0b1101, Block.box(4.04, 0.00, 4.04, 10.90, 4.10, 11.96)),
			Map.entry(0b1110, Block.box(4.04, 0.00, 5.10, 11.96, 4.10, 11.96)),
			Map.entry(0b1111, Block.box(4.04, 0.00, 4.04, 11.96, 4.10, 11.96)));

	private static final Map<Direction, VoxelShape> STRING_ARMS = Map.of(
			Direction.NORTH, Block.box(6.00, 0.00, 0.00, 10.36, 1.52, 5.40),
			Direction.EAST, Block.box(10.60, 0.00, 6.00, 16.00, 1.52, 10.36),
			Direction.SOUTH, Block.box(5.64, 0.00, 10.60, 10.00, 1.52, 16.00),
			Direction.WEST, Block.box(0.00, 0.00, 5.64, 5.40, 1.52, 10.00));

	private static final Map<Direction, VoxelShape> STRING_CLIMBS = Map.of(
			Direction.NORTH, Block.box(5.95, 0.00, 0.00, 10.05, 16.00, 5.40),
			Direction.EAST, Block.box(10.60, 0.00, 5.95, 16.00, 16.00, 10.05),
			Direction.SOUTH, Block.box(5.95, 0.00, 10.60, 10.05, 16.00, 16.00),
			Direction.WEST, Block.box(0.00, 0.00, 5.95, 5.40, 16.00, 10.05));

	/** Which side is which bit of {@link #STRING_HUBS}'s key, in the order the generator numbers them. */
	private static final Direction[] MASK_ORDER = {Direction.NORTH, Direction.EAST, Direction.SOUTH,
			Direction.WEST};

	/** One shape per state, worked out once when the block is made. */
	private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

	private final DcCableSpec spec;

	public DcCableBlock(Properties properties, DcCableSpec spec) {
		super(properties.sound(SoundType.WOOL));
		this.spec = spec;
		registerDefaultState(defaultBlockState()
				.setValue(NORTH, RedstoneSide.NONE)
				.setValue(EAST, RedstoneSide.NONE)
				.setValue(SOUTH, RedstoneSide.NONE)
				.setValue(WEST, RedstoneSide.NONE)
				.setValue(BURIED, false));

		// Which of the two cables this is, asked of the catalogue rather than of a string literal here.
		boolean pair = spec.id().equals(CableCatalog.STRING_6.id());
		for (BlockState state : stateDefinition.getPossibleStates()) {
			shapes.put(state, pair
					? shapeOf(state, STRING_HUBS, STRING_ARMS, STRING_CLIMBS)
					: shapeOf(state, TRUNK_HUBS, TRUNK_ARMS, null));
		}
	}

	/**
	 * Either cable as it is drawn in this state: the middle its connection pattern gets, plus an arm or a
	 * climb for each side.  ``climbs`` is null for the trunk, which cannot turn up a wall inside one block
	 * - its elbow would sit inside its own link box - so an up side there draws the arm and the block
	 * above carries on.  gen_cable_models and gen_trunk_models print all six tables.
	 */
	private static VoxelShape shapeOf(BlockState state, Map<Integer, VoxelShape> hubs,
			Map<Direction, VoxelShape> arms, Map<Direction, VoxelShape> climbs) {
		if (state.getValue(BURIED)) return Shapes.block();

		int mask = 0;
		for (int i = 0; i < MASK_ORDER.length; i++) {
			if (state.getValue(SIDES.get(MASK_ORDER[i])) != RedstoneSide.NONE) {
				mask |= 1 << i;
			}
		}

		VoxelShape shape = hubs.get(mask);
		for (Map.Entry<Direction, EnumProperty<RedstoneSide>> side : SIDES.entrySet()) {
			RedstoneSide connection = state.getValue(side.getValue());
			if (connection == RedstoneSide.NONE) continue;

			// A climb is the *whole* run for that side - along the ground and then up the wall, one tube
			boolean up = connection == RedstoneSide.UP && climbs != null;
			shape = Shapes.or(shape, up ? climbs.get(side.getKey()) : arms.get(side.getKey()));
		}

		return shape;
	}

	private static Map<Direction, EnumProperty<RedstoneSide>> sides() {
		Map<Direction, EnumProperty<RedstoneSide>> map = new EnumMap<>(Direction.class);
		map.put(Direction.NORTH, NORTH);
		map.put(Direction.EAST, EAST);
		map.put(Direction.SOUTH, SOUTH);
		map.put(Direction.WEST, WEST);
		return Map.copyOf(map);
	}

	public DcCableSpec spec() {
		return spec;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, BURIED);
	}

	// ---- shape ----

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.getOrDefault(state, Shapes.empty());
	}

	/**
	 * The cable is solid: a laid run is a couple of pixels high and a joint in it is three, so a player
	 * steps over it the way they step onto a carpet.  Returning nothing here meant walking straight
	 * through the thing whose outline was drawn round it.  A trench is filled to the surface.
	 */
	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(BURIED) ? Shapes.block() : shapes.getOrDefault(state, Shapes.empty());
	}

	/** What the light and the face culling see. */
	@Override
	public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getValue(BURIED) ? Shapes.block() : Shapes.empty();
	}

	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		return !state.getValue(BURIED);
	}

	/** Light is worked out from the shape, because here the shape is what differs. */
	@Override
	public boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	// ---- where it will lie ----

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		// a trench has walls of its own; a surface run needs something under it, the same as dust
		if (state.getValue(BURIED)) return true;

		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return connected(defaultBlockState(), context.getLevel(), context.getClickedPos());
	}

	/** The state with all four sides worked out from what is actually around it. */
	public BlockState connected(BlockState state, BlockGetter level, BlockPos pos) {
		BlockState result = state;
		for (Map.Entry<Direction, EnumProperty<RedstoneSide>> side : SIDES.entrySet()) {
			result = result.setValue(side.getValue(), connection(level, pos, side.getKey()));
		}

		return result;
	}

	/** The ways a run can reach in one direction, which is more than one of them at a time. */
	private record Ways(boolean climbs, boolean flat, boolean steps, boolean wall) {
		boolean any() {
			return climbs || flat || steps;
		}
	}

	private Ways ways(BlockGetter level, BlockPos pos, Direction direction) {
		BlockPos beside = pos.relative(direction);
		BlockPos above = pos.above();
		BlockState alongside = level.getBlockState(beside);
		boolean wall = alongside.isCollisionShapeFullBlock(level, beside);

		boolean climbs = !level.getBlockState(above).isCollisionShapeFullBlock(level, above)
				&& alongside.isFaceSturdy(level, beside, Direction.UP)
				&& connectsTo(level.getBlockState(beside.above()), direction);
		boolean steps = !wall && connectsTo(level.getBlockState(beside.below()), direction);
		return new Ways(climbs, connectsTo(alongside, direction), steps, wall);
	}

	/** How a run at this position reaches in one direction, for the model to draw. */
	public RedstoneSide connection(BlockGetter level, BlockPos pos, Direction direction) {
		Ways ways = ways(level, pos, direction);
		if (ways.climbs() && ways.wall()) return RedstoneSide.UP;

		return ways.any() ? RedstoneSide.SIDE : RedstoneSide.NONE;
	}

	/** Every position a run reaches, machines included: what {@link DcNetwork} follows. */
	public void collectReached(BlockState state, BlockGetter level, BlockPos pos, Consumer<BlockPos> out) {
		for (Map.Entry<Direction, EnumProperty<RedstoneSide>> entry : SIDES.entrySet()) {
			if (state.getValue(entry.getValue()) == RedstoneSide.NONE) continue;

			Direction direction = entry.getKey();
			BlockPos beside = pos.relative(direction);
			Ways ways = ways(level, pos, direction);
			if (ways.climbs()) out.accept(beside.above());
			if (ways.flat()) out.accept(beside);
			if (ways.steps()) out.accept(beside.below());
		}
	}

	/** Whether this run reaches one particular position. */
	public boolean reaches(BlockState state, BlockGetter level, BlockPos pos, BlockPos target) {
		for (Map.Entry<Direction, EnumProperty<RedstoneSide>> entry : SIDES.entrySet()) {
			if (state.getValue(entry.getValue()) == RedstoneSide.NONE) continue;

			Direction direction = entry.getKey();
			BlockPos beside = pos.relative(direction);
			// the cheap test first, because this is asked of every neighbour of every machine on screen
			boolean candidate = target.equals(beside) || target.equals(beside.above()) || target.equals(beside.below());
			if (!candidate) continue;

			Ways ways = ways(level, pos, direction);
			if (ways.climbs() && target.equals(beside.above())) return true;
			if (ways.flat() && target.equals(beside)) return true;
			if (ways.steps() && target.equals(beside.below())) return true;
		}

		return false;
	}

	/** Whether a run of this gauge joins onto that block at all. */
	public boolean connectsTo(BlockState other, Direction towards) {
		if (other.getBlock() instanceof DcCableBlock cable) return cable.spec == spec;

		// the face of the *other* block, which is the one opposite the way this run is reaching
		return other.getBlock() instanceof DcTerminal terminal
				&& terminal.acceptsCable(other, spec, towards.getOpposite());
	}

	/** A cable is a terminal too, which is how the walk treats a run and a machine alike. */
	@Override
	public boolean acceptsCable(BlockState state, DcCableSpec cable, Direction side) {
		return cable == spec;
	}

	// ---- keeping up with the world ----

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
			BlockPos pos, BlockPos neighbourPos) {
		if (!canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();

		return connected(state, level, pos);
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!level.isClientSide) refreshRuns(level, pos);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		super.onRemove(state, level, pos, newState, movedByPiston);
		if (!level.isClientSide && !state.is(newState.getBlock())) refreshRuns(level, pos);
	}

	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
		if (level.isClientSide) return;

		if (!state.canSurvive(level, pos)) {
			dropResources(state, level, pos);
			level.removeBlock(pos, false);
		}
	}

	/** Re-reads the sides of every run this one could have changed the picture for. */
	private void refreshRuns(Level level, BlockPos pos) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos beside = pos.relative(direction);
			for (BlockPos candidate : new BlockPos[]{beside, beside.above(), beside.below()}) {
				if (!level.isLoaded(candidate)) continue;

				BlockState state = level.getBlockState(candidate);
				if (!(state.getBlock() instanceof DcCableBlock cable)) continue;

				BlockState updated = cable.connected(state, level, candidate);
				if (updated != state) level.setBlock(candidate, updated, Block.UPDATE_CLIENTS);
			}
		}
	}

	/** Whether any side of this run reaches anywhere, which is what tells a player they have a dead end. */
	public static boolean isolated(BlockState state) {
		if (!(state.getBlock() instanceof DcCableBlock)) return true;

		for (EnumProperty<RedstoneSide> side : SIDES.values()) {
			if (state.getValue(side) != RedstoneSide.NONE) return false;
		}

		return true;
	}

	/** How a run reaches in one direction */
	public static RedstoneSide side(BlockState state, Direction direction) {
		EnumProperty<RedstoneSide> property = SIDES.get(direction);
		return property == null ? RedstoneSide.NONE : state.getValue(property);
	}

	@Override
	public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.level.pathfinder.PathComputationType type) {
		return !state.getValue(BURIED);
	}

	/** Properties every cable block shares, so the two gauges cannot drift apart. */
	public static BlockBehaviour.Properties properties() {
		return Block.Properties.of().strength(0.2f);
	}
}
