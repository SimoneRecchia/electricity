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
 * {@link DcNetwork} follows copper and stops at machines. That is right for a combiner or an inverter -
 * current arriving at one leaves through its fuses or not at all - and wrong for a row of modules,
 * because a string *is* rows chained one to the next. What chains them is the harness each row has
 * fitted, so this takes the rows the copper reached and carries on along the strings from there.
 *
 * <h2>What a harness is for</h2>
 *
 * A row with copper at its socket end is wired with nothing fitted. A reel of cable fits the row's
 * outgoing leads, and those reach the row behind - so a line of six tables off one cable takes five
 * reels, not six, and the answer to "do I have to put the cable in?" is: only if you want another row
 * after this one.
 *
 * Every row crossed costs its ten metres like any other ten metres of cable, so a long line pays the
 * same volt drop a long run pays. That is not a penalty invented here; it is the same conductor.
 *
 * <h2>Shortest run wins</h2>
 *
 * A row can be at the end of two chains at once, and the one that counts is the shorter, because that
 * is the pair with less resistance in it. So this relaxes rather than sweeps: a row found again down a
 * shorter way replaces itself and passes the shorter run on. Plants are small enough that this settles
 * in a handful of passes, and the run length is what the whole loss model is built on.
 */
public final class PvStrings {
	private PvStrings() {
	}

	/**
	 * Every row a run of this gauge wires from this machine, shortest run first.
	 *
	 * Same shape of answer as {@link DcNetwork#reachable}, and the callers cannot tell which of the two
	 * they are talking to - which is the point, since a combiner asks for arrays and a trunk asks for
	 * combiners and only the first of those is a string.
	 */
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
