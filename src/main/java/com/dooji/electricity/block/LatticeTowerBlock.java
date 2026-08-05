package com.dooji.electricity.block;

import com.dooji.electricity.api.power.TowerSpec;
import com.dooji.electricity.main.Electricity;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A lattice transmission tower, in one of three duties.
 *
 * The duty is what makes them three objects rather than one: a suspension tower only holds a conductor
 * up, a tension tower takes the difference between the pulls either side of it, and a terminal tower
 * takes the whole pull of the line and is stayed back against it. So a line is a run of suspension towers
 * with a tension tower every few kilometres and a terminal tower at each end, which is what a player
 * builds here too.
 *
 * Eleven and a half blocks tall and seven wide, so it is a {@link MachineShell}: its collision is a table
 * of cells printed by {@code tools/gen_tower_models.py --java}.
 */
public class LatticeTowerBlock extends Block implements EntityBlock, MachineShell {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	/** The facing the three lattice_*.obj files are modelled at: the crossarms run east-west. */
	public static final Direction AUTHORED = Direction.NORTH;

	/**
	 * A suspension tower: the nine-in-ten one, hanging its chains vertically.
	 *
	 * Printed by {@code tools/gen_tower_models.py --java}, which authors it from the legs, the crossarms,
	 * the peak and the footings rather than cutting it from every group the model draws - on a lattice the
	 * diagonals sweep almost every cell of the bounding volume, and between the braces is air.
	 */
	private static final List<Cell> SUSPENSION_CELLS = List.of(
			new Cell(-2, 0, -2, Shapes.or(Block.box(6.56, 0.00, 6.56, 12.64, 1.60, 12.64),
					Block.box(8.39, 0.00, 8.39, 13.51, 16.00, 13.51))),
			new Cell(-2, 0, 2, Shapes.or(Block.box(6.56, 0.00, 3.36, 12.64, 1.60, 9.44),
					Block.box(8.39, 0.00, 2.49, 13.51, 16.00, 7.61))),
			new Cell(2, 0, -2, Shapes.or(Block.box(2.49, 0.00, 8.39, 7.61, 16.00, 13.51),
					Block.box(3.36, 0.00, 6.56, 9.44, 1.60, 12.64))),
			new Cell(2, 0, 2, Shapes.or(Block.box(2.49, 0.00, 2.49, 7.61, 16.00, 7.61),
					Block.box(3.36, 0.00, 3.36, 9.44, 1.60, 9.44))),
			new Cell(-2, 1, -2, Block.box(11.08, 0.00, 11.08, 16.00, 16.00, 16.00)),
			new Cell(-2, 1, 2, Block.box(11.08, 0.00, 0.00, 16.00, 16.00, 4.92)),
			new Cell(2, 1, -2, Block.box(0.00, 0.00, 11.08, 4.92, 16.00, 16.00)),
			new Cell(2, 1, 2, Block.box(0.00, 0.00, 0.00, 4.92, 16.00, 4.92)),
			new Cell(-2, 2, -2, Block.box(13.78, 0.00, 13.78, 16.00, 16.00, 16.00)),
			new Cell(-2, 2, -1, Block.box(13.78, 0.00, 0.00, 16.00, 16.00, 2.90)),
			new Cell(-2, 2, 1, Block.box(13.78, 0.00, 13.10, 16.00, 16.00, 16.00)),
			new Cell(-2, 2, 2, Block.box(13.78, 0.00, 0.00, 16.00, 16.00, 2.22)),
			new Cell(-1, 2, -2, Block.box(0.00, 0.00, 13.78, 2.90, 16.00, 16.00)),
			new Cell(-1, 2, -1, Block.box(0.00, 0.00, 0.00, 2.90, 16.00, 2.90)),
			new Cell(-1, 2, 1, Block.box(0.00, 0.00, 13.10, 2.90, 16.00, 16.00)),
			new Cell(-1, 2, 2, Block.box(0.00, 0.00, 0.00, 2.90, 16.00, 2.22)),
			new Cell(1, 2, -2, Block.box(13.10, 0.00, 13.78, 16.00, 16.00, 16.00)),
			new Cell(1, 2, -1, Block.box(13.10, 0.00, 0.00, 16.00, 16.00, 2.90)),
			new Cell(1, 2, 1, Block.box(13.10, 0.00, 13.10, 16.00, 16.00, 16.00)),
			new Cell(1, 2, 2, Block.box(13.10, 0.00, 0.00, 16.00, 16.00, 2.22)),
			new Cell(2, 2, -2, Block.box(0.00, 0.00, 13.78, 2.22, 16.00, 16.00)),
			new Cell(2, 2, -1, Block.box(0.00, 0.00, 0.00, 2.22, 16.00, 2.90)),
			new Cell(2, 2, 1, Block.box(0.00, 0.00, 13.10, 2.22, 16.00, 16.00)),
			new Cell(2, 2, 2, Block.box(0.00, 0.00, 0.00, 2.22, 16.00, 2.22)),
			new Cell(-1, 3, -1, Block.box(0.47, 0.00, 0.47, 5.59, 16.00, 5.59)),
			new Cell(-1, 3, 1, Block.box(0.47, 0.00, 10.41, 5.59, 16.00, 15.53)),
			new Cell(1, 3, -1, Block.box(10.41, 0.00, 0.47, 15.53, 16.00, 5.59)),
			new Cell(1, 3, 1, Block.box(10.41, 0.00, 10.41, 15.53, 16.00, 15.53)),
			new Cell(-1, 4, -1, Block.box(3.17, 0.00, 3.17, 8.29, 16.00, 8.29)),
			new Cell(-1, 4, 1, Block.box(3.17, 0.00, 7.71, 8.29, 16.00, 12.83)),
			new Cell(1, 4, -1, Block.box(7.71, 0.00, 3.17, 12.83, 16.00, 8.29)),
			new Cell(1, 4, 1, Block.box(7.71, 0.00, 7.71, 12.83, 16.00, 12.83)),
			new Cell(-1, 5, -1, Block.box(5.86, 0.00, 5.86, 10.98, 16.00, 10.98)),
			new Cell(-1, 5, 1, Block.box(5.86, 0.00, 5.02, 10.98, 16.00, 10.14)),
			new Cell(1, 5, -1, Block.box(5.02, 0.00, 5.86, 10.14, 16.00, 10.98)),
			new Cell(1, 5, 1, Block.box(5.02, 0.00, 5.02, 10.14, 16.00, 10.14)),
			new Cell(-1, 6, -1, Block.box(8.56, 0.00, 8.56, 13.68, 16.00, 13.68)),
			new Cell(-1, 6, 1, Block.box(8.56, 0.00, 2.32, 13.68, 16.00, 7.44)),
			new Cell(1, 6, -1, Block.box(2.32, 0.00, 8.56, 7.44, 16.00, 13.68)),
			new Cell(1, 6, 1, Block.box(2.32, 0.00, 2.32, 7.44, 16.00, 7.44)),
			new Cell(-3, 7, 0, Block.box(14.24, 8.42, 7.04, 16.00, 16.00, 8.96)),
			new Cell(-1, 7, -1, Block.box(11.25, 0.00, 11.25, 16.00, 16.00, 16.00)),
			new Cell(-1, 7, 0, Block.box(0.60, 8.42, 7.04, 2.52, 16.00, 8.96)),
			new Cell(-1, 7, 1, Block.box(11.25, 0.00, 0.00, 16.00, 16.00, 4.75)),
			new Cell(1, 7, -1, Block.box(0.00, 0.00, 11.25, 4.75, 16.00, 16.00)),
			new Cell(1, 7, 0, Block.box(13.48, 8.42, 7.04, 15.40, 16.00, 8.96)),
			new Cell(1, 7, 1, Block.box(0.00, 0.00, 0.00, 4.75, 16.00, 4.75)),
			new Cell(3, 7, 0, Block.box(0.00, 8.42, 7.04, 1.76, 16.00, 8.96)),
			new Cell(-3, 8, 0, Shapes.or(Block.box(13.92, 3.20, 1.28, 16.00, 6.40, 14.72),
					Block.box(14.24, 0.00, 7.04, 16.00, 4.96, 8.96))),
			new Cell(-2, 8, 0, Block.box(0.00, 3.20, 1.28, 16.00, 6.40, 14.72)),
			new Cell(-1, 8, -1, Block.box(11.52, 0.00, 11.52, 16.00, 16.00, 16.00)),
			new Cell(-1, 8, 0, Shapes.or(Block.box(0.00, 3.20, 1.28, 16.00, 6.40, 14.72),
					Block.box(0.60, 0.00, 7.04, 2.52, 4.96, 8.96),
					Block.box(11.52, 0.00, 0.00, 16.00, 16.00, 0.64),
					Block.box(11.52, 0.00, 15.36, 16.00, 16.00, 16.00))),
			new Cell(-1, 8, 1, Block.box(11.52, 0.00, 0.00, 16.00, 16.00, 4.48)),
			new Cell(0, 8, -1, Shapes.or(Block.box(0.00, 0.00, 11.52, 0.64, 16.00, 16.00),
					Block.box(15.36, 0.00, 11.52, 16.00, 16.00, 16.00))),
			new Cell(0, 8, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 0.64, 16.00, 0.64),
					Block.box(0.00, 0.00, 15.36, 0.64, 16.00, 16.00),
					Block.box(0.00, 3.20, 1.28, 16.00, 6.40, 14.72),
					Block.box(15.36, 0.00, 0.00, 16.00, 16.00, 0.64),
					Block.box(15.36, 0.00, 15.36, 16.00, 16.00, 16.00))),
			new Cell(0, 8, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 0.64, 16.00, 4.48),
					Block.box(15.36, 0.00, 0.00, 16.00, 16.00, 4.48))),
			new Cell(1, 8, -1, Block.box(0.00, 0.00, 11.52, 4.48, 16.00, 16.00)),
			new Cell(1, 8, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 4.48, 16.00, 0.64),
					Block.box(0.00, 0.00, 15.36, 4.48, 16.00, 16.00),
					Block.box(0.00, 3.20, 1.28, 16.00, 6.40, 14.72),
					Block.box(13.48, 0.00, 7.04, 15.40, 4.96, 8.96))),
			new Cell(1, 8, 1, Block.box(0.00, 0.00, 0.00, 4.48, 16.00, 4.48)),
			new Cell(2, 8, 0, Block.box(0.00, 3.20, 1.28, 16.00, 6.40, 14.72)),
			new Cell(3, 8, 0, Shapes.or(Block.box(0.00, 0.00, 7.04, 1.76, 4.96, 8.96),
					Block.box(0.00, 3.20, 1.28, 2.08, 6.40, 14.72))),
			new Cell(-2, 9, 0, Shapes.or(Block.box(13.92, 12.80, 2.56, 16.00, 16.00, 13.44),
					Block.box(14.24, 2.02, 7.04, 16.00, 14.56, 8.96))),
			new Cell(-1, 9, -1, Block.box(11.52, 0.00, 11.52, 16.00, 16.00, 16.00)),
			new Cell(-1, 9, 0, Shapes.or(Block.box(0.00, 12.80, 0.00, 16.00, 16.00, 13.44),
					Block.box(11.52, 0.00, 0.00, 16.00, 14.40, 0.64),
					Block.box(11.52, 0.00, 15.05, 16.00, 16.00, 16.00))),
			new Cell(-1, 9, 1, Block.box(11.52, 0.00, 0.00, 16.00, 16.00, 4.48)),
			new Cell(0, 9, -1, Shapes.or(Block.box(0.00, 0.00, 11.52, 0.95, 16.00, 16.00),
					Block.box(15.05, 0.00, 11.52, 16.00, 16.00, 16.00))),
			new Cell(0, 9, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 0.64, 14.40, 0.64),
					Block.box(0.00, 0.00, 15.36, 0.64, 14.40, 16.00),
					Block.box(0.00, 12.80, 0.00, 16.00, 16.00, 16.00),
					Block.box(15.36, 0.00, 0.00, 16.00, 14.40, 0.64),
					Block.box(15.36, 0.00, 15.36, 16.00, 14.40, 16.00))),
			new Cell(0, 9, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 0.95, 16.00, 4.48),
					Block.box(15.05, 0.00, 0.00, 16.00, 16.00, 4.48))),
			new Cell(1, 9, -1, Block.box(0.00, 0.00, 11.52, 4.48, 16.00, 16.00)),
			new Cell(1, 9, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 4.48, 16.00, 0.95),
					Block.box(0.00, 0.00, 15.05, 4.48, 16.00, 16.00),
					Block.box(0.00, 12.80, 2.56, 16.00, 16.00, 13.44))),
			new Cell(1, 9, 1, Block.box(0.00, 0.00, 0.00, 4.48, 16.00, 4.48)),
			new Cell(2, 9, 0, Shapes.or(Block.box(0.00, 2.02, 7.04, 1.76, 14.56, 8.96),
					Block.box(0.00, 12.80, 2.56, 2.08, 16.00, 13.44))),
			new Cell(-1, 10, -1, Block.box(15.24, 0.00, 15.24, 16.00, 16.00, 16.00)),
			new Cell(-1, 10, 0, Shapes.or(Block.box(15.24, 0.00, 0.00, 16.00, 16.00, 4.36),
					Block.box(15.24, 0.00, 11.64, 16.00, 16.00, 16.00))),
			new Cell(-1, 10, 1, Block.box(15.24, 0.00, 0.00, 16.00, 16.00, 0.76)),
			new Cell(0, 10, -1, Shapes.or(Block.box(0.00, 0.00, 15.24, 4.36, 16.00, 16.00),
					Block.box(11.64, 0.00, 15.24, 16.00, 16.00, 16.00))),
			new Cell(0, 10, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 4.36, 16.00, 4.36),
					Block.box(0.00, 0.00, 11.64, 4.36, 16.00, 16.00),
					Block.box(11.64, 0.00, 0.00, 16.00, 16.00, 4.36),
					Block.box(11.64, 0.00, 11.64, 16.00, 16.00, 16.00))),
			new Cell(0, 10, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 4.36, 16.00, 0.76),
					Block.box(11.64, 0.00, 0.00, 16.00, 16.00, 0.76))),
			new Cell(1, 10, -1, Block.box(0.00, 0.00, 15.24, 0.76, 16.00, 16.00)),
			new Cell(1, 10, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 0.76, 16.00, 4.36),
					Block.box(0.00, 0.00, 11.64, 0.76, 16.00, 16.00))),
			new Cell(1, 10, 1, Block.box(0.00, 0.00, 0.00, 0.76, 16.00, 0.76)),
			new Cell(0, 11, 0, Block.box(3.89, 0.00, 3.89, 12.11, 8.00, 12.11)));

	/** A tension tower: horizontal chains, double-braced for the difference between two pulls. */
	private static final List<Cell> TENSION_CELLS = List.of(
			new Cell(-2, 0, -2, Shapes.or(Block.box(6.56, 0.00, 6.56, 12.64, 1.60, 12.64),
					Block.box(8.39, 0.00, 8.39, 13.51, 16.00, 13.51))),
			new Cell(-2, 0, 2, Shapes.or(Block.box(6.56, 0.00, 3.36, 12.64, 1.60, 9.44),
					Block.box(8.39, 0.00, 2.49, 13.51, 16.00, 7.61))),
			new Cell(2, 0, -2, Shapes.or(Block.box(2.49, 0.00, 8.39, 7.61, 16.00, 13.51),
					Block.box(3.36, 0.00, 6.56, 9.44, 1.60, 12.64))),
			new Cell(2, 0, 2, Shapes.or(Block.box(2.49, 0.00, 2.49, 7.61, 16.00, 7.61),
					Block.box(3.36, 0.00, 3.36, 9.44, 1.60, 9.44))),
			new Cell(-2, 1, -2, Block.box(11.08, 0.00, 11.08, 16.00, 16.00, 16.00)),
			new Cell(-2, 1, 2, Block.box(11.08, 0.00, 0.00, 16.00, 16.00, 4.92)),
			new Cell(2, 1, -2, Block.box(0.00, 0.00, 11.08, 4.92, 16.00, 16.00)),
			new Cell(2, 1, 2, Block.box(0.00, 0.00, 0.00, 4.92, 16.00, 4.92)),
			new Cell(-2, 2, -2, Block.box(13.78, 0.00, 13.78, 16.00, 16.00, 16.00)),
			new Cell(-2, 2, -1, Block.box(13.78, 0.00, 0.00, 16.00, 16.00, 2.90)),
			new Cell(-2, 2, 1, Block.box(13.78, 0.00, 13.10, 16.00, 16.00, 16.00)),
			new Cell(-2, 2, 2, Block.box(13.78, 0.00, 0.00, 16.00, 16.00, 2.22)),
			new Cell(-1, 2, -2, Block.box(0.00, 0.00, 13.78, 2.90, 16.00, 16.00)),
			new Cell(-1, 2, -1, Block.box(0.00, 0.00, 0.00, 2.90, 16.00, 2.90)),
			new Cell(-1, 2, 1, Block.box(0.00, 0.00, 13.10, 2.90, 16.00, 16.00)),
			new Cell(-1, 2, 2, Block.box(0.00, 0.00, 0.00, 2.90, 16.00, 2.22)),
			new Cell(1, 2, -2, Block.box(13.10, 0.00, 13.78, 16.00, 16.00, 16.00)),
			new Cell(1, 2, -1, Block.box(13.10, 0.00, 0.00, 16.00, 16.00, 2.90)),
			new Cell(1, 2, 1, Block.box(13.10, 0.00, 13.10, 16.00, 16.00, 16.00)),
			new Cell(1, 2, 2, Block.box(13.10, 0.00, 0.00, 16.00, 16.00, 2.22)),
			new Cell(2, 2, -2, Block.box(0.00, 0.00, 13.78, 2.22, 16.00, 16.00)),
			new Cell(2, 2, -1, Block.box(0.00, 0.00, 0.00, 2.22, 16.00, 2.90)),
			new Cell(2, 2, 1, Block.box(0.00, 0.00, 13.10, 2.22, 16.00, 16.00)),
			new Cell(2, 2, 2, Block.box(0.00, 0.00, 0.00, 2.22, 16.00, 2.22)),
			new Cell(-1, 3, -1, Block.box(0.47, 0.00, 0.47, 5.59, 16.00, 5.59)),
			new Cell(-1, 3, 1, Block.box(0.47, 0.00, 10.41, 5.59, 16.00, 15.53)),
			new Cell(1, 3, -1, Block.box(10.41, 0.00, 0.47, 15.53, 16.00, 5.59)),
			new Cell(1, 3, 1, Block.box(10.41, 0.00, 10.41, 15.53, 16.00, 15.53)),
			new Cell(-1, 4, -1, Block.box(3.17, 0.00, 3.17, 8.29, 16.00, 8.29)),
			new Cell(-1, 4, 1, Block.box(3.17, 0.00, 7.71, 8.29, 16.00, 12.83)),
			new Cell(1, 4, -1, Block.box(7.71, 0.00, 3.17, 12.83, 16.00, 8.29)),
			new Cell(1, 4, 1, Block.box(7.71, 0.00, 7.71, 12.83, 16.00, 12.83)),
			new Cell(-1, 5, -1, Block.box(5.86, 0.00, 5.86, 10.98, 16.00, 10.98)),
			new Cell(-1, 5, 1, Block.box(5.86, 0.00, 5.02, 10.98, 16.00, 10.14)),
			new Cell(1, 5, -1, Block.box(5.02, 0.00, 5.86, 10.14, 16.00, 10.98)),
			new Cell(1, 5, 1, Block.box(5.02, 0.00, 5.02, 10.14, 16.00, 10.14)),
			new Cell(-1, 6, -1, Block.box(8.56, 0.00, 8.56, 13.68, 16.00, 13.68)),
			new Cell(-1, 6, 1, Block.box(8.56, 0.00, 2.32, 13.68, 16.00, 7.44)),
			new Cell(1, 6, -1, Block.box(2.32, 0.00, 8.56, 7.44, 16.00, 13.68)),
			new Cell(1, 6, 1, Block.box(2.32, 0.00, 2.32, 7.44, 16.00, 7.44)),
			new Cell(-1, 7, -1, Block.box(11.25, 0.00, 11.25, 16.00, 16.00, 16.00)),
			new Cell(-1, 7, 1, Block.box(11.25, 0.00, 0.00, 16.00, 16.00, 4.75)),
			new Cell(1, 7, -1, Block.box(0.00, 0.00, 11.25, 4.75, 16.00, 16.00)),
			new Cell(1, 7, 1, Block.box(0.00, 0.00, 0.00, 4.75, 16.00, 4.75)),
			new Cell(-3, 8, -1, Block.box(14.24, 3.84, 11.62, 16.00, 4.96, 16.00)),
			new Cell(-3, 8, 0, Block.box(13.92, 3.20, 0.00, 16.00, 6.40, 16.00)),
			new Cell(-3, 8, 1, Block.box(14.24, 3.84, 0.00, 16.00, 4.96, 4.38)),
			new Cell(-2, 8, 0, Block.box(0.00, 3.20, 1.28, 16.00, 6.40, 14.72)),
			new Cell(-1, 8, -1, Shapes.or(Block.box(0.60, 3.84, 11.62, 2.52, 4.96, 16.00),
					Block.box(11.52, 0.00, 11.52, 16.00, 16.00, 16.00))),
			new Cell(-1, 8, 0, Shapes.or(Block.box(0.00, 3.20, 0.00, 16.00, 6.40, 16.00),
					Block.box(11.52, 0.00, 0.00, 16.00, 16.00, 0.64),
					Block.box(11.52, 0.00, 15.36, 16.00, 16.00, 16.00))),
			new Cell(-1, 8, 1, Shapes.or(Block.box(0.60, 3.84, 0.00, 2.52, 4.96, 4.38),
					Block.box(11.52, 0.00, 0.00, 16.00, 16.00, 4.48))),
			new Cell(0, 8, -1, Shapes.or(Block.box(0.00, 0.00, 11.52, 0.64, 16.00, 16.00),
					Block.box(15.36, 0.00, 11.52, 16.00, 16.00, 16.00))),
			new Cell(0, 8, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 0.64, 16.00, 0.64),
					Block.box(0.00, 0.00, 15.36, 0.64, 16.00, 16.00),
					Block.box(0.00, 3.20, 1.28, 16.00, 6.40, 14.72),
					Block.box(15.36, 0.00, 0.00, 16.00, 16.00, 0.64),
					Block.box(15.36, 0.00, 15.36, 16.00, 16.00, 16.00))),
			new Cell(0, 8, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 0.64, 16.00, 4.48),
					Block.box(15.36, 0.00, 0.00, 16.00, 16.00, 4.48))),
			new Cell(1, 8, -1, Shapes.or(Block.box(0.00, 0.00, 11.52, 4.48, 16.00, 16.00),
					Block.box(13.48, 3.84, 11.62, 15.40, 4.96, 16.00))),
			new Cell(1, 8, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 4.48, 16.00, 0.64),
					Block.box(0.00, 0.00, 15.36, 4.48, 16.00, 16.00),
					Block.box(0.00, 3.20, 0.00, 16.00, 6.40, 16.00))),
			new Cell(1, 8, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 4.48, 16.00, 4.48),
					Block.box(13.48, 3.84, 0.00, 15.40, 4.96, 4.38))),
			new Cell(2, 8, 0, Block.box(0.00, 3.20, 1.28, 16.00, 6.40, 14.72)),
			new Cell(3, 8, -1, Block.box(0.00, 3.84, 11.62, 1.76, 4.96, 16.00)),
			new Cell(3, 8, 0, Block.box(0.00, 3.20, 0.00, 2.08, 6.40, 16.00)),
			new Cell(3, 8, 1, Block.box(0.00, 3.84, 0.00, 1.76, 4.96, 4.38)),
			new Cell(-2, 9, -1, Block.box(14.24, 13.44, 11.62, 16.00, 14.56, 16.00)),
			new Cell(-2, 9, 0, Block.box(13.92, 12.80, 0.00, 16.00, 16.00, 16.00)),
			new Cell(-2, 9, 1, Block.box(14.24, 13.44, 0.00, 16.00, 14.56, 4.38)),
			new Cell(-1, 9, -1, Block.box(11.52, 0.00, 11.52, 16.00, 16.00, 16.00)),
			new Cell(-1, 9, 0, Shapes.or(Block.box(0.00, 12.80, 0.00, 16.00, 16.00, 13.44),
					Block.box(11.52, 0.00, 0.00, 16.00, 14.40, 0.64),
					Block.box(11.52, 0.00, 15.05, 16.00, 16.00, 16.00))),
			new Cell(-1, 9, 1, Block.box(11.52, 0.00, 0.00, 16.00, 16.00, 4.48)),
			new Cell(0, 9, -1, Shapes.or(Block.box(0.00, 0.00, 11.52, 0.95, 16.00, 16.00),
					Block.box(15.05, 0.00, 11.52, 16.00, 16.00, 16.00))),
			new Cell(0, 9, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 0.64, 14.40, 0.64),
					Block.box(0.00, 0.00, 15.36, 0.64, 14.40, 16.00),
					Block.box(0.00, 12.80, 0.00, 16.00, 16.00, 16.00),
					Block.box(15.36, 0.00, 0.00, 16.00, 14.40, 0.64),
					Block.box(15.36, 0.00, 15.36, 16.00, 14.40, 16.00))),
			new Cell(0, 9, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 0.95, 16.00, 4.48),
					Block.box(15.05, 0.00, 0.00, 16.00, 16.00, 4.48))),
			new Cell(1, 9, -1, Block.box(0.00, 0.00, 11.52, 4.48, 16.00, 16.00)),
			new Cell(1, 9, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 4.48, 16.00, 0.95),
					Block.box(0.00, 0.00, 15.05, 4.48, 16.00, 16.00),
					Block.box(0.00, 12.80, 2.56, 16.00, 16.00, 13.44))),
			new Cell(1, 9, 1, Block.box(0.00, 0.00, 0.00, 4.48, 16.00, 4.48)),
			new Cell(2, 9, -1, Block.box(0.00, 13.44, 11.62, 1.76, 14.56, 16.00)),
			new Cell(2, 9, 0, Block.box(0.00, 12.80, 0.00, 2.08, 16.00, 16.00)),
			new Cell(2, 9, 1, Block.box(0.00, 13.44, 0.00, 1.76, 14.56, 4.38)),
			new Cell(-1, 10, -1, Block.box(15.24, 0.00, 15.24, 16.00, 16.00, 16.00)),
			new Cell(-1, 10, 0, Shapes.or(Block.box(15.24, 0.00, 0.00, 16.00, 16.00, 4.36),
					Block.box(15.24, 0.00, 11.64, 16.00, 16.00, 16.00))),
			new Cell(-1, 10, 1, Block.box(15.24, 0.00, 0.00, 16.00, 16.00, 0.76)),
			new Cell(0, 10, -1, Shapes.or(Block.box(0.00, 0.00, 15.24, 4.36, 16.00, 16.00),
					Block.box(11.64, 0.00, 15.24, 16.00, 16.00, 16.00))),
			new Cell(0, 10, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 4.36, 16.00, 4.36),
					Block.box(0.00, 0.00, 11.64, 4.36, 16.00, 16.00),
					Block.box(11.64, 0.00, 0.00, 16.00, 16.00, 4.36),
					Block.box(11.64, 0.00, 11.64, 16.00, 16.00, 16.00))),
			new Cell(0, 10, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 4.36, 16.00, 0.76),
					Block.box(11.64, 0.00, 0.00, 16.00, 16.00, 0.76))),
			new Cell(1, 10, -1, Block.box(0.00, 0.00, 15.24, 0.76, 16.00, 16.00)),
			new Cell(1, 10, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 0.76, 16.00, 4.36),
					Block.box(0.00, 0.00, 11.64, 0.76, 16.00, 16.00))),
			new Cell(1, 10, 1, Block.box(0.00, 0.00, 0.00, 0.76, 16.00, 0.76)),
			new Cell(0, 11, 0, Block.box(3.89, 0.00, 3.89, 12.11, 8.00, 12.11)));

	/** A terminal tower: the whole pull on one side, stayed back against it. */
	private static final List<Cell> TERMINAL_CELLS = List.of(
			new Cell(-3, 0, 3, Block.box(15.44, 0.00, 4.56, 16.00, 1.60, 9.68)),
			new Cell(-2, 0, -2, Shapes.or(Block.box(6.56, 0.00, 6.56, 12.64, 1.60, 12.64),
					Block.box(8.39, 0.00, 8.39, 13.51, 16.00, 13.51))),
			new Cell(-2, 0, 2, Shapes.or(Block.box(6.56, 0.00, 3.36, 12.64, 1.60, 9.44),
					Block.box(8.39, 0.00, 2.49, 13.51, 16.00, 7.61))),
			new Cell(-2, 0, 3, Block.box(0.00, 0.00, 4.56, 4.56, 1.60, 9.68)),
			new Cell(2, 0, -2, Shapes.or(Block.box(2.49, 0.00, 8.39, 7.61, 16.00, 13.51),
					Block.box(3.36, 0.00, 6.56, 9.44, 1.60, 12.64))),
			new Cell(2, 0, 2, Shapes.or(Block.box(2.49, 0.00, 2.49, 7.61, 16.00, 7.61),
					Block.box(3.36, 0.00, 3.36, 9.44, 1.60, 9.44))),
			new Cell(2, 0, 3, Block.box(11.44, 0.00, 4.56, 16.00, 1.60, 9.68)),
			new Cell(3, 0, 3, Block.box(0.00, 0.00, 4.56, 0.56, 1.60, 9.68)),
			new Cell(-2, 1, -2, Block.box(11.08, 0.00, 11.08, 16.00, 16.00, 16.00)),
			new Cell(-2, 1, 2, Block.box(11.08, 0.00, 0.00, 16.00, 16.00, 4.92)),
			new Cell(2, 1, -2, Block.box(0.00, 0.00, 11.08, 4.92, 16.00, 16.00)),
			new Cell(2, 1, 2, Block.box(0.00, 0.00, 0.00, 4.92, 16.00, 4.92)),
			new Cell(-2, 2, -2, Block.box(13.78, 0.00, 13.78, 16.00, 16.00, 16.00)),
			new Cell(-2, 2, -1, Block.box(13.78, 0.00, 0.00, 16.00, 16.00, 2.90)),
			new Cell(-2, 2, 1, Block.box(13.78, 0.00, 13.10, 16.00, 16.00, 16.00)),
			new Cell(-2, 2, 2, Block.box(13.78, 0.00, 0.00, 16.00, 16.00, 2.22)),
			new Cell(-1, 2, -2, Block.box(0.00, 0.00, 13.78, 2.90, 16.00, 16.00)),
			new Cell(-1, 2, -1, Block.box(0.00, 0.00, 0.00, 2.90, 16.00, 2.90)),
			new Cell(-1, 2, 1, Block.box(0.00, 0.00, 13.10, 2.90, 16.00, 16.00)),
			new Cell(-1, 2, 2, Block.box(0.00, 0.00, 0.00, 2.90, 16.00, 2.22)),
			new Cell(1, 2, -2, Block.box(13.10, 0.00, 13.78, 16.00, 16.00, 16.00)),
			new Cell(1, 2, -1, Block.box(13.10, 0.00, 0.00, 16.00, 16.00, 2.90)),
			new Cell(1, 2, 1, Block.box(13.10, 0.00, 13.10, 16.00, 16.00, 16.00)),
			new Cell(1, 2, 2, Block.box(13.10, 0.00, 0.00, 16.00, 16.00, 2.22)),
			new Cell(2, 2, -2, Block.box(0.00, 0.00, 13.78, 2.22, 16.00, 16.00)),
			new Cell(2, 2, -1, Block.box(0.00, 0.00, 0.00, 2.22, 16.00, 2.90)),
			new Cell(2, 2, 1, Block.box(0.00, 0.00, 13.10, 2.22, 16.00, 16.00)),
			new Cell(2, 2, 2, Block.box(0.00, 0.00, 0.00, 2.22, 16.00, 2.22)),
			new Cell(-1, 3, -1, Block.box(0.47, 0.00, 0.47, 5.59, 16.00, 5.59)),
			new Cell(-1, 3, 1, Block.box(0.47, 0.00, 10.41, 5.59, 16.00, 15.53)),
			new Cell(1, 3, -1, Block.box(10.41, 0.00, 0.47, 15.53, 16.00, 5.59)),
			new Cell(1, 3, 1, Block.box(10.41, 0.00, 10.41, 15.53, 16.00, 15.53)),
			new Cell(-1, 4, -1, Block.box(3.17, 0.00, 3.17, 8.29, 16.00, 8.29)),
			new Cell(-1, 4, 1, Block.box(3.17, 0.00, 7.71, 8.29, 16.00, 12.83)),
			new Cell(1, 4, -1, Block.box(7.71, 0.00, 3.17, 12.83, 16.00, 8.29)),
			new Cell(1, 4, 1, Block.box(7.71, 0.00, 7.71, 12.83, 16.00, 12.83)),
			new Cell(-1, 5, -1, Block.box(5.86, 0.00, 5.86, 10.98, 16.00, 10.98)),
			new Cell(-1, 5, 1, Block.box(5.86, 0.00, 5.02, 10.98, 16.00, 10.14)),
			new Cell(1, 5, -1, Block.box(5.02, 0.00, 5.86, 10.14, 16.00, 10.98)),
			new Cell(1, 5, 1, Block.box(5.02, 0.00, 5.02, 10.14, 16.00, 10.14)),
			new Cell(-1, 6, -1, Block.box(8.56, 0.00, 8.56, 13.68, 16.00, 13.68)),
			new Cell(-1, 6, 1, Block.box(8.56, 0.00, 2.32, 13.68, 16.00, 7.44)),
			new Cell(1, 6, -1, Block.box(2.32, 0.00, 8.56, 7.44, 16.00, 13.68)),
			new Cell(1, 6, 1, Block.box(2.32, 0.00, 2.32, 7.44, 16.00, 7.44)),
			new Cell(-1, 7, -1, Block.box(11.25, 0.00, 11.25, 16.00, 16.00, 16.00)),
			new Cell(-1, 7, 1, Block.box(11.25, 0.00, 0.00, 16.00, 16.00, 4.75)),
			new Cell(1, 7, -1, Block.box(0.00, 0.00, 11.25, 4.75, 16.00, 16.00)),
			new Cell(1, 7, 1, Block.box(0.00, 0.00, 0.00, 4.75, 16.00, 4.75)),
			new Cell(-3, 8, -1, Block.box(14.24, 3.84, 11.62, 16.00, 4.96, 16.00)),
			new Cell(-3, 8, 0, Block.box(13.92, 3.20, 0.00, 16.00, 6.40, 14.72)),
			new Cell(-2, 8, 0, Block.box(0.00, 3.20, 1.28, 16.00, 6.40, 14.72)),
			new Cell(-1, 8, -1, Shapes.or(Block.box(0.60, 3.84, 11.62, 2.52, 4.96, 16.00),
					Block.box(11.52, 0.00, 11.52, 16.00, 16.00, 16.00))),
			new Cell(-1, 8, 0, Shapes.or(Block.box(0.00, 3.20, 0.00, 16.00, 6.40, 14.72),
					Block.box(11.52, 0.00, 0.00, 16.00, 16.00, 0.64),
					Block.box(11.52, 0.00, 15.36, 16.00, 16.00, 16.00))),
			new Cell(-1, 8, 1, Block.box(11.52, 0.00, 0.00, 16.00, 16.00, 4.48)),
			new Cell(0, 8, -1, Shapes.or(Block.box(0.00, 0.00, 11.52, 0.64, 16.00, 16.00),
					Block.box(15.36, 0.00, 11.52, 16.00, 16.00, 16.00))),
			new Cell(0, 8, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 0.64, 16.00, 0.64),
					Block.box(0.00, 0.00, 15.36, 0.64, 16.00, 16.00),
					Block.box(0.00, 3.20, 1.28, 16.00, 6.40, 14.72),
					Block.box(15.36, 0.00, 0.00, 16.00, 16.00, 0.64),
					Block.box(15.36, 0.00, 15.36, 16.00, 16.00, 16.00))),
			new Cell(0, 8, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 0.64, 16.00, 4.48),
					Block.box(15.36, 0.00, 0.00, 16.00, 16.00, 4.48))),
			new Cell(1, 8, -1, Shapes.or(Block.box(0.00, 0.00, 11.52, 4.48, 16.00, 16.00),
					Block.box(13.48, 3.84, 11.62, 15.40, 4.96, 16.00))),
			new Cell(1, 8, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 4.48, 16.00, 0.64),
					Block.box(0.00, 0.00, 15.36, 4.48, 16.00, 16.00),
					Block.box(0.00, 3.20, 0.00, 16.00, 6.40, 14.72))),
			new Cell(1, 8, 1, Block.box(0.00, 0.00, 0.00, 4.48, 16.00, 4.48)),
			new Cell(2, 8, 0, Block.box(0.00, 3.20, 1.28, 16.00, 6.40, 14.72)),
			new Cell(3, 8, -1, Block.box(0.00, 3.84, 11.62, 1.76, 4.96, 16.00)),
			new Cell(3, 8, 0, Block.box(0.00, 3.20, 0.00, 2.08, 6.40, 14.72)),
			new Cell(-2, 9, -1, Block.box(14.24, 13.44, 11.62, 16.00, 14.56, 16.00)),
			new Cell(-2, 9, 0, Block.box(13.92, 12.80, 0.00, 16.00, 16.00, 13.44)),
			new Cell(-1, 9, -1, Block.box(11.52, 0.00, 11.52, 16.00, 16.00, 16.00)),
			new Cell(-1, 9, 0, Shapes.or(Block.box(0.00, 12.80, 0.00, 16.00, 16.00, 13.44),
					Block.box(11.52, 0.00, 0.00, 16.00, 14.40, 0.64),
					Block.box(11.52, 0.00, 15.05, 16.00, 16.00, 16.00))),
			new Cell(-1, 9, 1, Block.box(11.52, 0.00, 0.00, 16.00, 16.00, 4.48)),
			new Cell(0, 9, -1, Shapes.or(Block.box(0.00, 0.00, 11.52, 0.95, 16.00, 16.00),
					Block.box(15.05, 0.00, 11.52, 16.00, 16.00, 16.00))),
			new Cell(0, 9, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 0.64, 14.40, 0.64),
					Block.box(0.00, 0.00, 15.36, 0.64, 14.40, 16.00),
					Block.box(0.00, 12.80, 0.00, 16.00, 16.00, 16.00),
					Block.box(15.36, 0.00, 0.00, 16.00, 14.40, 0.64),
					Block.box(15.36, 0.00, 15.36, 16.00, 14.40, 16.00))),
			new Cell(0, 9, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 0.95, 16.00, 4.48),
					Block.box(15.05, 0.00, 0.00, 16.00, 16.00, 4.48))),
			new Cell(1, 9, -1, Block.box(0.00, 0.00, 11.52, 4.48, 16.00, 16.00)),
			new Cell(1, 9, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 4.48, 16.00, 0.95),
					Block.box(0.00, 0.00, 15.05, 4.48, 16.00, 16.00),
					Block.box(0.00, 12.80, 2.56, 16.00, 16.00, 13.44))),
			new Cell(1, 9, 1, Block.box(0.00, 0.00, 0.00, 4.48, 16.00, 4.48)),
			new Cell(2, 9, -1, Block.box(0.00, 13.44, 11.62, 1.76, 14.56, 16.00)),
			new Cell(2, 9, 0, Block.box(0.00, 12.80, 0.00, 2.08, 16.00, 13.44)),
			new Cell(-1, 10, -1, Block.box(15.24, 0.00, 15.24, 16.00, 16.00, 16.00)),
			new Cell(-1, 10, 0, Shapes.or(Block.box(15.24, 0.00, 0.00, 16.00, 16.00, 4.36),
					Block.box(15.24, 0.00, 11.64, 16.00, 16.00, 16.00))),
			new Cell(-1, 10, 1, Block.box(15.24, 0.00, 0.00, 16.00, 16.00, 0.76)),
			new Cell(0, 10, -1, Shapes.or(Block.box(0.00, 0.00, 15.24, 4.36, 16.00, 16.00),
					Block.box(11.64, 0.00, 15.24, 16.00, 16.00, 16.00))),
			new Cell(0, 10, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 4.36, 16.00, 4.36),
					Block.box(0.00, 0.00, 11.64, 4.36, 16.00, 16.00),
					Block.box(11.64, 0.00, 0.00, 16.00, 16.00, 4.36),
					Block.box(11.64, 0.00, 11.64, 16.00, 16.00, 16.00))),
			new Cell(0, 10, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 4.36, 16.00, 0.76),
					Block.box(11.64, 0.00, 0.00, 16.00, 16.00, 0.76))),
			new Cell(1, 10, -1, Block.box(0.00, 0.00, 15.24, 0.76, 16.00, 16.00)),
			new Cell(1, 10, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 0.76, 16.00, 4.36),
					Block.box(0.00, 0.00, 11.64, 0.76, 16.00, 16.00))),
			new Cell(1, 10, 1, Block.box(0.00, 0.00, 0.00, 0.76, 16.00, 0.76)),
			new Cell(0, 11, 0, Block.box(3.89, 0.00, 3.89, 12.11, 8.00, 12.11)));

	private final TowerSpec spec;

	public LatticeTowerBlock(Properties properties, TowerSpec spec) {
		super(properties.sound(SoundType.METAL));
		this.spec = spec;
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	public TowerSpec spec() {
		return spec;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shellShape(state);
	}

	@Override
	public List<Cell> shellCells() {
		return switch (spec.duty()) {
			case SUSPENSION -> SUSPENSION_CELLS;
			case TENSION -> TENSION_CELLS;
			case TERMINAL -> TERMINAL_CELLS;
		};
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
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return 0;
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Nullable @Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LatticeTowerBlockEntity(pos, state);
	}

	/** Nothing of its own to tick, but its cells are worth mending: a tower is a lot of them. */
	@Nullable @Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return (lvl, pos, blockState, blockEntity) -> MachineShell.heal(lvl, pos, blockState);
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
					&& level.getBlockEntity(pos) instanceof LatticeTowerBlockEntity tower) {
				Electricity.wireManager.removeConnectionsForInsulators(serverLevel, tower.getInsulatorIds());
			}
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
