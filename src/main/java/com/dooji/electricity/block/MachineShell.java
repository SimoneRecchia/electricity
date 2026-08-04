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
 * The cells are declared in the model's own frame and turned onto the machine's facing here, both the
 * offset and the shape inside it. That is not tidiness: the cabin's body is three cells deep in one axis
 * and one in the other, so a rotation applied to the model but not to the cells would put the solid part
 * beside the machine rather than inside it.
 *
 * <b>Which way the model's own frame points is the machine's to say</b>, through
 * {@link #shellAuthored()}. Assuming north cost a rewrite: the cabin's geometry is modelled facing east,
 * so its collision stood at a right angle to the cabin for as long as this class has existed. The same
 * fact is what the renderer turns the model by, and it is declared once, on the block, so the two cannot
 * drift apart again - {@code tools/check_hitboxes.py} fails if a renderer works it out for itself.
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

	/**
	 * Which way this machine's geometry was modelled, which is not always the way it is placed.
	 *
	 * The mod's own models face north, because that is the default facing. The inherited ones face
	 * whichever way they happened to be modelled - the cabin and the power box east - and the renderer
	 * turns them from there. The cells have to be turned from the same place, and this is that place: the
	 * block says it once and the renderer reads it, rather than each working it out.
	 */
	Direction shellAuthored();

	/** How many quarter turns take this machine's model onto the world, for this state. */
	default int shellTurns(BlockState state) {
		return ModelFacing.quarters(shellAuthored(), shellFacing(state));
	}

	/** The machine's own block's shape, which is the one cell of its table that stays a machine. */
	default VoxelShape shellShape(BlockState state) {
		int quarters = shellTurns(state);
		for (Cell cell : shellCells()) {
			if (cell.own()) return cell.shape(quarters);
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

		int quarters = machine.shellTurns(state);
		for (Cell cell : machine.shellCells()) {
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

		int quarters = machine.shellTurns(state);
		for (Cell cell : machine.shellCells()) {
			if (cell.own()) continue;

			BlockPos target = pos.offset(cell.at(quarters));
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
		int quarters = machine.shellTurns(state);
		for (Cell cell : machine.shellCells()) {
			if (!cell.own() && host.offset(cell.at(quarters)).equals(pos)) return true;
		}

		return false;
	}

	/** How far from its machine a cell can be. The tallest and widest claim in the mod, and no further. */
	int REACH_UP = 6;
	int REACH_SIDE = 2;

	/**
	 * A model-space offset turned by quarter turns.
	 *
	 * Anticlockwise seen from above, which is what {@code Axis.YP} does with a positive angle, and so what
	 * the renderer does to the geometry.
	 */
	static BlockPos turned(BlockPos offset, int quarters) {
		return switch (quarters) {
			case 1 -> new BlockPos(offset.getZ(), offset.getY(), -offset.getX());
			case 2 -> new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
			case 3 -> new BlockPos(-offset.getZ(), offset.getY(), offset.getX());
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
