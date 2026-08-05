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
			Map.entry(0b0001, Shapes.or(Block.box(7.12, 0.00, 3.92, 8.88, 2.43, 7.88),
					Block.box(6.45, 0.00, 8.20, 9.55, 3.10, 9.50),
					Block.box(7.02, 0.57, 10.05, 8.98, 2.53, 11.45),
					Block.box(6.95, 0.50, 9.50, 9.05, 2.60, 10.05),
					Block.box(7.12, 0.67, 7.00, 8.88, 2.43, 8.20))),
			Map.entry(0b0010, Shapes.or(Block.box(8.12, 0.00, 7.12, 12.08, 2.43, 8.88),
					Block.box(6.50, 0.00, 6.45, 7.80, 3.10, 9.55),
					Block.box(4.55, 0.57, 7.02, 5.95, 2.53, 8.98),
					Block.box(5.95, 0.50, 6.95, 6.50, 2.60, 9.05),
					Block.box(7.80, 0.67, 7.12, 9.00, 2.43, 8.88))),
			Map.entry(0b0011, Shapes.or(Block.box(8.12, 0.00, 6.12, 10.28, 1.76, 8.23),
					Block.box(7.77, 0.00, 5.72, 9.88, 1.76, 7.88),
					Block.box(7.49, 0.00, 5.26, 9.53, 1.76, 7.48),
					Block.box(8.52, 0.00, 6.47, 10.74, 1.76, 8.51),
					Block.box(8.98, 0.00, 6.75, 11.23, 1.76, 8.71),
					Block.box(7.29, 0.00, 4.77, 9.25, 1.76, 7.02),
					Block.box(9.47, 0.00, 6.95, 11.75, 1.76, 8.84),
					Block.box(7.16, 0.00, 4.25, 9.05, 1.76, 6.53),
					Block.box(7.12, 0.00, 3.72, 8.92, 1.76, 6.01),
					Block.box(9.99, 0.00, 7.08, 12.28, 1.76, 8.88))),
			Map.entry(0b0100, Shapes.or(Block.box(7.12, 0.00, 8.12, 8.88, 2.43, 12.08),
					Block.box(6.45, 0.00, 6.50, 9.55, 3.10, 7.80),
					Block.box(7.02, 0.57, 4.55, 8.98, 2.53, 5.95),
					Block.box(6.95, 0.50, 5.95, 9.05, 2.60, 6.50),
					Block.box(7.12, 0.67, 7.80, 8.88, 2.43, 9.00))),
			Map.entry(0b0101, Block.box(7.12, 0.00, 4.60, 8.88, 1.76, 11.40)),
			Map.entry(0b0110, Shapes.or(Block.box(7.77, 0.00, 8.12, 9.88, 1.76, 10.28),
					Block.box(8.12, 0.00, 7.77, 10.28, 1.76, 9.88),
					Block.box(8.52, 0.00, 7.49, 10.74, 1.76, 9.53),
					Block.box(7.49, 0.00, 8.52, 9.53, 1.76, 10.74),
					Block.box(7.29, 0.00, 8.98, 9.25, 1.76, 11.23),
					Block.box(8.98, 0.00, 7.29, 11.23, 1.76, 9.25),
					Block.box(9.47, 0.00, 7.16, 11.75, 1.76, 9.05),
					Block.box(7.16, 0.00, 9.47, 9.05, 1.76, 11.75),
					Block.box(9.99, 0.00, 7.12, 12.28, 1.76, 8.92),
					Block.box(7.12, 0.00, 9.99, 8.92, 1.76, 12.28))),
			Map.entry(0b0111, Shapes.or(Block.box(6.10, 0.00, 6.10, 9.90, 2.36, 9.90),
					Block.box(6.88, 0.00, 5.40, 9.12, 2.00, 6.10),
					Block.box(9.90, 0.00, 6.88, 10.60, 2.00, 9.12),
					Block.box(6.88, 0.00, 9.90, 9.12, 2.00, 10.60))),
			Map.entry(0b1000, Shapes.or(Block.box(3.92, 0.00, 7.12, 7.88, 2.43, 8.88),
					Block.box(8.20, 0.00, 6.45, 9.50, 3.10, 9.55),
					Block.box(10.05, 0.57, 7.02, 11.45, 2.53, 8.98),
					Block.box(9.50, 0.50, 6.95, 10.05, 2.60, 9.05),
					Block.box(7.00, 0.67, 7.12, 8.20, 2.43, 8.88))),
			Map.entry(0b1001, Shapes.or(Block.box(6.12, 0.00, 5.72, 8.23, 1.76, 7.88),
					Block.box(5.72, 0.00, 6.12, 7.88, 1.76, 8.23),
					Block.box(5.26, 0.00, 6.47, 7.48, 1.76, 8.51),
					Block.box(6.47, 0.00, 5.26, 8.51, 1.76, 7.48),
					Block.box(6.75, 0.00, 4.77, 8.71, 1.76, 7.02),
					Block.box(4.77, 0.00, 6.75, 7.02, 1.76, 8.71),
					Block.box(4.25, 0.00, 6.95, 6.53, 1.76, 8.84),
					Block.box(6.95, 0.00, 4.25, 8.84, 1.76, 6.53),
					Block.box(3.72, 0.00, 7.08, 6.01, 1.76, 8.88),
					Block.box(7.08, 0.00, 3.72, 8.88, 1.76, 6.01))),
			Map.entry(0b1010, Block.box(4.60, 0.00, 7.12, 11.40, 1.76, 8.88)),
			Map.entry(0b1011, Shapes.or(Block.box(6.10, 0.00, 6.10, 9.90, 2.36, 9.90),
					Block.box(5.40, 0.00, 6.88, 6.10, 2.00, 9.12),
					Block.box(9.90, 0.00, 6.88, 10.60, 2.00, 9.12),
					Block.box(6.88, 0.00, 5.40, 9.12, 2.00, 6.10))),
			Map.entry(0b1100, Shapes.or(Block.box(6.12, 0.00, 8.12, 8.23, 1.76, 10.28),
					Block.box(5.72, 0.00, 7.77, 7.88, 1.76, 9.88),
					Block.box(6.47, 0.00, 8.52, 8.51, 1.76, 10.74),
					Block.box(5.26, 0.00, 7.49, 7.48, 1.76, 9.53),
					Block.box(6.75, 0.00, 8.98, 8.71, 1.76, 11.23),
					Block.box(4.77, 0.00, 7.29, 7.02, 1.76, 9.25),
					Block.box(6.95, 0.00, 9.47, 8.84, 1.76, 11.75),
					Block.box(4.25, 0.00, 7.16, 6.53, 1.76, 9.05),
					Block.box(7.08, 0.00, 9.99, 8.88, 1.76, 12.28),
					Block.box(3.72, 0.00, 7.12, 6.01, 1.76, 8.92))),
			Map.entry(0b1101, Shapes.or(Block.box(6.10, 0.00, 6.10, 9.90, 2.36, 9.90),
					Block.box(6.88, 0.00, 9.90, 9.12, 2.00, 10.60),
					Block.box(5.40, 0.00, 6.88, 6.10, 2.00, 9.12),
					Block.box(6.88, 0.00, 5.40, 9.12, 2.00, 6.10))),
			Map.entry(0b1110, Shapes.or(Block.box(6.10, 0.00, 6.10, 9.90, 2.36, 9.90),
					Block.box(9.90, 0.00, 6.88, 10.60, 2.00, 9.12),
					Block.box(6.88, 0.00, 9.90, 9.12, 2.00, 10.60),
					Block.box(5.40, 0.00, 6.88, 6.10, 2.00, 9.12))),
			Map.entry(0b1111, Shapes.or(Block.box(6.10, 0.00, 6.10, 9.90, 2.36, 9.90),
					Block.box(6.88, 0.00, 5.40, 9.12, 2.00, 6.10),
					Block.box(5.40, 0.00, 6.88, 6.10, 2.00, 9.12),
					Block.box(9.90, 0.00, 6.88, 10.60, 2.00, 9.12),
					Block.box(6.88, 0.00, 9.90, 9.12, 2.00, 10.60))));

	private static final Map<Direction, VoxelShape> ABC_ARMS = Map.of(
			Direction.NORTH, Block.box(7.12, 0.00, 0.00, 8.88, 1.76, 4.60),
			Direction.EAST, Block.box(11.40, 0.00, 7.12, 16.00, 1.76, 8.88),
			Direction.SOUTH, Block.box(7.12, 0.00, 11.40, 8.88, 1.76, 16.00),
			Direction.WEST, Block.box(0.00, 0.00, 7.12, 4.60, 1.76, 8.88));

	private static final Map<Integer, VoxelShape> MV_HUBS = Map.ofEntries(
			Map.entry(0b0000, Block.box(7.49, 0.00, 3.00, 8.51, 1.02, 13.00)),
			Map.entry(0b0001, Shapes.or(Block.box(6.45, 0.00, 8.20, 9.55, 3.10, 9.50),
					Block.box(7.49, 0.00, 4.29, 8.51, 2.06, 7.51),
					Block.box(7.10, 0.65, 10.05, 8.90, 2.45, 11.45),
					Block.box(6.95, 0.50, 9.50, 9.05, 2.60, 10.05),
					Block.box(7.49, 1.04, 7.00, 8.51, 2.06, 8.20))),
			Map.entry(0b0010, Shapes.or(Block.box(6.50, 0.00, 6.45, 7.80, 3.10, 9.55),
					Block.box(8.49, 0.00, 7.49, 11.71, 2.06, 8.51),
					Block.box(4.55, 0.65, 7.10, 5.95, 2.45, 8.90),
					Block.box(5.95, 0.50, 6.95, 6.50, 2.60, 9.05),
					Block.box(7.80, 1.04, 7.49, 9.00, 2.06, 8.51))),
			Map.entry(0b0011, Shapes.or(Block.box(8.48, 0.00, 6.49, 9.91, 1.02, 7.86),
					Block.box(8.14, 0.00, 6.09, 9.51, 1.02, 7.52),
					Block.box(7.86, 0.00, 5.63, 9.16, 1.02, 7.11),
					Block.box(8.89, 0.00, 6.84, 10.37, 1.02, 8.14),
					Block.box(9.34, 0.00, 7.12, 10.86, 1.02, 8.35),
					Block.box(7.65, 0.00, 5.14, 8.88, 1.02, 6.66),
					Block.box(7.53, 0.00, 4.62, 8.68, 1.02, 6.16),
					Block.box(9.84, 0.00, 7.32, 11.38, 1.02, 8.47),
					Block.box(7.49, 0.00, 4.09, 8.55, 1.02, 5.64),
					Block.box(10.36, 0.00, 7.45, 11.91, 1.02, 8.51))),
			Map.entry(0b0100, Shapes.or(Block.box(6.45, 0.00, 6.50, 9.55, 3.10, 7.80),
					Block.box(7.49, 0.00, 8.49, 8.51, 2.06, 11.71),
					Block.box(7.10, 0.65, 4.55, 8.90, 2.45, 5.95),
					Block.box(6.95, 0.50, 5.95, 9.05, 2.60, 6.50),
					Block.box(7.49, 1.04, 7.80, 8.51, 2.06, 9.00))),
			Map.entry(0b0101, Block.box(7.49, 0.00, 4.60, 8.51, 1.02, 11.40)),
			Map.entry(0b0110, Shapes.or(Block.box(8.14, 0.00, 8.48, 9.51, 1.02, 9.91),
					Block.box(8.48, 0.00, 8.14, 9.91, 1.02, 9.51),
					Block.box(8.89, 0.00, 7.86, 10.37, 1.02, 9.16),
					Block.box(7.86, 0.00, 8.89, 9.16, 1.02, 10.37),
					Block.box(7.65, 0.00, 9.34, 8.88, 1.02, 10.86),
					Block.box(9.34, 0.00, 7.65, 10.86, 1.02, 8.88),
					Block.box(9.84, 0.00, 7.53, 11.38, 1.02, 8.68),
					Block.box(7.53, 0.00, 9.84, 8.68, 1.02, 11.38),
					Block.box(10.36, 0.00, 7.49, 11.91, 1.02, 8.55),
					Block.box(7.49, 0.00, 10.36, 8.55, 1.02, 11.91))),
			Map.entry(0b0111, Shapes.or(Block.box(6.10, 0.00, 6.10, 9.90, 1.62, 9.90),
					Block.box(7.25, 0.00, 5.40, 8.75, 1.26, 6.10),
					Block.box(9.90, 0.00, 7.25, 10.60, 1.26, 8.75),
					Block.box(7.25, 0.00, 9.90, 8.75, 1.26, 10.60))),
			Map.entry(0b1000, Shapes.or(Block.box(8.20, 0.00, 6.45, 9.50, 3.10, 9.55),
					Block.box(4.29, 0.00, 7.49, 7.51, 2.06, 8.51),
					Block.box(10.05, 0.65, 7.10, 11.45, 2.45, 8.90),
					Block.box(9.50, 0.50, 6.95, 10.05, 2.60, 9.05),
					Block.box(7.00, 1.04, 7.49, 8.20, 2.06, 8.51))),
			Map.entry(0b1001, Shapes.or(Block.box(6.49, 0.00, 6.09, 7.86, 1.02, 7.52),
					Block.box(6.09, 0.00, 6.49, 7.52, 1.02, 7.86),
					Block.box(5.63, 0.00, 6.84, 7.11, 1.02, 8.14),
					Block.box(6.84, 0.00, 5.63, 8.14, 1.02, 7.11),
					Block.box(7.12, 0.00, 5.14, 8.35, 1.02, 6.66),
					Block.box(5.14, 0.00, 7.12, 6.66, 1.02, 8.35),
					Block.box(4.62, 0.00, 7.32, 6.16, 1.02, 8.47),
					Block.box(7.32, 0.00, 4.62, 8.47, 1.02, 6.16),
					Block.box(4.09, 0.00, 7.45, 5.64, 1.02, 8.51),
					Block.box(7.45, 0.00, 4.09, 8.51, 1.02, 5.64))),
			Map.entry(0b1010, Block.box(4.60, 0.00, 7.49, 11.40, 1.02, 8.51)),
			Map.entry(0b1011, Shapes.or(Block.box(6.10, 0.00, 6.10, 9.90, 1.62, 9.90),
					Block.box(5.40, 0.00, 7.25, 6.10, 1.26, 8.75),
					Block.box(7.25, 0.00, 5.40, 8.75, 1.26, 6.10),
					Block.box(9.90, 0.00, 7.25, 10.60, 1.26, 8.75))),
			Map.entry(0b1100, Shapes.or(Block.box(6.09, 0.00, 8.14, 7.52, 1.02, 9.51),
					Block.box(6.49, 0.00, 8.48, 7.86, 1.02, 9.91),
					Block.box(6.84, 0.00, 8.89, 8.14, 1.02, 10.37),
					Block.box(5.63, 0.00, 7.86, 7.11, 1.02, 9.16),
					Block.box(5.14, 0.00, 7.65, 6.66, 1.02, 8.88),
					Block.box(7.12, 0.00, 9.34, 8.35, 1.02, 10.86),
					Block.box(7.32, 0.00, 9.84, 8.47, 1.02, 11.38),
					Block.box(4.62, 0.00, 7.53, 6.16, 1.02, 8.68),
					Block.box(7.45, 0.00, 10.36, 8.51, 1.02, 11.91),
					Block.box(4.09, 0.00, 7.49, 5.64, 1.02, 8.55))),
			Map.entry(0b1101, Shapes.or(Block.box(6.10, 0.00, 6.10, 9.90, 1.62, 9.90),
					Block.box(7.25, 0.00, 9.90, 8.75, 1.26, 10.60),
					Block.box(5.40, 0.00, 7.25, 6.10, 1.26, 8.75),
					Block.box(7.25, 0.00, 5.40, 8.75, 1.26, 6.10))),
			Map.entry(0b1110, Shapes.or(Block.box(6.10, 0.00, 6.10, 9.90, 1.62, 9.90),
					Block.box(9.90, 0.00, 7.25, 10.60, 1.26, 8.75),
					Block.box(5.40, 0.00, 7.25, 6.10, 1.26, 8.75),
					Block.box(7.25, 0.00, 9.90, 8.75, 1.26, 10.60))),
			Map.entry(0b1111, Shapes.or(Block.box(6.10, 0.00, 6.10, 9.90, 1.62, 9.90),
					Block.box(7.25, 0.00, 5.40, 8.75, 1.26, 6.10),
					Block.box(5.40, 0.00, 7.25, 6.10, 1.26, 8.75),
					Block.box(9.90, 0.00, 7.25, 10.60, 1.26, 8.75),
					Block.box(7.25, 0.00, 9.90, 8.75, 1.26, 10.60))));

	private static final Map<Direction, VoxelShape> MV_ARMS = Map.of(
			Direction.NORTH, Block.box(7.49, 0.00, 0.00, 8.51, 1.02, 4.60),
			Direction.EAST, Block.box(11.40, 0.00, 7.49, 16.00, 1.02, 8.51),
			Direction.SOUTH, Block.box(7.49, 0.00, 11.40, 8.51, 1.02, 16.00),
			Direction.WEST, Block.box(0.00, 0.00, 7.49, 4.60, 1.02, 8.51));

	private static final Map<Integer, VoxelShape> HV_HUBS = Map.ofEntries(
			Map.entry(0b0000, Shapes.or(Block.box(6.98, 0.00, 3.00, 7.82, 0.83, 13.00),
					Block.box(8.18, 0.00, 3.00, 9.02, 0.83, 13.00),
					Block.box(6.98, 1.20, 3.00, 7.82, 2.03, 13.00),
					Block.box(8.18, 1.20, 3.00, 9.02, 2.03, 13.00),
					Block.box(7.22, 0.86, 4.84, 8.78, 1.18, 5.16),
					Block.box(7.22, 0.86, 10.84, 8.78, 1.18, 11.16),
					Block.box(7.22, 0.42, 4.84, 7.58, 1.62, 5.16),
					Block.box(8.42, 0.42, 4.84, 8.78, 1.62, 5.16),
					Block.box(7.22, 0.42, 10.84, 7.58, 1.62, 11.16),
					Block.box(8.42, 0.42, 10.84, 8.78, 1.62, 11.16))),
			Map.entry(0b0001, Shapes.or(Block.box(5.88, 1.20, 4.38, 7.82, 5.38, 7.42),
					Block.box(8.18, 1.20, 4.38, 10.12, 5.38, 7.42),
					Block.box(8.15, 0.00, 8.20, 11.26, 3.10, 9.50),
					Block.box(8.15, 3.41, 8.20, 11.26, 6.51, 9.50),
					Block.box(4.75, 0.00, 8.20, 7.84, 3.10, 9.50),
					Block.box(4.75, 3.41, 8.20, 7.84, 6.51, 9.50),
					Block.box(5.88, 0.00, 4.38, 7.82, 1.97, 7.42),
					Block.box(8.18, 0.00, 4.38, 10.12, 1.97, 7.42),
					Block.box(5.39, 0.65, 10.05, 7.20, 2.45, 11.45),
					Block.box(8.80, 0.65, 10.05, 10.61, 2.45, 11.45),
					Block.box(5.39, 4.06, 10.05, 7.20, 5.86, 11.45),
					Block.box(8.80, 4.06, 10.05, 10.61, 5.86, 11.45),
					Block.box(8.65, 0.50, 9.50, 10.76, 2.60, 10.05),
					Block.box(8.65, 3.91, 9.50, 10.76, 6.01, 10.05),
					Block.box(5.25, 0.50, 9.50, 7.34, 2.60, 10.05),
					Block.box(5.25, 3.91, 9.50, 7.34, 6.01, 10.05),
					Block.box(5.88, 4.54, 7.00, 6.71, 5.38, 8.20),
					Block.box(9.29, 4.54, 7.00, 10.12, 5.38, 8.20),
					Block.box(5.88, 1.13, 7.00, 6.71, 1.97, 8.20),
					Block.box(9.29, 1.13, 7.00, 10.12, 1.97, 8.20),
					Block.box(7.22, 0.86, 5.64, 8.78, 1.18, 5.96))),
			Map.entry(0b0010, Shapes.or(Block.box(8.58, 1.20, 5.88, 11.62, 5.38, 7.82),
					Block.box(8.58, 1.20, 8.18, 11.62, 5.38, 10.12),
					Block.box(6.50, 0.00, 8.15, 7.80, 3.10, 11.26),
					Block.box(6.50, 3.41, 8.15, 7.80, 6.51, 11.26),
					Block.box(6.50, 0.00, 4.75, 7.80, 3.10, 7.84),
					Block.box(6.50, 3.41, 4.75, 7.80, 6.51, 7.84),
					Block.box(8.58, 0.00, 5.88, 11.62, 1.97, 7.82),
					Block.box(8.58, 0.00, 8.18, 11.62, 1.97, 10.12),
					Block.box(4.55, 0.65, 5.39, 5.95, 2.45, 7.20),
					Block.box(4.55, 0.65, 8.80, 5.95, 2.45, 10.61),
					Block.box(4.55, 4.06, 5.39, 5.95, 5.86, 7.20),
					Block.box(4.55, 4.06, 8.80, 5.95, 5.86, 10.61),
					Block.box(5.95, 0.50, 8.65, 6.50, 2.60, 10.76),
					Block.box(5.95, 3.91, 8.65, 6.50, 6.01, 10.76),
					Block.box(5.95, 0.50, 5.25, 6.50, 2.60, 7.34),
					Block.box(5.95, 3.91, 5.25, 6.50, 6.01, 7.34),
					Block.box(7.80, 4.54, 5.88, 9.00, 5.38, 6.71),
					Block.box(7.80, 4.54, 9.29, 9.00, 5.38, 10.12),
					Block.box(7.80, 1.13, 5.88, 9.00, 1.97, 6.71),
					Block.box(7.80, 1.13, 9.29, 9.00, 1.97, 10.12),
					Block.box(10.04, 0.86, 7.22, 10.36, 1.18, 8.78))),
			Map.entry(0b0011, Shapes.or(Block.box(8.16, 0.00, 7.01, 9.46, 0.83, 8.25),
					Block.box(8.16, 1.20, 7.01, 9.46, 2.03, 8.25),
					Block.box(7.75, 0.00, 6.54, 8.99, 0.83, 7.84),
					Block.box(7.75, 1.20, 6.54, 8.99, 2.03, 7.84),
					Block.box(7.42, 0.00, 6.00, 8.58, 0.83, 7.37),
					Block.box(7.42, 1.20, 6.00, 8.58, 2.03, 7.37),
					Block.box(8.63, 0.00, 7.42, 10.00, 0.83, 8.58),
					Block.box(8.63, 1.20, 7.42, 10.00, 2.03, 8.58),
					Block.box(7.18, 0.00, 5.42, 8.25, 0.83, 6.83),
					Block.box(9.17, 0.00, 7.75, 10.58, 0.83, 8.82),
					Block.box(7.18, 1.20, 5.42, 8.25, 2.03, 6.83),
					Block.box(9.17, 1.20, 7.75, 10.58, 2.03, 8.82),
					Block.box(9.75, 0.00, 7.99, 11.19, 0.83, 8.97),
					Block.box(9.75, 1.20, 7.99, 11.19, 2.03, 8.97),
					Block.box(7.03, 0.00, 4.81, 8.01, 0.83, 6.25),
					Block.box(7.03, 1.20, 4.81, 8.01, 2.03, 6.25),
					Block.box(6.98, 0.00, 4.18, 7.87, 0.83, 5.64),
					Block.box(6.98, 1.20, 4.18, 7.87, 2.03, 5.64),
					Block.box(10.36, 0.00, 8.13, 11.82, 0.83, 9.02),
					Block.box(10.36, 1.20, 8.13, 11.82, 2.03, 9.02),
					Block.box(8.72, 0.00, 5.83, 9.84, 0.83, 7.00),
					Block.box(9.00, 0.00, 6.16, 10.17, 0.83, 7.28),
					Block.box(8.72, 1.20, 5.83, 9.84, 2.03, 7.00),
					Block.box(9.00, 1.20, 6.16, 10.17, 2.03, 7.28),
					Block.box(8.49, 0.00, 5.46, 9.55, 0.83, 6.66),
					Block.box(8.49, 1.20, 5.46, 9.55, 2.03, 6.66),
					Block.box(9.34, 0.00, 6.45, 10.54, 0.83, 7.51),
					Block.box(9.34, 1.20, 6.45, 10.54, 2.03, 7.51),
					Block.box(8.32, 0.00, 5.05, 9.32, 0.83, 6.29),
					Block.box(9.71, 0.00, 6.68, 10.95, 0.83, 7.68),
					Block.box(8.32, 1.20, 5.05, 9.32, 2.03, 6.29),
					Block.box(9.71, 1.20, 6.68, 10.95, 2.03, 7.68),
					Block.box(10.12, 0.00, 6.85, 11.38, 0.83, 7.78),
					Block.box(10.12, 1.20, 6.85, 11.38, 2.03, 7.78),
					Block.box(8.22, 0.00, 4.62, 9.15, 0.83, 5.88),
					Block.box(8.22, 1.20, 4.62, 9.15, 2.03, 5.88),
					Block.box(8.18, 0.00, 4.18, 9.05, 0.83, 5.45),
					Block.box(8.18, 1.20, 4.18, 9.05, 2.03, 5.45),
					Block.box(10.55, 0.00, 6.95, 11.82, 0.83, 7.82),
					Block.box(10.55, 1.20, 6.95, 11.82, 2.03, 7.82))),
			Map.entry(0b0100, Shapes.or(Block.box(8.18, 1.20, 8.58, 10.12, 5.38, 11.62),
					Block.box(5.88, 1.20, 8.58, 7.82, 5.38, 11.62),
					Block.box(4.74, 0.00, 6.50, 7.85, 3.10, 7.80),
					Block.box(4.74, 3.41, 6.50, 7.85, 6.51, 7.80),
					Block.box(8.16, 0.00, 6.50, 11.25, 3.10, 7.80),
					Block.box(8.16, 3.41, 6.50, 11.25, 6.51, 7.80),
					Block.box(8.18, 0.00, 8.58, 10.12, 1.97, 11.62),
					Block.box(5.88, 0.00, 8.58, 7.82, 1.97, 11.62),
					Block.box(8.80, 0.65, 4.55, 10.61, 2.45, 5.95),
					Block.box(5.39, 0.65, 4.55, 7.20, 2.45, 5.95),
					Block.box(8.80, 4.06, 4.55, 10.61, 5.86, 5.95),
					Block.box(5.39, 4.06, 4.55, 7.20, 5.86, 5.95),
					Block.box(5.24, 0.50, 5.95, 7.35, 2.60, 6.50),
					Block.box(5.24, 3.91, 5.95, 7.35, 6.01, 6.50),
					Block.box(8.66, 0.50, 5.95, 10.75, 2.60, 6.50),
					Block.box(8.66, 3.91, 5.95, 10.75, 6.01, 6.50),
					Block.box(9.29, 4.54, 7.80, 10.12, 5.38, 9.00),
					Block.box(5.88, 4.54, 7.80, 6.71, 5.38, 9.00),
					Block.box(9.29, 1.13, 7.80, 10.12, 1.97, 9.00),
					Block.box(5.88, 1.13, 7.80, 6.71, 1.97, 9.00),
					Block.box(7.22, 0.86, 10.04, 8.78, 1.18, 10.36))),
			Map.entry(0b0101, Shapes.or(Block.box(6.98, 0.00, 4.60, 7.82, 0.83, 11.40),
					Block.box(8.18, 0.00, 4.60, 9.02, 0.83, 11.40),
					Block.box(6.98, 1.20, 4.60, 7.82, 2.03, 11.40),
					Block.box(8.18, 1.20, 4.60, 9.02, 2.03, 11.40),
					Block.box(7.22, 0.86, 7.84, 8.78, 1.18, 8.16),
					Block.box(7.22, 0.42, 7.84, 7.58, 1.62, 8.16),
					Block.box(8.42, 0.42, 7.84, 8.78, 1.62, 8.16))),
			Map.entry(0b0110, Shapes.or(Block.box(7.75, 0.00, 8.16, 8.99, 0.83, 9.46),
					Block.box(7.75, 1.20, 8.16, 8.99, 2.03, 9.46),
					Block.box(8.16, 0.00, 7.75, 9.46, 0.83, 8.99),
					Block.box(8.16, 1.20, 7.75, 9.46, 2.03, 8.99),
					Block.box(8.63, 0.00, 7.42, 10.00, 0.83, 8.58),
					Block.box(8.63, 1.20, 7.42, 10.00, 2.03, 8.58),
					Block.box(7.42, 0.00, 8.63, 8.58, 0.83, 10.00),
					Block.box(7.42, 1.20, 8.63, 8.58, 2.03, 10.00),
					Block.box(9.17, 0.00, 7.18, 10.58, 0.83, 8.25),
					Block.box(7.18, 0.00, 9.17, 8.25, 0.83, 10.58),
					Block.box(9.17, 1.20, 7.18, 10.58, 2.03, 8.25),
					Block.box(7.18, 1.20, 9.17, 8.25, 2.03, 10.58),
					Block.box(9.75, 0.00, 7.03, 11.19, 0.83, 8.01),
					Block.box(7.03, 0.00, 9.75, 8.01, 0.83, 11.19),
					Block.box(9.75, 1.20, 7.03, 11.19, 2.03, 8.01),
					Block.box(7.03, 1.20, 9.75, 8.01, 2.03, 11.19),
					Block.box(10.36, 0.00, 6.98, 11.82, 0.83, 7.87),
					Block.box(6.98, 0.00, 10.36, 7.87, 0.83, 11.82),
					Block.box(10.36, 1.20, 6.98, 11.82, 2.03, 7.87),
					Block.box(6.98, 1.20, 10.36, 7.87, 2.03, 11.82),
					Block.box(9.00, 0.00, 8.72, 10.17, 0.83, 9.84),
					Block.box(9.00, 1.20, 8.72, 10.17, 2.03, 9.84),
					Block.box(8.72, 0.00, 9.00, 9.84, 0.83, 10.17),
					Block.box(8.72, 1.20, 9.00, 9.84, 2.03, 10.17),
					Block.box(9.34, 0.00, 8.49, 10.54, 0.83, 9.55),
					Block.box(9.34, 1.20, 8.49, 10.54, 2.03, 9.55),
					Block.box(8.49, 0.00, 9.34, 9.55, 0.83, 10.54),
					Block.box(8.49, 1.20, 9.34, 9.55, 2.03, 10.54),
					Block.box(9.71, 0.00, 8.32, 10.95, 0.83, 9.32),
					Block.box(8.32, 0.00, 9.71, 9.32, 0.83, 10.95),
					Block.box(9.71, 1.20, 8.32, 10.95, 2.03, 9.32),
					Block.box(8.32, 1.20, 9.71, 9.32, 2.03, 10.95),
					Block.box(8.22, 0.00, 10.12, 9.15, 0.83, 11.38),
					Block.box(8.22, 1.20, 10.12, 9.15, 2.03, 11.38),
					Block.box(10.12, 0.00, 8.22, 11.38, 0.83, 9.15),
					Block.box(10.12, 1.20, 8.22, 11.38, 2.03, 9.15),
					Block.box(10.55, 0.00, 8.18, 11.82, 0.83, 9.05),
					Block.box(10.55, 1.20, 8.18, 11.82, 2.03, 9.05),
					Block.box(8.18, 0.00, 10.55, 9.05, 0.83, 11.82),
					Block.box(8.18, 1.20, 10.55, 9.05, 2.03, 11.82))),
			Map.entry(0b0111, Shapes.or(Block.box(6.10, 0.00, 6.10, 9.90, 2.63, 9.90),
					Block.box(6.74, 0.96, 5.40, 8.06, 2.27, 6.10),
					Block.box(7.94, 0.96, 5.40, 9.26, 2.27, 6.10),
					Block.box(9.90, 0.96, 6.74, 10.60, 2.27, 8.06),
					Block.box(9.90, 0.96, 7.94, 10.60, 2.27, 9.26),
					Block.box(6.74, 0.96, 9.90, 8.06, 2.27, 10.60),
					Block.box(7.94, 0.96, 9.90, 9.26, 2.27, 10.60),
					Block.box(6.74, 0.00, 5.40, 8.06, 1.07, 6.10),
					Block.box(7.94, 0.00, 5.40, 9.26, 1.07, 6.10),
					Block.box(9.90, 0.00, 6.74, 10.60, 1.07, 8.06),
					Block.box(9.90, 0.00, 7.94, 10.60, 1.07, 9.26),
					Block.box(6.74, 0.00, 9.90, 8.06, 1.07, 10.60),
					Block.box(7.94, 0.00, 9.90, 9.26, 1.07, 10.60))),
			Map.entry(0b1000, Shapes.or(Block.box(4.38, 1.20, 8.18, 7.42, 5.38, 10.12),
					Block.box(4.38, 1.20, 5.88, 7.42, 5.38, 7.82),
					Block.box(8.20, 0.00, 4.74, 9.50, 3.10, 7.85),
					Block.box(8.20, 3.41, 4.74, 9.50, 6.51, 7.85),
					Block.box(8.20, 0.00, 8.16, 9.50, 3.10, 11.25),
					Block.box(8.20, 3.41, 8.16, 9.50, 6.51, 11.25),
					Block.box(4.38, 0.00, 8.18, 7.42, 1.97, 10.12),
					Block.box(4.38, 0.00, 5.88, 7.42, 1.97, 7.82),
					Block.box(10.05, 0.65, 8.80, 11.45, 2.45, 10.61),
					Block.box(10.05, 0.65, 5.39, 11.45, 2.45, 7.20),
					Block.box(10.05, 4.06, 8.80, 11.45, 5.86, 10.61),
					Block.box(10.05, 4.06, 5.39, 11.45, 5.86, 7.20),
					Block.box(9.50, 0.50, 5.24, 10.05, 2.60, 7.35),
					Block.box(9.50, 3.91, 5.24, 10.05, 6.01, 7.35),
					Block.box(9.50, 0.50, 8.66, 10.05, 2.60, 10.75),
					Block.box(9.50, 3.91, 8.66, 10.05, 6.01, 10.75),
					Block.box(7.00, 4.54, 9.29, 8.20, 5.38, 10.12),
					Block.box(7.00, 4.54, 5.88, 8.20, 5.38, 6.71),
					Block.box(7.00, 1.13, 9.29, 8.20, 1.97, 10.12),
					Block.box(7.00, 1.13, 5.88, 8.20, 1.97, 6.71),
					Block.box(5.64, 0.86, 7.22, 5.96, 1.18, 8.78))),
			Map.entry(0b1001, Shapes.or(Block.box(7.01, 0.00, 6.54, 8.25, 0.83, 7.84),
					Block.box(7.01, 1.20, 6.54, 8.25, 2.03, 7.84),
					Block.box(6.54, 0.00, 7.01, 7.84, 0.83, 8.25),
					Block.box(6.54, 1.20, 7.01, 7.84, 2.03, 8.25),
					Block.box(6.00, 0.00, 7.42, 7.37, 0.83, 8.58),
					Block.box(6.00, 1.20, 7.42, 7.37, 2.03, 8.58),
					Block.box(7.42, 0.00, 6.00, 8.58, 0.83, 7.37),
					Block.box(7.42, 1.20, 6.00, 8.58, 2.03, 7.37),
					Block.box(5.42, 0.00, 7.75, 6.83, 0.83, 8.82),
					Block.box(7.75, 0.00, 5.42, 8.82, 0.83, 6.83),
					Block.box(5.42, 1.20, 7.75, 6.83, 2.03, 8.82),
					Block.box(7.75, 1.20, 5.42, 8.82, 2.03, 6.83),
					Block.box(7.99, 0.00, 4.81, 8.97, 0.83, 6.25),
					Block.box(7.99, 1.20, 4.81, 8.97, 2.03, 6.25),
					Block.box(4.81, 0.00, 7.99, 6.25, 0.83, 8.97),
					Block.box(4.81, 1.20, 7.99, 6.25, 2.03, 8.97),
					Block.box(4.18, 0.00, 8.13, 5.64, 0.83, 9.02),
					Block.box(4.18, 1.20, 8.13, 5.64, 2.03, 9.02),
					Block.box(8.13, 0.00, 4.18, 9.02, 0.83, 5.64),
					Block.box(8.13, 1.20, 4.18, 9.02, 2.03, 5.64),
					Block.box(5.83, 0.00, 6.16, 7.00, 0.83, 7.28),
					Block.box(6.16, 0.00, 5.83, 7.28, 0.83, 7.00),
					Block.box(5.83, 1.20, 6.16, 7.00, 2.03, 7.28),
					Block.box(6.16, 1.20, 5.83, 7.28, 2.03, 7.00),
					Block.box(5.46, 0.00, 6.45, 6.66, 0.83, 7.51),
					Block.box(5.46, 1.20, 6.45, 6.66, 2.03, 7.51),
					Block.box(6.45, 0.00, 5.46, 7.51, 0.83, 6.66),
					Block.box(6.45, 1.20, 5.46, 7.51, 2.03, 6.66),
					Block.box(5.05, 0.00, 6.68, 6.29, 0.83, 7.68),
					Block.box(6.68, 0.00, 5.05, 7.68, 0.83, 6.29),
					Block.box(5.05, 1.20, 6.68, 6.29, 2.03, 7.68),
					Block.box(6.68, 1.20, 5.05, 7.68, 2.03, 6.29),
					Block.box(6.85, 0.00, 4.62, 7.78, 0.83, 5.88),
					Block.box(6.85, 1.20, 4.62, 7.78, 2.03, 5.88),
					Block.box(4.62, 0.00, 6.85, 5.88, 0.83, 7.78),
					Block.box(4.62, 1.20, 6.85, 5.88, 2.03, 7.78),
					Block.box(4.18, 0.00, 6.95, 5.45, 0.83, 7.82),
					Block.box(4.18, 1.20, 6.95, 5.45, 2.03, 7.82),
					Block.box(6.95, 0.00, 4.18, 7.82, 0.83, 5.45),
					Block.box(6.95, 1.20, 4.18, 7.82, 2.03, 5.45))),
			Map.entry(0b1010, Shapes.or(Block.box(4.60, 0.00, 6.98, 11.40, 0.83, 7.82),
					Block.box(4.60, 0.00, 8.18, 11.40, 0.83, 9.02),
					Block.box(4.60, 1.20, 6.98, 11.40, 2.03, 7.82),
					Block.box(4.60, 1.20, 8.18, 11.40, 2.03, 9.02),
					Block.box(7.84, 0.86, 7.22, 8.16, 1.18, 8.78),
					Block.box(7.84, 0.42, 7.22, 8.16, 1.62, 7.58),
					Block.box(7.84, 0.42, 8.42, 8.16, 1.62, 8.78))),
			Map.entry(0b1011, Shapes.or(Block.box(6.10, 0.00, 6.10, 9.90, 2.63, 9.90),
					Block.box(5.40, 0.96, 7.94, 6.10, 2.27, 9.26),
					Block.box(5.40, 0.96, 6.74, 6.10, 2.27, 8.06),
					Block.box(9.90, 0.96, 7.94, 10.60, 2.27, 9.26),
					Block.box(9.90, 0.96, 6.74, 10.60, 2.27, 8.06),
					Block.box(6.74, 0.96, 5.40, 8.06, 2.27, 6.10),
					Block.box(7.94, 0.96, 5.40, 9.26, 2.27, 6.10),
					Block.box(5.40, 0.00, 7.94, 6.10, 1.07, 9.26),
					Block.box(5.40, 0.00, 6.74, 6.10, 1.07, 8.06),
					Block.box(9.90, 0.00, 7.94, 10.60, 1.07, 9.26),
					Block.box(9.90, 0.00, 6.74, 10.60, 1.07, 8.06),
					Block.box(6.74, 0.00, 5.40, 8.06, 1.07, 6.10),
					Block.box(7.94, 0.00, 5.40, 9.26, 1.07, 6.10))),
			Map.entry(0b1100, Shapes.or(Block.box(6.54, 0.00, 7.75, 7.84, 0.83, 8.99),
					Block.box(6.54, 1.20, 7.75, 7.84, 2.03, 8.99),
					Block.box(7.01, 0.00, 8.16, 8.25, 0.83, 9.46),
					Block.box(7.01, 1.20, 8.16, 8.25, 2.03, 9.46),
					Block.box(7.42, 0.00, 8.63, 8.58, 0.83, 10.00),
					Block.box(7.42, 1.20, 8.63, 8.58, 2.03, 10.00),
					Block.box(6.00, 0.00, 7.42, 7.37, 0.83, 8.58),
					Block.box(6.00, 1.20, 7.42, 7.37, 2.03, 8.58),
					Block.box(7.75, 0.00, 9.17, 8.82, 0.83, 10.58),
					Block.box(5.42, 0.00, 7.18, 6.83, 0.83, 8.25),
					Block.box(7.75, 1.20, 9.17, 8.82, 2.03, 10.58),
					Block.box(5.42, 1.20, 7.18, 6.83, 2.03, 8.25),
					Block.box(7.99, 0.00, 9.75, 8.97, 0.83, 11.19),
					Block.box(4.81, 0.00, 7.03, 6.25, 0.83, 8.01),
					Block.box(7.99, 1.20, 9.75, 8.97, 2.03, 11.19),
					Block.box(4.81, 1.20, 7.03, 6.25, 2.03, 8.01),
					Block.box(8.13, 0.00, 10.36, 9.02, 0.83, 11.82),
					Block.box(4.18, 0.00, 6.98, 5.64, 0.83, 7.87),
					Block.box(8.13, 1.20, 10.36, 9.02, 2.03, 11.82),
					Block.box(4.18, 1.20, 6.98, 5.64, 2.03, 7.87),
					Block.box(6.16, 0.00, 9.00, 7.28, 0.83, 10.17),
					Block.box(6.16, 1.20, 9.00, 7.28, 2.03, 10.17),
					Block.box(5.83, 0.00, 8.72, 7.00, 0.83, 9.84),
					Block.box(5.83, 1.20, 8.72, 7.00, 2.03, 9.84),
					Block.box(6.45, 0.00, 9.34, 7.51, 0.83, 10.54),
					Block.box(6.45, 1.20, 9.34, 7.51, 2.03, 10.54),
					Block.box(5.46, 0.00, 8.49, 6.66, 0.83, 9.55),
					Block.box(5.46, 1.20, 8.49, 6.66, 2.03, 9.55),
					Block.box(6.68, 0.00, 9.71, 7.68, 0.83, 10.95),
					Block.box(5.05, 0.00, 8.32, 6.29, 0.83, 9.32),
					Block.box(6.68, 1.20, 9.71, 7.68, 2.03, 10.95),
					Block.box(5.05, 1.20, 8.32, 6.29, 2.03, 9.32),
					Block.box(4.62, 0.00, 8.22, 5.88, 0.83, 9.15),
					Block.box(4.62, 1.20, 8.22, 5.88, 2.03, 9.15),
					Block.box(6.85, 0.00, 10.12, 7.78, 0.83, 11.38),
					Block.box(6.85, 1.20, 10.12, 7.78, 2.03, 11.38),
					Block.box(6.95, 0.00, 10.55, 7.82, 0.83, 11.82),
					Block.box(6.95, 1.20, 10.55, 7.82, 2.03, 11.82),
					Block.box(4.18, 0.00, 8.18, 5.45, 0.83, 9.05),
					Block.box(4.18, 1.20, 8.18, 5.45, 2.03, 9.05))),
			Map.entry(0b1101, Shapes.or(Block.box(6.10, 0.00, 6.10, 9.90, 2.63, 9.90),
					Block.box(7.94, 0.96, 9.90, 9.26, 2.27, 10.60),
					Block.box(6.74, 0.96, 9.90, 8.06, 2.27, 10.60),
					Block.box(5.40, 0.96, 7.94, 6.10, 2.27, 9.26),
					Block.box(5.40, 0.96, 6.74, 6.10, 2.27, 8.06),
					Block.box(7.94, 0.96, 5.40, 9.26, 2.27, 6.10),
					Block.box(6.74, 0.96, 5.40, 8.06, 2.27, 6.10),
					Block.box(7.94, 0.00, 9.90, 9.26, 1.07, 10.60),
					Block.box(6.74, 0.00, 9.90, 8.06, 1.07, 10.60),
					Block.box(5.40, 0.00, 7.94, 6.10, 1.07, 9.26),
					Block.box(5.40, 0.00, 6.74, 6.10, 1.07, 8.06),
					Block.box(7.94, 0.00, 5.40, 9.26, 1.07, 6.10),
					Block.box(6.74, 0.00, 5.40, 8.06, 1.07, 6.10))),
			Map.entry(0b1110, Shapes.or(Block.box(6.10, 0.00, 6.10, 9.90, 2.63, 9.90),
					Block.box(9.90, 0.96, 6.74, 10.60, 2.27, 8.06),
					Block.box(9.90, 0.96, 7.94, 10.60, 2.27, 9.26),
					Block.box(7.94, 0.96, 9.90, 9.26, 2.27, 10.60),
					Block.box(6.74, 0.96, 9.90, 8.06, 2.27, 10.60),
					Block.box(5.40, 0.96, 6.74, 6.10, 2.27, 8.06),
					Block.box(5.40, 0.96, 7.94, 6.10, 2.27, 9.26),
					Block.box(9.90, 0.00, 6.74, 10.60, 1.07, 8.06),
					Block.box(9.90, 0.00, 7.94, 10.60, 1.07, 9.26),
					Block.box(7.94, 0.00, 9.90, 9.26, 1.07, 10.60),
					Block.box(6.74, 0.00, 9.90, 8.06, 1.07, 10.60),
					Block.box(5.40, 0.00, 6.74, 6.10, 1.07, 8.06),
					Block.box(5.40, 0.00, 7.94, 6.10, 1.07, 9.26))),
			Map.entry(0b1111, Shapes.or(Block.box(6.10, 0.00, 6.10, 9.90, 2.63, 9.90),
					Block.box(6.74, 0.96, 5.40, 8.06, 2.27, 6.10),
					Block.box(7.94, 0.96, 5.40, 9.26, 2.27, 6.10),
					Block.box(5.40, 0.96, 6.74, 6.10, 2.27, 8.06),
					Block.box(5.40, 0.96, 7.94, 6.10, 2.27, 9.26),
					Block.box(9.90, 0.96, 6.74, 10.60, 2.27, 8.06),
					Block.box(9.90, 0.96, 7.94, 10.60, 2.27, 9.26),
					Block.box(6.74, 0.96, 9.90, 8.06, 2.27, 10.60),
					Block.box(7.94, 0.96, 9.90, 9.26, 2.27, 10.60),
					Block.box(6.74, 0.00, 5.40, 8.06, 1.07, 6.10),
					Block.box(7.94, 0.00, 5.40, 9.26, 1.07, 6.10),
					Block.box(5.40, 0.00, 6.74, 6.10, 1.07, 8.06),
					Block.box(5.40, 0.00, 7.94, 6.10, 1.07, 9.26),
					Block.box(9.90, 0.00, 6.74, 10.60, 1.07, 8.06),
					Block.box(9.90, 0.00, 7.94, 10.60, 1.07, 9.26),
					Block.box(6.74, 0.00, 9.90, 8.06, 1.07, 10.60),
					Block.box(7.94, 0.00, 9.90, 9.26, 1.07, 10.60))));

	private static final Map<Direction, VoxelShape> HV_ARMS = Map.of(
			Direction.NORTH, Shapes.or(Block.box(6.98, 0.00, 0.00, 7.82, 0.83, 4.60),
					Block.box(8.18, 0.00, 0.00, 9.02, 0.83, 4.60),
					Block.box(6.98, 1.20, 0.00, 7.82, 2.03, 4.60),
					Block.box(8.18, 1.20, 0.00, 9.02, 2.03, 4.60)),
			Direction.EAST, Shapes.or(Block.box(11.40, 0.00, 6.98, 16.00, 0.83, 7.82),
					Block.box(11.40, 0.00, 8.18, 16.00, 0.83, 9.02),
					Block.box(11.40, 1.20, 6.98, 16.00, 2.03, 7.82),
					Block.box(11.40, 1.20, 8.18, 16.00, 2.03, 9.02)),
			Direction.SOUTH, Shapes.or(Block.box(8.18, 0.00, 11.40, 9.02, 0.83, 16.00),
					Block.box(6.98, 0.00, 11.40, 7.82, 0.83, 16.00),
					Block.box(8.18, 1.20, 11.40, 9.02, 2.03, 16.00),
					Block.box(6.98, 1.20, 11.40, 7.82, 2.03, 16.00)),
			Direction.WEST, Shapes.or(Block.box(0.00, 0.00, 8.18, 4.60, 0.83, 9.02),
					Block.box(0.00, 0.00, 6.98, 4.60, 0.83, 7.82),
					Block.box(0.00, 1.20, 8.18, 4.60, 2.03, 9.02),
					Block.box(0.00, 1.20, 6.98, 4.60, 2.03, 7.82)));

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

	/** A conductor is not something to walk into: it is ankle-high and a player steps over it. */
	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
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
