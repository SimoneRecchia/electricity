package com.dooji.electricity.block;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A machine that fills more than the block it stands in, and the collision cut to fit it.
  *
 * <b>Which way the model's own frame points is the machine's to say</b>, through {@link #shellAuthored()}.
 */
public interface MachineShell {
	/** One cell of a machine, and exactly what the machine puts in it. */
	final class Cell {
		private final BlockPos offset;
		private final BlockPos[] turnedOffset = new BlockPos[4];
		private final VoxelShape[] turned = new VoxelShape[4];

		public Cell(int x, int y, int z, VoxelShape shape) {
			this.offset = new BlockPos(x, y, z);
			for (int quarters = 0; quarters < 4; quarters++) {
				this.turnedOffset[quarters] = MachineShell.turned(offset, quarters);
				this.turned[quarters] = MachineShell.turned(shape, quarters);
			}
		}

		/** Where this cell is, relative to the machine, after that many quarter turns. */
		public BlockPos at(int quarters) {
			return turnedOffset[quarters];
		}

		/** What the machine fills this cell with, turned the same way. */
		public VoxelShape shape(int quarters) {
			return turned[quarters];
		}

		/** Where this cell is relative to the machine, as authored: the key a table is composed on. */
		public BlockPos offset() {
			return offset;
		}

		/** Whether this is the machine's own cell, which is the one that never becomes a shell. */
		public boolean own() {
			return offset.equals(BlockPos.ZERO);
		}
	}

	/** Every cell this machine fills, its own first */
	List<Cell> shellCells();

	/** The same table for one state, for a machine with an optional part. */
	default List<Cell> shellCells(BlockState state) {
		return shellCells();
	}

	/** Which way this machine's model has been turned. */
	Direction shellFacing(BlockState state);

	/** Which way this machine's geometry was modelled, which is not always the way it is placed. */
	Direction shellAuthored();

	/** How many quarter turns take this machine's model onto the world, for this state. */
	default int shellTurns(BlockState state) {
		return ModelFacing.quarters(shellAuthored(), shellFacing(state));
	}

	/**
	 * The machine's own block's shape: the one cell of its table that stays a machine.
	 *
	 * Empty where the table has no cell of its own, and a lattice tower has none - its origin is the middle of
	 * a footprint 3.8 blocks across and there is nothing there. Shapes.block() was the fallback, so every
	 * tower in the world had an invisible solid cube standing on the ground in the middle of it. A machine
	 * with no cell of its own is still reached through its shells, which forward a click and a break to it.
	 */
	default VoxelShape shellShape(BlockState state) {
		int quarters = shellTurns(state);
		for (Cell cell : shellCells(state)) {
			if (cell.own()) return cell.shape(quarters);
		}

		return Shapes.empty();
	}

	/** Fills the cells, and mends any that are wrong. */
	static void place(Level level, BlockPos pos, BlockState state) {
		if (level.isClientSide || !(state.getBlock() instanceof MachineShell machine)) return;

		int quarters = machine.shellTurns(state);
		for (Cell cell : machine.shellCells(state)) {
			if (cell.own()) continue;

			BlockPos target = pos.offset(cell.at(quarters));
			if (!level.hasChunkAt(target)) continue;

			BlockState want = MachineShellBlock.pointingAt(pos, target);
			BlockState there = level.getBlockState(target);
			if (there != want && (there.canBeReplaced() || mine(level, pos, target, there))) {
				level.setBlock(target, want, Block.UPDATE_ALL);
			}
		}
	}

	/** Whether a cell already standing here is this machine's to overwrite. */
	private static boolean mine(Level level, BlockPos pos, BlockPos target, BlockState there) {
		if (!(there.getBlock() instanceof MachineShellBlock)) return false;

		BlockPos owner = hostOf(level, target);
		return owner == null || owner.equals(pos);
	}

	/**
	 * Takes back the cells that point at this machine, and nothing else.
	  *
	 * Read off the cell's own back-pointer rather than through {@link #hostOf}: this runs from
	 * {@code onRemove}, and by then the chunk already holds the new state, so the machine asking is air
	 * and hostOf disowns every cell it has. Nothing was cleared, and only a machine whose cells touch its
	 * own block hid it - those go through MachineShellBlock.updateShape instead. A tower's do not touch
	 * it, so all seventy-eight of its cells stayed behind, solid and invisible.
	 */
	static void clear(Level level, BlockPos pos, BlockState state) {
		if (level.isClientSide || !(state.getBlock() instanceof MachineShell machine)) return;

		int quarters = machine.shellTurns(state);
		for (Cell cell : machine.shellCells(state)) {
			if (cell.own()) continue;

			BlockPos target = pos.offset(cell.at(quarters));
			if (!level.hasChunkAt(target)) continue;

			BlockState there = level.getBlockState(target);
			if (there.getBlock() instanceof MachineShellBlock && pos.equals(MachineShellBlock.pointsAt(target, there))) {
				level.removeBlock(target, false);
			}
		}
	}

	/** How often a ticking machine checks its cells, in ticks. */
	int HEAL_INTERVAL = 40;

	/** Puts right any cell that has gone missing or gone stale, for a machine that ticks. */
	static void heal(Level level, BlockPos pos, BlockState state) {
		if (level.isClientSide || level.getGameTime() % HEAL_INTERVAL != 0) return;

		place(level, pos, state);
	}

	/** The machine a cell belongs to, or null if it is not a cell or its machine has gone. */
	static BlockPos hostOf(BlockGetter level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof MachineShellBlock)) return null;

		BlockPos host = MachineShellBlock.pointsAt(pos, state);
		BlockState hostState = level.getBlockState(host);
		if (!(hostState.getBlock() instanceof MachineShell machine)) return null;

		return claims(machine, hostState, host, pos) ? host : null;
	}

	/** Where a click on this block was really aimed. */
	static BlockPos hostOr(BlockGetter level, BlockPos pos) {
		BlockPos host = hostOf(level, pos);
		return host == null ? pos : host;
	}

	/** Whether a machine at {@code host} says the cell at {@code pos} is one of its own. */
	private static boolean claims(MachineShell machine, BlockState state, BlockPos host, BlockPos pos) {
		int quarters = machine.shellTurns(state);
		for (Cell cell : machine.shellCells(state)) {
			if (!cell.own() && host.offset(cell.at(quarters)).equals(pos)) return true;
		}

		return false;
	}

	/**
	 * How far from its machine a cell can be.
	 *
	 * Sized by the biggest machine in the mod, which is a lattice tower: eleven cells up and three out.
	 * MachineShellBlock stores the offset as {@code value + REACH_SIDE}, so raising either renumbers every
	 * shell already in a world - they fail canSurvive, delete themselves, and the machine's ticker puts
	 * them back within two seconds.
	 */
	int REACH_UP = 11;
	int REACH_SIDE = 3;

	/** A model-space offset turned by quarter turns. */
	static BlockPos turned(BlockPos offset, int quarters) {
		return switch (quarters) {
			case 1 -> new BlockPos(offset.getZ(), offset.getY(), -offset.getX());
			case 2 -> new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
			case 3 -> new BlockPos(-offset.getZ(), offset.getY(), offset.getX());
			default -> offset;
		};
	}

	/** The same turn for a shape inside its cell. */
	static VoxelShape turned(VoxelShape shape, int quarters) {
		if (quarters == 0) return shape;

		VoxelShape result = Shapes.empty();
		for (AABB box : shape.toAabbs()) {
			result = Shapes.or(result, switch (quarters) {
				case 1 -> Shapes.box(box.minZ, box.minY, 1.0 - box.maxX, box.maxZ, box.maxY, 1.0 - box.minX);
				case 2 -> Shapes.box(1.0 - box.maxX, box.minY, 1.0 - box.maxZ, 1.0 - box.minX, box.maxY, 1.0 - box.minZ);
				default -> Shapes.box(1.0 - box.maxZ, box.minY, box.minX, 1.0 - box.minZ, box.maxY, box.maxX);
			});
		}

		return result;
	}
}
