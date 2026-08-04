package com.dooji.electricity.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Reads a block entity somewhere else in the world without dragging its chunk into memory.
 *
 * {@link Level#getBlockEntity} loads whatever chunk the position lands in. That is right for a
 * player's click and wrong for machines talking to each other, because a plant remembers where its
 * neighbours are and asks after them constantly - every tick while it runs, and again while it is
 * being taken apart.
 *
 * Asking wastefully is the mild half of it. The fatal half is that a world stops by draining its
 * chunks: it removes every ticket and then spins until no chunk has any work left. A chunk that loads
 * another one on its way out puts work back, so the drain never finishes, and the server sits in that
 * loop for ever having logged "Saving worlds" and nothing after it. The window closes, one core stays
 * pinned, and the process has to be killed by hand.
 *
 * So machines ask through here and take a null for an answer. An unloaded neighbour is not an error;
 * it is a part of the plant that is not there at the moment, which is what an unloaded chunk means.
 */
final class LoadedBlockEntities {
	private LoadedBlockEntities() {
	}

	/** The block entity at that position, or null if it is absent, the wrong sort, or not loaded. */
	@Nullable
	static <T extends BlockEntity> T find(@Nullable Level level, @Nullable BlockPos pos, Class<T> type) {
		if (level == null || pos == null || !level.isLoaded(pos)) return null;

		BlockEntity blockEntity = level.getBlockEntity(pos);
		return type.isInstance(blockEntity) ? type.cast(blockEntity) : null;
	}
}
