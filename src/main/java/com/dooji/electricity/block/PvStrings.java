package com.dooji.electricity.block;

import com.dooji.electricity.api.power.DcCableSpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which rows a collector is really wired to, copper and harnesses together.
  *
 * {@link DcNetwork} follows copper and stops at machines.
 */
public final class PvStrings {
	private PvStrings() {
	}

	/** Every row a run of this gauge wires from this machine */
	public static List<DcNetwork.Reach> reachable(Level level, BlockPos origin, DcCableSpec cable, int maxRuns) {
		Map<BlockPos, DcNetwork.Reach> best = new LinkedHashMap<>();
		Deque<DcNetwork.Reach> pending = new ArrayDeque<>();
		for (DcNetwork.Reach reach : DcNetwork.reachable(level, origin, cable, PvArrayBlock::row, maxRuns)) {
			if (PvArrayBlock.meets(level.getBlockState(reach.pos()), reach.from())) pending.add(reach);
		}

		while (!pending.isEmpty()) {
			DcNetwork.Reach at = pending.poll();
			DcNetwork.Reach had = best.get(at.pos());
			if (had != null && had.runs() <= at.runs()) continue;

			best.put(at.pos(), at);
			onward(level, at, maxRuns, pending);
		}

		List<DcNetwork.Reach> found = new ArrayList<>(best.values());
		found.sort(Comparator.comparingInt(DcNetwork.Reach::runs));
		return found;
	}

	/** The rows this one's own harness carries the connection on to. */
	private static void onward(Level level, DcNetwork.Reach at, int maxRuns, Deque<DcNetwork.Reach> pending) {
		if (at.runs() >= maxRuns) return;

		BlockState state = level.getBlockState(at.pos());
		for (Direction face : Direction.Plane.HORIZONTAL) {
			if (face == at.from() || !PvArrayBlock.gives(state, face)) continue;

			BlockPos next = at.pos().relative(face);
			if (!level.isLoaded(next) || !PvArrayBlock.meets(level.getBlockState(next), face.getOpposite())) continue;

			pending.add(new DcNetwork.Reach(next, at.runs() + 1, at.buried(), face.getOpposite()));
		}
	}
}
