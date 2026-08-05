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

	/** A run's outline, cut to what is drawn rather than to the block. */
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
			Map.entry(0b0000, Shapes.or(Block.box(6.30, 0.00, 3.20, 7.60, 1.30, 8.18),
					Block.box(6.10, 0.00, 7.53, 7.60, 1.75, 10.58),
					Block.box(8.40, 0.00, 5.53, 9.90, 1.75, 8.58),
					Block.box(8.40, 0.00, 3.20, 9.70, 1.30, 6.18),
					Block.box(5.65, 0.00, 13.08, 7.85, 2.20, 14.23),
					Block.box(8.15, 0.00, 11.08, 10.35, 2.20, 12.23),
					Block.box(5.65, 0.00, 14.23, 7.85, 2.20, 14.78),
					Block.box(8.15, 0.00, 12.23, 10.35, 2.20, 12.78),
					Block.box(5.80, 0.15, 10.93, 7.70, 2.05, 11.73),
					Block.box(8.30, 0.15, 8.93, 10.20, 2.05, 9.73),
					Block.box(8.45, 0.30, 9.73, 10.05, 1.90, 11.08),
					Block.box(5.95, 0.30, 11.73, 7.55, 1.90, 13.08),
					Block.box(8.43, 0.28, 8.38, 10.07, 1.92, 8.93),
					Block.box(5.93, 0.28, 10.38, 7.57, 1.92, 10.93),
					Block.box(8.60, 0.45, 7.93, 9.90, 1.75, 8.68),
					Block.box(6.10, 0.45, 9.93, 7.40, 1.75, 10.68),
					Block.box(6.53, 0.88, 14.78, 6.97, 1.32, 15.00))),
			Map.entry(0b0001, Shapes.or(Block.box(6.10, 0.00, 7.53, 7.60, 1.75, 10.58),
					Block.box(8.40, 0.00, 5.53, 9.90, 1.75, 8.58),
					Block.box(5.65, 0.00, 13.08, 7.85, 2.20, 14.23),
					Block.box(8.15, 0.00, 11.08, 10.35, 2.20, 12.23),
					Block.box(6.30, 0.00, 5.40, 7.60, 1.30, 8.18),
					Block.box(5.65, 0.00, 14.23, 7.85, 2.20, 14.78),
					Block.box(8.15, 0.00, 12.23, 10.35, 2.20, 12.78),
					Block.box(5.80, 0.15, 10.93, 7.70, 2.05, 11.73),
					Block.box(8.30, 0.15, 8.93, 10.20, 2.05, 9.73),
					Block.box(8.45, 0.30, 9.73, 10.05, 1.90, 11.08),
					Block.box(5.95, 0.30, 11.73, 7.55, 1.90, 13.08),
					Block.box(8.43, 0.28, 8.38, 10.07, 1.92, 8.93),
					Block.box(5.93, 0.28, 10.38, 7.57, 1.92, 10.93),
					Block.box(8.40, 0.00, 5.40, 9.70, 1.30, 6.18),
					Block.box(8.60, 0.45, 7.93, 9.90, 1.75, 8.68),
					Block.box(6.10, 0.45, 9.93, 7.40, 1.75, 10.68),
					Block.box(6.53, 0.88, 14.78, 6.97, 1.32, 15.00))),
			Map.entry(0b0010, Shapes.or(Block.box(5.42, 0.00, 6.10, 8.47, 1.75, 7.60),
					Block.box(7.42, 0.00, 8.40, 10.47, 1.75, 9.90),
					Block.box(1.77, 0.00, 5.65, 2.92, 2.20, 7.85),
					Block.box(3.77, 0.00, 8.15, 4.92, 2.20, 10.35),
					Block.box(7.82, 0.00, 6.30, 10.60, 1.30, 7.60),
					Block.box(1.22, 0.00, 5.65, 1.77, 2.20, 7.85),
					Block.box(3.22, 0.00, 8.15, 3.77, 2.20, 10.35),
					Block.box(4.27, 0.15, 5.80, 5.07, 2.05, 7.70),
					Block.box(6.27, 0.15, 8.30, 7.07, 2.05, 10.20),
					Block.box(4.92, 0.30, 8.45, 6.27, 1.90, 10.05),
					Block.box(2.92, 0.30, 5.95, 4.27, 1.90, 7.55),
					Block.box(7.07, 0.28, 8.43, 7.62, 1.92, 10.07),
					Block.box(5.07, 0.28, 5.93, 5.62, 1.92, 7.57),
					Block.box(9.82, 0.00, 8.40, 10.60, 1.30, 9.70),
					Block.box(5.32, 0.45, 6.10, 6.07, 1.75, 7.40),
					Block.box(7.32, 0.45, 8.60, 8.07, 1.75, 9.90),
					Block.box(1.00, 0.88, 6.53, 1.22, 1.32, 6.97))),
			Map.entry(0b0011, Shapes.or(Block.box(6.92, 0.00, 6.78, 8.67, 1.30, 8.63),
					Block.box(7.37, 0.00, 7.33, 9.22, 1.30, 9.08),
					Block.box(7.92, 0.00, 7.78, 9.85, 1.30, 9.42),
					Block.box(6.58, 0.00, 6.15, 8.22, 1.30, 8.08),
					Block.box(6.37, 0.00, 5.46, 7.88, 1.30, 7.45),
					Block.box(8.55, 0.00, 8.12, 10.54, 1.30, 9.63),
					Block.box(9.24, 0.00, 8.33, 11.25, 1.30, 9.70),
					Block.box(6.30, 0.00, 4.75, 7.67, 1.30, 6.76),
					Block.box(8.85, 0.00, 5.85, 10.39, 1.30, 7.34),
					Block.box(8.66, 0.00, 5.61, 10.15, 1.30, 7.15),
					Block.box(8.52, 0.00, 5.34, 9.96, 1.30, 6.91),
					Block.box(9.09, 0.00, 6.04, 10.66, 1.30, 7.48),
					Block.box(8.43, 0.00, 5.05, 9.82, 1.30, 6.64),
					Block.box(9.36, 0.00, 6.18, 10.95, 1.30, 7.57),
					Block.box(9.65, 0.00, 6.27, 11.25, 1.30, 7.60),
					Block.box(8.40, 0.00, 4.75, 9.73, 1.30, 6.35))),
			Map.entry(0b0100, Shapes.or(Block.box(8.40, 0.00, 5.42, 9.90, 1.75, 8.47),
					Block.box(6.10, 0.00, 7.42, 7.60, 1.75, 10.47),
					Block.box(8.15, 0.00, 1.77, 10.35, 2.20, 2.92),
					Block.box(5.65, 0.00, 3.77, 7.85, 2.20, 4.92),
					Block.box(8.40, 0.00, 7.82, 9.70, 1.30, 10.60),
					Block.box(8.15, 0.00, 1.22, 10.35, 2.20, 1.77),
					Block.box(5.65, 0.00, 3.22, 7.85, 2.20, 3.77),
					Block.box(8.30, 0.15, 4.27, 10.20, 2.05, 5.07),
					Block.box(5.80, 0.15, 6.27, 7.70, 2.05, 7.07),
					Block.box(8.45, 0.30, 2.92, 10.05, 1.90, 4.27),
					Block.box(5.95, 0.30, 4.92, 7.55, 1.90, 6.27),
					Block.box(8.43, 0.28, 5.07, 10.07, 1.92, 5.62),
					Block.box(5.93, 0.28, 7.07, 7.57, 1.92, 7.62),
					Block.box(6.30, 0.00, 9.82, 7.60, 1.30, 10.60),
					Block.box(8.60, 0.45, 5.32, 9.90, 1.75, 6.07),
					Block.box(6.10, 0.45, 7.32, 7.40, 1.75, 8.07),
					Block.box(9.03, 0.88, 1.00, 9.47, 1.32, 1.22))),
			Map.entry(0b0101, Shapes.or(Block.box(6.10, 0.00, 4.75, 7.60, 1.75, 7.15),
					Block.box(6.10, 0.00, 8.85, 7.60, 1.75, 11.25),
					Block.box(8.40, 0.00, 4.75, 9.90, 1.75, 7.15),
					Block.box(8.40, 0.00, 8.85, 9.90, 1.75, 11.25),
					Block.box(5.65, 0.00, 7.50, 7.85, 2.20, 8.50),
					Block.box(8.15, 0.00, 7.50, 10.35, 2.20, 8.50),
					Block.box(5.80, 0.15, 6.60, 7.70, 2.05, 7.25),
					Block.box(5.80, 0.15, 8.75, 7.70, 2.05, 9.40),
					Block.box(8.30, 0.15, 6.60, 10.20, 2.05, 7.25),
					Block.box(8.30, 0.15, 8.75, 10.20, 2.05, 9.40),
					Block.box(5.91, 0.26, 9.40, 7.59, 1.94, 9.80),
					Block.box(8.41, 0.26, 9.40, 10.09, 1.94, 9.80),
					Block.box(5.91, 0.26, 6.20, 7.59, 1.94, 6.60),
					Block.box(8.41, 0.26, 6.20, 10.09, 1.94, 6.60),
					Block.box(8.45, 0.30, 7.25, 10.05, 1.90, 7.50),
					Block.box(8.45, 0.30, 8.50, 10.05, 1.90, 8.75),
					Block.box(5.95, 0.30, 7.25, 7.55, 1.90, 7.50),
					Block.box(5.95, 0.30, 8.50, 7.55, 1.90, 8.75))),
			Map.entry(0b0110, Shapes.or(Block.box(7.37, 0.00, 6.92, 9.22, 1.30, 8.67),
					Block.box(6.92, 0.00, 7.37, 8.67, 1.30, 9.22),
					Block.box(6.58, 0.00, 7.92, 8.22, 1.30, 9.85),
					Block.box(7.92, 0.00, 6.58, 9.85, 1.30, 8.22),
					Block.box(8.55, 0.00, 6.37, 10.54, 1.30, 7.88),
					Block.box(6.37, 0.00, 8.55, 7.88, 1.30, 10.54),
					Block.box(6.30, 0.00, 9.24, 7.67, 1.30, 11.25),
					Block.box(9.24, 0.00, 6.30, 11.25, 1.30, 7.67),
					Block.box(8.85, 0.00, 8.66, 10.39, 1.30, 10.15),
					Block.box(8.66, 0.00, 8.85, 10.15, 1.30, 10.39),
					Block.box(8.52, 0.00, 9.09, 9.96, 1.30, 10.66),
					Block.box(9.09, 0.00, 8.52, 10.66, 1.30, 9.96),
					Block.box(9.36, 0.00, 8.43, 10.95, 1.30, 9.82),
					Block.box(8.43, 0.00, 9.36, 9.82, 1.30, 10.95),
					Block.box(9.65, 0.00, 8.40, 11.25, 1.30, 9.73),
					Block.box(8.40, 0.00, 9.65, 9.73, 1.30, 11.25))),
			Map.entry(0b0111, Shapes.or(Block.box(5.40, 0.00, 5.40, 10.60, 3.80, 10.60),
					Block.box(5.10, 3.80, 5.10, 10.90, 4.10, 10.90),
					Block.box(6.09, 0.00, 4.04, 7.81, 1.51, 5.06),
					Block.box(10.94, 0.00, 6.09, 11.96, 1.51, 7.81),
					Block.box(6.09, 0.00, 10.94, 7.81, 1.51, 11.96),
					Block.box(8.19, 0.00, 4.04, 9.91, 1.51, 5.06),
					Block.box(10.94, 0.00, 8.19, 11.96, 1.51, 9.91),
					Block.box(8.19, 0.00, 10.94, 9.91, 1.51, 11.96),
					Block.box(6.03, 0.00, 5.06, 7.87, 1.57, 5.40),
					Block.box(8.13, 0.00, 5.06, 9.97, 1.57, 5.40),
					Block.box(10.60, 0.00, 6.03, 10.94, 1.57, 7.87),
					Block.box(10.60, 0.00, 8.13, 10.94, 1.57, 9.97),
					Block.box(6.03, 0.00, 10.60, 7.87, 1.57, 10.94),
					Block.box(8.13, 0.00, 10.60, 9.97, 1.57, 10.94))),
			Map.entry(0b1000, Shapes.or(Block.box(7.53, 0.00, 8.40, 10.58, 1.75, 9.90),
					Block.box(5.53, 0.00, 6.10, 8.58, 1.75, 7.60),
					Block.box(13.08, 0.00, 8.15, 14.23, 2.20, 10.35),
					Block.box(11.08, 0.00, 5.65, 12.23, 2.20, 7.85),
					Block.box(5.40, 0.00, 8.40, 8.18, 1.30, 9.70),
					Block.box(14.23, 0.00, 8.15, 14.78, 2.20, 10.35),
					Block.box(12.23, 0.00, 5.65, 12.78, 2.20, 7.85),
					Block.box(10.93, 0.15, 8.30, 11.73, 2.05, 10.20),
					Block.box(8.93, 0.15, 5.80, 9.73, 2.05, 7.70),
					Block.box(11.73, 0.30, 8.45, 13.08, 1.90, 10.05),
					Block.box(9.73, 0.30, 5.95, 11.08, 1.90, 7.55),
					Block.box(10.38, 0.28, 8.43, 10.93, 1.92, 10.07),
					Block.box(8.38, 0.28, 5.93, 8.93, 1.92, 7.57),
					Block.box(5.40, 0.00, 6.30, 6.18, 1.30, 7.60),
					Block.box(7.93, 0.45, 6.10, 8.68, 1.75, 7.40),
					Block.box(9.93, 0.45, 8.60, 10.68, 1.75, 9.90),
					Block.box(14.78, 0.88, 9.03, 15.00, 1.32, 9.47))),
			Map.entry(0b1001, Shapes.or(Block.box(6.78, 0.00, 7.33, 8.63, 1.30, 9.08),
					Block.box(7.33, 0.00, 6.78, 9.08, 1.30, 8.63),
					Block.box(7.78, 0.00, 6.15, 9.42, 1.30, 8.08),
					Block.box(6.15, 0.00, 7.78, 8.08, 1.30, 9.42),
					Block.box(5.46, 0.00, 8.12, 7.45, 1.30, 9.63),
					Block.box(8.12, 0.00, 5.46, 9.63, 1.30, 7.45),
					Block.box(8.33, 0.00, 4.75, 9.70, 1.30, 6.76),
					Block.box(4.75, 0.00, 8.33, 6.76, 1.30, 9.70),
					Block.box(5.85, 0.00, 5.61, 7.34, 1.30, 7.15),
					Block.box(5.61, 0.00, 5.85, 7.15, 1.30, 7.34),
					Block.box(5.34, 0.00, 6.04, 6.91, 1.30, 7.48),
					Block.box(6.04, 0.00, 5.34, 7.48, 1.30, 6.91),
					Block.box(5.05, 0.00, 6.18, 6.64, 1.30, 7.57),
					Block.box(6.18, 0.00, 5.05, 7.57, 1.30, 6.64),
					Block.box(6.27, 0.00, 4.75, 7.60, 1.30, 6.35),
					Block.box(4.75, 0.00, 6.27, 6.35, 1.30, 7.60))),
			Map.entry(0b1010, Shapes.or(Block.box(8.85, 0.00, 6.10, 11.25, 1.75, 7.60),
					Block.box(4.75, 0.00, 6.10, 7.15, 1.75, 7.60),
					Block.box(8.85, 0.00, 8.40, 11.25, 1.75, 9.90),
					Block.box(4.75, 0.00, 8.40, 7.15, 1.75, 9.90),
					Block.box(7.50, 0.00, 5.65, 8.50, 2.20, 7.85),
					Block.box(7.50, 0.00, 8.15, 8.50, 2.20, 10.35),
					Block.box(6.60, 0.15, 5.80, 7.25, 2.05, 7.70),
					Block.box(8.75, 0.15, 5.80, 9.40, 2.05, 7.70),
					Block.box(6.60, 0.15, 8.30, 7.25, 2.05, 10.20),
					Block.box(8.75, 0.15, 8.30, 9.40, 2.05, 10.20),
					Block.box(6.20, 0.26, 5.91, 6.60, 1.94, 7.59),
					Block.box(6.20, 0.26, 8.41, 6.60, 1.94, 10.09),
					Block.box(9.40, 0.26, 5.91, 9.80, 1.94, 7.59),
					Block.box(9.40, 0.26, 8.41, 9.80, 1.94, 10.09),
					Block.box(8.50, 0.30, 8.45, 8.75, 1.90, 10.05),
					Block.box(7.25, 0.30, 8.45, 7.50, 1.90, 10.05),
					Block.box(8.50, 0.30, 5.95, 8.75, 1.90, 7.55),
					Block.box(7.25, 0.30, 5.95, 7.50, 1.90, 7.55))),
			Map.entry(0b1011, Shapes.or(Block.box(5.40, 0.00, 5.40, 10.60, 3.80, 10.60),
					Block.box(5.10, 3.80, 5.10, 10.90, 4.10, 10.90),
					Block.box(4.04, 0.00, 8.19, 5.06, 1.51, 9.91),
					Block.box(6.09, 0.00, 4.04, 7.81, 1.51, 5.06),
					Block.box(10.94, 0.00, 8.19, 11.96, 1.51, 9.91),
					Block.box(4.04, 0.00, 6.09, 5.06, 1.51, 7.81),
					Block.box(8.19, 0.00, 4.04, 9.91, 1.51, 5.06),
					Block.box(10.94, 0.00, 6.09, 11.96, 1.51, 7.81),
					Block.box(5.06, 0.00, 8.13, 5.40, 1.57, 9.97),
					Block.box(5.06, 0.00, 6.03, 5.40, 1.57, 7.87),
					Block.box(6.03, 0.00, 5.06, 7.87, 1.57, 5.40),
					Block.box(8.13, 0.00, 5.06, 9.97, 1.57, 5.40),
					Block.box(10.60, 0.00, 8.13, 10.94, 1.57, 9.97),
					Block.box(10.60, 0.00, 6.03, 10.94, 1.57, 7.87))),
			Map.entry(0b1100, Shapes.or(Block.box(7.33, 0.00, 7.37, 9.08, 1.30, 9.22),
					Block.box(6.78, 0.00, 6.92, 8.63, 1.30, 8.67),
					Block.box(6.15, 0.00, 6.58, 8.08, 1.30, 8.22),
					Block.box(7.78, 0.00, 7.92, 9.42, 1.30, 9.85),
					Block.box(8.12, 0.00, 8.55, 9.63, 1.30, 10.54),
					Block.box(5.46, 0.00, 6.37, 7.45, 1.30, 7.88),
					Block.box(4.75, 0.00, 6.30, 6.76, 1.30, 7.67),
					Block.box(8.33, 0.00, 9.24, 9.70, 1.30, 11.25),
					Block.box(5.85, 0.00, 8.85, 7.34, 1.30, 10.39),
					Block.box(5.61, 0.00, 8.66, 7.15, 1.30, 10.15),
					Block.box(5.34, 0.00, 8.52, 6.91, 1.30, 9.96),
					Block.box(6.04, 0.00, 9.09, 7.48, 1.30, 10.66),
					Block.box(6.18, 0.00, 9.36, 7.57, 1.30, 10.95),
					Block.box(5.05, 0.00, 8.43, 6.64, 1.30, 9.82),
					Block.box(6.27, 0.00, 9.65, 7.60, 1.30, 11.25),
					Block.box(4.75, 0.00, 8.40, 6.35, 1.30, 9.73))),
			Map.entry(0b1101, Shapes.or(Block.box(5.40, 0.00, 5.40, 10.60, 3.80, 10.60),
					Block.box(5.10, 3.80, 5.10, 10.90, 4.10, 10.90),
					Block.box(8.19, 0.00, 10.94, 9.91, 1.51, 11.96),
					Block.box(4.04, 0.00, 8.19, 5.06, 1.51, 9.91),
					Block.box(8.19, 0.00, 4.04, 9.91, 1.51, 5.06),
					Block.box(6.09, 0.00, 10.94, 7.81, 1.51, 11.96),
					Block.box(4.04, 0.00, 6.09, 5.06, 1.51, 7.81),
					Block.box(6.09, 0.00, 4.04, 7.81, 1.51, 5.06),
					Block.box(8.13, 0.00, 10.60, 9.97, 1.57, 10.94),
					Block.box(6.03, 0.00, 10.60, 7.87, 1.57, 10.94),
					Block.box(5.06, 0.00, 8.13, 5.40, 1.57, 9.97),
					Block.box(5.06, 0.00, 6.03, 5.40, 1.57, 7.87),
					Block.box(8.13, 0.00, 5.06, 9.97, 1.57, 5.40),
					Block.box(6.03, 0.00, 5.06, 7.87, 1.57, 5.40))),
			Map.entry(0b1110, Shapes.or(Block.box(5.40, 0.00, 5.40, 10.60, 3.80, 10.60),
					Block.box(5.10, 3.80, 5.10, 10.90, 4.10, 10.90),
					Block.box(10.94, 0.00, 6.09, 11.96, 1.51, 7.81),
					Block.box(8.19, 0.00, 10.94, 9.91, 1.51, 11.96),
					Block.box(4.04, 0.00, 6.09, 5.06, 1.51, 7.81),
					Block.box(10.94, 0.00, 8.19, 11.96, 1.51, 9.91),
					Block.box(6.09, 0.00, 10.94, 7.81, 1.51, 11.96),
					Block.box(4.04, 0.00, 8.19, 5.06, 1.51, 9.91),
					Block.box(10.60, 0.00, 6.03, 10.94, 1.57, 7.87),
					Block.box(10.60, 0.00, 8.13, 10.94, 1.57, 9.97),
					Block.box(8.13, 0.00, 10.60, 9.97, 1.57, 10.94),
					Block.box(6.03, 0.00, 10.60, 7.87, 1.57, 10.94),
					Block.box(5.06, 0.00, 6.03, 5.40, 1.57, 7.87),
					Block.box(5.06, 0.00, 8.13, 5.40, 1.57, 9.97))),
			Map.entry(0b1111, Shapes.or(Block.box(5.40, 0.00, 5.40, 10.60, 3.80, 10.60),
					Block.box(5.10, 3.80, 5.10, 10.90, 4.10, 10.90),
					Block.box(6.09, 0.00, 4.04, 7.81, 1.51, 5.06),
					Block.box(10.94, 0.00, 6.09, 11.96, 1.51, 7.81),
					Block.box(6.09, 0.00, 10.94, 7.81, 1.51, 11.96),
					Block.box(4.04, 0.00, 6.09, 5.06, 1.51, 7.81),
					Block.box(8.19, 0.00, 4.04, 9.91, 1.51, 5.06),
					Block.box(10.94, 0.00, 8.19, 11.96, 1.51, 9.91),
					Block.box(8.19, 0.00, 10.94, 9.91, 1.51, 11.96),
					Block.box(4.04, 0.00, 8.19, 5.06, 1.51, 9.91),
					Block.box(6.03, 0.00, 5.06, 7.87, 1.57, 5.40),
					Block.box(8.13, 0.00, 5.06, 9.97, 1.57, 5.40),
					Block.box(10.60, 0.00, 6.03, 10.94, 1.57, 7.87),
					Block.box(10.60, 0.00, 8.13, 10.94, 1.57, 9.97),
					Block.box(6.03, 0.00, 10.60, 7.87, 1.57, 10.94),
					Block.box(8.13, 0.00, 10.60, 9.97, 1.57, 10.94),
					Block.box(5.06, 0.00, 6.03, 5.40, 1.57, 7.87),
					Block.box(5.06, 0.00, 8.13, 5.40, 1.57, 9.97))));

	private static final Map<Direction, VoxelShape> STRING_ARMS = Map.of(
			Direction.NORTH, Shapes.or(Block.box(6.30, 0.00, 0.00, 7.60, 1.30, 5.40),
					Block.box(8.40, 0.00, 0.00, 9.70, 1.30, 5.40),
					Block.box(5.96, 1.30, 2.50, 10.04, 1.58, 3.50),
					Block.box(5.96, 0.00, 2.50, 6.24, 1.30, 3.50),
					Block.box(9.76, 0.00, 2.50, 10.04, 1.30, 3.50),
					Block.box(10.04, 0.20, 2.40, 10.46, 0.95, 3.60)),
			Direction.EAST, Shapes.or(Block.box(10.60, 0.00, 6.30, 16.00, 1.30, 7.60),
					Block.box(10.60, 0.00, 8.40, 16.00, 1.30, 9.70),
					Block.box(12.50, 1.30, 5.96, 13.50, 1.58, 10.04),
					Block.box(12.50, 0.00, 5.96, 13.50, 1.30, 6.24),
					Block.box(12.50, 0.00, 9.76, 13.50, 1.30, 10.04),
					Block.box(12.40, 0.20, 10.04, 13.60, 0.95, 10.46)),
			Direction.SOUTH, Shapes.or(Block.box(8.40, 0.00, 10.60, 9.70, 1.30, 16.00),
					Block.box(6.30, 0.00, 10.60, 7.60, 1.30, 16.00),
					Block.box(5.96, 1.30, 12.50, 10.04, 1.58, 13.50),
					Block.box(9.76, 0.00, 12.50, 10.04, 1.30, 13.50),
					Block.box(5.96, 0.00, 12.50, 6.24, 1.30, 13.50),
					Block.box(5.54, 0.20, 12.40, 5.96, 0.95, 13.60)),
			Direction.WEST, Shapes.or(Block.box(0.00, 0.00, 8.40, 5.40, 1.30, 9.70),
					Block.box(0.00, 0.00, 6.30, 5.40, 1.30, 7.60),
					Block.box(2.50, 1.30, 5.96, 3.50, 1.58, 10.04),
					Block.box(2.50, 0.00, 9.76, 3.50, 1.30, 10.04),
					Block.box(2.50, 0.00, 5.96, 3.50, 1.30, 6.24),
					Block.box(2.40, 0.20, 5.54, 3.60, 0.95, 5.96)));

	private static final Map<Direction, VoxelShape> STRING_CLIMBS = Map.of(
			Direction.NORTH, Shapes.or(Block.box(6.30, 3.05, 0.50, 7.60, 16.00, 1.80),
					Block.box(8.40, 3.05, 0.50, 9.70, 16.00, 1.80),
					Block.box(5.40, 9.00, 1.78, 10.60, 10.40, 2.20),
					Block.box(6.30, 9.00, 0.00, 9.70, 10.40, 1.78),
					Block.box(6.30, 0.70, 0.90, 7.60, 2.37, 2.50),
					Block.box(8.40, 0.70, 0.90, 9.70, 2.37, 2.50),
					Block.box(6.30, 0.40, 1.20, 7.60, 2.00, 2.87),
					Block.box(8.40, 0.40, 1.20, 9.70, 2.00, 2.87),
					Block.box(6.30, 0.18, 1.57, 7.60, 1.70, 3.28),
					Block.box(6.30, 1.07, 0.68, 7.60, 2.78, 2.20),
					Block.box(8.40, 0.18, 1.57, 9.70, 1.70, 3.28),
					Block.box(8.40, 1.07, 0.68, 9.70, 2.78, 2.20),
					Block.box(6.30, 0.05, 1.98, 7.60, 1.48, 3.73),
					Block.box(8.40, 0.05, 1.98, 9.70, 1.48, 3.73),
					Block.box(6.30, 1.48, 0.55, 7.60, 3.23, 1.98),
					Block.box(8.40, 1.48, 0.55, 9.70, 3.23, 1.98),
					Block.box(6.30, 0.00, 3.55, 7.60, 1.30, 5.40),
					Block.box(8.40, 0.00, 3.55, 9.70, 1.30, 5.40),
					Block.box(6.30, 0.00, 2.43, 7.60, 1.35, 4.20),
					Block.box(6.30, 1.93, 0.50, 7.60, 3.70, 1.85),
					Block.box(8.40, 0.00, 2.43, 9.70, 1.35, 4.20),
					Block.box(8.40, 1.93, 0.50, 9.70, 3.70, 1.85),
					Block.box(7.66, 9.36, 2.20, 8.34, 10.04, 2.50)),
			Direction.EAST, Shapes.or(Block.box(14.20, 3.05, 6.30, 15.50, 16.00, 7.60),
					Block.box(14.20, 3.05, 8.40, 15.50, 16.00, 9.70),
					Block.box(13.80, 9.00, 5.40, 14.22, 10.40, 10.60),
					Block.box(14.22, 9.00, 6.30, 16.00, 10.40, 9.70),
					Block.box(13.50, 0.70, 6.30, 15.10, 2.37, 7.60),
					Block.box(13.50, 0.70, 8.40, 15.10, 2.37, 9.70),
					Block.box(13.13, 0.40, 6.30, 14.80, 2.00, 7.60),
					Block.box(13.13, 0.40, 8.40, 14.80, 2.00, 9.70),
					Block.box(13.80, 1.07, 6.30, 15.32, 2.78, 7.60),
					Block.box(13.80, 1.07, 8.40, 15.32, 2.78, 9.70),
					Block.box(12.72, 0.18, 6.30, 14.43, 1.70, 7.60),
					Block.box(12.72, 0.18, 8.40, 14.43, 1.70, 9.70),
					Block.box(12.27, 0.05, 6.30, 14.02, 1.48, 7.60),
					Block.box(12.27, 0.05, 8.40, 14.02, 1.48, 9.70),
					Block.box(14.02, 1.48, 6.30, 15.45, 3.23, 7.60),
					Block.box(14.02, 1.48, 8.40, 15.45, 3.23, 9.70),
					Block.box(10.60, 0.00, 6.30, 12.45, 1.30, 7.60),
					Block.box(10.60, 0.00, 8.40, 12.45, 1.30, 9.70),
					Block.box(11.80, 0.00, 6.30, 13.57, 1.35, 7.60),
					Block.box(11.80, 0.00, 8.40, 13.57, 1.35, 9.70),
					Block.box(14.15, 1.93, 6.30, 15.50, 3.70, 7.60),
					Block.box(14.15, 1.93, 8.40, 15.50, 3.70, 9.70),
					Block.box(13.50, 9.36, 7.66, 13.80, 10.04, 8.34)),
			Direction.SOUTH, Shapes.or(Block.box(8.40, 3.05, 14.20, 9.70, 16.00, 15.50),
					Block.box(6.30, 3.05, 14.20, 7.60, 16.00, 15.50),
					Block.box(5.40, 9.00, 13.80, 10.60, 10.40, 14.22),
					Block.box(6.30, 9.00, 14.22, 9.70, 10.40, 16.00),
					Block.box(8.40, 0.70, 13.50, 9.70, 2.37, 15.10),
					Block.box(6.30, 0.70, 13.50, 7.60, 2.37, 15.10),
					Block.box(8.40, 0.40, 13.13, 9.70, 2.00, 14.80),
					Block.box(6.30, 0.40, 13.13, 7.60, 2.00, 14.80),
					Block.box(8.40, 1.07, 13.80, 9.70, 2.78, 15.32),
					Block.box(6.30, 1.07, 13.80, 7.60, 2.78, 15.32),
					Block.box(8.40, 0.18, 12.72, 9.70, 1.70, 14.43),
					Block.box(6.30, 0.18, 12.72, 7.60, 1.70, 14.43),
					Block.box(8.40, 0.05, 12.27, 9.70, 1.48, 14.02),
					Block.box(6.30, 0.05, 12.27, 7.60, 1.48, 14.02),
					Block.box(8.40, 1.48, 14.02, 9.70, 3.23, 15.45),
					Block.box(6.30, 1.48, 14.02, 7.60, 3.23, 15.45),
					Block.box(8.40, 0.00, 10.60, 9.70, 1.30, 12.45),
					Block.box(6.30, 0.00, 10.60, 7.60, 1.30, 12.45),
					Block.box(8.40, 0.00, 11.80, 9.70, 1.35, 13.57),
					Block.box(6.30, 0.00, 11.80, 7.60, 1.35, 13.57),
					Block.box(8.40, 1.93, 14.15, 9.70, 3.70, 15.50),
					Block.box(6.30, 1.93, 14.15, 7.60, 3.70, 15.50),
					Block.box(7.66, 9.36, 13.50, 8.34, 10.04, 13.80)),
			Direction.WEST, Shapes.or(Block.box(0.50, 3.05, 8.40, 1.80, 16.00, 9.70),
					Block.box(0.50, 3.05, 6.30, 1.80, 16.00, 7.60),
					Block.box(1.78, 9.00, 5.40, 2.20, 10.40, 10.60),
					Block.box(0.00, 9.00, 6.30, 1.78, 10.40, 9.70),
					Block.box(0.90, 0.70, 8.40, 2.50, 2.37, 9.70),
					Block.box(0.90, 0.70, 6.30, 2.50, 2.37, 7.60),
					Block.box(1.20, 0.40, 8.40, 2.87, 2.00, 9.70),
					Block.box(1.20, 0.40, 6.30, 2.87, 2.00, 7.60),
					Block.box(1.57, 0.18, 8.40, 3.28, 1.70, 9.70),
					Block.box(0.68, 1.07, 8.40, 2.20, 2.78, 9.70),
					Block.box(1.57, 0.18, 6.30, 3.28, 1.70, 7.60),
					Block.box(0.68, 1.07, 6.30, 2.20, 2.78, 7.60),
					Block.box(1.98, 0.05, 8.40, 3.73, 1.48, 9.70),
					Block.box(1.98, 0.05, 6.30, 3.73, 1.48, 7.60),
					Block.box(0.55, 1.48, 8.40, 1.98, 3.23, 9.70),
					Block.box(0.55, 1.48, 6.30, 1.98, 3.23, 7.60),
					Block.box(3.55, 0.00, 8.40, 5.40, 1.30, 9.70),
					Block.box(3.55, 0.00, 6.30, 5.40, 1.30, 7.60),
					Block.box(2.43, 0.00, 8.40, 4.20, 1.35, 9.70),
					Block.box(0.50, 1.93, 8.40, 1.85, 3.70, 9.70),
					Block.box(2.43, 0.00, 6.30, 4.20, 1.35, 7.60),
					Block.box(0.50, 1.93, 6.30, 1.85, 3.70, 7.60),
					Block.box(2.20, 9.36, 7.66, 2.50, 10.04, 8.34)));

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
			shapes.put(state, pair ? stringShapeOf(state) : trunkShapeOf(state));
		}
	}

	/** The string cable as it is drawn in this state: the middle its pattern gets, the arms, any climb. */
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

			// A climb is the *whole* run for that side - along the ground
			// wall, swept as one tube
			shape = Shapes.or(shape, connection == RedstoneSide.UP
					? STRING_CLIMBS.get(side.getKey())
					: STRING_ARMS.get(side.getKey()));
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
	 * The cable is solid: a laid run is a couple of pixels high and a joint in it is three, so a player
	 * steps over it the way they step onto a carpet.  Returning nothing here meant walking straight
	 * through the thing whose outline was drawn round it.  A trench is filled to the surface.
	 */
	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(BURIED) ? Shapes.block() : shapes.getOrDefault(state, TRUNK_HUB);
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
