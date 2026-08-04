package com.dooji.electricity.block;

import com.dooji.electricity.api.power.DcCableSpec;
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
 * <h2>Why it behaves like dust</h2>
 *
 * Because the job is the same job: get a line from one machine to another across ground the player
 * chose, round corners, up a step, without a placement dialogue. Redstone's rules are the ones every
 * Minecraft player already knows, so the connection logic here is deliberately dust's - connect
 * sideways, connect diagonally down off a step, and climb the wall of a solid block to reach a run on
 * top of it. Vanilla's own {@link RedstoneSide} property is reused rather than a private copy, which
 * also means the blockstate reads the way a redstone one does.
 *
 * What is *not* borrowed is any notion of a signal. This carries nothing by itself; it is a shape that
 * {@link DcNetwork} walks. The two agree about connection because they ask the same method.
 *
 * <h2>The trench</h2>
 *
 * A real plant's home runs are clipped along the racking in free air, and its trunk between combiner
 * and cabinet is in a trench - which is why {@link #BURIED} exists rather than everything being a
 * surface run. A buried cable takes the ground block's place: the soil comes back as an item, the
 * cable is drawn flush with the surface on its bedding, and the block is a full cube so it is walked
 * over rather than tripped in.
 *
 * It also costs a fifth of the cable's rating, because the ground is a worse place to shed heat into
 * than moving air. That is the trade, and it is a real one.
 */
public class DcCableBlock extends Block implements DcTerminal {
	public static final EnumProperty<RedstoneSide> NORTH = BlockStateProperties.NORTH_REDSTONE;
	public static final EnumProperty<RedstoneSide> EAST = BlockStateProperties.EAST_REDSTONE;
	public static final EnumProperty<RedstoneSide> SOUTH = BlockStateProperties.SOUTH_REDSTONE;
	public static final EnumProperty<RedstoneSide> WEST = BlockStateProperties.WEST_REDSTONE;
	/** Laid in a trench rather than on the surface. */
	public static final BooleanProperty BURIED = BooleanProperty.create("buried");

	private static final Map<Direction, EnumProperty<RedstoneSide>> SIDES = sides();

	/**
	 * A run's outline, cut to what is drawn rather than to the block.
	 *
	 * It was a slab the whole width of the block and two pixels tall: so pointing anywhere in the block
	 * picked out the cable, and the highlight box a player saw was eight times the width of the thing they
	 * were pointing at.
	 *
	 * The string cable's tables are printed by {@code tools/gen_cable_models.py --java} from the very parts
	 * the model is built from, one box per part - a core's three round steps are one cable as far as
	 * pointing at it goes. The middle of the block depends on the *set* of sides that connect, which is why
	 * {@link #STRING_HUBS} is keyed by a bitmask: a bend is a different shape from a crossing, and drawing
	 * one while claiming the other is exactly the mismatch this mod checks for everywhere else.
	 *
	 * The trunk cable is still the old painted bar, so it keeps the old figures until it is redrawn too.
	 *
	 * A few pixels of outline is the right answer for a cable and not a compromise: the collision is still
	 * nothing at all, so a player does not catch a boot on every metre of their own plant.
	 */
	private static final VoxelShape TRUNK_HUB = Block.box(7.0, 0.0, 7.0, 9.0, 1.0, 9.0);
	private static final Map<Direction, VoxelShape> TRUNK_ARMS = Map.of(
			Direction.NORTH, Block.box(7.0, 0.0, 0.0, 9.0, 1.0, 7.0),
			Direction.SOUTH, Block.box(7.0, 0.0, 9.0, 9.0, 1.0, 16.0),
			Direction.WEST, Block.box(0.0, 0.0, 7.0, 7.0, 1.0, 9.0),
			Direction.EAST, Block.box(9.0, 0.0, 7.0, 16.0, 1.0, 9.0));
	private static final Map<Direction, VoxelShape> TRUNK_CLIMBS = Map.of(
			Direction.NORTH, Block.box(7.0, 0.0, 0.0, 9.0, 16.0, 1.0),
			Direction.SOUTH, Block.box(7.0, 0.0, 15.0, 9.0, 16.0, 16.0),
			Direction.WEST, Block.box(0.0, 0.0, 7.0, 1.0, 16.0, 9.0),
			Direction.EAST, Block.box(15.0, 0.0, 7.0, 16.0, 16.0, 9.0));

	private static final Map<Integer, VoxelShape> STRING_HUBS = Map.ofEntries(
			Map.entry(0b0000, Shapes.or(Block.box(5.90, 0.00, 2.50, 7.90, 2.00, 8.00),
					Block.box(5.85, 0.00, 8.00, 7.95, 3.10, 9.30),
					Block.box(5.85, 0.00, 9.30, 7.95, 3.10, 12.20),
					Block.box(6.10, 0.00, 12.20, 7.70, 3.10, 13.00),
					Block.box(8.10, 0.00, 2.50, 10.10, 2.00, 8.00),
					Block.box(8.05, 0.00, 8.00, 10.15, 3.10, 9.30),
					Block.box(8.05, 0.00, 9.30, 10.15, 3.10, 12.20),
					Block.box(8.30, 0.00, 12.20, 9.90, 3.10, 13.00))),
			Map.entry(0b0001, Shapes.or(Block.box(5.90, 0.00, 5.50, 7.90, 2.00, 8.00),
					Block.box(5.85, 0.00, 8.00, 7.95, 3.10, 9.30),
					Block.box(5.85, 0.00, 9.30, 7.95, 3.10, 12.20),
					Block.box(6.10, 0.00, 12.20, 7.70, 3.10, 13.00),
					Block.box(8.10, 0.00, 5.50, 10.10, 2.00, 8.00),
					Block.box(8.05, 0.00, 8.00, 10.15, 3.10, 9.30),
					Block.box(8.05, 0.00, 9.30, 10.15, 3.10, 12.20),
					Block.box(8.30, 0.00, 12.20, 9.90, 3.10, 13.00))),
			Map.entry(0b0010, Shapes.or(Block.box(8.00, 0.00, 5.90, 10.50, 2.00, 7.90),
					Block.box(6.70, 0.00, 5.85, 8.00, 3.10, 7.95),
					Block.box(3.80, 0.00, 5.85, 6.70, 3.10, 7.95),
					Block.box(3.00, 0.00, 6.10, 3.80, 3.10, 7.70),
					Block.box(8.00, 0.00, 8.10, 10.50, 2.00, 10.10),
					Block.box(6.70, 0.00, 8.05, 8.00, 3.10, 10.15),
					Block.box(3.80, 0.00, 8.05, 6.70, 3.10, 10.15),
					Block.box(3.00, 0.00, 8.30, 3.80, 3.10, 9.90))),
			Map.entry(0b0011, Shapes.or(Block.box(8.10, 0.00, 5.50, 10.10, 2.00, 7.90),
					Block.box(10.10, 0.00, 5.90, 10.50, 2.00, 7.90),
					Block.box(5.90, 0.00, 5.50, 7.90, 2.00, 10.10),
					Block.box(7.90, 0.00, 8.10, 10.50, 2.00, 10.10))),
			Map.entry(0b0100, Shapes.or(Block.box(8.10, 0.00, 8.00, 10.10, 2.00, 10.50),
					Block.box(8.05, 0.00, 6.70, 10.15, 3.10, 8.00),
					Block.box(8.05, 0.00, 3.80, 10.15, 3.10, 6.70),
					Block.box(8.30, 0.00, 3.00, 9.90, 3.10, 3.80),
					Block.box(5.90, 0.00, 8.00, 7.90, 2.00, 10.50),
					Block.box(5.85, 0.00, 6.70, 7.95, 3.10, 8.00),
					Block.box(5.85, 0.00, 3.80, 7.95, 3.10, 6.70),
					Block.box(6.10, 0.00, 3.00, 7.70, 3.10, 3.80))),
			Map.entry(0b0101, Shapes.or(Block.box(5.90, 0.00, 5.50, 7.90, 2.00, 10.50),
					Block.box(8.10, 0.00, 5.50, 10.10, 2.00, 10.50),
					Block.box(5.50, 0.00, 7.20, 5.90, 2.00, 8.80),
					Block.box(10.10, 0.00, 7.20, 10.50, 2.00, 8.80),
					Block.box(5.50, 2.00, 7.20, 10.50, 2.70, 8.80))),
			Map.entry(0b0110, Shapes.or(Block.box(8.10, 0.00, 8.10, 10.50, 2.00, 10.10),
					Block.box(8.10, 0.00, 10.10, 10.10, 2.00, 10.50),
					Block.box(5.90, 0.00, 5.90, 10.50, 2.00, 7.90),
					Block.box(5.90, 0.00, 7.90, 7.90, 2.00, 10.50))),
			Map.entry(0b0111, Block.box(5.50, 0.00, 5.50, 10.50, 3.60, 10.50)),
			Map.entry(0b1000, Shapes.or(Block.box(5.50, 0.00, 8.10, 8.00, 2.00, 10.10),
					Block.box(8.00, 0.00, 8.05, 9.30, 3.10, 10.15),
					Block.box(9.30, 0.00, 8.05, 12.20, 3.10, 10.15),
					Block.box(12.20, 0.00, 8.30, 13.00, 3.10, 9.90),
					Block.box(5.50, 0.00, 5.90, 8.00, 2.00, 7.90),
					Block.box(8.00, 0.00, 5.85, 9.30, 3.10, 7.95),
					Block.box(9.30, 0.00, 5.85, 12.20, 3.10, 7.95),
					Block.box(12.20, 0.00, 6.10, 13.00, 3.10, 7.70))),
			Map.entry(0b1001, Shapes.or(Block.box(5.50, 0.00, 5.90, 7.90, 2.00, 7.90),
					Block.box(5.90, 0.00, 5.50, 7.90, 2.00, 5.90),
					Block.box(5.50, 0.00, 8.10, 10.10, 2.00, 10.10),
					Block.box(8.10, 0.00, 5.50, 10.10, 2.00, 8.10))),
			Map.entry(0b1010, Shapes.or(Block.box(5.50, 0.00, 5.90, 10.50, 2.00, 7.90),
					Block.box(5.50, 0.00, 8.10, 10.50, 2.00, 10.10),
					Block.box(7.20, 0.00, 5.50, 8.80, 2.00, 5.90),
					Block.box(7.20, 0.00, 10.10, 8.80, 2.00, 10.50),
					Block.box(7.20, 2.00, 5.50, 8.80, 2.70, 10.50))),
			Map.entry(0b1011, Block.box(5.50, 0.00, 5.50, 10.50, 3.60, 10.50)),
			Map.entry(0b1100, Shapes.or(Block.box(5.90, 0.00, 8.10, 7.90, 2.00, 10.50),
					Block.box(5.50, 0.00, 8.10, 5.90, 2.00, 10.10),
					Block.box(8.10, 0.00, 5.90, 10.10, 2.00, 10.50),
					Block.box(5.50, 0.00, 5.90, 8.10, 2.00, 7.90))),
			Map.entry(0b1101, Block.box(5.50, 0.00, 5.50, 10.50, 3.60, 10.50)),
			Map.entry(0b1110, Block.box(5.50, 0.00, 5.50, 10.50, 3.60, 10.50)),
			Map.entry(0b1111, Block.box(5.50, 0.00, 5.50, 10.50, 3.60, 10.50)));

	private static final Map<Direction, VoxelShape> STRING_ARMS = Map.of(
			Direction.NORTH, Shapes.or(Block.box(5.90, 0.00, 0.00, 7.90, 2.00, 5.50),
					Block.box(8.10, 0.00, 0.00, 10.10, 2.00, 5.50)),
			Direction.EAST, Shapes.or(Block.box(10.50, 0.00, 5.90, 16.00, 2.00, 7.90),
					Block.box(10.50, 0.00, 8.10, 16.00, 2.00, 10.10)),
			Direction.SOUTH, Shapes.or(Block.box(8.10, 0.00, 10.50, 10.10, 2.00, 16.00),
					Block.box(5.90, 0.00, 10.50, 7.90, 2.00, 16.00)),
			Direction.WEST, Shapes.or(Block.box(0.00, 0.00, 8.10, 5.50, 2.00, 10.10),
					Block.box(0.00, 0.00, 5.90, 5.50, 2.00, 7.90)));

	private static final Map<Direction, VoxelShape> STRING_ARMS_UP = Map.of(
			Direction.NORTH, Shapes.or(Block.box(5.90, 0.00, 2.00, 7.90, 2.00, 5.50),
					Block.box(8.10, 0.00, 2.00, 10.10, 2.00, 5.50)),
			Direction.EAST, Shapes.or(Block.box(10.50, 0.00, 5.90, 14.00, 2.00, 7.90),
					Block.box(10.50, 0.00, 8.10, 14.00, 2.00, 10.10)),
			Direction.SOUTH, Shapes.or(Block.box(8.10, 0.00, 10.50, 10.10, 2.00, 14.00),
					Block.box(5.90, 0.00, 10.50, 7.90, 2.00, 14.00)),
			Direction.WEST, Shapes.or(Block.box(2.00, 0.00, 8.10, 5.50, 2.00, 10.10),
					Block.box(2.00, 0.00, 5.90, 5.50, 2.00, 7.90)));

	private static final Map<Direction, VoxelShape> STRING_CLIMBS = Map.of(
			Direction.NORTH, Shapes.or(Block.box(5.90, 0.00, 0.00, 7.90, 2.00, 2.00),
					Block.box(8.10, 0.00, 0.00, 10.10, 2.00, 2.00),
					Block.box(5.90, 2.00, 0.00, 7.90, 16.00, 2.00),
					Block.box(8.10, 2.00, 0.00, 10.10, 16.00, 2.00)),
			Direction.EAST, Shapes.or(Block.box(14.00, 0.00, 5.90, 16.00, 2.00, 7.90),
					Block.box(14.00, 0.00, 8.10, 16.00, 2.00, 10.10),
					Block.box(14.00, 2.00, 5.90, 16.00, 16.00, 7.90),
					Block.box(14.00, 2.00, 8.10, 16.00, 16.00, 10.10)),
			Direction.SOUTH, Shapes.or(Block.box(8.10, 0.00, 14.00, 10.10, 2.00, 16.00),
					Block.box(5.90, 0.00, 14.00, 7.90, 2.00, 16.00),
					Block.box(8.10, 2.00, 14.00, 10.10, 16.00, 16.00),
					Block.box(5.90, 2.00, 14.00, 7.90, 16.00, 16.00)),
			Direction.WEST, Shapes.or(Block.box(0.00, 0.00, 8.10, 2.00, 2.00, 10.10),
					Block.box(0.00, 0.00, 5.90, 2.00, 2.00, 7.90),
					Block.box(0.00, 2.00, 8.10, 2.00, 16.00, 10.10),
					Block.box(0.00, 2.00, 5.90, 2.00, 16.00, 7.90)));

	/** Which side is which bit of {@link #STRING_HUBS}'s key, in the order the generator numbers them. */
	private static final Direction[] MASK_ORDER = {Direction.NORTH, Direction.EAST, Direction.SOUTH,
			Direction.WEST};

	/**
	 * One shape per state, worked out once when the block is made.
	 *
	 * A hundred and sixty-two states, which is four sides at three values each and buried or not - few
	 * enough to hold and far cheaper than unioning five boxes on every collision test a player makes
	 * against a run. This is what {@code RedStoneWireBlock} does with dust, for the same reason.
	 */
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

		boolean pair = spec.id().getPath().equals("dc_string_cable");
		for (BlockState state : stateDefinition.getPossibleStates()) {
			shapes.put(state, pair ? stringShapeOf(state) : trunkShapeOf(state));
		}
	}

	/**
	 * The string cable as it is drawn in this state: the middle its pattern gets, the arms, any climb.
	 *
	 * The middle comes out of {@link #STRING_HUBS} by the same bitmask the model's blockstate chooses it
	 * by, so a bend claims the bend and a crossing claims the junction box. A buried run is the whole
	 * block, because it has taken a block of ground out of the world and the trench model really does
	 * fill the cell.
	 */
	private static VoxelShape stringShapeOf(BlockState state) {
		if (state.getValue(BURIED)) return Shapes.block();

		int mask = 0;
		for (int i = 0; i < MASK_ORDER.length; i++) {
			if (state.getValue(SIDES.get(MASK_ORDER[i])) != RedstoneSide.NONE) {
				mask |= 1 << i;
			}
		}

		VoxelShape shape = STRING_HUBS.get(mask);
		for (Map.Entry<Direction, EnumProperty<RedstoneSide>> side : SIDES.entrySet()) {
			RedstoneSide connection = state.getValue(side.getValue());
			if (connection == RedstoneSide.NONE) continue;

			if (connection == RedstoneSide.UP) {
				shape = Shapes.or(shape, STRING_ARMS_UP.get(side.getKey()),
						STRING_CLIMBS.get(side.getKey()));
			} else {
				shape = Shapes.or(shape, STRING_ARMS.get(side.getKey()));
			}
		}

		return shape;
	}

	/** The trunk cable, still one painted bar: a hub, an arm a side, and a climb up a wall. */
	private static VoxelShape trunkShapeOf(BlockState state) {
		if (state.getValue(BURIED)) return Shapes.block();

		VoxelShape shape = TRUNK_HUB;
		for (Map.Entry<Direction, EnumProperty<RedstoneSide>> side : SIDES.entrySet()) {
			RedstoneSide connection = state.getValue(side.getValue());
			if (connection == RedstoneSide.NONE) continue;

			shape = Shapes.or(shape, TRUNK_ARMS.get(side.getKey()));
			if (connection == RedstoneSide.UP) {
				shape = Shapes.or(shape, TRUNK_CLIMBS.get(side.getKey()));
			}
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
		return shapes.getOrDefault(state, TRUNK_HUB);
	}

	/**
	 * A trench is walked over; a surface run is not walked into at all.
	 *
	 * Nothing for a surface run, so it is as unobtrusive as dust and a player does not catch a boot on
	 * every metre of their own plant. A full cube for a trench, because it has taken a block of ground
	 * out of the world and something has to stand in for it, or the player crossing their own trench
	 * falls into it.
	 */
	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(BURIED) ? Shapes.block() : Shapes.empty();
	}

	/**
	 * What the light and the face culling see.
	 *
	 * Full for a trench, because the bedding really does fill the cell and the neighbouring soil should
	 * stop drawing the faces it turns towards it - two coplanar faces at a block boundary flicker
	 * against each other, and that would be visible along every metre of every buried run.
	 */
	@Override
	public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getValue(BURIED) ? Shapes.block() : Shapes.empty();
	}

	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		return !state.getValue(BURIED);
	}

	/**
	 * Light is worked out from the shape, because here the shape is what differs.
	 *
	 * Without this the engine takes one answer for the whole block and caches it, and a surface run
	 * would put a block of ground's worth of shadow under itself.
	 */
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

	/**
	 * The state with all four sides worked out from what is actually around it.
	 *
	 * One method, called from placement, from a neighbour changing, and from {@link DcNetwork} - so a
	 * run cannot be drawn connected and walked as separate, or the other way about.
	 */
	public BlockState connected(BlockState state, BlockGetter level, BlockPos pos) {
		BlockState result = state;
		for (Map.Entry<Direction, EnumProperty<RedstoneSide>> side : SIDES.entrySet()) {
			result = result.setValue(side.getValue(), connection(level, pos, side.getKey()));
		}

		return result;
	}

	/**
	 * The ways a run can reach in one direction, which is more than one of them at a time.
	 *
	 * Dust's three cases. {@code climbs} is a run on top of the block alongside, reached by going up its
	 * wall - which needs headroom over this block to leave through and a top on that one to land on.
	 * {@code flat} is the plain case. {@code steps} is a run a level below, reached over the top of a
	 * neighbour too low to be in the way. {@code wall} is whether the block alongside is a full cube,
	 * which is the only thing that separates going *up* it from lying against it.
	 */
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

	/**
	 * Every position a run reaches, machines included: what {@link DcNetwork} follows.
	 *
	 * The same three tests the model is drawn from, so what a player sees connected and what the plant
	 * counts are the same thing by construction.
	 */
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

	/**
	 * Whether this run reaches one particular position.
	 *
	 * What a machine's renderer asks of its neighbours to decide which of its four cable entries to
	 * draw, so a plant shows copper going *into* a cabinet rather than stopping a pixel short of it over
	 * open ground. Asked of the cable rather than worked out by the machine, so the picture agrees with
	 * {@link DcNetwork} by construction.
	 */
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

	/**
	 * Whether a run of this gauge joins onto that block at all.
	 *
	 * Its own kind, or a machine whose terminals take this gauge. The two gauges do not join to each
	 * other: a 240 mm² lug does not go into an MC4 plug, and one rule stated once beats a page of
	 * exceptions about which end of a plant a cable is at.
	 */
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

	/**
	 * Re-reads the sides of every run this one could have changed the picture for.
	 *
	 * {@link #updateShape} only fires for the six positions sharing a face, and this cable connects
	 * diagonally as well - so a run laid a block higher than its neighbour would draw its own climb and
	 * the neighbour would go on showing a dead end. The twelve positions checked here are exactly the
	 * ones {@link #connection} can reach, and the cost of reading twelve block states when a cable is
	 * laid or cut is nothing.
	 */
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

	/** How a run reaches in one direction, for anything walking it. */
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
