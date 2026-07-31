package com.dooji.electricity.block;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * A tower coming down, over about a second and a half.
 *
 * Minecraft has no structural physics to lose, so a tower that has been built past what
 * it can hold cannot actually buckle. What it can do is fail visibly: the fracture that
 * would run down a real tower is drawn with the crack overlay a block wears while it is
 * being mined, starting at the top and rippling to the foot, and each block shatters
 * when its cracks finish. Ten blocks are cracking at any moment at different depths, so
 * what a player sees is a break travelling down the steel rather than a stack of blocks
 * winking out.
 *
 * Advanced from the server tick rather than by ticking the tower's block entities: a
 * collapse lasts a second and towers stand for days, so paying a tick per block for the
 * whole life of every tower to animate its last moment would be the wrong trade. Nothing
 * here is persisted - a collapse interrupted by a server stopping simply leaves the tower
 * standing, which is a better failure than half a tower saved mid-crack.
 */
public final class TowerCollapse {
	/** Crack stages a block shows before it breaks, the same ten a pickaxe draws. */
	private static final int STAGES = 10;
	/** Ticks each crack stage is held. One, so a block takes half a second to shatter. */
	private static final int TICKS_PER_STAGE = 1;
	/** Ticks between one block starting to crack and the next one down starting. */
	private static final int RIPPLE_TICKS = 1;
	/**
	 * Where the ids handed to {@code destroyBlockProgress} start.
	 *
	 * That call keys its overlay by breaker id, one position each, so every block cracking
	 * at once needs an id of its own. Entity ids count up from small numbers, so starting
	 * high keeps a collapse from erasing the cracks of a player mining nearby.
	 */
	private static final int BREAKER_ID_BASE = 0x40000000;

	private static final List<TowerCollapse> ACTIVE = new ArrayList<>();
	private static int nextBreakerId = BREAKER_ID_BASE;

	private final ServerLevel level;
	private final List<BlockPos> segments;
	private final int firstBreakerId;
	private int tick;

	private TowerCollapse(ServerLevel level, List<BlockPos> segments, int firstBreakerId) {
		this.level = level;
		this.segments = segments;
		this.firstBreakerId = firstBreakerId;
	}

	/**
	 * Starts a tower failing, from its foot upward.
	 *
	 * Ignored if that tower is already coming down, so standing on a collapsing tower or
	 * stacking onto one does not restart the animation or double the sound.
	 */
	public static void begin(ServerLevel level, BlockPos foot, int height) {
		if (height <= 0) return;

		for (TowerCollapse collapse : ACTIVE) {
			if (collapse.level == level && collapse.segments.contains(foot)) return;
		}

		List<BlockPos> segments = new ArrayList<>(height);
		for (int segment = 0; segment < height; segment++) {
			segments.add(foot.above(segment));
		}

		int firstId = nextBreakerId;
		// wrapping rather than growing without bound: by the time it comes round again every
		// id it reuses belongs to a collapse that finished long ago
		nextBreakerId += height;
		if (nextBreakerId > BREAKER_ID_BASE + 0x3F000000) nextBreakerId = BREAKER_ID_BASE;

		ACTIVE.add(new TowerCollapse(level, segments, firstId));
		// the groan of steel giving way, at the top where the failure starts
		BlockPos top = foot.above(height - 1);
		level.playSound(null, top, SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.BLOCKS, 1.2f, 0.6f);
	}

	/** Advances every collapse in progress. Call once per server tick. */
	public static void tickAll() {
		Iterator<TowerCollapse> collapses = ACTIVE.iterator();
		while (collapses.hasNext()) {
			if (collapses.next().advance()) collapses.remove();
		}
	}

	public static void clear() {
		ACTIVE.clear();
	}

	/** @return whether this collapse has finished. */
	private boolean advance() {
		tick++;
		boolean anyLeft = false;

		for (int index = 0; index < segments.size(); index++) {
			BlockPos pos = segments.get(index);
			if (!(level.getBlockState(pos).getBlock() instanceof TurbineTowerBlock)) continue;

			anyLeft = true;
			// counted from the top, so the fracture starts where the tower failed
			int fromTop = segments.size() - 1 - index;
			int elapsed = tick - fromTop * RIPPLE_TICKS;
			if (elapsed <= 0) continue;

			int stage = elapsed / TICKS_PER_STAGE;
			if (stage < STAGES) {
				level.destroyBlockProgress(firstBreakerId + index, pos, stage);
				continue;
			}

			// clearing the overlay before the block goes stops the cracks outliving it
			level.destroyBlockProgress(firstBreakerId + index, pos, -1);
			level.destroyBlock(pos, true);
		}

		if (anyLeft) return false;

		BlockPos foot = segments.get(0);
		level.playSound(null, foot, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.9f, 0.7f);
		return true;
	}
}
