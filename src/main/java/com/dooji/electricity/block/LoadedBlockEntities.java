package com.dooji.electricity.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Reads a block entity somewhere else in the world without dragging its chunk into memory.
  *
 * {@link Level#getBlockEntity} loads whatever chunk the position lands in.
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
