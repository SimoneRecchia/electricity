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
 * <h2>Cut to fit, not rounded off</h2>
 *
 * Each cell carries the exact part of itself the machine's body occupies, taken from the model's own
 * geometry - so a cabin's roof stops 10.03 pixels up because that is where the steel stops, and the
 * cells beside the body are filled to 6.34 because that is how far the body reaches into them. Whole
 * cells and half slabs were tried first and both are wrong the same way: they make the player collide
 * with air, which is worse than falling through, because at least falling through looks like a bug.
 *
 * The numbers are not typed in by hand. {@code tools/check_hitboxes.py} cuts them out of the OBJ and
 * says, to a hundredth of a pixel, where a table and its model have drifted apart.
 *
 * <h2>Turning</h2>
 *
 * The cells are declared in the model's own frame - authored facing north, exactly as the geometry is -
 * and turned onto the machine's facing here, both the offset and the shape inside it. That is not
 * tidiness: the cabin's body is three cells deep in one axis and one in the other, so a rotation
 * applied to the model but not to the cells would put the solid part beside the machine rather than
 * inside it.
 *
 * @see MachineShellBlock for why the cells have to be blocks rather than one oversized shape
 */
public interface MachineShell {
	/**
	 * One cell of a machine, and exactly what the machine puts in it.
	 *
	 * The shape is in the cell's own coordinates and in the model's frame, so both it and the offset have
	 * to be turned with the machine. All four turns of both are worked out once, when the machine's table
	 * is first read, because a cell is asked where it is and what it holds on every collision test a
	 * player makes against it, and neither answer should cost an allocation.
	 */
	final class Cell {
		private final BlockPos offset;
		private final BlockPos[] turnedOffset = new BlockPos[4];
		private final VoxelShape[] turned = new VoxelShape[4];

		public Cell(int x, int y, int z, VoxelShape shape) {
			this.offset = new BlockPos(x, y, z);
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				this.turnedOffset[facing.get2DDataValue()] = MachineShell.turned(offset, facing);
				this.turned[facing.get2DDataValue()] = MachineShell.turned(shape, facing);
			}
		}

		/** Where this cell is, relative to the machine, once the machine faces this way. */
		public BlockPos at(Direction facing) {
			return turnedOffset[facing.get2DDataValue()];
		}

		/** What the machine fills this cell with, turned the same way. */
		public VoxelShape shape(Direction facing) {
			return turned[facing.get2DDataValue()];
		}

