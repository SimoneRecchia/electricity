package com.dooji.electricity.block;

import com.dooji.electricity.api.power.ConductorSpec;
import com.dooji.electricity.main.registry.ConductorCatalog;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An overhead line conductor laid on the ground.
 *
 * The same conductor a player strings between two towers, lying down: same radius, same bundle spacing,
 * same texture, so a run that reaches a tower and carries on along the ground is one conductor and not
 * two. What it is *for* is the last few metres - out of a substation, into a kiosk, across a yard - which
 * is what a real installation does with a cable trench or a ground-level bus.
 *
 * Connects along its own axes like the string cable does, with a purpose-made piece per pattern: a run, a
 * bend on a wide radius, a dead-end compression clamp, a parallel-groove clamp where a third leg joins,
 * and a length lying loose. Shapes printed by {@code tools/gen_conductor_models.py --java}, which also
 * proves the block still declares them.
 */
public class GroundConductorBlock extends Block implements EntityBlock {
	public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
	public static final BooleanProperty EAST = BlockStateProperties.EAST;
	public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
	public static final BooleanProperty WEST = BlockStateProperties.WEST;

	/** The order gen_conductor_models numbers the sides in, which is the bitmask's own order. */
	private static final Direction[] MASK_ORDER = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};



	private static final Map<Integer, VoxelShape> ABC_HUBS = Map.ofEntries(
			Map.entry(0b0000, Block.box(7.12, 0.00, 3.00, 8.88, 1.76, 13.00)),
			Map.entry(0b0001, Block.box(6.45, 0.00, 3.92, 9.55, 3.10, 11.45)),
			Map.entry(0b0010, Block.box(4.55, 0.00, 6.45, 12.08, 3.10, 9.55)),
			Map.entry(0b0011, Block.box(7.12, 0.00, 3.72, 12.28, 1.76, 8.88)),
			Map.entry(0b0100, Block.box(6.45, 0.00, 4.55, 9.55, 3.10, 12.08)),
			Map.entry(0b0101, Block.box(7.12, 0.00, 4.60, 8.88, 1.76, 11.40)),
			Map.entry(0b0110, Block.box(7.12, 0.00, 7.12, 12.28, 1.76, 12.28)),
			Map.entry(0b0111, Block.box(6.10, 0.00, 5.40, 10.60, 2.36, 10.60)),
			Map.entry(0b1000, Block.box(3.92, 0.00, 6.45, 11.45, 3.10, 9.55)),
			Map.entry(0b1001, Block.box(3.72, 0.00, 3.72, 8.88, 1.76, 8.88)),
			Map.entry(0b1010, Block.box(4.60, 0.00, 7.12, 11.40, 1.76, 8.88)),
			Map.entry(0b1011, Block.box(5.40, 0.00, 5.40, 10.60, 2.36, 9.90)),
			Map.entry(0b1100, Block.box(3.72, 0.00, 7.12, 8.88, 1.76, 12.28)),
			Map.entry(0b1101, Block.box(5.40, 0.00, 5.40, 9.90, 2.36, 10.60)),
			Map.entry(0b1110, Block.box(5.40, 0.00, 6.10, 10.60, 2.36, 10.60)),
			Map.entry(0b1111, Block.box(5.40, 0.00, 5.40, 10.60, 2.36, 10.60)));

	private static final Map<Direction, VoxelShape> ABC_ARMS = Map.of(
			Direction.NORTH, Block.box(7.12, 0.00, 0.00, 8.88, 1.76, 4.60),
			Direction.EAST, Block.box(11.40, 0.00, 7.12, 16.00, 1.76, 8.88),
			Direction.SOUTH, Block.box(7.12, 0.00, 11.40, 8.88, 1.76, 16.00),
			Direction.WEST, Block.box(0.00, 0.00, 7.12, 4.60, 1.76, 8.88));

	private static final Map<Integer, VoxelShape> MV_HUBS = Map.ofEntries(
			Map.entry(0b0000, Block.box(7.49, 0.00, 3.00, 8.51, 1.02, 13.00)),
			Map.entry(0b0001, Shapes.or(Block.box(7.49, 0.00, 4.29, 8.51, 2.06, 8.20),
					Block.box(6.45, 0.00, 8.20, 9.55, 3.10, 11.45))),
			Map.entry(0b0010, Shapes.or(Block.box(7.80, 0.00, 7.49, 11.71, 2.06, 8.51),
					Block.box(4.55, 0.00, 6.45, 7.80, 3.10, 9.55))),
			Map.entry(0b0011, Block.box(7.49, 0.00, 4.09, 11.91, 1.02, 8.51)),
			Map.entry(0b0100, Shapes.or(Block.box(7.49, 0.00, 7.80, 8.51, 2.06, 11.71),
					Block.box(6.45, 0.00, 4.55, 9.55, 3.10, 7.80))),
			Map.entry(0b0101, Block.box(7.49, 0.00, 4.60, 8.51, 1.02, 11.40)),
			Map.entry(0b0110, Block.box(7.49, 0.00, 7.49, 11.91, 1.02, 11.91)),
			Map.entry(0b0111, Block.box(6.10, 0.00, 5.40, 10.60, 1.62, 10.60)),
			Map.entry(0b1000, Shapes.or(Block.box(4.29, 0.00, 7.49, 8.20, 2.06, 8.51),
					Block.box(8.20, 0.00, 6.45, 11.45, 3.10, 9.55))),
			Map.entry(0b1001, Block.box(4.09, 0.00, 4.09, 8.51, 1.02, 8.51)),
			Map.entry(0b1010, Block.box(4.60, 0.00, 7.49, 11.40, 1.02, 8.51)),
			Map.entry(0b1011, Block.box(5.40, 0.00, 5.40, 10.60, 1.62, 9.90)),
			Map.entry(0b1100, Block.box(4.09, 0.00, 7.49, 8.51, 1.02, 11.91)),
			Map.entry(0b1101, Block.box(5.40, 0.00, 5.40, 9.90, 1.62, 10.60)),
			Map.entry(0b1110, Block.box(5.40, 0.00, 6.10, 10.60, 1.62, 10.60)),
			Map.entry(0b1111, Block.box(5.40, 0.00, 5.40, 10.60, 1.62, 10.60)));

	private static final Map<Direction, VoxelShape> MV_ARMS = Map.of(
			Direction.NORTH, Block.box(7.49, 0.00, 0.00, 8.51, 1.02, 4.60),
			Direction.EAST, Block.box(11.40, 0.00, 7.49, 16.00, 1.02, 8.51),
			Direction.SOUTH, Block.box(7.49, 0.00, 11.40, 8.51, 1.02, 16.00),
			Direction.WEST, Block.box(0.00, 0.00, 7.49, 4.60, 1.02, 8.51));

	private static final Map<Integer, VoxelShape> HV_HUBS = Map.ofEntries(
			Map.entry(0b0000, Block.box(6.98, 0.00, 3.00, 9.02, 2.03, 13.00)),
			Map.entry(0b0001, Block.box(4.75, 0.00, 4.38, 11.26, 6.51, 11.45)),
			Map.entry(0b0010, Block.box(4.55, 0.00, 4.75, 11.62, 6.51, 11.26)),
			Map.entry(0b0011, Block.box(6.98, 0.00, 4.18, 11.82, 2.03, 9.02)),
			Map.entry(0b0100, Block.box(4.74, 0.00, 4.55, 11.25, 6.51, 11.62)),
			Map.entry(0b0101, Block.box(6.98, 0.00, 4.60, 9.02, 2.03, 11.40)),
			Map.entry(0b0110, Block.box(6.98, 0.00, 6.98, 11.82, 2.03, 11.82)),
			Map.entry(0b0111, Block.box(6.10, 0.00, 5.40, 10.60, 2.63, 10.60)),
			Map.entry(0b1000, Block.box(4.38, 0.00, 4.74, 11.45, 6.51, 11.25)),
			Map.entry(0b1001, Block.box(4.18, 0.00, 4.18, 9.02, 2.03, 9.02)),
			Map.entry(0b1010, Block.box(4.60, 0.00, 6.98, 11.40, 2.03, 9.02)),
			Map.entry(0b1011, Block.box(5.40, 0.00, 5.40, 10.60, 2.63, 9.90)),
			Map.entry(0b1100, Block.box(4.18, 0.00, 6.98, 9.02, 2.03, 11.82)),
			Map.entry(0b1101, Block.box(5.40, 0.00, 5.40, 9.90, 2.63, 10.60)),
			Map.entry(0b1110, Block.box(5.40, 0.00, 6.10, 10.60, 2.63, 10.60)),
			Map.entry(0b1111, Block.box(5.40, 0.00, 5.40, 10.60, 2.63, 10.60)));

	private static final Map<Direction, VoxelShape> HV_ARMS = Map.of(
			Direction.NORTH, Block.box(6.98, 0.00, 0.00, 9.02, 2.03, 4.60),
			Direction.EAST, Block.box(11.40, 0.00, 6.98, 16.00, 2.03, 9.02),
			Direction.SOUTH, Block.box(6.98, 0.00, 11.40, 9.02, 2.03, 16.00),
			Direction.WEST, Block.box(0.00, 0.00, 6.98, 4.60, 2.03, 9.02));

	private final ConductorSpec spec;

	public GroundConductorBlock(Properties properties, ConductorSpec spec) {
		super(properties.sound(SoundType.CHAIN).strength(1.0f, 4.0f).noOcclusion());
		this.spec = spec;
		registerDefaultState(defaultBlockState().setValue(NORTH, false).setValue(EAST, false)
				.setValue(SOUTH, false).setValue(WEST, false));
	}

	public ConductorSpec spec() {
		return spec;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST);
	}

	private static Map<Direction, BooleanProperty> sides() {
		Map<Direction, BooleanProperty> map = new EnumMap<>(Direction.class);
		map.put(Direction.NORTH, NORTH);
		map.put(Direction.EAST, EAST);
		map.put(Direction.SOUTH, SOUTH);
		map.put(Direction.WEST, WEST);
		return map;
	}

	private static final Map<Direction, BooleanProperty> SIDES = sides();

	/** Which table this conductor takes: the three are three different objects, not one at three sizes. */
	private Map<Integer, VoxelShape> hubs() {
		if (spec.id().equals(ConductorCatalog.ABC_70.id())) return ABC_HUBS;
		if (spec.id().equals(ConductorCatalog.AAAC_228.id())) return MV_HUBS;

		return HV_HUBS;
	}

	private Map<Direction, VoxelShape> arms() {
		if (spec.id().equals(ConductorCatalog.ABC_70.id())) return ABC_ARMS;
		if (spec.id().equals(ConductorCatalog.AAAC_228.id())) return MV_ARMS;

		return HV_ARMS;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		int mask = 0;
		VoxelShape shape = Shapes.empty();
		for (int bit = 0; bit < MASK_ORDER.length; bit++) {
			if (state.getValue(SIDES.get(MASK_ORDER[bit]))) {
				mask |= 1 << bit;
				shape = Shapes.or(shape, arms().get(MASK_ORDER[bit]));
			}
		}

		VoxelShape hub = hubs().get(mask);
		return hub == null ? shape : Shapes.or(shape, hub);
	}

	/** Solid, like the string cable: it is ankle-high, so a player steps onto it rather than through it. */
	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	/** It needs the ground under it, the way a run of conductor pulled along a yard does. */
	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return connected(context.getLevel(), context.getClickedPos(), defaultBlockState());
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		if (!canSurvive(state, level, pos)) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		if (!direction.getAxis().isHorizontal()) return state;

		return state.setValue(SIDES.get(direction), joins(level, pos, direction));
	}

	private BlockState connected(LevelReader level, BlockPos pos, BlockState state) {
		for (Direction side : MASK_ORDER) {
			state = state.setValue(SIDES.get(side), joins(level, pos, side));
		}

		return state;
	}

	/**
	 * Whether this joins whatever is on that side.
	 *
	 * Only to the same conductor, and that is the point of the three being three blocks: a 1 kV street
	 * bundle does not splice onto a 400 kV transmission phase, and a run that changes conductor halfway is
	 * a run somebody would have to explain.
	 */
	/** Every run carries a fitting, so a span may be anchored to one: see GroundConductorBlockEntity. */
	@Nullable @Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GroundConductorBlockEntity(pos, state);
	}

	@Override
	public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && !level.isClientSide
				&& level instanceof net.minecraft.server.level.ServerLevel serverLevel
				&& level.getBlockEntity(pos) instanceof GroundConductorBlockEntity run) {
			com.dooji.electricity.main.Electricity.wireManager.removeConnectionsForInsulators(serverLevel, run.getInsulatorIds());
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	private boolean joins(LevelReader level, BlockPos pos, Direction side) {
		BlockState there = level.getBlockState(pos.relative(side));
		return there.getBlock() instanceof GroundConductorBlock other && other.spec.id().equals(spec.id());
	}
}
