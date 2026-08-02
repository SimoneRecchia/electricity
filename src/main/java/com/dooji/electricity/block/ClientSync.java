package com.dooji.electricity.block;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Publishes a machine's state to the clients watching it, no more often than it is worth.
 *
 * Two things, because they are two different questions with the same answer. A control that a player
 * has just changed goes out at once, or the panel they are looking at lies to them until the next
 * gate opens. A reading that drifts goes out on the gate, because a plant's numbers move every tick
 * and nothing on a screen updates faster than the eye.
 *
 * Half a second is the interval, which is five readings a second and about as fine as a panel is worth
 * reading. Three machines had a copy of this and the copies had already begun to differ - one of them
 * checked for a client level before sending and the others after.
 */
final class ClientSync {
	private static final int INTERVAL_TICKS = 10;

	private long lastTick = Long.MIN_VALUE;

	/** Sends now if the gate is open, and answers whether it went. */
	boolean throttled(BlockEntity blockEntity) {
		Level level = blockEntity.getLevel();
		if (level == null || level.isClientSide()) return false;

		long now = level.getGameTime();
		if (now - lastTick < INTERVAL_TICKS) return false;

		lastTick = now;
		now(blockEntity);
		return true;
	}

	/** Sends regardless: for a setting a player or a program has just changed. */
	static void now(BlockEntity blockEntity) {
		Level level = blockEntity.getLevel();
		if (level == null || level.isClientSide()) return;

		blockEntity.setChanged();
		level.sendBlockUpdated(blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity.getBlockState(),
				Block.UPDATE_CLIENTS);
	}
}
