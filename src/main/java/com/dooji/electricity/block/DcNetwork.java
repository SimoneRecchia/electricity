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

/** Follows the copper, and answers what a machine is actually wired to. */
public final class DcNetwork {
	/** Runs one walk will follow before it gives up, as a backstop rather than a design limit. */
	private static final int MAX_RUNS = 4096;

	/** One block of a run the walk still has to follow. */
	private record Step(BlockPos pos, int runs, int buried) {
	}

	/** A machine the walk got to, and the run it took to get there. */
	public record Reach(BlockPos pos, int runs, int buried, Direction from) {
		/** Length of the run in metres. */
		public double metres() {
			return runs * WorldConditions.METRES_PER_BLOCK;
		}

		/** How much of the run is in the ground */
		public double buriedFraction() {
			return runs <= 0 ? 0.0 : (double) buried / runs;
		}
	}

	private DcNetwork() {
	}

	/** Every machine of interest a run of one gauge reaches from a starting machine */
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

	/** Which face of the machine the run came up against. */
	private static Direction arrivedAt(BlockPos cable, BlockPos machine) {
		return Direction.fromDelta(cable.getX() - machine.getX(), 0, cable.getZ() - machine.getZ());
	}

	/** The runs of one gauge that reach a machine, which is where a walk out of it begins. */
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