		/** Whether this is the machine's own cell, which is the one that never becomes a shell. */
		public boolean own() {
			return offset.equals(BlockPos.ZERO);
		}
	}

	/**
	 * Every cell this machine fills, its own first, and what it puts in each.
	 *
	 * Cut from the model rather than guessed - {@code tools/check_hitboxes.py} reads both and prints the
	 * table, so the model stays the authority and the Java only repeats it.
	 */
	List<Cell> shellCells();

	/** Which way this machine's model has been turned. */
	Direction shellFacing(BlockState state);

	/** The machine's own block's shape, which is the one cell of its table that stays a machine. */
	default VoxelShape shellShape(BlockState state) {
		Direction facing = shellFacing(state);
		for (Cell cell : shellCells()) {
			if (cell.own()) return cell.shape(facing);
		}

		return Shapes.block();
	}

	/**
	 * Fills the cells, and mends any that are wrong.
	 *
	 * A machine hemmed in by terrain gets the collision it has room for rather than being refused
	 * placement: refusing would mean a substation could not be put against a hillside, which is where
	 * substations go.
	 *
	 * Called on placement and again while the machine ticks, which is not belt and braces - it is the
	 * only thing that makes a machine placed by an older build of the mod match its model. A cabin whose
	 * cells were whole cubes cannot fix itself at placement, because it was placed long ago, so a cell
	 * that is standing but wrong is replaced and not only an empty one - see {@link #mine} for which
	 * standing cells count as this machine's. Cheap enough to repeat: nothing is written where the state
	 * is already right, which is nearly always.
	 *
	 * Chunks are checked before they are touched. Reaching into an unloading chunk is what stopped a
	 * world from finishing its save once already, and a cabin's cells reach into the chunk next door.
	 */
	static void place(Level level, BlockPos pos, BlockState state) {
		if (level.isClientSide || !(state.getBlock() instanceof MachineShell machine)) return;

		Direction facing = machine.shellFacing(state);
		for (Cell cell : machine.shellCells()) {
			if (cell.own()) continue;

			BlockPos target = pos.offset(cell.at(facing));
			if (!level.hasChunkAt(target)) continue;

			BlockState want = MachineShellBlock.pointingAt(pos, target);
			BlockState there = level.getBlockState(target);
			if (there != want && (there.canBeReplaced() || mine(level, pos, target, there))) {
				level.setBlock(target, want, Block.UPDATE_ALL);
			}
		}
	}

	/**
	 * Whether a cell already standing here is this machine's to overwrite.
	 *
	 * Its own, or nobody's. The second case is what mends a machine from an older build of the mod: the
	 * cells it left behind describe themselves in a way this one cannot read, so they come back as a cell
	 * belonging to nothing, and are replaced.
	 *
	 * The first case matters because two machines can be built into each other - nothing stops a cabin
	 * being placed two blocks from another one, and then they both want the cell between them. Whichever
	 * got there first keeps it. Letting the other one take it would have them trading it back and forth
	 * every couple of seconds for as long as both stood.
	 */
	private static boolean mine(Level level, BlockPos pos, BlockPos target, BlockState there) {
		if (!(there.getBlock() instanceof MachineShellBlock)) return false;

		BlockPos owner = hostOf(level, target);
		return owner == null || owner.equals(pos);
	}

	/** Takes back the cells that point at this machine, and nothing else. */
	static void clear(Level level, BlockPos pos, BlockState state) {
		if (level.isClientSide || !(state.getBlock() instanceof MachineShell machine)) return;

		Direction facing = machine.shellFacing(state);
		for (Cell cell : machine.shellCells()) {
			if (cell.own()) continue;

			BlockPos target = pos.offset(cell.at(facing));
			if (level.hasChunkAt(target) && pos.equals(hostOf(level, target))) {
				level.removeBlock(target, false);
			}
		}
	}

	/**
	 * How often a ticking machine checks its cells, in ticks.
	 *
	 * Two seconds. Slow enough to cost nothing and fast enough that a player who has just loaded an old
	 * world does not notice the gap - and most of the time it is a handful of state reads that find
	 * everything already as it should be.
	 */
	int HEAL_INTERVAL = 40;

	/** Puts right any cell that has gone missing or gone stale, for a machine that ticks. */
	static void heal(Level level, BlockPos pos, BlockState state) {
		if (level.isClientSide || level.getGameTime() % HEAL_INTERVAL != 0) return;

		place(level, pos, state);
	}

	/**
	 * The machine a cell belongs to, or null if it is not a cell or its machine has gone.
	 *
	 * A shell carries the way back to its machine in its own state, so this is one block read rather
	 * than a search - but it is still checked rather than trusted. A machine that has been replaced by
	 * something else, or turned, no longer claims the cell, and then the cell is an orphan that takes
	 * itself away.
	 */
	static BlockPos hostOf(BlockGetter level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof MachineShellBlock)) return null;

		BlockPos host = MachineShellBlock.pointsAt(pos, state);
		BlockState hostState = level.getBlockState(host);
		if (!(hostState.getBlock() instanceof MachineShell machine)) return null;

		return claims(machine, hostState, host, pos) ? host : null;
	}

	/**
	 * Where a click on this block was really aimed.
	 *
	 * Most of what a player can see of a cabin is cells rather than the machine itself, and a player
	 * pointing at the middle of a cabin means the cabin. So anything that acts on a machine - opening
	 * it, wiring it, mining it - asks this first and gets the machine back whichever part was hit.
	 */
	static BlockPos hostOr(BlockGetter level, BlockPos pos) {
		BlockPos host = hostOf(level, pos);
		return host == null ? pos : host;
	}

	/** Whether a machine at {@code host} says the cell at {@code pos} is one of its own. */
	private static boolean claims(MachineShell machine, BlockState state, BlockPos host, BlockPos pos) {
		Direction facing = machine.shellFacing(state);
		for (Cell cell : machine.shellCells()) {
			if (!cell.own() && host.offset(cell.at(facing)).equals(pos)) return true;
		}

		return false;
	}

	/** How far from its machine a cell can be. The tallest and widest claim in the mod, and no further. */
	int REACH_UP = 6;
	int REACH_SIDE = 2;

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

	/**
	 * The same turn for a shape inside its cell.
	 *
	 * The rotation is about the machine's own vertical axis, and a quarter turn about it carries cell
	 * centres onto cell centres - so within a cell it is a quarter turn about that cell's own centre,
	 * which is all this has to do.
	 */
	static VoxelShape turned(VoxelShape shape, Direction facing) {
		if (facing == Direction.NORTH) return shape;

		VoxelShape result = Shapes.empty();
		for (AABB box : shape.toAabbs()) {
			result = Shapes.or(result, switch (facing) {
				case SOUTH -> Shapes.box(1.0 - box.maxX, box.minY, 1.0 - box.maxZ, 1.0 - box.minX, box.maxY, 1.0 - box.minZ);
				case WEST -> Shapes.box(box.minZ, box.minY, 1.0 - box.maxX, box.maxZ, box.maxY, 1.0 - box.minX);
				default -> Shapes.box(1.0 - box.maxZ, box.minY, box.minX, 1.0 - box.minZ, box.maxY, box.maxX);
			});
		}

		return result;
	}
}
