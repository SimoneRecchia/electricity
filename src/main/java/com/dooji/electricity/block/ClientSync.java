package com.dooji.electricity.block;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Publishes a machine's state to the clients watching it, no more often than it is worth. */
final class ClientSync {
	private static final int INTERVAL_TICKS = 10;
	/** Nothing has been sent yet, which is a state and not a time. */
	private static final long NEVER = Long.MIN_VALUE;

	private long lastTick = NEVER;

	/** Sends now if the gate is open, and answers whether it went. */
	boolean throttled(BlockEntity blockEntity) {
		Level level = blockEntity.getLevel();
		if (level == null || level.isClientSide()) return false;

		long now = level.getGameTime();
		if (lastTick != NEVER && now - lastTick < INTERVAL_TICKS) return false;

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
