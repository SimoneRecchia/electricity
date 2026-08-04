package com.dooji.electricity.block;

import com.dooji.electricity.api.WorldConditions;
import com.dooji.electricity.api.power.DcCableSpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Follows the copper, and answers what a machine is actually wired to.
 *
 * This is what replaced a radius. An inverter used to sweep a sphere and claim whatever arrays were
 * inside it, which is not a connection - it is a coincidence, and it meant a plant could be laid out
 * any way at all and still work. Now a machine is wired to exactly what a continuous run of the right
 * gauge reaches, and the length of that run costs what a length of copper costs.
 *
 * <h2>Two rules it will not break</h2>
 *
 * It only ever reads positions the world already has in memory. Reaching into an unloading chunk is
 * what stopped a world from finishing its save once already: {@code getBlockState} on an absent chunk
 * loads it, and a load inside the shutdown drain never completes. So every step checks first, and an
 * array whose ground has gone out of memory reads as temporarily not there - which is what its lease
 * is for.
 *
 * And it asks {@link DcCableBlock} where a run reaches rather than deciding for itself, so what the
 * player sees connected and what the plant counts are the same thing by construction. A cable drawn
 * running into a machine that the machine does not count would be the worst of both.
 */
public final class DcNetwork {
	/**
	 * Runs one walk will follow before it gives up, as a backstop rather than a design limit.
	 *
	 * Four thousand is far past any plant anybody would lay by hand; the limit that actually binds is
	 * the volt drop, which has made the run useless long before this.
	 */
	private static final int MAX_RUNS = 4096;

	/** One block of a run the walk still has to follow. */
	private record Step(BlockPos pos, int runs, int buried) {
	}

	/**
	 * A machine the walk got to, and the run it took to get there.
	 *
	 * {@code from} is the machine's own face the run arrived at, because for some machines that decides
	 * whether the arrival is a connection at all: a row of modules has its leads at its two ends and
	 * nothing along its flanks, so copper up against a flank reaches it and wires nothing.
	 */
	public record Reach(BlockPos pos, int runs, int buried, Direction from) {
		/**
		 * Length of the run in metres.
		 *
		 * A block is ten metres throughout this mod, so a cable laid across five blocks is fifty metres
		 * of cable - which is the right order for a plant: a home run of a couple of hundred metres is
		 * ordinary, and it is exactly the length at which volt drop starts to matter.
		 */
		public double metres() {
			return runs * WorldConditions.METRES_PER_BLOCK;
		}

		/**
		 * How much of the run is in the ground, from nothing to all of it.
		 *
		 * A fraction rather than a count because that is how a cable is derated: it is only as good as
		 * its worst-cooled metre, so any burial at all pulls the rating down.
		 */
		public double buriedFraction() {
			return runs <= 0 ? 0.0 : (double) buried / runs;
		}
	}

	private DcNetwork() {
	}

	/**
	 * Every machine of interest a run of one gauge reaches from a starting machine, nearest first.
	 *
	 * Breadth first, so the first path found to each machine is the shortest one - which is also the one
	 * with the least resistance, since every block of cable is the same ten metres. A machine found once
	 * is not looked for again down a longer way round.
	 *
	 * The walk stops at machines rather than passing through them. That is not an optimisation: a
	 * combiner box is not a junction box, and current arriving at one leaves through its output and its
	 * fuses or not at all.
	 */
	public static List<Reach> reachable(Level level, BlockPos origin, DcCableSpec cable,
			Predicate<BlockState> wanted, int maxRuns) {
		List<Reach> found = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		Set<BlockPos> claimed = new HashSet<>();
		Deque<Step> frontier = new ArrayDeque<>();
		int budget = Math.min(MAX_RUNS, Math.max(1, maxRuns));

		for (BlockPos start : reaching(level, origin, cable)) {
			if (visited.add(start)) frontier.add(step(level, start, 0, 0));
		}

		while (!frontier.isEmpty() && visited.size() <= budget) {
			Step at = frontier.poll();
			BlockState state = level.getBlockState(at.pos());
			if (!(state.getBlock() instanceof DcCableBlock run)) continue;

			for (BlockPos target : reached(level, run, state, at.pos())) {
				if (target.equals(origin)) continue;

				BlockState there = level.getBlockState(target);
				if (there.getBlock() instanceof DcCableBlock next && next.spec() == cable) {
					if (at.runs() < budget && visited.add(target)) {
						frontier.add(step(level, target, at.runs(), at.buried()));
					}

					continue;
				}

				if (wanted.test(there) && claimed.add(target)) {
					found.add(new Reach(target, at.runs(), at.buried(), arrivedAt(at.pos(), target)));
				}
			}
		}

		return found;
	}

	/** One more block of run, counting whether it was in the ground. */
	private static Step step(Level level, BlockPos pos, int runs, int buried) {
		boolean inGround = level.getBlockState(pos).getValue(DcCableBlock.BURIED);
		return new Step(pos, runs + 1, buried + (inGround ? 1 : 0));
	}

	/**
	 * Which face of the machine the run came up against.
	 *
	 * Horizontally, because that is the only part that matters: a run climbing a bank onto a machine
	 * still arrives at one of its four sides, and the side is what a machine's own leads are arranged
	 * about. Null if the two are stacked, which no reaching run is.
	 */
	private static Direction arrivedAt(BlockPos cable, BlockPos machine) {
		return Direction.fromDelta(cable.getX() - machine.getX(), 0, cable.getZ() - machine.getZ());
	}

	/**
	 * The runs of one gauge that reach a machine, which is where a walk out of it begins.
	 *
	 * Asked from the cable's side rather than the machine's, because the cable is the thing that knows
	 * whether it is lying flat against the cabinet, stepping down off a bank onto it, or coming up a
	 * wall to it. Twelve positions, which are the twelve a run can reach from.
	 */
	private static List<BlockPos> reaching(Level level, BlockPos machine, DcCableSpec cable) {
		List<BlockPos> starts = new ArrayList<>();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos beside = machine.relative(direction);
			for (BlockPos candidate : new BlockPos[]{beside, beside.above(), beside.below()}) {
				if (!level.isLoaded(candidate)) continue;

				BlockState state = level.getBlockState(candidate);
				if (!(state.getBlock() instanceof DcCableBlock run) || run.spec() != cable) continue;
				if (reached(level, run, state, candidate).contains(machine)) starts.add(candidate);
			}
		}

		return starts;
	}

	/** Everywhere one run reaches, filtered down to what is in memory. */
	private static List<BlockPos> reached(Level level, DcCableBlock run, BlockState state, BlockPos pos) {
		List<BlockPos> targets = new ArrayList<>();
		run.collectReached(state, level, pos, target -> {
			if (level.isLoaded(target)) targets.add(target);
		});

		return targets;
	}
}
