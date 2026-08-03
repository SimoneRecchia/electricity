package com.dooji.electricity.block;

import com.dooji.electricity.main.Electricity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A machine that fills more than the block it stands in, and the two operations that keeps honest.
 *
 * The cells are declared in the model's own frame - authored facing north, exactly as the geometry is -
 * and turned onto the machine's facing here, so a machine and its collision cannot disagree about which
 * way round they are. That is not tidiness: the cabin's body is three deep in one axis and one in the
 * other, and a rotation applied to the model but not to the cells would have put the solid part beside
 * the machine rather than inside it.
 *
 * @see MachineShellBlock for why the cells have to be blocks rather than one oversized shape
 */
public interface MachineShell {
	/** One cell a machine's body reaches into, in the model's own frame, and how much of it is filled. */
	record Cell(int x, int y, int z, MachineShellBlock.Fill fill) {
	}

	/**
	 * The cells besides its own that this machine fills, before rotation.
	 *
	 * Measured off the model rather than guessed - {@code tools/check_hitboxes.py} reads both and says
	 * where a body reaches into a cell that nothing claims.
	 */
	List<Cell> shellCells();

	/** Which way this machine's model has been turned. */
	Direction shellFacing(BlockState state);

	/**
	 * Fills the cells, and only the ones that are free.
	 *
	 * A machine hemmed in by terrain gets the collision it has room for rather than being refused
	 * placement: refusing would mean a substation could not be put against a hillside, which is where
	 * substations go.
	 *
	 * Called on placement and again while the machine ticks, which is not belt and braces - it is the
	 * only thing that makes a machine placed before any of this existed solid. A world full of cabins
	 * that are one block of collision under three blocks of steel does not fix itself at placement,
	 * because they were placed long ago. Cheap enough to repeat: it only writes a cell that is empty.
	 *
	 * Chunks are checked before they are touched. Reaching into an unloading chunk is what stopped a world
	 * from finishing its save once already, and a cabin's cells reach into the chunk next door.
	 */
	static void place(Level level, BlockPos pos, BlockState state) {
		if (level.isClientSide || !(state.getBlock() instanceof MachineShell machine)) return;

		BlockState shell = Electricity.MACHINE_SHELL_BLOCK.get().defaultBlockState();
		Direction facing = machine.shellFacing(state);
		for (Cell cell : machine.shellCells()) {
			BlockPos target = pos.offset(turned(new BlockPos(cell.x(), cell.y(), cell.z()), facing));
			if (!level.hasChunkAt(target) || !level.getBlockState(target).canBeReplaced()) continue;

			level.setBlock(target, shell.setValue(MachineShellBlock.FILL, cell.fill().turned(facing)), Block.UPDATE_ALL);
		}
	}

	/** Takes back the cells this machine filled, and nothing else. */
	static void clear(Level level, BlockPos pos, BlockState state) {
		if (level.isClientSide || !(state.getBlock() instanceof MachineShell machine)) return;

		Direction facing = machine.shellFacing(state);
		for (Cell cell : machine.shellCells()) {
			BlockPos target = pos.offset(turned(new BlockPos(cell.x(), cell.y(), cell.z()), facing));
			if (level.hasChunkAt(target) && level.getBlockState(target).getBlock() instanceof MachineShellBlock) {
				level.removeBlock(target, false);
			}
		}
	}

	/**
	 * How often a ticking machine checks that its cells are still there, in ticks.
	 *
	 * Two seconds. Slow enough to cost nothing and fast enough that a player who has just loaded an old
	 * world does not notice the gap - and it is only ever eight state reads, because a cell that is
	 * already filled is not written again.
	 */
	int HEAL_INTERVAL = 40;

	/** Fills any cell that has gone missing, for a machine that ticks. */
	static void heal(Level level, BlockPos pos, BlockState state) {
		if (level.isClientSide || level.getGameTime() % HEAL_INTERVAL != 0) return;

		place(level, pos, state);
	}

	/**
	 * The machine a cell belongs to, or null.
	 *
	 * Searched rather than remembered, and the box searched is the largest any machine here claims: a
	 * pole reaches five above itself and a cabin one to each side. Bounded, so this stays cheap enough
	 * to answer on a neighbour update.
	 */
	static BlockPos hostOf(BlockGetter level, BlockPos pos) {
		for (int dy = 0; dy <= REACH_DOWN; dy++) {
			for (int dx = -REACH_SIDE; dx <= REACH_SIDE; dx++) {
				for (int dz = -REACH_SIDE; dz <= REACH_SIDE; dz++) {
					BlockPos candidate = pos.offset(dx, -dy, dz);
					BlockState state = level.getBlockState(candidate);
					if (!(state.getBlock() instanceof MachineShell machine)) continue;
					if (claims(machine, state, candidate, pos)) return candidate;
				}
			}
		}

		return null;
	}

	/** Whether a machine at {@code host} says the cell at {@code pos} is one of its own. */
	private static boolean claims(MachineShell machine, BlockState state, BlockPos host, BlockPos pos) {
		Direction facing = machine.shellFacing(state);
		for (Cell cell : machine.shellCells()) {
			if (host.offset(turned(new BlockPos(cell.x(), cell.y(), cell.z()), facing)).equals(pos)) return true;
		}

		return false;
	}

	/** How far from a cell its machine can be. The tallest and widest claim in the mod, and no further. */
	int REACH_DOWN = 6;
	int REACH_SIDE = 1;

	/**
	 * A model-space offset turned onto a facing.
	 *
	 * The same quarter turns the renderers use: north is no rotation, because that is the facing the
	 * geometry is authored at, and the rest follow round.
	 */
	static BlockPos turned(BlockPos offset, Direction facing) {
		return switch (facing) {
			case SOUTH -> new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
			case WEST -> new BlockPos(offset.getZ(), offset.getY(), -offset.getX());
			case EAST -> new BlockPos(-offset.getZ(), offset.getY(), offset.getX());
			default -> offset;
		};
	}

	/** The same turn for a face, which is what a partly filled cell needs so its slab lands the right way. */
	static Direction turned(Direction side, Direction facing) {
		if (side.getAxis() == Direction.Axis.Y) return side;

		return switch (facing) {
			case SOUTH -> side.getOpposite();
			case WEST -> side.getCounterClockWise();
			case EAST -> side.getClockWise();
			default -> side;
		};
	}
}
